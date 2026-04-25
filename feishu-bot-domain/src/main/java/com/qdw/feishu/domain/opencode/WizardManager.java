package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.card.CardButton;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.card.CardElement;
import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.model.ImContextRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 3步入门向导状态机。
 *
 * 管理每个话题（topicId）的向导进度：SELECT_PROJECT → SELECT_SESSION → CONFIRM → COMPLETED。
 *
 * <p>存储策略：使用 ConcurrentHashMap + 时间戳 TTL 实现带过期的向导状态缓存。
 * 定时任务每分钟清理过期条目，避免内存泄漏。
 *
 * <p>并发安全：状态转换使用 {@link ConcurrentHashMap#compute} 保证原子性。
 */
@Slf4j
@Component
public class WizardManager {

    /** 向导步骤枚举 */
    public enum WizardStep {
        INACTIVE,         // 未在向导中
        SELECT_PROJECT,   // 步骤1：选择项目
        SELECT_SESSION,   // 步骤2：选择会话
        CONFIRM,          // 步骤3：确认绑定
        COMPLETED         // 已完成
    }

    /** 向导内部状态 */
    static class WizardState {
        final String chatId;
        final String topicId;
        volatile WizardStep step;
        volatile String selectedProject;
        volatile String selectedSessionId;
        final long createdAt;

        WizardState(String chatId, String topicId) {
            this.chatId = chatId;
            this.topicId = topicId;
            this.step = WizardStep.SELECT_PROJECT;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }

    /** 向导结果 */
    public static class WizardResult {
        private final CardContent cardContent;
        private final String textContent;
        private final WizardStep step;
        private final boolean completed;
        private final String openCodeSessionId;

        private WizardResult(CardContent cardContent, String textContent, WizardStep step,
                             boolean completed, String openCodeSessionId) {
            this.cardContent = cardContent;
            this.textContent = textContent;
            this.step = step;
            this.completed = completed;
            this.openCodeSessionId = openCodeSessionId;
        }

        public static WizardResult of(CardContent cardContent, WizardStep step) {
            return new WizardResult(cardContent, null, step, false, null);
        }

        public static WizardResult ofText(String text) {
            return new WizardResult(null, text, WizardStep.INACTIVE, false, null);
        }

        public static WizardResult completed(String openCodeSessionId) {
            CardContent successCard = CardContent.builder()
                .headerTitle("✅ 绑定成功！")
                .headerTemplate("green")
                .wideScreenMode(true)
                .addElement(CardElement.markdown("已成功绑定会话 `" + openCodeSessionId + "`\n\n"
                    + "💬 现在可以直接在话题中输入问题开始对话！"))
                .build();
            return new WizardResult(successCard, null, WizardStep.COMPLETED, true, openCodeSessionId);
        }

        public CardContent getCardContent() { return cardContent; }
        public String getTextContent() { return textContent; }
        public WizardStep getStep() { return step; }
        public boolean isCompleted() { return completed; }
        public String getOpenCodeSessionId() { return openCodeSessionId; }
    }

    // 10 分钟 TTL（默认）
    static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000L;

    private final Map<String, WizardState> activeWizards = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ScheduledExecutorService cleanupScheduler;

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeSessionManager sessionManager;
    private final CardRenderer cardRenderer;

    /** 生产环境构造器（默认 TTL = 10 分钟） */
    public WizardManager(OpenCodeGateway openCodeGateway,
                         OpenCodeSessionManager sessionManager,
                         CardRenderer cardRenderer) {
        this(openCodeGateway, sessionManager, cardRenderer, DEFAULT_TTL_MILLIS);
    }

    /** 测试友好构造器（可自定义 TTL） */
    public WizardManager(OpenCodeGateway openCodeGateway,
                         OpenCodeSessionManager sessionManager,
                         CardRenderer cardRenderer,
                         long ttlMillis) {
        this.openCodeGateway = openCodeGateway;
        this.sessionManager = sessionManager;
        this.cardRenderer = cardRenderer;
        this.ttlMillis = ttlMillis;
        // 定时清理过期向导（每分钟一次）
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wizard-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredWizards, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * 启动向导，生成步骤1卡片（项目列表）。
     */
    public WizardResult start(String chatId, String topicId) {
        WizardState state = new WizardState(chatId, topicId);
        activeWizards.put(topicId, state);
        log.info("向导已启动: topicId={}", topicId);

        String projectsText = openCodeGateway.listProjects();
        List<CardButton> buttons = parseProjectsToButtons(projectsText);

        if (buttons.isEmpty()) {
            buttons.add(CardButton.primary("feishu-backend", "wizard_select_project:feishu-backend"));
        }

        CardContent card = CardContent.builder()
            .headerTitle("🎯 欢迎使用 OpenCode！第 1 步：选择项目")
            .headerTemplate("blue")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("请选择一个项目开始："))
            .addElement(CardElement.buttonGroup(buttons))
            .addElement(CardElement.buttonGroup(CardButton.defaults("❌ 取消", "wizard_cancel")))
            .build();

        return WizardResult.of(card, WizardStep.SELECT_PROJECT);
    }

    /**
     * 处理向导中的卡片按钮点击（原子性状态转换）。
     *
     * @return WizardResult，或 null 如果不是向导相关的 action
     */
    public WizardResult handleAction(String action, String chatId, String topicId) {
        WizardState state = activeWizards.get(topicId);
        if (state == null || state.isExpired(ttlMillis)) {
            activeWizards.remove(topicId);
            return null;
        }

        if (action.startsWith("wizard_select_project:")) {
            String project = action.substring("wizard_select_project:".length());
            return handleSelectProject(state, project);
        } else if (action.startsWith("wizard_select_session:")) {
            String sessionId = action.substring("wizard_select_session:".length());
            return handleSelectSession(state, sessionId);
        } else if (action.equals("wizard_confirm")) {
            return handleConfirm(state);
        } else if (action.equals("wizard_cancel")) {
            activeWizards.remove(topicId);
            log.info("向导已取消: topicId={}", topicId);
            return WizardResult.ofText("已取消向导。使用 `/oc p` 查看项目列表，或 `/oc sc <session_id>` 直接绑定会话。");
        }
        return null;
    }

    /** 向导是否活跃（非 COMPLETED 且未过期） */
    public boolean isWizardActive(String topicId) {
        WizardState state = activeWizards.get(topicId);
        if (state == null) return false;
        if (state.isExpired(ttlMillis)) {
            activeWizards.remove(topicId);
            return false;
        }
        return state.step != WizardStep.COMPLETED;
    }

    /** 手动清除向导 */
    public void clearWizard(String topicId) {
        activeWizards.remove(topicId);
        log.info("向导已清除: topicId={}", topicId);
    }

    // ============ 内部步骤处理 ============

    private WizardResult handleSelectProject(WizardState state, String project) {
        state.selectedProject = project;
        state.step = WizardStep.SELECT_SESSION;
        log.info("向导步骤1完成，选择项目: topicId={}, project={}", state.topicId, project);

        String sessionsText = openCodeGateway.listRecentSessions(project, 10);
        List<CardButton> buttons = parseSessionsToButtons(sessionsText, project);

        CardContent card = CardContent.builder()
            .headerTitle("📋 第 2 步：选择会话")
            .headerTemplate("blue")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("项目 **" + project + "** 的最近会话："))
            .addElement(CardElement.buttonGroup(buttons))
            .addElement(CardElement.buttonGroup(
                CardButton.primary("+ 新建会话", "wizard_new_session:" + project),
                CardButton.defaults("❌ 取消", "wizard_cancel")
            ))
            .build();

        return WizardResult.of(card, WizardStep.SELECT_SESSION);
    }

    private WizardResult handleSelectSession(WizardState state, String sessionId) {
        state.selectedSessionId = sessionId;
        state.step = WizardStep.CONFIRM;
        log.info("向导步骤2完成，选择会话: topicId={}, sessionId={}", state.topicId, sessionId);

        CardContent card = CardContent.builder()
            .headerTitle("✅ 第 3 步：确认绑定")
            .headerTemplate("green")
            .wideScreenMode(true)
            .addElement(CardElement.markdown(
                "确认将以下会话绑定到当前话题？\n\n"
                + "📁 **项目**: " + state.selectedProject + "\n"
                + "🆔 **会话**: `" + sessionId + "`\n\n"
                + "绑定后，在此话题中输入任何文字都会发送给 OpenCode。"
            ))
            .addElement(CardElement.buttonGroup(
                CardButton.primary("✅ 确认绑定", "wizard_confirm"),
                CardButton.defaults("❌ 取消", "wizard_cancel")
            ))
            .build();

        return WizardResult.of(card, WizardStep.CONFIRM);
    }

    private WizardResult handleConfirm(WizardState state) {
        String sessionId = state.selectedSessionId;
        log.info("向导步骤3完成，执行绑定: topicId={}, sessionId={}", state.topicId, sessionId);

        try {
            // 构造 ImContextRef 并绑定会话（Feishu 话题 = thread context）
            ImContextRef contextRef = ImContextRef.feishuThread(state.topicId);
            sessionManager.saveSession(contextRef, sessionId);

            state.step = WizardStep.COMPLETED;
            activeWizards.remove(state.topicId);
            log.info("向导完成，会话已绑定: topicId={}, sessionId={}", state.topicId, sessionId);
            return WizardResult.completed(sessionId);
        } catch (Exception e) {
            log.error("向导绑定失败: topicId={}", state.topicId, e);
            return WizardResult.ofText("❌ 绑定失败：" + e.getMessage() + "\n\n请重试或联系管理员。");
        }
    }

    // ============ 工具方法 ============

    /**
     * 从项目列表文本中解析项目名称并生成按钮列表。
     * 文本格式：每个项目以 "**项目名**" 表示。
     */
    private List<CardButton> parseProjectsToButtons(String projectsText) {
        List<CardButton> buttons = new ArrayList<>();
        if (projectsText == null || projectsText.isEmpty()) return buttons;

        // 解析格式：数字. **项目名**
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\*\\*([^*]+)\\*\\*");
        java.util.regex.Matcher matcher = pattern.matcher(projectsText);
        while (matcher.find()) {
            String projectName = matcher.group(1).trim();
            if (!projectName.isEmpty() && !projectName.equals("OpenCode 项目列表")) {
                buttons.add(CardButton.primary(projectName, "wizard_select_project:" + projectName));
            }
        }
        return buttons;
    }

    /**
     * 从会话列表文本中解析会话 ID 并生成按钮列表。
     * 文本格式：每个会话包含 "ID: `ses_xxx`"。
     */
    private List<CardButton> parseSessionsToButtons(String sessionsText, String project) {
        List<CardButton> buttons = new ArrayList<>();
        if (sessionsText == null || sessionsText.isEmpty()) return buttons;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?m)^\\s*(\\d+)\\. (.+?)\\s*\\n\\s*ID: `(ses_[^`]+)`");
        java.util.regex.Matcher matcher = pattern.matcher(sessionsText);
        while (matcher.find()) {
            String title = matcher.group(2).trim();
            String sessionId = matcher.group(3).trim();
            String label = title.length() > 30 ? title.substring(0, 27) + "..." : title;
            buttons.add(CardButton.defaults(label, "wizard_select_session:" + sessionId));
        }
        return buttons;
    }

    /** 定时清理过期向导条目，防止内存泄漏 */
    private void cleanupExpiredWizards() {
        activeWizards.entrySet().removeIf(entry -> entry.getValue().isExpired(ttlMillis));
    }
}

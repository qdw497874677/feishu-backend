package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * OpenCode 任务执行器
 *
 * 所有对话统一走异步处理：
 * - 开始：添加 HEART 表情（BotMessageService 的 THUMBSUP 会在消息处理时添加）
 * - 完成：添加 THUMBSUP 表情
 */
@Slf4j
@Component
public class OpenCodeTaskExecutor {

    private static final String EMOJI_START = "HEART";
    private static final String EMOJI_DONE = "CLAP";
    private static final int EXECUTE_TIMEOUT = 120;

    private final OpenCodeGateway openCodeGateway;
    private final FeishuGateway feishuGateway;
    private final OpenCodeResponseFormatter responseFormatter;
    private final OpenCodeSessionManager sessionManager;

    public OpenCodeTaskExecutor(OpenCodeGateway openCodeGateway,
                                 FeishuGateway feishuGateway,
                                 OpenCodeResponseFormatter responseFormatter,
                                 OpenCodeSessionManager sessionManager) {
        this.openCodeGateway = openCodeGateway;
        this.feishuGateway = feishuGateway;
        this.responseFormatter = responseFormatter;
        this.sessionManager = sessionManager;
    }

    public String executeWithAutoSession(Message message, String prompt) {
        String topicId = message.getTopicId();
        log.info("自动选择会话执行: topicId={}, prompt='{}'", topicId, prompt);

        if (topicId == null || topicId.isEmpty()) {
            log.info("不在话题中，使用临时会话执行");
            return executeTask(message, prompt, null);
        }

        return sessionManager.getSessionId(topicId)
            .map(sessionId -> {
                log.info("找到活跃会话，继续使用: sessionId={}", sessionId);
                return executeTask(message, prompt, sessionId);
            })
            .orElseGet(() -> {
                log.info("话题无活跃会话，创建新会话: topicId={}", topicId);
                return executeWithNewSession(message, prompt);
            });
    }

    public String executeWithNewSession(Message message, String prompt) {
        return executeWithNewSession(message, prompt, null);
    }

    public String executeWithNewSession(Message message, String prompt, String project) {
        String topicId = message.getTopicId();
        sessionManager.clearSession(topicId);
        String enhancedPrompt = enhancePromptWithWorkDirectory(prompt, project);
        return executeTask(message, enhancedPrompt, null);
    }

    private String enhancePromptWithWorkDirectory(String prompt, String project) {
        if (project == null || project.isEmpty()) {
            String projectRoot = getProjectRoot();
            return String.format("[工作目录: %s]\n\n%s", projectRoot, prompt);
        }
        return String.format("[工作目录: /root/workspace/%s]\n\n%s", project, prompt);
    }

    private String getProjectRoot() {
        return System.getProperty("user.dir", "/root/workspace/feishu-backend");
    }

    public String executeWithSpecificSession(Message message, String prompt, String sessionId) {
        log.info("使用指定会话执行: sessionId={}", sessionId);
        String topicId = message.getTopicId();
        sessionManager.saveSession(topicId, sessionId);

        if (prompt == null || prompt.isEmpty()) {
            return buildInitializationSuccessResponse(topicId, sessionId);
        }
        return executeTask(message, prompt, sessionId);
    }

    public String createSessionOnly(Message message) throws Exception {
        String topicId = message.getTopicId();
        log.info("创建新会话（不执行任务）: topicId={}", topicId);

        String workDir = getProjectRoot();
        log.info("使用默认工作目录创建会话: {}", workDir);

        String sessionId = openCodeGateway.createSession(workDir);

        if (sessionId != null && !sessionId.isEmpty()) {
            log.info("会话创建成功: sessionId={}, 工作目录={}", sessionId, workDir);
            String response = buildSessionCreatedResponse(topicId, sessionId, workDir);

            if (topicId != null && !topicId.isEmpty()) {
                sessionManager.saveSession(topicId, sessionId);
            } else {
                log.info("topicId 为空，将在飞书返回 threadId 后保存会话");
                response += "\n\n⚠️ **提示**：会话已创建，将在话题创建后自动绑定";
            }
            return response;
        } else {
            log.warn("会话创建失败");
            return "❌ 会话创建失败，请稍后重试";
        }
    }

    private String buildSessionCreatedResponse(String topicId, String sessionId, String workDir) {
        StringBuilder response = new StringBuilder();
        response.append("✅ **会话已创建并绑定到话题**\n\n");
        response.append("📋 **会话信息**\n");
        response.append("  🆔 Session ID: `").append(sessionId).append("`\n");
        if (topicId != null && !topicId.isEmpty()) {
            response.append("  💬 话题 ID: `").append(topicId).append("`\n");
        }
        response.append("  ✅ 状态: 已绑定\n\n");
        response.append("💡 **开始对话**\n");
        response.append("  在当前话题中发送：\n");
        response.append("  `chat <你的问题>`\n");
        response.append("  或直接输入问题\n\n");
        response.append("📁 **工作目录**\n");
        response.append("  `").append(workDir).append("`\n\n");
        return response.toString();
    }

    public String executeTask(Message message, String prompt, String sessionId) {
        String messageId = message.getMessageId();
        log.info("提交异步任务: sessionId={}, prompt='{}'", sessionId, prompt);
        
        boolean reactionAdded = feishuGateway.addReaction(messageId, EMOJI_START);
        if (!reactionAdded) {
            log.debug("表情添加失败，但不影响主流程");
        }
        executeAsync(message, prompt, sessionId);
        return "";
    }

    @Async("opencodeExecutor")
    public void executeAsync(Message message, String prompt, String sessionId) {
        String messageId = message.getMessageId();
        log.info("异步执行开始: messageId={}, sessionId={}", messageId, sessionId);

        try {
            String result = openCodeGateway.executeCommand(prompt, sessionId, EXECUTE_TIMEOUT);

            if (result == null) {
                log.warn("异步执行超时（{}秒）", EXECUTE_TIMEOUT);
                feishuGateway.sendMessage(message, 
                    "⚠️ 任务执行超时，请稍后重试或尝试简化问题。", 
                    message.getTopicId());
                return;
            }

            boolean reactionAdded = feishuGateway.addReaction(messageId, EMOJI_DONE);
            if (!reactionAdded) {
                log.debug("完成表情添加失败，但不影响主流程");
            }
            log.info("异步完成，添加表情: {}", EMOJI_DONE);

            String extractedSessionId = responseFormatter.extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionManager.saveSession(message.getTopicId(), extractedSessionId);
            }

            String formatted = responseFormatter.format(result, extractedSessionId);
            feishuGateway.sendMessage(message, formatted, message.getTopicId());

        } catch (Exception e) {
            log.error("异步执行失败", e);
            feishuGateway.sendMessage(message, "❌ 执行失败: " + e.getMessage(), message.getTopicId());
        }
    }

    private String buildInitializationSuccessResponse(String topicId, String sessionId) {
        StringBuilder response = new StringBuilder();
        response.append("✅ **话题已初始化成功！**\n\n");
        response.append("📋 会话信息\n");
        response.append("  🆔 Session ID: `").append(sessionId).append("`\n");
        if (topicId != null && !topicId.isEmpty()) {
            response.append("  💬 话题 ID: `").append(topicId).append("`\n");
        }
        response.append("  ✅ 状态: 已绑定\n\n");
        response.append("**💡 现在可以开始对话了！**\n\n");
        response.append("发送命令：\n");
        response.append("  `/opencode chat <你的问题>` - 发送对话\n");
        response.append("  或直接输入问题（无需前缀）\n\n");
        response.append("示例：\n");
        response.append("  `/opencode chat 帮我重构这个函数`\n");
        return response.toString();
    }
}

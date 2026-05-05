package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.card.CardButton;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.card.CardElement;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.ProjectInfo;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.session.ContextSessionState;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * OpenCode 应用 - 支持多轮对话
 *
 * 主应用协调者，负责入口和元数据，具体逻辑委托给专用组件
 */
@Slf4j
public class OpenCodeApp implements FishuAppI {

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeCommandHandler commandHandler;
    private final OpenCodeSessionManager sessionManager;
    private final FeishuGateway feishuGateway;
    private final CardRenderer cardRenderer;

    public OpenCodeApp(OpenCodeGateway openCodeGateway,
                       OpenCodeCommandHandler commandHandler,
                       OpenCodeSessionManager sessionManager,
                       FeishuGateway feishuGateway,
                       CardRenderer cardRenderer) {
        this.openCodeGateway = openCodeGateway;
        this.commandHandler = commandHandler;
        this.sessionManager = sessionManager;
        this.feishuGateway = feishuGateway;
        this.cardRenderer = cardRenderer;
    }

    @Override
    public String getAppId() {
        return "opencode";
    }

    @Override
    public String getAppName() {
        return "OpenCode 助手";
    }

    @Override
    public String getDescription() {
        return "通过飞书对话控制 OpenCode，支持多轮对话";
    }

    @Override
    public String getHelp() {
        return """
            🤖 **OpenCode 助手** - AI代码助手，支持多轮对话

            🚀 **快速开始**（任选一种）

              ⚡️ **方式1：立即对话**（推荐，最简单）
                `/opencode chatnow 帮我写代码`
                或 `/oc cn 帮我写代码`
                系统会自动创建会话并绑定到话题

              📋 **方式2：话题内继续对话**（已绑定话题）
                `/opencode chat 继续优化`
                或直接输入问题（无需命令前缀）

              🔧 **方式3：选择现有会话**（高级，精细控制）
                1. `/opencode sessions feishu-backend`
                2. `/opencode sc <会话ID>`
                3. `/opencode chat <问题>`

            📝 **对话命令**
              `/opencode chatnow <内容>`       - 立即对话（自动创建并绑定会话，推荐）
              `/opencode chat <内容>`         - 继续对话（话题内）
              `/opencode new <内容>`          - 在当前项目创建新会话（话题已绑定）
              `/opencode new <项目> <内容>`    - 在指定项目创建新会话（所有场景）

            📁 **项目管理**
              `/opencode projects`           - 查看项目列表
              `/opencode sessions <项目名>`   - 查看项目的最近会话

            🔧 **会话管理**
              `/opencode status`             - 快速查看当前绑定状态
              `/opencode session status`     - 查看当前会话信息
              `/opencode session list`       - 查看所有会话
              `/opencode sc <会话ID>`        - 绑定会话到话题（简写）
              `/opencode reset`              - 重置话题（允许重新绑定）

            ⚡️ **其他命令**
              `/opencode commands`           - 查看所有可用斜杠命令

            💡 **使用场景**

              **话题外**（无 topicId）：
              ✅ 允许：`/opencode chatnow`, `/opencode help`, `/opencode projects`
              ❌ 禁止：`/opencode chat`, `/opencode session`, `/opencode sc`

              **话题未初始化**（有 topicId 但未绑定会话）：
              ✅ 允许：`/opencode chatnow`, `/opencode sc`, `/opencode sessions`, `/opencode reset`
              ❌ 禁止：`/opencode chat`（需先绑定会话）

              **话题已初始化**（已绑定会话）：
              ✅ 允许：所有命令

            💡 **使用示例**

              立即对话（自动绑定）：
              `/oc cn 帮我写个排序函数`
              → 创建会话 → 绑定话题 → 返回会话信息 + 对话结果

              话题内继续对话：
              `/opencode chat 添加单元测试`
              或直接：`添加单元测试`

              指定项目：
              `/opencode new feishu-backend 重构登录模块`
              → 在 /root/workspace/feishu-backend/ 创建会话

              话题已绑定，在当前项目创建新会话：
              `/opencode new 优化算法`
              → 在当前项目创建新会话并更换话题绑定

            💡 **提示**

              - `chatnow` 命令用于立即对话，自动创建并绑定会话到话题
              - `chat` 命令用于话题内继续对话
              - 默认路径：项目启动目录/workspace/{YYYY-MM-DD}/
              - 在已绑定的话题中可直接输入问题（无需前缀）
            """;
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("oc", "code");
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;
    }

    @Override
    public CommandWhitelist getCommandWhitelist(ContextSessionState state) {
        return switch (state) {
            case UNBOUND -> CommandWhitelist.builder()
                // 话题外允许的命令：基础命令 + 初始化命令 + 快速对话
                .add("help", "connect", "projects", "p",           // 基础命令
                     "sessions", "s", "session", "sc",            // 会话管理（初始化命令）
                     "chatnow", "cn", "new")                       // 快速对话 + 指定项目创建
                .build();
            case IN_APP_NO_SESSION -> CommandWhitelist.builder()
                // 话题未初始化：允许初始化相关命令 + 向导 action
                .add("help", "connect", "projects", "p",           // 基础命令
                     "sessions", "s", "session", "sc",            // 会话管理
                     "reset", "commands", "chatnow", "cn",        // 重置、命令列表、快速对话
                     "new", "status",                              // 新会话创建 + 快速状态查看
                     // 向导 action（卡片按钮点击时传入，以 wizard_ 开头）
                     "wizard_select_project", "wizard_select_session",
                     "wizard_new_session", "wizard_confirm", "wizard_cancel")
                .build();
            case IN_APP_WITH_SESSION -> CommandWhitelist.all();  // 话题已初始化：允许所有命令
            default -> CommandWhitelist.all();  // BOUND_TO_OTHER_APP 等状态不限制
        };
    }

    @Override
    public boolean isTopicInitialized(Message message) {
        return sessionManager.isTopicInitialized(message);
    }

    /**
     * Execute with basic message context.
     */
    @Override
    public AppExecutionResult execute(Message message) {
        return execute(message, MessageContext.unresolved());
    }

    @Override
    public AppExecutionResult execute(Message message, MessageContext messageContext) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 3);

        log.info("OpenCodeApp.execute: msgId={}", message.getMessageId());
        log.debug("OpenCodeApp.execute: content='{}'", content);

        // 空命令（只有 /opencode），进入话题模式，显示欢迎消息
        if (parts.length < 2) {
            String topicId = message.getTopicId();
            if (topicId == null || topicId.isEmpty()) {
                // 话题外，返回简短引导
                return AppExecutionResult.text("""
                    🤖 **OpenCode 助手**
                    
                    已进入话题模式！
                    
                    📋 **常用命令**：
                    • `projects` - 查看项目列表
                    • `sessions <项目名>` - 查看会话
                    • `chatnow <内容>` - 立即开始对话
                    • `help` - 查看完整帮助
                    
                    💡 直接输入命令即可（无需 /opencode 前缀）
                    """);
            }
            // 话题内，显示状态和引导 — use MessageContext overload
            return AppExecutionResult.text(sessionManager.getCurrentSessionStatus(messageContext));
        }

        String subCommand = parts[1].toLowerCase();

        // help 命令直接返回帮助信息
        if (subCommand.equals("help")) {
            return AppExecutionResult.text(getHelp());
        }

        // projects 命令：尝试发送卡片，降级为文本
        if (subCommand.equals("projects") || subCommand.equals("p")) {
            return trySendProjectsCard(message);
        }

        // 委托给命令处理器（传递白名单确保一致性）— use MessageContext overloads
        ContextSessionState state = sessionManager.detectTopicState(messageContext);
        CommandWhitelist whitelist = getCommandWhitelist(state);
        AppExecutionResult result = commandHandler.handle(message, subCommand, parts, whitelist, messageContext);
        if (result != null) {
            return result;
        }

        // 如果处理器返回 null，说明是需要进一步处理的情况
        log.warn("命令处理器返回 null: subCommand={}", subCommand);
        return AppExecutionResult.text(getHelp());
    }

    /**
     * 尝试发送项目列表卡片。失败时降级为纯文本。
     */
    private AppExecutionResult trySendProjectsCard(Message message) {
        try {
            List<ProjectInfo> projects = openCodeGateway.listProjectsStructured();
            if (projects.isEmpty()) {
                return AppExecutionResult.text("📁 暂无项目记录");
            }

            CardActionContext ctx = CardActionContext.builder()
                    .chatId(message.getChatId())
                    .topicId(message.getTopicId())
                    .build();
            String cardJson = buildProjectsCardJson(projects, ctx);
            feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
            return AppExecutionResult.text(null);  // 卡片已发送，跳过文本回复
        } catch (Exception e) {
            log.warn("项目卡片发送失败，降级为文本: {}", e.getMessage());
            return AppExecutionResult.text(openCodeGateway.listProjects());
        }
    }

    /**
     * 构建项目列表卡片 JSON。
     * 每个项目一个按钮，点击触发 `/opencode sessions <项目名>`。
     */
    private String buildProjectsCardJson(List<ProjectInfo> projects, CardActionContext context) {
        List<CardButton> buttons = projects.stream()
                .map(p -> CardButton.builder()
                        .label("📁 " + p.getName())
                        .action("opencode sessions " + p.getName())
                        .style("default")
                        .build())
                .collect(java.util.stream.Collectors.toList());

        CardContent card = CardContent.builder()
                .headerTitle("📁 OpenCode 项目列表")
                .headerTemplate("turquoise")
                .wideScreenMode(true)
                .addElement(CardElement.markdown(
                        "共 " + projects.size() + " 个项目，点击查看会话列表"))
                .addElement(CardElement.buttonGroup(buttons))
                .build();

        return cardRenderer.render(card, context);
    }
}

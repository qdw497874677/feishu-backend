package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.topic.TopicState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * OpenCode 命令处理器
 *
 * 负责解析和路由命令到对应的处理逻辑
 */
@Slf4j
@Component
public class OpenCodeCommandHandler {

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeTaskExecutor taskExecutor;
    private final OpenCodeSessionManager sessionManager;
    private final TopicCommandValidator commandValidator;

    public OpenCodeCommandHandler(OpenCodeGateway openCodeGateway,
                                   OpenCodeTaskExecutor taskExecutor,
                                   OpenCodeSessionManager sessionManager,
                                   TopicCommandValidator commandValidator) {
        this.openCodeGateway = openCodeGateway;
        this.taskExecutor = taskExecutor;
        this.sessionManager = sessionManager;
        this.commandValidator = commandValidator;
    }

    /**
     * 处理命令
     *
     * @param message 消息对象
     * @param subCommand 子命令
     * @param parts 解析后的命令部分
     * @return 命令响应
     */
    public String handle(Message message, String subCommand, String[] parts) {
        log.info("准备验证命令: subCommand={}", subCommand);

        TopicState state = detectTopicState(message);
        log.info("话题状态: {}, subCommand={}", state.getDescription(), subCommand);

        if (state == TopicState.UNINITIALIZED && !isInitializationCommand(subCommand) && !subCommand.equals("chat")) {
            log.info("话题未初始化，显示初始化引导");
            return buildInitializationGuide();
        }

        if (state == TopicState.NON_TOPIC && !subCommand.equals("connect")
            && !subCommand.equals("help") && !subCommand.equals("projects")
            && !subCommand.equals("reset")) {
            log.info("非话题环境且不是允许的命令，显示连接引导");
            return buildConnectGuide();
        }

        if (subCommand.equals("chat") && shouldRequireInitialization(message)) {
            log.info("chat 命令需要先完成初始化");
            return buildInitializationRequiredMessage();
        }

        // 验证命令是否允许
        CommandWhitelist whitelist = getCommandWhitelist(state);
        if (whitelist != null) {
            ValidationResult result = commandValidator.validateCommand(subCommand, state, whitelist);
            if (!result.isAllowed()) {
                log.info("命令受限: command={}, state={}", subCommand, state);
                return result.getMessage();
            }
        }

        // 路由到具体处理逻辑
        return switch (subCommand) {
            case "help" -> null;
            case "connect" -> handleConnect();
            case "new" -> handleNewCommand(parts, message);
            case "chat" -> handleChatCommand(parts, message);
            case "sessions", "s" -> sessionManager.handleSessionsCommand(parts);
            case "session", "sc" -> handleSessionCommand(parts, message);
            case "projects", "p" -> openCodeGateway.listProjects();
            case "commands" -> openCodeGateway.listCommands();
            case "reset" -> handleResetCommand(message);
            default -> handleUnknownCommand(message, subCommand, parts);
        };
    }

    private TopicState detectTopicState(Message message) {
        String topicId = message.getTopicId();

        if (topicId == null || topicId.isEmpty()) {
            return TopicState.NON_TOPIC;
        }

        boolean hasSession = sessionManager.getSessionId(topicId).isPresent();
        return hasSession ? TopicState.INITIALIZED : TopicState.UNINITIALIZED;
    }

    private boolean hasActiveSession(Message message) {
        String topicId = message.getTopicId();
        if (topicId == null || topicId.isEmpty()) {
            return false;
        }
        return sessionManager.getSessionId(topicId).isPresent();
    }

    private boolean shouldRequireInitialization(Message message) {
        String topicId = message.getTopicId();
        if (topicId == null || topicId.isEmpty()) {
            return true;
        }
        return !sessionManager.isExplicitlyInitialized(topicId);
    }

    private void clearStaleSessionData(Message message) {
        String topicId = message.getTopicId();
        if (topicId != null && !topicId.isEmpty()) {
            sessionManager.clearSession(topicId);
            log.info("已清除话题的陈旧会话数据: topicId={}", topicId);
        }
    }

    private boolean isInitializationCommand(String subCommand) {
        return subCommand.equals("help")
            || subCommand.equals("connect")
            || subCommand.equals("projects")
            || subCommand.equals("p")
            || subCommand.equals("sessions")
            || subCommand.equals("s")
            || subCommand.equals("session")
            || subCommand.equals("sc")
            || subCommand.equals("reset");
    }

    private String buildInitializationGuide() {
        StringBuilder response = new StringBuilder();

        response.append("🎯 **欢迎来到 OpenCode 助手！**\n\n");
        response.append("📋 **开始使用前，需要完成以下初始化步骤：**\n\n");

        response.append("**第 1 步：查看可用项目**\n");
        response.append("  `/opencode p` （或 `/opencode projects`）\n\n");

        response.append("**第 2 步：查看项目的最近会话**\n");
        response.append("  `/opencode s <项目名称>` （或 `/opencode sessions`）\n");
        response.append("  示例：`/opencode s feishu-backend`\n\n");

        response.append("**第 3 步：选择会话并绑定到话题**\n");
        response.append("  `/opencode sc <会话ID>` （或 `session continue <id>`）\n\n");

        response.append("✅ **完成！** 初始化后可以：\n");
        response.append("  • 使用 `/opencode chat <问题>` 开始对话\n");
        response.append("  • 直接输入问题（无需命令前缀）\n\n");

        response.append("**💡 简化别名：**\n");
        response.append("  `p` → projects，`s` → sessions，`sc` → session continue\n\n");

        response.append("**🔄 其他命令：**\n");
        response.append("  `/opencode reset` - 重置话题（允许重新绑定会话）\n");
        response.append("  `/opencode help` - 查看完整帮助\n");
        response.append("  `/opencode commands` - 查看所有可用命令\n");

        return response.toString();
    }

    private String buildConnectGuide() {
        StringBuilder response = new StringBuilder();

        response.append("🔗 **OpenCode 连接引导**\n\n");
        response.append("**请先连接到 OpenCode 服务：**\n\n");
        response.append("  `/opencode connect`\n\n");
        response.append("连接成功后，可以：\n");
        response.append("  • 查看项目列表\n");
        response.append("  • 创建会话并开始对话\n");

        return response.toString();
    }

    /**
     * 获取命令白名单
     */
    private CommandWhitelist getCommandWhitelist(TopicState state) {
        return switch (state) {
            case NON_TOPIC -> CommandWhitelist.builder()
                .add("connect", "help", "projects")
                .build();
            case UNINITIALIZED -> CommandWhitelist.allExcept("chat", "new");
            case INITIALIZED -> CommandWhitelist.all();
        };
    }

    /**
     * 处理 connect 命令
     */
    private String handleConnect() {
        StringBuilder response = new StringBuilder();

        response.append("🔗 **OpenCode 连接成功**\n\n");

        // 获取健康信息
        try {
            String status = openCodeGateway.getServerStatus();
            response.append("**服务状态**\n").append(status).append("\n\n");
        } catch (Exception e) {
            response.append("**服务状态**\n❌ 无法获取 (").append(e.getMessage()).append(")\n\n");
        }

        // 获取项目列表
        response.append("**📁 可用项目**\n\n");
        try {
            String projects = openCodeGateway.listProjects();
            response.append(projects).append("\n");
        } catch (Exception e) {
            response.append("❌ 无法获取项目列表 (").append(e.getMessage()).append(")\n\n");
        }

        // 引导用户查询项目 session
        response.append("**💡 下一步操作**\n\n");
        response.append("1️⃣ 查看项目的最近会话：\n");
        response.append("   `/opencode sessions <项目名称>`\n");
        response.append("   示例：`/opencode sessions feishu-backend`\n\n");
        response.append("2️⃣ 选择会话并绑定：\n");
        response.append("   `/opencode session continue <会话ID>`\n\n");
        response.append("3️⃣ 开始对话：\n");
        response.append("   `/opencode chat <你的问题>`\n");
        response.append("   或直接输入（在已初始化的话题中）\n\n");

        response.append("**📝 其他命令**\n");
        response.append(" `/opencode help` - 查看完整帮助\n");
        response.append(" `/opencode commands` - 查看所有斜杠命令\n");

        return response.toString();
    }

    /**
     * 处理 new 命令
     */
    private String handleNewCommand(String[] parts, Message message) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode new <提示词>`\n\n" +
                   "示例：`/opencode new 重构登录模块`";
        }
        String prompt = parts[2].trim();
        return taskExecutor.executeWithNewSession(message, prompt);
    }

    /**
     * 处理 chat 命令
     */
    private String handleChatCommand(String[] parts, Message message) {
        String topicId = message.getTopicId();
        boolean inTopic = topicId != null && !topicId.isEmpty();

        if (parts.length < 3) {
            if (inTopic) {
                return sessionManager.getSessionId(topicId)
                    .map(sessionId -> buildChatStatusWithSession(topicId, sessionId))
                    .orElse(buildInitializationRequiredMessage());
            }
            return "❌ 用法：`/opencode chat <对话内容>`\n\n" +
                   "示例：`/opencode chat 帮我写一个排序函数`\n\n" +
                   "💡 提示：在已绑定的话题中，也可以直接输入内容（无前缀）";
        }

        if (inTopic && !sessionManager.isExplicitlyInitialized(topicId)) {
            log.warn("话题未显式初始化，要求先执行初始化流程: topicId={}", topicId);
            return buildInitializationRequiredMessage();
        }

        String prompt = extractChatContent(parts, message);
        return taskExecutor.executeWithAutoSession(message, prompt);
    }

    private String buildInitializationRequiredMessage() {
        return "❌ **话题未初始化**\n\n" +
               "请先完成以下初始化步骤：\n\n" +
               "**第 1 步：查看可用项目**\n" +
               "  `/opencode projects`\n\n" +
               "**第 2 步：查看项目的最近会话**\n" +
               "  `/opencode sessions <项目名称>`\n" +
               "  示例：`/opencode sessions feishu-backend`\n\n" +
               "**第 3 步：选择会话并绑定到话题**\n" +
               "  `/opencode session continue <会话ID>`\n\n" +
               "✅ **完成后即可使用 chat 命令**\n\n" +
               "💡 使用方式：\n" +
               "  `/opencode chat <你的问题>`\n" +
               "  或直接输入问题（无需命令前缀）";
    }

    private String buildChatStatusWithSession(String topicId, String sessionId) {
        StringBuilder response = new StringBuilder();

        response.append("💬 **当前会话信息**\n\n");
        response.append("  🆔 Session ID: `").append(sessionId).append("`\n");
        response.append("  💬 话题 ID: `").append(topicId).append("`\n");
        response.append("  ✅ 状态: 已绑定\n\n");

        response.append("**💡 使用方式：**\n");
        response.append("  `/opencode chat <你的问题>` - 发送对话\n");
        response.append("  或直接输入问题（无需命令前缀）\n\n");

        response.append("**示例：**\n");
        response.append("  `/opencode chat 帮我重构这个函数`\n");
        response.append("  或直接：`帮我重构这个函数`\n");

        return response.toString();
    }

    /**
     * 提取 chat 命令的实际内容
     */
    private String extractChatContent(String[] parts, Message message) {
        // 优先使用 parts 数组（更简单可靠）
        if (parts.length >= 3) {
            return String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        }
        
        // 降级到字符串处理
        String content = message.getContent().trim();
        int firstSpace = content.indexOf(' ');
        if (firstSpace < 0) {
            return "";
        }
        
        String remaining = content.substring(firstSpace + 1).trim();
        // 移除 "chat" 子命令，提取实际对话内容
        if (remaining.toLowerCase().startsWith("chat ")) {
            remaining = remaining.substring("chat ".length()).trim();
        }
        return remaining;
    }

    private String handleSessionCommand(String[] parts, Message message) {
        String subCommand = parts[1].toLowerCase();

        if (subCommand.equals("sc")) {
            if (parts.length < 3) {
                return "❌ 用法：`/opencode sc <session_id>`\n\n示例：`/opencode sc ses_abc123`";
            }
            String sessionId = parts[2].trim();
            return taskExecutor.executeWithSpecificSession(message, null, sessionId);
        }

        if (parts.length < 3) {
            return "❌ 用法：`/opencode session <status|list|continue> [args]`";
        }

        String action = parts[2].toLowerCase();

        return switch (action) {
            case "status" -> sessionManager.getCurrentSessionStatus(message);
            case "list" -> sessionManager.handleListSessions();
            case "continue" -> handleSessionContinue(parts, message);
            default -> "❌ 未知的 session 命令: `" + action + "`\n\n" +
                       "可用命令：`status`, `list`, `continue` 或简写 `sc <id>`";
        };
    }

    private String handleSessionContinue(String[] parts, Message message) {
        if (parts.length < 4) {
            return "❌ 用法：`/opencode session continue <session_id>`\n\n或使用简写：`/opencode sc <session_id>`";
        }
        String sessionId = parts[3].trim();
        return taskExecutor.executeWithSpecificSession(message, null, sessionId);
    }

    private String handleResetCommand(Message message) {
        String topicId = message.getTopicId();

        if (topicId == null || topicId.isEmpty()) {
            return "❌ **只能在话题中使用 reset 命令**\n\n" +
                   "reset 命令用于清除话题的初始化状态，允许重新绑定会话。\n\n" +
                   "💡 使用场景：\n" +
                   "  • 需要切换到不同的会话\n" +
                   "  • 当前会话已失效\n" +
                   "  • 想要重新开始初始化流程";
        }

        Optional<String> currentSession = sessionManager.getSessionId(topicId);

        sessionManager.clearSession(topicId);
        sessionManager.clearExplicitlyInitialized(topicId);

        log.info("已重置话题初始化状态: topicId={}", topicId);

        StringBuilder response = new StringBuilder();
        response.append("🔄 **话题已重置**\n\n");

        if (currentSession.isPresent()) {
            response.append("已解除绑定的会话: `").append(currentSession.get()).append("`\n\n");
        }

        response.append("✅ **可以重新初始化了**\n\n");
        response.append("**下一步操作**：\n\n");
        response.append("1️⃣ 查看可用项目：\n");
        response.append("   `/opencode p` （或 `/opencode projects`）\n\n");
        response.append("2️⃣ 查看项目的最近会话：\n");
        response.append("   `/opencode s <项目名称>` （或 `/opencode sessions`）\n");
        response.append("   示例：`/opencode s feishu-backend`\n\n");
        response.append("3️⃣ 选择会话并绑定：\n");
        response.append("   `/opencode sc <会话ID>` （或 `session continue <id>`）\n\n");

        return response.toString();
    }

    /**
      * 处理未知命令
      */
    private String handleUnknownCommand(Message message, String subCommand, String[] parts) {
        // 必须使用 chat 子命令才能触发对话
        return buildUnknownCommandResponse(subCommand, "");
    }

    private String buildUnknownCommandResponse(String subCommand, String prompt) {
        return String.format(
            "❌ 未知的子命令: `%s`\n\n" +
            "📝 可用子命令：\n" +
            "  `/opencode chat <内容>` - 对话（推荐）\n" +
            "  `/opencode new <内容>` - 创建新会话\n" +
            "  `/opencode projects` - 查看项目\n" +
            "  `/opencode commands` - 查看命令\n" +
            "  `/opencode session <status|list>` - 会话管理\n\n" +
            "💡 如果你想对话，请使用：`/opencode chat %s`",
            subCommand, prompt
        );
    }
}

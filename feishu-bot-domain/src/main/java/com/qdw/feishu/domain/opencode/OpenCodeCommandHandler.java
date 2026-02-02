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

        // 检测话题状态（简化版：只判断是否在话题中）
        String topicId = message.getTopicId();
        TopicState state = (topicId != null && !topicId.isEmpty())
            ? TopicState.INITIALIZED  // 在话题中默认为已初始化
            : TopicState.NON_TOPIC;
        log.info("话题状态: {}, subCommand={}", state.getDescription(), subCommand);

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
            case "help" -> null; // 由主应用处理
            case "connect" -> handleConnect();
            case "new" -> handleNewCommand(parts, message);
            case "chat" -> handleChatCommand(parts, message);
            case "sessions" -> sessionManager.handleSessionsCommand(parts);
            case "session" -> handleSessionCommand(parts, message);
            case "projects" -> openCodeGateway.listProjects();
            case "commands" -> openCodeGateway.listCommands();
            default -> handleUnknownCommand(message, subCommand, parts);
        };
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
        if (parts.length < 3) {
            return "❌ 用法：`/opencode chat <对话内容>`\n\n" +
                   "示例：`/opencode chat 帮我写一个排序函数`\n\n" +
                   "💡 提示：在已绑定的话题中，也可以直接输入内容（无前缀）";
        }
        String prompt = extractChatContent(parts, message);
        return taskExecutor.executeWithAutoSession(message, prompt);
    }

    /**
     * 提取 chat 命令的实际内容
     */
    private String extractChatContent(String[] parts, Message message) {
        String content = message.getContent().trim();
        String chatPrompt = content.substring(content.indexOf(' ') + 1).trim();
        // 移除 "chat" 子命令，提取实际对话内容
        if (chatPrompt.toLowerCase().startsWith("chat ")) {
            chatPrompt = chatPrompt.substring(5).trim();
        }
        return chatPrompt;
    }

    /**
     * 处理 session 命令
     */
    private String handleSessionCommand(String[] parts, Message message) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode session <status|list|continue> [args]`";
        }

        String action = parts[2].toLowerCase();

        return switch (action) {
            case "status" -> sessionManager.getCurrentSessionStatus(message);
            case "list" -> sessionManager.handleListSessions();
            case "continue" -> handleSessionContinue(parts, message);
            default -> "❌ 未知的 session 命令: `" + action + "`\n\n" +
                       "可用命令：`status`, `list`, `continue`";
        };
    }

    /**
     * 处理 session continue 命令
     */
    private String handleSessionContinue(String[] parts, Message message) {
        if (parts.length < 4) {
            return "❌ 用法：`/opencode session continue <session_id>`";
        }
        String sessionId = parts[3].trim();
        return taskExecutor.executeWithSpecificSession(message, null, sessionId);
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

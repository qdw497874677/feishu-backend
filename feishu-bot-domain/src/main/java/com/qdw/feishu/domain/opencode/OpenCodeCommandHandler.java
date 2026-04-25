package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
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
    private final NextStepSuggester nextStepSuggester;
    private final OpenCodeMessageFormatter messageFormatter;
    private final CardRenderer cardRenderer;
    private final FeishuGateway feishuGateway;
    private final WizardManager wizardManager;

    public OpenCodeCommandHandler(OpenCodeGateway openCodeGateway,
                                   OpenCodeTaskExecutor taskExecutor,
                                   OpenCodeSessionManager sessionManager,
                                   TopicCommandValidator commandValidator,
                                   NextStepSuggester nextStepSuggester,
                                   OpenCodeMessageFormatter messageFormatter,
                                   CardRenderer cardRenderer,
                                   FeishuGateway feishuGateway,
                                   WizardManager wizardManager) {
        this.openCodeGateway = openCodeGateway;
        this.taskExecutor = taskExecutor;
        this.sessionManager = sessionManager;
        this.commandValidator = commandValidator;
        this.nextStepSuggester = nextStepSuggester;
        this.messageFormatter = messageFormatter;
        this.cardRenderer = cardRenderer;
        this.feishuGateway = feishuGateway;
        this.wizardManager = wizardManager;
    }

    /**
     * 处理命令
     * @deprecated Use {@link #handle(Message, String, String[], CommandWhitelist, MessageContext)} instead.
     */
    @Deprecated(since = "Phase 1", forRemoval = true)
    public AppExecutionResult handle(Message message, String subCommand, String[] parts, CommandWhitelist whitelist) {
        return handle(message, subCommand, parts, whitelist, MessageContext.unresolved());
    }

    /** 处理命令（使用 MessageContext 避免重复 findBinding 调用） */
    public AppExecutionResult handle(Message message, String subCommand, String[] parts,
                                      CommandWhitelist whitelist, MessageContext messageContext) {
        log.info("准备验证命令: subCommand={}", subCommand);

        TopicState state = sessionManager.detectTopicState(messageContext);
        log.info("话题状态: {}, subCommand={}", state.getDescription(), subCommand);

        // 验证命令是否允许（通过 CommandWhitelist）
        if (whitelist != null) {
            ValidationResult result = commandValidator.validateCommand(subCommand, state, whitelist);
            if (!result.isAllowed()) {
                log.info("命令受限: command={}, state={}", subCommand, state);
                return AppExecutionResult.text(result.getMessage());
            }
        }

        // 向导优先拦截：向导进行中，只允许向导 action 和白名单内的非侵入命令
        String topicId = message.getTopicId();
        boolean inTopic = topicId != null && !topicId.isEmpty();
        if (inTopic && wizardManager != null && wizardManager.isWizardActive(topicId)) {
            if (!isWizardAction(subCommand)) {
                log.info("向导进行中，拦截非向导命令: subCommand={}, topicId={}", subCommand, topicId);
                return AppExecutionResult.text(
                    "⚠️ 向导进行中，请先完成向导。\n\n"
                    + "点击上方卡片按钮继续，或输入 `/oc wizard_cancel` 取消向导。"
                );
            }
        }

        // 路由到具体处理逻辑
        AppExecutionResult result = switch (subCommand) {
            case "help" -> null; // caller handles
            case "connect" -> AppExecutionResult.text(handleConnect());
            case "status" -> AppExecutionResult.text(sessionManager.getCurrentSessionStatus(messageContext));
            case "new" -> handleNewCommand(parts, message, messageContext);
            case "chat", "chatnow", "cn" -> handleChatCommand(parts, message, messageContext);
            case "sessions", "s" -> AppExecutionResult.text(sessionManager.handleSessionsCommand(parts));
            case "session", "sc" -> handleSessionCommand(parts, message, messageContext);
            case "projects", "p" -> AppExecutionResult.text(openCodeGateway.listProjects());
            case "commands" -> AppExecutionResult.text(openCodeGateway.listCommands());
            case "reset" -> AppExecutionResult.text(handleResetCommand(message));
            // 向导 action 路由（卡片按钮点击时 subCommand 以 wizard_ 开头）
            default -> isWizardAction(subCommand)
                ? handleWizardAction(subCommand, message, messageContext)
                : AppExecutionResult.text(handleUnknownCommand(message, subCommand, parts));
        };

        // CMD-04: 附加下一步建议（chat/chatnow/help/commands 不附加）
        return appendNextStepSuggestion(result, subCommand, state);
    }

    /** 在命令执行结果后附加下一步操作建议（仅对有文本回复的结果附加）。 */
    private AppExecutionResult appendNextStepSuggestion(AppExecutionResult result,
                                                          String subCommand, TopicState state) {
        if (result == null || result.getReplyContent() == null) {
            return result;
        }
        String suggestion = nextStepSuggester.suggest(subCommand, state);
        if (suggestion == null || suggestion.isEmpty()) {
            return result;
        }
        String enhanced = result.getReplyContent() + "\n\n---\n" + suggestion;
        if (result.getOpenCodeSessionId() != null) {
            return AppExecutionResult.withSession(enhanced, result.getOpenCodeSessionId(), result.isSessionCreated());
        }
        return AppExecutionResult.text(enhanced);
    }

    /**
     * 处理 connect 命令
     */
    private String handleConnect() {
        String status;
        try {
            status = openCodeGateway.getServerStatus();
        } catch (Exception e) {
            status = "❌ 无法获取 (" + e.getMessage() + ")";
        }

        String projects;
        try {
            projects = openCodeGateway.listProjects();
        } catch (Exception e) {
            projects = "❌ 无法获取项目列表 (" + e.getMessage() + ")";
        }

        return messageFormatter.buildConnectSuccessResponse(status, projects);
    }

    /** 处理 new 命令：`new <prompt>` 或 `new <project> <prompt>` */
     @Deprecated(since = "Phase 1", forRemoval = true)
     private AppExecutionResult handleNewCommand(String[] parts, Message message) {
        return handleNewCommand(parts, message, MessageContext.unresolved());
     }

     private AppExecutionResult handleNewCommand(String[] parts, Message message, MessageContext messageContext) {
        String topicId = message.getTopicId();
        boolean inTopic = topicId != null && !topicId.isEmpty();
        boolean isInitialized = inTopic && sessionManager.isTopicInitialized(messageContext);

        if (parts.length < 3) {
            return AppExecutionResult.text(messageFormatter.buildNewCommandUsage(isInitialized));
        }

        // 判断格式：/opencode new <prompt> 还是 /opencode new <project> <prompt>
        String project = null;
        String prompt;

        if (parts.length >= 4) {
            // /opencode new <project> <prompt>
            project = parts[2].trim();
            prompt = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
        } else {
            // /opencode new <prompt>
            prompt = parts[2].trim();

            // 话题已绑定：使用当前项目
            if (isInitialized) {
                log.info("话题已绑定，将在当前项目创建新会话: topicId={}", topicId);
            } else {
                // 话题未绑定：必须指定项目
                log.warn("话题未绑定，必须指定项目名称");
                return AppExecutionResult.text(messageFormatter.buildNewCommandUsage(false));
            }
        }

        return taskExecutor.executeWithNewSession(message, prompt, project);
    }

    /** 处理 chat 命令 */
      @Deprecated(since = "Phase 1", forRemoval = true)
      private AppExecutionResult handleChatCommand(String[] parts, Message message) {
          return handleChatCommand(parts, message, MessageContext.unresolved());
      }

      private AppExecutionResult handleChatCommand(String[] parts, Message message, MessageContext messageContext) {
         String topicId = message.getTopicId();
         boolean inTopic = topicId != null && !topicId.isEmpty();
         String subCommand = parts.length > 1 ? parts[1].toLowerCase() : "chat";

         boolean isChatNow = "chatnow".equals(subCommand) || "cn".equals(subCommand);

         if (isChatNow) {
             return handleChatNowCommand(message, messageContext);
         }

         if (parts.length < 3) {
             if (inTopic) {
                 return AppExecutionResult.text(
                     sessionManager.getSessionId(messageContext)
                         .map(sessionId -> messageFormatter.buildChatStatusWithSession(topicId, sessionId))
                         .orElse(messageFormatter.buildChatQuickStart())
                 );
             }
             return AppExecutionResult.text(messageFormatter.buildChatQuickStart());
         }

         String prompt = extractChatContent(parts, message);

         if (inTopic && !sessionManager.isTopicInitialized(messageContext)) {
                log.info("话题未初始化，自动创建新会话");
                return taskExecutor.executeWithNewSession(message, prompt, null);
         }

         return taskExecutor.executeWithAutoSession(message, prompt);
     }

     @Deprecated(since = "Phase 1", forRemoval = true)
     private AppExecutionResult handleChatNowCommand(Message message) {
         return handleChatNowCommand(message, MessageContext.unresolved());
     }

     private AppExecutionResult handleChatNowCommand(Message message, MessageContext messageContext) {
         String topicId = message.getTopicId();
         boolean inTopic = topicId != null && !topicId.isEmpty();

         if (inTopic && sessionManager.isTopicInitialized(messageContext)) {
             Optional<String> currentSessionId = sessionManager.getSessionId(messageContext);
             if (currentSessionId.isPresent()) {
                 return AppExecutionResult.text(messageFormatter.buildSessionInitializedInfo(topicId, currentSessionId.get()));
             }
         }

         log.info("cn 命令：创建新会话并绑定到话题");
         sessionManager.clearSession(message);

         try {
             String result = taskExecutor.createSessionOnly(message);
             Optional<String> newSessionId = sessionManager.getSessionId(message);
             if (newSessionId.isPresent()) {
                 return AppExecutionResult.withSession(
                     messageFormatter.buildSessionInitializedInfo(message.getTopicId(), newSessionId.get()),
                     newSessionId.get(),
                     true
                 );
             }
             return AppExecutionResult.text(result);
         } catch (Exception e) {
             log.error("创建会话失败", e);
             return AppExecutionResult.text("❌ 创建会话失败: " + e.getMessage());
         }
     }

    /** 提取 chat 命令的实际内容 */
    private String extractChatContent(String[] parts, Message message) {
        if (parts.length >= 3) {
            return String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        }

        String content = message.getContent().trim();
        int firstSpace = content.indexOf(' ');
        if (firstSpace < 0) {
            return "";
        }

        String remaining = content.substring(firstSpace + 1).trim();
        if (remaining.toLowerCase().startsWith("chat ")) {
            remaining = remaining.substring("chat ".length()).trim();
        }
        return remaining;
    }

    @Deprecated(since = "Phase 1", forRemoval = true)
    private AppExecutionResult handleSessionCommand(String[] parts, Message message) {
        return handleSessionCommand(parts, message, MessageContext.unresolved());
    }

    private AppExecutionResult handleSessionCommand(String[] parts, Message message, MessageContext messageContext) {
        String subCommand = parts[1].toLowerCase();

        if (subCommand.equals("sc")) {
            if (parts.length < 3) {
                return AppExecutionResult.text("❌ 用法：`/opencode sc <session_id>`\n\n示例：`/opencode sc ses_abc123`");
            }
            String sessionId = parts[2].trim();
            return taskExecutor.executeWithSpecificSession(message, null, sessionId);
        }

        if (parts.length < 3) {
            return AppExecutionResult.text("❌ 用法：`/opencode session <status|list|continue> [args]`");
        }

        String action = parts[2].toLowerCase();

        return switch (action) {
            case "status" -> AppExecutionResult.text(sessionManager.getCurrentSessionStatus(messageContext));
            case "list" -> AppExecutionResult.text(sessionManager.handleListSessions());
            case "continue" -> handleSessionContinue(parts, message);
            default -> AppExecutionResult.text("❌ 未知的 session 命令: `" + action + "`\n\n" +
                       "可用命令：`status`, `list`, `continue` 或简写 `sc <id>`");
        };
    }

    private AppExecutionResult handleSessionContinue(String[] parts, Message message) {
        if (parts.length < 4) {
            return AppExecutionResult.text("❌ 用法：`/opencode session continue <session_id>`\n\n或使用简写：`/opencode sc <session_id>`");
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

        Optional<String> currentSession = sessionManager.getSessionId(message);

        sessionManager.clearSession(message);
        sessionManager.clearExplicitlyInitialized(message);

        log.info("已重置话题初始化状态: topicId={}", topicId);

        return messageFormatter.buildResetResponse(topicId, currentSession);
    }

    /** 处理未知命令 */
    private String handleUnknownCommand(Message message, String subCommand, String[] parts) {
        return messageFormatter.buildUnknownCommandResponse(subCommand, "");
    }

    // ============ 向导相关方法 ============

    /**
     * 判断 subCommand 是否是向导 action。
     * 向导 action 以 "wizard_" 开头。
     */
    private boolean isWizardAction(String subCommand) {
        return subCommand != null && subCommand.startsWith("wizard_");
    }

    /**
     * 处理向导 action（卡片按钮点击触发）。
     *
     * <p>卡片发送模式：handler 内直接调用 feishuGateway.sendInteractiveMessage() 发送卡片
     * + 返回 AppExecutionResult.noReply() 抑制文本回复。
     * 与 HelpApp.trySendCardHelp() 已有模式一致。
     */
    private AppExecutionResult handleWizardAction(String subCommand, Message message, MessageContext messageContext) {
        String topicId = message.getTopicId();
        String chatId = message.getChatId();

        if (wizardManager == null) {
            return AppExecutionResult.text("❌ 向导功能不可用");
        }

        try {
            WizardManager.WizardResult wizardResult = wizardManager.handleAction(subCommand, chatId, topicId);

            if (wizardResult == null) {
                // 非向导相关 action，或向导已过期
                return AppExecutionResult.text(handleUnknownCommand(message, subCommand, new String[]{}));
            }

            if (wizardResult.isCompleted()) {
                // 向导完成：发送成功卡片 + 返回 withSession 通知会话绑定
                String sessionId = wizardResult.getOpenCodeSessionId();
                if (wizardResult.getCardContent() != null && cardRenderer != null) {
                    try {
                        CardActionContext actionCtx = CardActionContext.from(messageContext);
                        String cardJson = cardRenderer.render(wizardResult.getCardContent(), actionCtx);
                        feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
                        return AppExecutionResult.withSession(null, sessionId, false);
                    } catch (Exception e) {
                        log.warn("向导完成卡片发送失败，降级为文本: {}", e.getMessage());
                    }
                }
                return AppExecutionResult.withSession(
                    "✅ 已绑定会话 `" + sessionId + "`\n\n💬 现在可以直接输入问题开始对话！",
                    sessionId, false);
            }

            if (wizardResult.getCardContent() != null && cardRenderer != null) {
                // 有卡片内容：发送卡片 + noReply
                try {
                    CardActionContext actionCtx = CardActionContext.from(messageContext);
                    String cardJson = cardRenderer.render(wizardResult.getCardContent(), actionCtx);
                    feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
                    return AppExecutionResult.noReply();
                } catch (Exception e) {
                    log.warn("向导卡片发送失败，降级为文本: {}", e.getMessage());
                }
            }

            // 降级：文本形式
            if (wizardResult.getTextContent() != null) {
                return AppExecutionResult.text(wizardResult.getTextContent());
            }

            return AppExecutionResult.noReply();

        } catch (Exception e) {
            log.error("处理向导 action 失败: subCommand={}", subCommand, e);
            return AppExecutionResult.text("❌ 向导处理失败：" + e.getMessage());
        }
    }
}

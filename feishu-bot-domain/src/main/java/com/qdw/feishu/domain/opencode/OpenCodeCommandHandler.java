package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.session.ContextSessionState;
import com.qdw.feishu.domain.opencode.handler.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenCode 命令处理器（调度器）。
 *
 * <p>负责命令验证、向导拦截、子命令路由和下一步建议追加。
 * 具体子命令逻辑委托给 {@link SubCommandHandler} 实现。
 */
@Slf4j
public class OpenCodeCommandHandler {

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeSessionManager sessionManager;
    private final TopicCommandValidator commandValidator;
    private final NextStepSuggester nextStepSuggester;
    private final WizardManager wizardManager;
    private final CardRenderer cardRenderer;
    private final FeishuGateway feishuGateway;

    private final Map<String, SubCommandHandler> handlers;
    private final DefaultHandler defaultHandler;

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
        this.sessionManager = sessionManager;
        this.commandValidator = commandValidator;
        this.nextStepSuggester = nextStepSuggester;
        this.wizardManager = wizardManager;
        this.cardRenderer = cardRenderer;
        this.feishuGateway = feishuGateway;

        this.handlers = buildHandlerMap(openCodeGateway, taskExecutor, sessionManager, messageFormatter,
            cardRenderer, feishuGateway, wizardManager);
        this.defaultHandler = new DefaultHandler(messageFormatter, wizardManager, cardRenderer, feishuGateway, sessionManager);
    }

    private static Map<String, SubCommandHandler> buildHandlerMap(
            OpenCodeGateway gw, OpenCodeTaskExecutor executor, OpenCodeSessionManager sm,
            OpenCodeMessageFormatter fmt, CardRenderer cr, FeishuGateway fg, WizardManager wm) {
        Map<String, SubCommandHandler> map = new HashMap<>();
        map.put("connect", new ConnectHandler(gw, fmt));
        map.put("status", new StatusHandler(sm));
        map.put("new", new NewHandler(sm, executor, fmt));
        ChatHandler chat = new ChatHandler(sm, executor, fmt);
        map.put("chat", chat);
        map.put("chatnow", chat);
        map.put("cn", chat);
        SessionsHandler sessions = new SessionsHandler(sm, fmt, gw, cr, fg);
        map.put("sessions", sessions);
        map.put("s", sessions);
        SessionHandler session = new SessionHandler(sm, executor);
        map.put("session", session);
        map.put("sc", session);
        ProjectsHandler projects = new ProjectsHandler(gw);
        map.put("projects", projects);
        map.put("p", projects);
        map.put("reset", new ResetHandler(sm, fmt));
        return map;
    }

    /**
     * 处理命令（向后兼容）。
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

        ContextSessionState state = sessionManager.detectTopicState(messageContext);
        log.info("话题状态: {}, subCommand={}", state.getDescription(), subCommand);

        if (whitelist != null) {
            ValidationResult result = commandValidator.validateCommand(subCommand, state, whitelist);
            if (!result.isAllowed()) {
                log.info("命令受限: command={}, state={}", subCommand, state);
                return AppExecutionResult.text(result.getMessage());
            }
        }

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

        if (inTopic && state == ContextSessionState.IN_APP_NO_SESSION
                && wizardManager != null && !wizardManager.isWizardActive(topicId)
                && !isExplicitControlCommand(subCommand)) {
            log.info("UNINITIALIZED 话题自动触发向导: topicId={}, subCommand={}", topicId, subCommand);
            String chatId = message.getChatId();
            WizardManager.WizardResult wizardResult = wizardManager.start(chatId, topicId);
            if (wizardResult != null && wizardResult.getCardContent() != null) {
                CardActionContext actionCtx = CardActionContext.from(messageContext);
                String cardJson = cardRenderer.render(wizardResult.getCardContent(), actionCtx);
                feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
                return AppExecutionResult.noReply();
            }
        }

        AppExecutionResult result = dispatch(subCommand, message, parts, messageContext);
        return appendNextStepSuggestion(result, subCommand, state);
    }

    private AppExecutionResult dispatch(String subCommand, Message message, String[] parts, MessageContext messageContext) {
        if ("help".equals(subCommand)) {
            return null;
        }
        if ("commands".equals(subCommand)) {
            return AppExecutionResult.text(openCodeGateway.listCommands());
        }

        SubCommandHandler handler = handlers.get(subCommand);
        if (handler != null) {
            return handler.handle(message, parts, messageContext);
        }
        return defaultHandler.handle(message, parts, messageContext);
    }

    private AppExecutionResult appendNextStepSuggestion(AppExecutionResult result,
                                                           String subCommand, ContextSessionState state) {
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

    private boolean isWizardAction(String subCommand) {
        return subCommand != null && subCommand.startsWith("wizard_");
    }

    private boolean isExplicitControlCommand(String subCommand) {
        return switch (subCommand) {
            case "connect", "help", "status", "projects", "p",
                 "sessions", "s", "session", "sc",
                 "chatnow", "cn", "new", "reset", "commands" -> true;
            default -> subCommand != null && subCommand.startsWith("wizard_");
        };
    }
}

package com.qdw.feishu.app.opencode;

import com.qdw.feishu.app.message.BotMessageAppService;
import com.qdw.feishu.app.session.ContextSessionOrchestrator;
import com.qdw.feishu.app.session.ContextSessionStatus;
import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.HandledMessageResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.model.opencode.OpenCodeSessionData;
import com.qdw.feishu.domain.opencode.OpenCodeApp;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.session.ContextSessionState;
import com.qdw.feishu.domain.session.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class OpenCodeMessageAppService {

    static final String APP_ID = "opencode";
    private static final TypeToken<OpenCodeSessionData> TYPE_TOKEN = new TypeToken<OpenCodeSessionData>() {};

    private final ContextSessionOrchestrator contextSessionOrchestrator;
    private final BotMessageAppService botMessageAppService;
    private final FeishuGateway feishuGateway;
    private final OpenCodeSessionManager openCodeSessionManager;
    private final OpenCodeApp openCodeApp;

    public OpenCodeMessageAppService(ContextSessionOrchestrator contextSessionOrchestrator,
                                     BotMessageAppService botMessageAppService,
                                     FeishuGateway feishuGateway,
                                     OpenCodeSessionManager openCodeSessionManager,
                                     OpenCodeApp openCodeApp) {
        this.contextSessionOrchestrator = contextSessionOrchestrator;
        this.botMessageAppService = botMessageAppService;
        this.feishuGateway = feishuGateway;
        this.openCodeSessionManager = openCodeSessionManager;
        this.openCodeApp = openCodeApp;
    }

    /**
     * @deprecated Use {@link #tryHandle(Message, MessageContext)} instead.
     */
    @Deprecated
    public boolean supports(Message message) {
        if (isExplicitOpenCodeCommand(message)) {
            return true;
        }

        return resolveContext(message)
                .map(contextRef -> contextSessionOrchestrator.loadStatus(contextRef, APP_ID, TYPE_TOKEN))
                .map(status -> status.getState() == ContextSessionState.IN_APP_NO_SESSION
                        || status.getState() == ContextSessionState.IN_APP_WITH_SESSION)
                .orElse(false);
    }

    /**
     * @deprecated Use {@link #tryHandle(Message, MessageContext)} instead.
     */
    @Deprecated
    public boolean tryHandle(Message message) {
        Optional<ImContextRef> contextRefOpt = resolveContext(message);
        Optional<ContextSessionStatus<OpenCodeSessionData>> statusOpt = loadStatus(contextRefOpt);
        if (!shouldHandle(message, statusOpt)) {
            return false;
        }

        handleMessageInternal(message, contextRefOpt, statusOpt);
        return true;
    }

    public boolean tryHandle(Message message, MessageContext messageContext) {
        if (!messageContext.isResolved()) {
            // Cannot resolve IM context — use pre-resolved binding from messageContext
            Optional<ImContextRef> contextRefOpt = Optional.empty();
            Optional<ContextSessionStatus<OpenCodeSessionData>> statusOpt = Optional.empty();
            if (!shouldHandle(message, statusOpt)) {
                return false;
            }
            handleMessageInternal(message, messageContext, contextRefOpt, statusOpt);
            return true;
        }

        ImContextRef contextRef = messageContext.getContextRef();
        // Use pre-resolved binding from MessageContext to build status without re-querying
        Optional<ContextSessionStatus<OpenCodeSessionData>> statusOpt =
                Optional.of(buildStatusFromContext(messageContext));
        if (!shouldHandle(message, statusOpt)) {
            return false;
        }

        handleMessageInternal(message, messageContext, Optional.of(contextRef), statusOpt);
        return true;
    }

    public SendResult handleMessage(Message message, MessageContext messageContext) {
        if (!messageContext.isResolved()) {
            return botMessageAppService.handleMessage(message, messageContext).getSendResult();
        }
        ImContextRef contextRef = messageContext.getContextRef();
        ContextSessionStatus<OpenCodeSessionData> status = buildStatusFromContext(messageContext);
        return handleMessageInternal(message, messageContext, Optional.of(contextRef), Optional.of(status));
    }

    public SendResult handleMessage(Message message) {
        Optional<ImContextRef> contextRefOpt = resolveContext(message);
        return handleMessageInternal(message, contextRefOpt, loadStatus(contextRefOpt));
    }

    private SendResult handleMessageInternal(Message message,
                                             Optional<ImContextRef> contextRefOpt,
                                             Optional<ContextSessionStatus<OpenCodeSessionData>> statusOpt) {
        return handleMessageInternal(message, null, contextRefOpt, statusOpt);
    }

    private SendResult handleMessageInternal(Message message,
                                             MessageContext messageContext,
                                             Optional<ImContextRef> contextRefOpt,
                                             Optional<ContextSessionStatus<OpenCodeSessionData>> statusOpt) {
        if (contextRefOpt.isEmpty()) {
            if (messageContext != null) {
                return botMessageAppService.handleMessage(message, messageContext).getSendResult();
            }
            return botMessageAppService.handleMessage(message).getSendResult();
        }

        ImContextRef contextRef = contextRefOpt.get();
        ContextSessionStatus<OpenCodeSessionData> status = statusOpt.orElseGet(ContextSessionStatus::unbound);

        if (status.isDangling()) {
            contextSessionOrchestrator.repairDanglingSessionBinding(contextRef, APP_ID);
            return sendGuidance(message, buildDanglingSessionGuidance(message));
        }

        if (status.getState() == ContextSessionState.UNBOUND) {
            // Graceful degradation for unbound threads (old topics with no binding)
            if (messageContext != null && messageContext.isThreadContext() && !isExplicitOpenCodeCommand(message)) {
                log.debug("Graceful degradation for unbound topic {}: no binding found",
                        contextRef.toStorageKey());
                return sendGuidance(message,
                        "该话题未绑定 OpenCode 会话。请在群聊中使用 /oc projects 开始绑定。");
            }
            contextSessionOrchestrator.enterAppContext(contextRef, APP_ID);
            return handleOpenCodeResult(message, messageContext);
        }

        if (status.getState() == ContextSessionState.BOUND_TO_OTHER_APP) {
            String boundAppId = status.getBinding().map(ImContextBinding::getAppId).orElse("unknown");
            return sendGuidance(message, buildCrossAppRebindRejectedMessage(boundAppId));
        }

        if (status.getState() == ContextSessionState.IN_APP_NO_SESSION && isChatCommand(message)) {
            return sendGuidance(message, openCodeSessionManager.getCurrentSessionStatus(message));
        }

        return handleOpenCodeResult(message, messageContext);
    }

    /**
     * Build ContextSessionStatus from pre-resolved MessageContext binding.
     * Avoids a second findBinding() call by using the binding from MessageContext.
     */
    private ContextSessionStatus<OpenCodeSessionData> buildStatusFromContext(MessageContext messageContext) {
        if (!messageContext.isBound()) {
            return ContextSessionStatus.unbound();
        }

        ImContextBinding binding = messageContext.getBinding();
        if (!binding.isForApp(APP_ID)) {
            return ContextSessionStatus.boundToOtherApp(binding);
        }

        if (binding.getSessionId() == null) {
            return ContextSessionStatus.inAppNoSession(binding);
        }

        // Has sessionId — need to verify session exists (requires gateway call)
        return contextSessionOrchestrator.loadStatus(
                messageContext.getContextRef(), APP_ID, TYPE_TOKEN);
    }

    private boolean shouldHandle(Message message, Optional<ContextSessionStatus<OpenCodeSessionData>> statusOpt) {
        if (isExplicitOpenCodeCommand(message)) {
            return true;
        }

        return statusOpt
                .map(status -> status.getState() == ContextSessionState.IN_APP_NO_SESSION
                        || status.getState() == ContextSessionState.IN_APP_WITH_SESSION)
                .orElse(false);
    }

    private Optional<ContextSessionStatus<OpenCodeSessionData>> loadStatus(Optional<ImContextRef> contextRefOpt) {
        return contextRefOpt.map(contextRef -> contextSessionOrchestrator.loadStatus(contextRef, APP_ID, TYPE_TOKEN));
    }

    private Optional<ImContextRef> resolveContext(Message message) {
        try {
            return Optional.of(FeishuContextResolver.resolve(message));
        } catch (IllegalArgumentException e) {
            log.debug("Cannot resolve IM context from message: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isChatCommand(Message message) {
        String content = message.getContent();
        if (content == null) {
            return false;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("/")) {
            return true;
        }

        String[] parts = trimmed.split("\\s+", 3);
        return parts.length >= 2 && "chat".equalsIgnoreCase(parts[1]);
    }

    private boolean isExplicitOpenCodeCommand(Message message) {
        String content = message.getContent();
        if (content == null) {
            return false;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("/")) {
            return false;
        }

        String command = trimmed.split("\\s+", 2)[0].substring(1).toLowerCase();
        if (openCodeApp.getAppId().equalsIgnoreCase(command)) {
            return true;
        }

        return openCodeApp.getAppAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(command));
    }

    private SendResult handleOpenCodeResult(Message message, MessageContext messageContext) {
        HandledMessageResult result;
        if (messageContext != null) {
            result = botMessageAppService.handleMessage(message, messageContext);
        } else {
            result = botMessageAppService.handleMessage(message);
        }
        progressSessionIfNeeded(message, result);
        return result.getSendResult();
    }

    private void progressSessionIfNeeded(Message message, HandledMessageResult result) {
        if (result == null || result.getSendResult() == null || !result.getSendResult().isSuccess()) {
            return;
        }
        if (!APP_ID.equals(result.getAppId())) {
            return;
        }

        // Use structured openCodeSessionId from AppExecutionResult (Task 1B)
        // instead of fragile text parsing
        com.qdw.feishu.domain.app.AppExecutionResult execResult = result.getExecutionResult();
        if (execResult == null || execResult.getOpenCodeSessionId() == null) {
            return;
        }

        String openCodeSessionId = execResult.getOpenCodeSessionId();

        // Determine the correct context to bind the session to:
        // If the reply created a new thread, bind to that thread
        SendResult sendResult = result.getSendResult();
        if (sendResult.getThreadId() != null && !sendResult.getThreadId().isEmpty()) {
            ImContextRef targetContext = ImContextRef.feishuThread(sendResult.getThreadId());
            openCodeSessionManager.saveSession(targetContext, openCodeSessionId);
        } else {
            openCodeSessionManager.saveSession(message, openCodeSessionId);
        }
    }

    private SendResult sendGuidance(Message message, String content) {
        return feishuGateway.sendMessage(message, content, message.getTopicId());
    }

    private String buildDanglingSessionGuidance(Message message) {
        return "⚠️ 检测到当前 OpenCode 会话已失效，已自动修复为未激活会话状态。\n\n"
                + openCodeSessionManager.getCurrentSessionStatus(message);
    }

    private String buildCrossAppRebindRejectedMessage(String boundAppId) {
        return "❌ 当前上下文已绑定到其他应用：`" + boundAppId + "`\n\n"
                + "请先退出当前应用上下文，或在新的消息/话题中使用 OpenCode。";
    }
}

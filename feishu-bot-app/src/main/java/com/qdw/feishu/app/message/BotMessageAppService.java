package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.BotRoutingDecision;
import com.qdw.feishu.domain.message.HandledMessageResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.service.BotMessageService;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BotMessageAppService {

    private final BotMessageService botMessageService;
    private final FeishuGateway feishuGateway;
    private final ImContextBindingGateway bindingGateway;
    private final ReplyStrategyFactory replyStrategyFactory;
    private final OpenCodeSessionManager openCodeSessionManager;
    private final AppRegistry appRegistry;

    public BotMessageAppService(BotMessageService botMessageService,
                                FeishuGateway feishuGateway,
                                ImContextBindingGateway bindingGateway,
                                ReplyStrategyFactory replyStrategyFactory,
                                OpenCodeSessionManager openCodeSessionManager,
                                AppRegistry appRegistry) {
        this.botMessageService = botMessageService;
        this.feishuGateway = feishuGateway;
        this.bindingGateway = bindingGateway;
        this.replyStrategyFactory = replyStrategyFactory;
        this.openCodeSessionManager = openCodeSessionManager;
        this.appRegistry = appRegistry;
    }

    public HandledMessageResult handleMessage(Message message) {
        return handleMessage(message, MessageContext.unresolved());
    }

    public HandledMessageResult handleMessage(Message message, MessageContext messageContext) {
        BotRoutingDecision decision = botMessageService.routeMessage(message, messageContext);
        if (decision == null || decision.getAppId() == null) {
            return new HandledMessageResult(SendResult.failure("应用不存在"), null, null);
        }

        FishuAppI app = appRegistry.getApp(decision.getAppId()).orElse(null);
        if (app == null) {
            return new HandledMessageResult(SendResult.failure("应用不存在: " + decision.getAppId()), null, null);
        }
        AppExecutionResult execResult = app.execute(message, messageContext);
        String replyContent = execResult != null ? execResult.getReplyContent() : null;

        // UX-03: 为 OpenCode 回复添加状态指示器
        replyContent = prependStatusIndicator(replyContent, app, messageContext, message);

        SendResult sendResult = sendReply(message, app, replyContent);
        persistBindingIfNeeded(message, sendResult, decision, messageContext);
        return new HandledMessageResult(sendResult, decision.getAppId(), execResult);
    }

    /**
     * 为 OpenCode 应用的回复添加状态行。
     * 格式：📎 opencode | ses_xxx 或 📎 opencode | 未绑定会话
     *
     * <p>排除：非 OpenCode 应用、null/空回复、help 命令
     */
    private String prependStatusIndicator(String replyContent, FishuAppI app,
                                           MessageContext messageContext, Message message) {
        if (!"opencode".equals(app.getAppId())) {
            return replyContent;
        }
        if (replyContent == null || replyContent.trim().isEmpty()) {
            return replyContent;
        }
        if (isHelpCommand(message)) {
            return replyContent;
        }
        String statusLine = buildStatusLine(messageContext);
        if (statusLine == null) {
            return replyContent;
        }
        return statusLine + "\n\n" + replyContent;
    }

    private boolean isHelpCommand(Message message) {
        String content = message.getContent();
        if (content == null) {
            return false;
        }
        String trimmed = content.trim().toLowerCase();
        return trimmed.matches("/(?:opencode|oc|code)\\s+help.*");
    }

    private String buildStatusLine(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved()) {
            return null;
        }
        if (!messageContext.isBoundToApp("opencode")) {
            return null;
        }
        Optional<String> displayId = getDisplaySessionId(messageContext);
        if (displayId.isPresent()) {
            return "📎 opencode | " + displayId.get();
        }
        return "📎 opencode | 未绑定会话";
    }

    private Optional<String> getDisplaySessionId(MessageContext messageContext) {
        return openCodeSessionManager.getSessionId(messageContext)
                .map(id -> id.length() > 16 ? id.substring(0, 16) + "..." : id);
    }

    private SendResult sendReply(Message message, FishuAppI app, String replyContent) {
        if (replyContent == null || replyContent.trim().isEmpty()) {
            log.debug("App {} returned empty content, skipping reply (likely async or no-op)", app.getAppId());
            return SendResult.success(null);
        }
        ReplyMode replyMode = app.getReplyMode();
        ReplyStrategy strategy = replyStrategyFactory.getStrategy(replyMode);
        if (strategy == null) {
            strategy = replyStrategyFactory.getStrategy(ReplyMode.DEFAULT);
        }
        return strategy.reply(message, replyContent, message.getTopicId());
    }

    private void persistBindingIfNeeded(Message message, SendResult sendResult,
                                         BotRoutingDecision decision, MessageContext messageContext) {
        if (decision == null || !decision.shouldPersistBinding() || sendResult == null || !sendResult.isSuccess()) {
            return;
        }

        String persistedThreadId = sendResult.getThreadId();
        if (persistedThreadId == null || persistedThreadId.isEmpty()) {
            return;
        }

        // Copy the existing binding (appId + internal sessionId) to the new thread context
        String appId = decision.getAppId();
        String internalSessionId = null;

        if (messageContext != null && messageContext.getBinding() != null
                && messageContext.getBinding().isForApp(appId)) {
            internalSessionId = messageContext.getBinding().getSessionId();
        }

        ImContextRef threadRef = ImContextRef.feishuThread(persistedThreadId);
        BindingResult bindingResult = bindingGateway.bind(threadRef, appId, internalSessionId);
        log.info("Propagated binding to new thread: {} -> (app={}, session={}), result={}",
                persistedThreadId, appId, internalSessionId, bindingResult);
    }
}

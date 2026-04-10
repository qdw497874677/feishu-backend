package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.BotRoutingDecision;
import com.qdw.feishu.domain.message.HandledMessageResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.service.BotMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BotMessageAppService {

    private final BotMessageService botMessageService;
    private final FeishuGateway feishuGateway;
    private final ImContextBindingGateway bindingGateway;
    private final ReplyStrategyFactory replyStrategyFactory;

    public BotMessageAppService(BotMessageService botMessageService,
                                FeishuGateway feishuGateway,
                                ImContextBindingGateway bindingGateway,
                                ReplyStrategyFactory replyStrategyFactory) {
        this.botMessageService = botMessageService;
        this.feishuGateway = feishuGateway;
        this.bindingGateway = bindingGateway;
        this.replyStrategyFactory = replyStrategyFactory;
    }

    public HandledMessageResult handleMessage(Message message) {
        return handleMessage(message, MessageContext.unresolved());
    }

    public HandledMessageResult handleMessage(Message message, MessageContext messageContext) {
        BotRoutingDecision decision = botMessageService.routeMessage(message, messageContext);
        if (decision == null || decision.getApp() == null) {
            return new HandledMessageResult(SendResult.failure("应用不存在"), null, null);
        }

        FishuAppI app = decision.getApp();
        AppExecutionResult execResult = app.execute(message, messageContext);
        String replyContent = execResult != null ? execResult.getReplyContent() : null;
        SendResult sendResult = sendReply(message, app, replyContent);
        persistBindingIfNeeded(message, sendResult, decision, messageContext);
        return new HandledMessageResult(sendResult, decision.getAppId(), execResult);
    }

    private SendResult sendReply(Message message, FishuAppI app, String replyContent) {
        if (replyContent == null) {
            log.debug("App {} returned null content, skipping reply (likely sent card directly or async)", app.getAppId());
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

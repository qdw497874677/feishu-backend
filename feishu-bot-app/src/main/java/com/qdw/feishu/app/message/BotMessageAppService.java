package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.BotRoutingDecision;
import com.qdw.feishu.domain.message.HandledMessageResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextRef;
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
        BotRoutingDecision decision = botMessageService.routeMessage(message);
        if (decision == null || decision.getApp() == null) {
            return new HandledMessageResult(SendResult.failure("应用不存在"), null, null);
        }

        FishuAppI app = decision.getApp();
        String replyContent = app.execute(message);
        SendResult sendResult = sendReply(message, app, replyContent);
        persistBindingIfNeeded(message, sendResult, decision);
        return new HandledMessageResult(sendResult, decision.getAppId(), replyContent);
    }

    private SendResult sendReply(Message message, FishuAppI app, String replyContent) {
        if (replyContent == null) {
            log.debug("App {} returned null, skipping reply (likely sent card directly)", app.getAppId());
            return SendResult.success(null);
        }
        ReplyMode replyMode = app.getReplyMode();
        ReplyStrategy strategy = replyStrategyFactory.getStrategy(replyMode);
        if (strategy == null) {
            strategy = replyStrategyFactory.getStrategy(ReplyMode.DEFAULT);
        }
        return strategy.reply(message, replyContent, message.getTopicId());
    }

    private void persistBindingIfNeeded(Message message, SendResult sendResult, BotRoutingDecision decision) {
        if (decision == null || !decision.shouldPersistBinding() || sendResult == null || !sendResult.isSuccess()) {
            return;
        }

        String persistedThreadId = sendResult.getThreadId();
        if (persistedThreadId == null || persistedThreadId.isEmpty()) {
            return;
        }

        ImContextRef contextRef = ImContextRef.feishuThread(persistedThreadId);
        BindingResult bindingResult = bindingGateway.bind(contextRef, decision.getAppId(), null);
        log.debug("Persisted context binding: {}", bindingResult);
    }
}

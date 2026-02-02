package com.qdw.feishu.infrastructure.reply;

import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 直接回复策略
 * 
 * 特点：
 * - 不创建话题，直接向发送者发送消息
 * - 适用于独立的、不需要上下文的回复
 * - 使用飞书 SDK 的 createMessage API
 */
@Slf4j
@Component
public class DirectReplyStrategy implements ReplyStrategy {

    private final FeishuGateway feishuGateway;

    public DirectReplyStrategy(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.DIRECT;
    }

    @Override
    public SendResult reply(Message message, String replyContent, String topicId) {
        log.debug("DirectReplyStrategy: 直接回复消息，不创建话题");
        return feishuGateway.sendDirectReply(message, replyContent);
    }
}

package com.qdw.feishu.infrastructure.reply;

import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 话题回复策略
 * 
 * 特点：
 * - 支持创建新话题或回复到现有话题
 * - 使用飞书 SDK 的 replyMessage API
 * - 适用于需要保持会话上下文的场景
 */
@Slf4j
@Component
public class TopicReplyStrategy implements ReplyStrategy {

    private final FeishuGateway feishuGateway;

    public TopicReplyStrategy(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;
    }

    @Override
    public SendResult reply(Message message, String replyContent, String topicId) {
        log.debug("TopicReplyStrategy: 话题回复, topicId={}", topicId);
        // 无论是否提供 topicId，都使用 sendMessage 方法
        // 如果 topicId 为空，sendMessage 会自动创建新话题
        return feishuGateway.sendMessage(message, replyContent, topicId);
    }
}

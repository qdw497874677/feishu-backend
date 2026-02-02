package com.qdw.feishu.infrastructure.reply;

import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认回复策略
 * 
 * 特点：
 * - 行为与话题回复策略相同，但可配置为不同行为
 * - 作为回退策略，当无法确定具体策略时使用
 * - 保持向后兼容性
 */
@Slf4j
@Component
public class DefaultReplyStrategy implements ReplyStrategy {

    private final FeishuGateway feishuGateway;

    public DefaultReplyStrategy(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.DEFAULT;
    }

    @Override
    public SendResult reply(Message message, String replyContent, String topicId) {
        log.debug("DefaultReplyStrategy: 默认回复, topicId={}", topicId);
        // 默认行为：透传 topicId，让 FeishuGateway 决定具体行为
        return feishuGateway.sendMessage(message, replyContent, topicId);
    }
}

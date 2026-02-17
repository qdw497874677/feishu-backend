package com.qdw.feishu.infrastructure.adapter;

import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageResponseAdapter implements ResponseAdapter {
    private final FeishuGateway feishuGateway;
    private final ReplyStrategyFactory replyStrategyFactory;
    
    public MessageResponseAdapter(FeishuGateway feishuGateway,
                                  ReplyStrategyFactory replyStrategyFactory) {
        this.feishuGateway = feishuGateway;
        this.replyStrategyFactory = replyStrategyFactory;
    }
    
    @Override
    public void respond(UnifiedCommand command, BizResult result) {
        String content = formatContent(result);
        
        Message message = createMessage(command);
        ReplyStrategy strategy = replyStrategyFactory.getStrategy(
            com.qdw.feishu.domain.core.ReplyMode.DEFAULT);
        
        SendResult sendResult = strategy.reply(message, content, command.getTopicId());
        
        if (sendResult.isSuccess()) {
            log.info("Message response sent: messageId={}", command.getMessageId());
        } else {
            log.error("Failed to send message response: {}", sendResult.getErrorMessage());
        }
    }
    
    @Override
    public boolean supports(EventSource source, UnifiedCommand command) {
        return source == EventSource.MESSAGE;
    }
    
    private String formatContent(BizResult result) {
        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            return result.getMessage();
        }
        if (result.getData() != null) {
            return result.getData().toString();
        }
        return result.isSuccess() ? "操作成功" : "操作失败";
    }
    
    private Message createMessage(UnifiedCommand command) {
        Message message = new Message();
        message.setMessageId(command.getMessageId());
        message.setSender(new Sender(command.getOpenId(), "user"));
        message.setTopicId(command.getTopicId());
        return message;
    }
}

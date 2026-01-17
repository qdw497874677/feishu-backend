package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.event.MessageReceivedEvent;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.service.BotMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 消息事件监听器
 *
 * 异步监听并处理 MessageReceivedEvent，实现非阻塞的消息处理机制
 */
@Slf4j
@Component
public class MessageEventListener {

    @Autowired
    private BotMessageService botMessageService;

    public void setBotMessageService(BotMessageService botMessageService) {
        this.botMessageService = botMessageService;
    }

    /**
     * 异步处理消息接收事件
     *
     * @param event 消息接收事件
     */
    @Async
    @org.springframework.context.event.EventListener
    public void handleMessage(MessageReceivedEvent event) {
        if (!validateEvent(event)) {
            return;
        }

        String correlationId = java.util.UUID.randomUUID().toString();
        log.info("Received message event, messageId: {}, sender: {}, correlationId: {}",
            event.getMessageId(), event.getSender().getOpenId(), correlationId);

        try {
            Message message = new Message(
                event.getMessageId(),
                event.getContent(),
                event.getSender()
            );

            log.debug("Processing message with async listener, messageId: {}, correlationId: {}", 
                message.getMessageId(), correlationId);
            SendResult result = botMessageService.handleMessage(message);
            
            if (result.isSuccess()) {
                log.info("Message processed successfully via async listener, messageId: {}, correlationId: {}", 
                    result.getMessageId(), correlationId);
            } else {
                log.warn("Message processing failed, messageId: {}, correlationId: {}, error: {}", 
                    result.getMessageId(), correlationId, result.getErrorMessage());
            }

        } catch (MessageBizException e) {
            log.warn("Business exception while processing message, messageId: {}, correlationId: {}, error: {}", 
                event.getMessageId(), correlationId, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error processing message asynchronously, messageId: {}, correlationId: {}", 
                event.getMessageId(), correlationId, e);
        }
    }

    private boolean validateEvent(MessageReceivedEvent event) {
        if (event.getMessageId() == null || event.getMessageId().trim().isEmpty()) {
            log.error("MessageId is null or empty");
            return false;
        }

        if (event.getContent() == null || event.getContent().trim().isEmpty()) {
            log.error("Content is null or empty for messageId: {}", event.getMessageId());
            return false;
        }

        if (event.getSender() == null) {
            log.error("Sender is null for messageId: {}", event.getMessageId());
            return false;
        }

        return true;
    }
}

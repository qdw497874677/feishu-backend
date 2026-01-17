package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.event.MessageReceivedEvent;
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
    @Async("webhookTaskExecutor")
    @org.springframework.context.event.EventListener
    public void handleMessage(MessageReceivedEvent event) {
        log.info("Received message event, messageId: {}, sender: {}", 
            event.getMessageId(), event.getSender().getOpenId());

        try {
            Message message = new Message(
                event.getMessageId(),
                event.getContent(),
                event.getSender()
            );

            log.debug("Processing message with async listener: {}", message.getMessageId());
            SendResult result = botMessageService.handleMessage(message);
            
            if (result.isSuccess()) {
                log.info("Message processed successfully via async listener, messageId: {}", 
                    result.getMessageId());
            } else {
                log.warn("Message processing failed, messageId: {}, error: {}", 
                    result.getMessageId(), result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Error processing message asynchronously, messageId: {}", 
                event.getMessageId(), e);
        }
    }
}

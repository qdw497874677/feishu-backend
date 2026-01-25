package com.qdw.feishu.app.listener;

import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.service.BotMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 接收消息监听器执行器
 * 处理从长连接接收到的消息
 */
@Slf4j
@Component
public class ReceiveMessageListenerExe {

    private final BotMessageService botMessageService;

    public ReceiveMessageListenerExe(BotMessageService botMessageService) {
        this.botMessageService = botMessageService;
    }

    /**
     * 处理接收到的消息
     * 异步处理避免阻塞接收线程
     */
    @Async
    public void execute(Message message) {
        log.info("=== 收到新消息 ===");
        log.info("发送者: {}", message.getSender());
        log.info("消息内容: {}", message.getContent());
        log.info("消息ID: {}", message.getMessageId());

        try {
            log.info("开始处理消息...");
            botMessageService.handleMessage(message);
            log.info("消息处理成功");
        } catch (MessageBizException e) {
            log.warn("业务异常: {}", e.getMessage());
        } catch (Exception e) {
            log.error("消息处理失败", e);
        }

        log.info("=== 消息处理流程结束 ===\n");
    }
}

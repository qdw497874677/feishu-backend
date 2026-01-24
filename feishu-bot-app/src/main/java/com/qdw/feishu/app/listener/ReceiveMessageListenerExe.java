package com.qdw.feishu.app.listener;

import com.qdw.feishu.app.router.CommandRouter;
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
    private final CommandRouter commandRouter;

    public ReceiveMessageListenerExe(BotMessageService botMessageService, CommandRouter commandRouter) {
        this.botMessageService = botMessageService;
        this.commandRouter = commandRouter;
    }

    /**
     * 处理接收到的消息
     * 异步处理避免阻塞接收线程
     */
    @Async
    public void execute(Message message) {
        try {
            log.info("Processing message from sender: {}", message.getSender());

            message.validate();

            String reply = commandRouter.route(message);
            if (reply == null) {
                reply = message.generateReply();
            }

            botMessageService.sendReply(message, reply);

            log.info("Message processed successfully");
        } catch (MessageBizException e) {
            log.warn("Business exception while processing message: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while processing message", e);
        }
    }
}

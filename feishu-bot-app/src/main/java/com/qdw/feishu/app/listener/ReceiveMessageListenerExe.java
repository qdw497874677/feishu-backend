package com.qdw.feishu.app.listener;

import com.qdw.feishu.app.router.CommandRouter;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.service.BotMessageService;
import com.qdw.feishu.domain.service.MessageDeduplicator;
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
    private final MessageDeduplicator messageDeduplicator;

    public ReceiveMessageListenerExe(BotMessageService botMessageService,
                                    CommandRouter commandRouter,
                                    MessageDeduplicator messageDeduplicator) {
        this.botMessageService = botMessageService;
        this.commandRouter = commandRouter;
        this.messageDeduplicator = messageDeduplicator;
    }

    /**
     * 处理接收到的消息
     * 异步处理避免阻塞接收线程
     */
    @Async
    public void execute(Message message) {
        log.info("=== 收到新消息 ===");
        log.info("事件ID: {}", message.getEventId());
        log.info("发送者: {}", message.getSender());
        log.info("消息内容: {}", message.getDisplayContent());
        log.info("消息ID: {}", message.getMessageId());

        if (messageDeduplicator.isProcessed(message.getEventId())) {
            log.info("消息已处理过，跳过");
            log.info("=== 消息处理流程结束 ===\n");
            return;
        }

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

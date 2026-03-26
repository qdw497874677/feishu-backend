package com.qdw.feishu.app.listener;

import com.qdw.feishu.app.message.BotMessageAppService;
import com.qdw.feishu.app.opencode.OpenCodeMessageAppService;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.SendResult;
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

    private final BotMessageAppService botMessageAppService;
    private final OpenCodeMessageAppService openCodeMessageAppService;
    private final MessageDeduplicator messageDeduplicator;
    private final FeishuGateway feishuGateway;

    public ReceiveMessageListenerExe(BotMessageAppService botMessageAppService,
                                     OpenCodeMessageAppService openCodeMessageAppService,
                                     MessageDeduplicator messageDeduplicator,
                                     FeishuGateway feishuGateway) {
        this.botMessageAppService = botMessageAppService;
        this.openCodeMessageAppService = openCodeMessageAppService;
        this.messageDeduplicator = messageDeduplicator;
        this.feishuGateway = feishuGateway;
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
            if (!openCodeMessageAppService.tryHandle(message)) {
                botMessageAppService.handleMessage(message);
            }
            log.info("消息处理成功");
        } catch (MessageBizException e) {
            String errorReply = e.getMessage();
            if (errorReply == null || errorReply.isEmpty()) {
                errorReply = "操作失败，请稍后重试";
            }
            SendResult result = feishuGateway.sendMessage(message, errorReply, message.getTopicId());
            if (result.isSuccess()) {
                log.info("业务异常已回复给用户: {}", errorReply);
            } else {
                log.warn("业务异常回复发送失败: {}", result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("消息处理失败", e);
        }

        log.info("=== 消息处理流程结束 ===\n");
    }
}

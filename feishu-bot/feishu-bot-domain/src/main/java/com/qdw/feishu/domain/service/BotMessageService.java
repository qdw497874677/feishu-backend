package com.qdw.feishu.domain.service;

import com.alibaba.cola.extension.ExtensionExecutor;
import com.qdw.feishu.domain.extension.ReplyExtensionPt;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 机器人消息处理领域服务
 *
 * 负责协调消息处理的业务逻辑
 */
@Slf4j
@Service
public class BotMessageService {

    @Autowired
    private FeishuGateway feishuGateway;

    /**
     * 处理消息并生成回复
     *
     * @param message 消息实体
     * @return 发送结果
     */
    public SendResult handleMessage(Message message) {
        log.debug("Handling message: {}", message.getId());

        // 1. 生成原始回复（领域逻辑）
        String originalReply = message.generateReply();

        // 2. 通过扩展点增强回复
        ReplyExtensionPt replyExt = ExtensionExecutor.execute(ReplyExtensionPt.class);
        String replyContent = replyExt.enhanceReply(originalReply, message);

        log.debug("Enhanced reply: {}", replyContent);

        // 3. 发送回复
        SendResult result = feishuGateway.sendReply(
            message.getSender().getOpenId(),
            replyContent
        );

        // 4. 根据发送结果更新消息状态
        if (result.isSuccess()) {
            message.markProcessed();
            log.info("Message processed successfully: {}", result.getMessageId());
        } else {
            log.error("Failed to send message: {}", result.getErrorMessage());
        }

        return result;
    }
}

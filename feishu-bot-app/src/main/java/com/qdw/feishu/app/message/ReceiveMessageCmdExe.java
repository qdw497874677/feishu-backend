package com.qdw.feishu.app.message;

import com.alibaba.cola.catchlog.CatchAndLog;
import com.alibaba.cola.extension.ExtensionExecutor;
import com.qdw.feishu.client.message.MessageServiceI;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.extension.ReplyExtensionPt;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 接收消息命令执行器
 * 
 * 协调领域服务完成业务逻辑
 */
@Slf4j
@CatchAndLog
@Component
public class ReceiveMessageCmdExe implements MessageServiceI {

    @Autowired
    private FeishuGateway feishuGateway;

    @Override
    public SendResult execute(ReceiveMessageCmd cmd) {
        log.info("Executing ReceiveMessageCmd: {}", cmd);

        try {
            // 1. 参数校验
            if (cmd.getContent() == null || cmd.getContent().trim().isEmpty()) {
                throw new MessageBizException("CONTENT_EMPTY", "消息内容为空");
            }

            // 2. 构造发送者对象
            Sender sender = new Sender(
                cmd.getSenderOpenId(),
                cmd.getSenderUserId(),
                cmd.getSenderName() != null ? cmd.getSenderName() : "Unknown"
            );

            // 3. 创建消息实体
            Message message = new Message(
                cmd.getMessageId(),
                cmd.getContent(),
                sender
            );

            // 4. 生成回复内容（领域逻辑）
            String originalReply = message.generateReply();

            // 5. 调用扩展点处理（可插拔）
            ReplyExtensionPt replyExt = ExtensionExecutor.execute(ReplyExtensionPt.class);
            String replyContent = replyExt.enhanceReply(originalReply, message);

            // 6. 发送回复（通过 Gateway）
            SendResult result = feishuGateway.sendReply(
                sender.getOpenId(),
                replyContent
            );

            if (result.isSuccess()) {
                log.info("Reply sent successfully: {}", result.getMessageId());
                // 7. 更新消息状态
                message.markProcessed();
            } else {
                log.error("Failed to send reply: {}", result.getErrorMessage());
            }

            return result;

            return result;

        } catch (MessageBizException e) {
            log.error("Business error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("System error processing message", e);
            throw e;
        }
    }
}

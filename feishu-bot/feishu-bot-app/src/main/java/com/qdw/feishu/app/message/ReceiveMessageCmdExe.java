package com.qdw.feishu.app.message;

import com.alibaba.cola.dto.Response;
import com.alibaba.cola.catchlog.CatchAndLog;
import com.qdw.feishu.client.message.MessageServiceI;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.service.BotMessageService;
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
    private BotMessageService botMessageService;

    @Override
    public Response execute(ReceiveMessageCmd cmd) {
        log.info("Executing ReceiveMessageCmd: {}", cmd);

        try {
            if (cmd.getContent() == null || cmd.getContent().trim().isEmpty()) {
                throw new MessageBizException("CONTENT_EMPTY", "消息内容为空");
            }

            Sender sender = new Sender(
                cmd.getSenderOpenId(),
                cmd.getSenderUserId(),
                cmd.getSenderName() != null ? cmd.getSenderName() : "Unknown"
            );

            Message message = new Message(
                cmd.getMessageId(),
                cmd.getContent(),
                sender
            );

            SendResult result = botMessageService.handleMessage(message);

            return Response.of(result);

        } catch (MessageBizException e) {
            log.error("Business error: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("System error processing message", e);
            throw e;
        }
    }
}

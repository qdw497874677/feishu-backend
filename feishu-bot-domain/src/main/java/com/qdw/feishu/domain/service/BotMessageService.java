package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.router.CommandRouter;
import com.lark.oapi.service.im.v1.model.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotMessageService {

    private final FeishuGateway feishuGateway;
    private final CommandRouter commandRouter;

    public BotMessageService(FeishuGateway feishuGateway, CommandRouter commandRouter) {
        this.feishuGateway = feishuGateway;
        this.commandRouter = commandRouter;
    }

    private String getOpenIdFromSender(Object sender) {
        if (sender instanceof UserId) {
            UserId userId = (UserId) sender;
            return userId.getOpenId() != null ? userId.getOpenId() : "";
        }
        if (sender instanceof String) {
            return (String) sender;
        }
        return "";
    }

    public SendResult handleMessage(Message message) {
        try {
            message.validate();

            String reply = null;
            if (message.getContent().trim().startsWith("/")) {
                reply = commandRouter.route(message);
                log.info("Command routed, reply: {}", reply);
            }

            if (reply == null) {
                reply = message.generateReply();
            }

            Object sender = message.getSender().getOpenId();
            String openId = getOpenIdFromSender(sender);

            SendResult result = feishuGateway.sendReply(
                openId,
                reply
            );

            message.markProcessed();

            return result;

        } catch (MessageBizException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageSysException("MESSAGE_HANDLE_FAILED", "消息处理失败", e);
        }
    }
}

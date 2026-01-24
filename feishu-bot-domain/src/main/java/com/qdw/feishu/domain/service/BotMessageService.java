package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.lark.oapi.service.im.v1.model.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BotMessageService {

    private final FeishuGateway feishuGateway;

    public BotMessageService(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
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

    public SendResult sendReply(Message message, String reply) {
        try {
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

    @Deprecated
    public SendResult handleMessage(Message message) {
        String reply = message.generateReply();
        return sendReply(message, reply);
    }
}

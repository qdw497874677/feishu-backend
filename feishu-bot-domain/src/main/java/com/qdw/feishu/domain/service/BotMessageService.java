package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;

public class BotMessageService {

    private final FeishuGateway feishuGateway;

    public BotMessageService(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }

    public SendResult handleMessage(Message message) {
        try {
            message.validate();

            String reply = message.generateReply();

            SendResult result = feishuGateway.sendReply(
                message.getSender().getOpenId(), 
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

package com.qdw.feishu.infrastructure.gateway;

import com.alibaba.cola.exception.SysException;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.MessageText;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.UserInfo;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 飞书 Gateway 实现
 * 
 * 封装对飞书 SDK 的调用
 * 技术细节（SDK、HTTP、异常）被隔离在基础设施层
 */
@Slf4j
@Component
public class FeishuGatewayImpl implements FeishuGateway {

    private final Client client;

    public FeishuGatewayImpl(@Value("${feishu.appid}") String appId,
                           @Value("${feishu.appsecret}") String appSecret) {
        // 创建 SDK 客户端
        this.client = Client.newBuilder(appId, appSecret)
            .openBaseUrl(BaseUrlEnum.FeiShu)
            .build();
        log.info("Feishu SDK Client initialized with appId: {}", appId);
    }

    @Override
    public SendResult sendReply(String receiveOpenId, String content) {
        log.info("Sending reply to: {}, content: {}", receiveOpenId, content);

        try {
            // 使用 SDK 发送消息
            CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType("open_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                    .receiveId(receiveOpenId)
                    .msgType("text")
                    .content(MessageText.newBuilder()
                        .text(content)
                        .build())
                    .build())
                .build());

            CreateMessageResp resp = client.im().message().create(req);

            if (resp.getCode() != 0) {
                log.error("Failed to send message: {}", resp.getMsg());
                throw new SysException("SEND_FAILED", resp.getMsg());
            }

            return SendResult.success(resp.getData().getMessageId());

        } catch (Exception e) {
            log.error("Exception sending message", e);
            throw new SysException("SEND_ERROR", "发送消息异常", e);
        }
    }

    @Override
    public UserInfo getUserInfo(String openId) {
        // TODO: 实现获取用户信息
        log.info("Getting user info for: {}", openId);
        return new UserInfo(openId, "unknown", "Unknown User");
    }
}

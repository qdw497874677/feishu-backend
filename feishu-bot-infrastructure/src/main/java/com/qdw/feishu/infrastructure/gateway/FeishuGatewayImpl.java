package com.qdw.feishu.infrastructure.gateway;

import com.alibaba.cola.exception.SysException;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.ext.MessageText;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.UserInfo;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class FeishuGatewayImpl implements FeishuGateway {

    private final com.lark.oapi.Client httpClient;

    public FeishuGatewayImpl(@Value("${feishu.appid}") String appId,
                            @Value("${feishu.appsecret}") String appSecret) {
        this.httpClient = com.lark.oapi.Client.newBuilder(appId, appSecret)
            .openBaseUrl(BaseUrlEnum.FeiShu)
            .build();
        log.info("Feishu SDK Client initialized with appId: {}", appId);
    }

    @Override
    public SendResult sendReply(String receiveOpenId, String content) {
        log.info("Sending reply to: {}, content: {}", receiveOpenId, content);

        try {
            // 构建消息请求，使用 JSON 格式的 content
            CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType("open_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                    .receiveId(receiveOpenId)
                    .msgType("text")
                    .content(MessageText.newBuilder().text(content).build())
                    .build())
                .build();

            CreateMessageResp resp = httpClient.im().message().create(req);

            if (resp.getCode() != 0) {
                log.error("Failed to send message: {}", resp.getMsg());
                throw new SysException("SEND_FAILED", resp.getMsg());
            }

            return SendResult.success(resp.getData().getMessageId());

        } catch (Exception e) {
            log.error("Exception sending message", e);
            throw new SysException("SEND_ERROR", "send message failed", e);
        }
    }

    @Override
    public UserInfo getUserInfo(String openId) {
        log.info("Getting user info for: {}", openId);
        return new UserInfo(openId, "unknown", "Unknown User");
    }
}

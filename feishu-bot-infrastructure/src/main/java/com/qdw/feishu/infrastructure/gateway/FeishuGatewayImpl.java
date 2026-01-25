package com.qdw.feishu.infrastructure.gateway;

import com.alibaba.cola.exception.SysException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.UserInfo;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Slf4j
@Component
public class FeishuGatewayImpl implements FeishuGateway {

    private final com.lark.oapi.Client httpClient;
    private final ObjectMapper objectMapper;

    public FeishuGatewayImpl(@Value("${feishu.appid}") String appId,
                            @Value("${feishu.appsecret}") String appSecret) {
        this.httpClient = com.lark.oapi.Client.newBuilder(appId, appSecret)
            .openBaseUrl(BaseUrlEnum.FeiShu)
            .build();
        this.objectMapper = new ObjectMapper();
        log.info("Feishu SDK Client initialized with appId: {}", appId);
    }

    @Override
    public SendResult sendReply(String receiveOpenId, String content) {
        log.info("Sending reply to: {}, content: {}", receiveOpenId, content);

        try {
            Map<String, String> textContent = new HashMap<>();
            textContent.put("text", content);
            String jsonContent = objectMapper.writeValueAsString(textContent);

            CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType("open_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                    .receiveId(receiveOpenId)
                    .msgType("text")
                    .content(jsonContent)
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
    public SendResult sendMessage(Message message, String content, String topicId) {
        log.info("Sending message to chatId: {}, content: {}, topicId: {}", message.getChatId(), content, topicId);

        try {
            Map<String, String> textContent = new HashMap<>();
            textContent.put("text", content);
            String jsonContent = objectMapper.writeValueAsString(textContent);

            CreateMessageReq.Builder reqBuilder = CreateMessageReq.newBuilder()
                .receiveIdType("chat_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                    .receiveId(message.getChatId())
                    .msgType("text")
                    .content(jsonContent)
                    .build());

            if (topicId != null && !topicId.isEmpty()) {
                log.info("Replying to topic: {}", topicId);

                CreateMessageReqBody.Builder bodyBuilder = CreateMessageReqBody.newBuilder()
                    .receiveId(message.getChatId())
                    .msgType("text")
                    .content(jsonContent);

                reqBuilder.createMessageReqBody(bodyBuilder.build());

                log.warn("Topic reply not fully implemented: SDK v2.5.2 does not support root_id field.");
                log.warn("To enable topic reply, upgrade SDK or implement custom request builder.");
            }

            CreateMessageReq req = reqBuilder.build();
            CreateMessageResp resp = httpClient.im().message().create(req);

            if (resp.getCode() != 0) {
                log.error("Failed to send message: {}", resp.getMsg());
                throw new SysException("SEND_FAILED", resp.getMsg());
            }

            log.info("Send message success: messageId={}, topicId={}", resp.getData().getMessageId(), topicId);
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

package com.qdw.feishu.infrastructure.gateway;

import com.alibaba.cola.exception.SysException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.core.response.RawResponse;
import com.lark.oapi.core.token.AccessTokenType;
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
            if (topicId != null && !topicId.isEmpty()) {
                log.info("Replying to thread: {}", topicId);
                return sendThreadReply(message.getChatId(), content, topicId);
            } else {
                log.info("Creating thread in chat");
                return createThread(message.getChatId(), content);
            }

        } catch (Exception e) {
            log.error("Exception sending message", e);
            throw new SysException("SEND_ERROR", "send message failed", e);
        }
    }

    private SendResult createThread(String chatId, String content) throws Exception {
        Map<String, String> textContent = new HashMap<>();
        textContent.put("text", content);
        String jsonContent = objectMapper.writeValueAsString(textContent);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("receive_id_type", "chat_id");
        requestBody.put("receive_id", chatId);
        requestBody.put("msg_type", "text");
        requestBody.put("content", jsonContent);
        requestBody.put("reply_in_thread", "true");

        RawResponse response = httpClient.post("/open-apis/im/v1/messages", requestBody, AccessTokenType.Tenant, null);

        if (response.getStatusCode() != 200) {
            String errorMsg = "Failed to create thread: HTTP " + response.getStatusCode();
            log.error(errorMsg);
            throw new SysException("SEND_FAILED", errorMsg);
        }

        String responseBody = new String(response.getBody(), "UTF-8");
        Map<String, Object> responseData = objectMapper.readValue(responseBody, Map.class);
        int code = (int) responseData.get("code");
        if (code != 0) {
            String errorMsg = (String) responseData.get("msg");
            log.error("Failed to create thread: {}", errorMsg);
            throw new SysException("SEND_FAILED", errorMsg);
        }

        Map<String, Object> data = (Map<String, Object>) responseData.get("data");
        String messageId = (String) data.get("message_id");
        log.info("Thread created success: messageId={}", messageId);
        return SendResult.success(messageId);
    }

    private SendResult sendThreadReply(String chatId, String content, String threadId) throws Exception {
        Map<String, String> textContent = new HashMap<>();
        textContent.put("text", content);
        String jsonContent = objectMapper.writeValueAsString(textContent);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("receive_id_type", "thread_id");
        requestBody.put("receive_id", threadId);
        requestBody.put("msg_type", "text");
        requestBody.put("content", jsonContent);

        RawResponse response = httpClient.post("/open-apis/im/v1/messages", requestBody, AccessTokenType.Tenant, null);

        if (response.getStatusCode() != 200) {
            String errorMsg = "Failed to send thread reply: HTTP " + response.getStatusCode();
            log.error(errorMsg);
            throw new SysException("SEND_FAILED", errorMsg);
        }

        String responseBody = new String(response.getBody(), "UTF-8");
        Map<String, Object> responseData = objectMapper.readValue(responseBody, Map.class);
        int code = (int) responseData.get("code");
        if (code != 0) {
            String errorMsg = (String) responseData.get("msg");
            log.error("Failed to send thread reply: {}", errorMsg);
            throw new SysException("SEND_FAILED", errorMsg);
        }

        Map<String, Object> data = (Map<String, Object>) responseData.get("data");
        String messageId = (String) data.get("message_id");
        log.info("Thread reply success: messageId={}, threadId={}", messageId, threadId);
        return SendResult.success(messageId);
    }

    @Override
    public UserInfo getUserInfo(String openId) {
        log.info("Getting user info for: {}", openId);
        return new UserInfo(openId, "unknown", "Unknown User");
    }
}

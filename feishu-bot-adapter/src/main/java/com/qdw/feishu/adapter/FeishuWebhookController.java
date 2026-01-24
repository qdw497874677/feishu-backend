package com.qdw.feishu.adapter;

import com.alibaba.cola.dto.Response;
import com.alibaba.cola.exception.SysException;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.BotMessageService;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * 飞书 Webhook 接口适配器
 *
 * 职责：
 * - 接收飞书服务器的 Webhook 请求
 * - 验证请求的合法性（URL 验证）
 * - 解析消息数据并传递到应用层
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class FeishuWebhookController {

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private BotMessageService botMessageService;

    private final Gson gson = new Gson();

    /**
     * 接收飞书消息 Webhook
     */
    @PostMapping("/feishu")
    public Response receiveMessage(@RequestBody String requestBody) {
        try {
            log.info("Received webhook request: {}", requestBody);

            // 解析 JSON
            JsonObject eventJson = gson.fromJson(requestBody, JsonObject.class);
            String eventType = eventJson.get("type").getAsString();
            log.info("Event type: {}", eventType);

            // 只处理消息接收事件
            if ("im.message.receive_v1".equals(eventType)) {
                JsonObject event = eventJson.getAsJsonObject("event");
                JsonObject message = event.getAsJsonObject("message");

                String messageId = message.get("message_id").getAsString();
                String senderOpenId = message.getAsJsonObject("sender").get("open_id").getAsString();
                String senderUserId = message.getAsJsonObject("sender").get("user_id").getAsString();
                String senderName = message.getAsJsonObject("sender").get("name").getAsString();

                String content = "";
                if (message.has("content") && message.getAsJsonObject("content").has("text")) {
                    content = message.getAsJsonObject("content").get("text").getAsString();
                }

                handleReceivedMessage(messageId, content, senderOpenId, senderUserId, senderName);
            }

            return Response.buildSuccess();

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            throw new SysException("WEBHOOK_ERROR", "处理 Webhook 请求失败", e);
        }
    }

    /**
     * URL 验证 - 飞书会在配置 Webhook 时调用此接口验证
     */
    @RequestMapping(value = "/feishu", method = {RequestMethod.GET})
    public String verifyUrl(@RequestHeader("X-Lark-Request-Timestamp") String timestamp,
                        @RequestHeader("X-Lark-Request-Nonce") String nonce,
                        @RequestHeader("X-Lark-Signature") String signature) {
        try {
            log.info("URL verification request");

            if (!verifySignature(timestamp, nonce, signature)) {
                log.warn("Signature verification failed");
                return "";
            }

            // 返回 encrypt_key 作为验证响应
            return feishuProperties.getEncryptKey();

        } catch (Exception e) {
            log.error("Error verifying URL", e);
            return "";
        }
    }

    /**
     * 验证请求签名
     */
    private boolean verifySignature(String timestamp, String nonce, String signature) {
        try {
            String encryptKey = feishuProperties.getEncryptKey();
            if (encryptKey == null || encryptKey.isEmpty()) {
                log.warn("Encrypt key not configured, skipping signature verification");
                return true;
            }

            String signStr = timestamp + nonce + encryptKey;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(encryptKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signBytes = mac.doFinal(signStr.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(signBytes);

            return expectedSignature.equals(signature);

        } catch (Exception e) {
            log.error("Error verifying signature", e);
            return false;
        }
    }

    /**
     * 处理接收到的消息
     */
    private void handleReceivedMessage(String messageId, String content, String senderOpenId, String senderUserId, String senderName) {
        try {
            Sender sender = new Sender();
            sender.setUserId(senderOpenId);
            sender.setOpenId(senderOpenId);
            sender.setName(senderName);

            Message message = new Message(messageId, content, sender);

            log.info("Processing message from {}: {}", senderName, content);
            botMessageService.handleMessage(message);

        } catch (Exception e) {
            log.error("Error handling received message", e);
        }
    }

    /**
     * 健康检查接口
     */
    @PostMapping("/health")
    public Response health() {
        return Response.buildSuccess();
    }
}

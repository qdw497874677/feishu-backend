package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.FeishuProperties;
import com.qdw.feishu.domain.gateway.WebhookValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

/**
 * 飞书 Webhook 签名验证实现
 *
 * 使用 HMAC-SHA256 算法验证请求签名
 * 参考飞书官方文档：https://open.feishu.cn/document/server-docs/event-subscription-guide/
 */
@Slf4j
@Component
public class WebhookValidatorImpl implements WebhookValidator {

    private static final String SIGNATURE_HEADER = "X-Lark-Request-Nonce";
    private static final String TIMESTAMP_HEADER = "X-Lark-Request-Timestamp";
    private static final String SIGNATURE_V2_HEADER = "X-Lark-Signature-v2";

    // 时间戳有效窗口：5分钟
    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    @Autowired
    private FeishuProperties feishuProperties;

    @Override
    public WebhookValidationResult validate(Map<String, String> headers, String body) {
        log.debug("Validating webhook request with headers: {}", headers.keySet());

        try {
            // 1. 检查必需的请求头
            String signature = headers.get(SIGNATURE_V2_HEADER);
            String timestamp = headers.get(TIMESTAMP_HEADER);
            String nonce = headers.get(SIGNATURE_HEADER);

            if (signature == null || signature.trim().isEmpty()) {
                return WebhookValidationResult.failure("Missing signature header");
            }

            if (timestamp == null || timestamp.trim().isEmpty()) {
                return WebhookValidationResult.failure("Missing timestamp header");
            }

            if (nonce == null || nonce.trim().isEmpty()) {
                return WebhookValidationResult.failure("Missing nonce header");
            }

            // 2. 验证时间戳（防止重放攻击）
            if (!validateTimestamp(timestamp)) {
                return WebhookValidationResult.failure("Timestamp validation failed: request too old or in future");
            }

            // 3. 计算 HMAC-SHA256 签名
            String expectedSignature = calculateSignature(timestamp, nonce, body);

            // 4. 比对签名
            if (!signature.equals(expectedSignature)) {
                log.warn("Signature mismatch. Expected: {}, Received: {}", expectedSignature, signature);
                return WebhookValidationResult.failure("Invalid signature");
            }

            log.info("Webhook validation successful");
            return WebhookValidationResult.success();

        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return WebhookValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * 验证时间戳是否在有效窗口内
     *
     * @param timestampStr 时间戳字符串（毫秒）
     * @return 是否有效
     */
    private boolean validateTimestamp(String timestampStr) {
        try {
            long requestTimestamp = Long.parseLong(timestampStr);
            long currentTime = Instant.now().toEpochMilli();

            long diffSeconds = Math.abs(currentTime - requestTimestamp) / 1000;

            if (diffSeconds > TIMESTAMP_TOLERANCE_SECONDS) {
                log.warn("Timestamp out of tolerance. Request: {}, Current: {}, Diff: {}s",
                    requestTimestamp, currentTime, diffSeconds);
                return false;
            }

            return true;
        } catch (NumberFormatException e) {
            log.error("Invalid timestamp format: {}", timestampStr);
            return false;
        }
    }

    /**
     * 计算 HMAC-SHA256 签名
     *
     * 飞书签名算法：HMAC-SHA256(timestamp + nonce + body)
     *
     * @param timestamp 时间戳（毫秒）
     * @param nonce 随机字符串
     * @param body 请求体
     * @return Base64 编码的签名
     */
    private String calculateSignature(String timestamp, String nonce, String body) {
        try {
            String encryptKey = feishuProperties.getEncryptKey();
            if (encryptKey == null || encryptKey.trim().isEmpty()) {
                throw new IllegalStateException("Feishu encrypt key is not configured");
            }

            // 构造签名内容：timestamp + nonce + body
            String signContent = timestamp + nonce + body;

            // 初始化 MAC
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                encryptKey.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);

            // 计算签名
            byte[] signatureBytes = mac.doFinal(signContent.getBytes(StandardCharsets.UTF_8));

            // Base64 编码
            return Base64.getEncoder().encodeToString(signatureBytes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate signature", e);
        }
    }
}

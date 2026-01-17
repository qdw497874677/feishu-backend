package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.FeishuProperties;
import com.qdw.feishu.domain.gateway.WebhookValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookValidator tests")
class WebhookValidatorImplTest {

    @Mock
    private FeishuProperties feishuProperties;

    private WebhookValidator webhookValidator;

    private static final String ENCRYPT_KEY = "test_encrypt_key_1234567890123456";
    private static final String NONCE = "test_nonce";
    private static final String BODY = "{\"type\":\"message\"}";

    @BeforeEach
    void setUp() {
        when(feishuProperties.getEncryptKey()).thenReturn(ENCRYPT_KEY);
        webhookValidator = new WebhookValidatorImpl();
        ((WebhookValidatorImpl) webhookValidator).setFeishuProperties(feishuProperties);
    }

    @Test
    @DisplayName("Should validate valid signature")
    void shouldValidateValidSignature() {
        long timestamp = Instant.now().toEpochMilli();
        String expectedSignature = calculateSignature(timestamp, NONCE, BODY);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Lark-Signature-v2", expectedSignature);
        headers.put("X-Lark-Request-Timestamp", String.valueOf(timestamp));
        headers.put("X-Lark-Request-Nonce", NONCE);

        var result = webhookValidator.validate(headers, BODY);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should reject missing signature")
    void shouldRejectMissingSignature() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Lark-Request-Timestamp", String.valueOf(Instant.now().toEpochMilli()));
        headers.put("X-Lark-Request-Nonce", NONCE);

        var result = webhookValidator.validate(headers, BODY);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("Missing signature");
    }

    @Test
    @DisplayName("Should reject invalid signature")
    void shouldRejectInvalidSignature() {
        long timestamp = Instant.now().toEpochMilli();

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Lark-Signature-v2", "invalid_signature");
        headers.put("X-Lark-Request-Timestamp", String.valueOf(timestamp));
        headers.put("X-Lark-Request-Nonce", NONCE);

        var result = webhookValidator.validate(headers, BODY);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("Invalid signature");
    }

    @Test
    @DisplayName("Should reject timestamp too old")
    void shouldRejectOldTimestamp() {
        long timestamp = Instant.now().minus(400, java.time.temporal.ChronoUnit.SECONDS)
            .toEpochMilli();
        String signature = calculateSignature(timestamp, NONCE, BODY);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Lark-Signature-v2", signature);
        headers.put("X-Lark-Request-Timestamp", String.valueOf(timestamp));
        headers.put("X-Lark-Request-Nonce", NONCE);

        var result = webhookValidator.validate(headers, BODY);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("Timestamp validation failed");
    }

    @Test
    @DisplayName("Should reject timestamp in future")
    void shouldRejectFutureTimestamp() {
        long timestamp = Instant.now().plus(400, java.time.temporal.ChronoUnit.SECONDS)
            .toEpochMilli();
        String signature = calculateSignature(timestamp, NONCE, BODY);

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Lark-Signature-v2", signature);
        headers.put("X-Lark-Request-Timestamp", String.valueOf(timestamp));
        headers.put("X-Lark-Request-Nonce", NONCE);

        var result = webhookValidator.validate(headers, BODY);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getError()).contains("Timestamp validation failed");
    }

    private String calculateSignature(long timestamp, String nonce, String body) {
        try {
            String signContent = timestamp + nonce + body;

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                ENCRYPT_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] signatureBytes = mac.doFinal(signContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate signature", e);
        }
    }
}

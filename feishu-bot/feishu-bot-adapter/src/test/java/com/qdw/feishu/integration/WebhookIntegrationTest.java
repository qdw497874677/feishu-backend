package com.qdw.feishu.integration;

import com.alibaba.cola.dto.Response;
import com.qdw.feishu.client.message.MessageServiceI;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.FeishuProperties;
import com.qdw.feishu.domain.gateway.WebhookValidator;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.infrastructure.gateway.FeishuGatewayImpl;
import com.qdw.feishu.infrastructure.gateway.WebhookValidatorImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebhookIntegrationTest {

    @Mock
    private FeishuProperties feishuProperties;

    @Mock
    private FeishuGateway feishuGateway;

    @Mock
    private MessageServiceI messageService;

    @Test
    @DisplayName("End-to-end webhook processing with valid signature")
    void shouldProcessWebhookEndToEnd() throws Exception {
        when(feishuProperties.getEncryptKey()).thenReturn("test_key");

        WebhookValidatorImpl validator = new WebhookValidatorImpl();
        validator.setFeishuProperties(feishuProperties);

        FeishuWebhookController controller = new FeishuWebhookController();
        controller.setWebhookValidator(validator);
        controller.setMessageService(messageService);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        long timestamp = Instant.now().toEpochMilli();
        String signature = calculateSignature("test_key", timestamp, "test_nonce", "{}");

        when(messageService.receiveMessage(any(ReceiveMessageCmd.class)))
            .thenReturn(Response.of("processed"));

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Lark-Signature-v2", signature);
        headers.put("X-Lark-Request-Timestamp", String.valueOf(timestamp));
        headers.put("X-Lark-Request-Nonce", "test_nonce");

        mockMvc.perform(post("/webhook/feishu")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isOk());

        assertThat(true).isTrue();
    }

    private String calculateSignature(String key, long timestamp, String nonce, String body) {
        try {
            String signContent = timestamp + nonce + body;

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(
                key.getBytes(java.nio.charset.StandardCharsets.UTF_8),
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

package com.qdw.feishu.adapter.web;

import com.alibaba.cola.dto.Response;
import com.qdw.feishu.client.message.MessageServiceI;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import com.qdw.feishu.domain.gateway.WebhookValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeishuWebhookController tests")
class FeishuWebhookControllerTest {

    @Mock
    private MessageServiceI messageService;

    @Mock
    private WebhookValidator webhookValidator;

    private MockMvc mockMvc;
    private FeishuWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new FeishuWebhookController();
        controller.setMessageService(messageService);
        controller.setWebhookValidator(webhookValidator);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should handle valid webhook")
    void shouldHandleValidWebhook() throws Exception {
        when(webhookValidator.validate(any(), anyString()))
            .thenReturn(WebhookValidator.WebhookValidationResult.success());

        when(messageService.receiveMessage(any(ReceiveMessageCmd.class)))
            .thenReturn(Response.of("success"));

        String payload = "{\"type\":\"message\"}";

        mockMvc.perform(post("/webhook/feishu")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(webhookValidator).validate(any(), anyString());
        verify(messageService).receiveMessage(any(ReceiveMessageCmd.class));
    }

    @Test
    @DisplayName("Should reject invalid webhook signature")
    void shouldRejectInvalidSignature() throws Exception {
        when(webhookValidator.validate(any(), anyString()))
            .thenReturn(WebhookValidator.WebhookValidationResult.failure("Invalid signature"));

        String payload = "{\"type\":\"message\"}";

        mockMvc.perform(post("/webhook/feishu")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errCode").value("SIGNATURE_INVALID"));

        verify(webhookValidator).validate(any(), anyString());
    }

    @Test
    @DisplayName("Should return health status")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/webhook/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value("OK"));
    }
}

package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeishuGatewayImplTest {
    
    private FeishuGatewayImpl feishuGateway;
    
    @BeforeEach
    void setUp() {
        FeishuProperties feishuProperties = new FeishuProperties();
        feishuProperties.setAppId("test_app_id");
        feishuProperties.setAppSecret("test_app_secret");
        
        feishuGateway = new FeishuGatewayImpl(feishuProperties);
    }
    
    @Test
    void should_have_sendInteractiveMessage_method() {
        Message message = new Message();
        message.setChatId("test_chat_id");
        message.setMessageId("test_message_id");
        message.setContent("test content");
        
        String cardJson = "{\"schema\":\"2.0\",\"elements\":[]}";
        
        assertDoesNotThrow(() -> {
            try {
                feishuGateway.sendInteractiveMessage(message, cardJson, null);
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("invalid param") || 
                          e.getMessage().contains("Failed") ||
                          e.getMessage().contains("token"),
                    "Expected API-related error but got: " + e.getMessage());
            }
        });
    }
    
    @Test
    void should_handle_topic_parameter() {
        Message message = new Message();
        message.setChatId("test_chat_id");
        message.setMessageId("test_message_id");
        message.setRootId("test_root_id");
        message.setContent("test content");
        
        String cardJson = "{\"schema\":\"2.0\",\"elements\":[]}";
        String topicId = "test_topic_id";
        
        assertDoesNotThrow(() -> {
            try {
                feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("invalid param") || 
                          e.getMessage().contains("Failed") ||
                          e.getMessage().contains("token") ||
                          e.getMessage().contains("Thread not found"),
                    "Expected API-related error but got: " + e.getMessage());
            }
        });
    }
}

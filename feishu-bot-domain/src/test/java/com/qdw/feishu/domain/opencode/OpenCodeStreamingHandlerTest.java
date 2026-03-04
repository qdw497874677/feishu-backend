package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.card.StreamingCardManager;
import com.qdw.feishu.domain.config.CardProperties;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OpenCodeStreamingHandler 单元测试
 */
class OpenCodeStreamingHandlerTest {

    @Mock
    private FeishuGateway feishuGateway;

    @Mock
    private StreamingCardManager cardManager;

    private CardProperties cardProperties;
    private OpenCodeStreamingHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cardProperties = new CardProperties();
        cardProperties.setEnabled(true);
        cardProperties.setFallbackOnError(true);
        cardProperties.setTitle("🤖 AI 助手");
        cardProperties.setThinkingText("⏳ 正在思考...");
        cardProperties.setProcessingText("⏳ 处理中...");
        cardProperties.setCompleteText("✅ 完成");
        handler = new OpenCodeStreamingHandler(feishuGateway, cardManager, cardProperties);
    }

    @Test
    @DisplayName("registerSession - 卡片创建成功，应使用卡片模式")
    void registerSession_cardSuccess_shouldUseCardMode() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        String cardId = "card-789";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(eq(message), eq(cardProperties.getThinkingText()), eq(topicId)))
                .thenReturn(cardId);

        handler.registerSession(sessionId, message);

        verify(cardManager).createAndSend(eq(message), eq(cardProperties.getThinkingText()), eq(topicId));
        verify(feishuGateway, never()).sendMessage(any(), anyString(), any());
    }

    @Test
    @DisplayName("registerSession - 卡片创建失败，应降级为普通消息")
    void registerSession_cardFailed_shouldFallback() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(any(), anyString(), anyString()))
                .thenReturn(null);

        handler.registerSession(sessionId, message);

        verify(cardManager).createAndSend(any(), anyString(), anyString());
        verify(feishuGateway).sendMessage(eq(message), eq(cardProperties.getThinkingText()), eq(topicId));
    }

    @Test
    @DisplayName("handleTextDelta - 卡片模式应更新卡片")
    void handleTextDelta_cardMode_shouldUpdateCard() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        String cardId = "card-789";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(any(), anyString(), anyString()))
                .thenReturn(cardId);
        when(cardManager.update(eq(cardId), anyString())).thenReturn(true);

        handler.registerSession(sessionId, message);

        OpenCodeEvent event = createTextEvent(sessionId, "Hello");
        handler.handleEvent(event);

        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(cardManager, atLeastOnce()).update(eq(cardId), contains("Hello"));
    }

    @Test
    @DisplayName("handleTextDelta - 降级模式应发送普通消息")
    void handleTextDelta_fallbackMode_shouldSendMessage() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(any(), anyString(), anyString()))
                .thenReturn(null);
        when(feishuGateway.sendMessage(any(), anyString(), any()))
                .thenReturn(SendResult.success("msg-id"));

        handler.registerSession(sessionId, message);

        OpenCodeEvent event = createTextEvent(sessionId, "Hello");
        handler.handleEvent(event);

        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        verify(feishuGateway, atLeast(2)).sendMessage(eq(message), anyString(), eq(topicId));
    }

    @Test
    @DisplayName("handleSessionComplete - 卡片模式应更新完成状态并清理")
    void handleSessionComplete_cardMode_shouldUpdateCompleteAndCleanup() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        String cardId = "card-789";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(any(), anyString(), anyString()))
                .thenReturn(cardId);
        when(cardManager.update(eq(cardId), anyString())).thenReturn(true);

        handler.registerSession(sessionId, message);

        OpenCodeEvent textEvent = createTextEvent(sessionId, "Hello");
        handler.handleEvent(textEvent);

        OpenCodeEvent completeEvent = createCompleteEvent(sessionId);
        handler.handleEvent(completeEvent);

        verify(cardManager).cleanup(eq(cardId));
        verify(cardManager, atLeastOnce()).update(eq(cardId), contains(cardProperties.getCompleteText()));
    }

    @Test
    @DisplayName("handleSessionComplete - 降级模式应发送完成消息")
    void handleSessionComplete_fallbackMode_shouldSendCompleteMessage() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(any(), anyString(), anyString()))
                .thenReturn(null);
        when(feishuGateway.sendMessage(any(), anyString(), any()))
                .thenReturn(SendResult.success("msg-id"));

        handler.registerSession(sessionId, message);

        OpenCodeEvent textEvent = createTextEvent(sessionId, "Hello");
        handler.handleEvent(textEvent);

        OpenCodeEvent completeEvent = createCompleteEvent(sessionId);
        handler.handleEvent(completeEvent);

        verify(cardManager, never()).cleanup(anyString());
        verify(feishuGateway, atLeast(2)).sendMessage(eq(message), anyString(), eq(topicId));
    }

    @Test
    @DisplayName("unregisterSession - 应清理所有资源")
    void unregisterSession_shouldCleanupAllResources() {
        String sessionId = "session-123";
        String topicId = "topic-456";
        String cardId = "card-789";
        Message message = createTestMessage(topicId);

        when(cardManager.isEnabled()).thenReturn(true);
        when(cardManager.createAndSend(any(), anyString(), anyString()))
                .thenReturn(cardId);

        handler.registerSession(sessionId, message);
        handler.unregisterSession(sessionId);

        OpenCodeEvent event = createTextEvent(sessionId, "Hello");
        handler.handleEvent(event);

        verify(cardManager, never()).update(anyString(), anyString());
    }

    private Message createTestMessage(String topicId) {
        Message message = mock(Message.class);
        when(message.getTopicId()).thenReturn(topicId);
        when(message.getMessageId()).thenReturn("msg-id");
        when(message.getContent()).thenReturn("/opencode chat test");
        return message;
    }

    private OpenCodeEvent createTextEvent(String sessionId, String delta) {
        OpenCodeEvent event = mock(OpenCodeEvent.class);
        when(event.getSessionId()).thenReturn(sessionId);
        when(event.isTextUpdate()).thenReturn(true);
        when(event.isStatusUpdate()).thenReturn(false);
        when(event.getDelta()).thenReturn(delta);
        return event;
    }

    private OpenCodeEvent createCompleteEvent(String sessionId) {
        OpenCodeEvent event = mock(OpenCodeEvent.class);
        when(event.getSessionId()).thenReturn(sessionId);
        when(event.isTextUpdate()).thenReturn(false);
        when(event.isStatusUpdate()).thenReturn(true);
        when(event.isSessionIdle()).thenReturn(true);
        return event;
    }
}

package com.qdw.feishu.domain.card;

import com.qdw.feishu.domain.gateway.CardGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StreamingCardManagerTest {

    private StreamingCardManager manager;
    private CardGateway cardGateway;

    @BeforeEach
    void setUp() {
        cardGateway = mock(CardGateway.class);
        manager = new StreamingCardManager(cardGateway);
    }

    @Test
    void should_createCardAndSendMessage_when_createAndSendCalled() {
        Message message = createTestMessage();
        String cardId = "card_123";
        
        when(cardGateway.createCard("Test Title", "Initial Content"))
            .thenReturn(cardId);
        when(cardGateway.sendCardMessage(message, cardId, "topic_1"))
            .thenReturn(SendResult.success("msg_123"));

        String result = manager.createAndSend(message, "Test Title", "Initial Content", "topic_1");

        assertEquals(cardId, result);
        verify(cardGateway).createCard("Test Title", "Initial Content");
        verify(cardGateway).sendCardMessage(message, cardId, "topic_1");
        assertEquals(1, manager.getSequence(cardId));
    }

    @Test
    void should_returnNull_when_createCardFails() {
        Message message = createTestMessage();
        
        when(cardGateway.createCard(anyString(), anyString()))
            .thenReturn(null);

        String result = manager.createAndSend(message, "Title", "Content", null);

        assertNull(result);
        verify(cardGateway, never()).sendCardMessage(any(), any(), any());
    }

    @Test
    void should_returnNullAndCleanup_when_sendCardMessageFails() {
        Message message = createTestMessage();
        String cardId = "card_123";
        
        when(cardGateway.createCard("Title", "Content"))
            .thenReturn(cardId);
        when(cardGateway.sendCardMessage(message, cardId, null))
            .thenReturn(SendResult.failure("Network error"));

        String result = manager.createAndSend(message, "Title", "Content", null);

        assertNull(result);
        assertFalse(manager.exists(cardId));
    }

    @Test
    void should_updateCardWithIncrementingSequence_when_updateCalled() {
        Message message = createTestMessage();
        String cardId = "card_123";
        
        when(cardGateway.createCard("Title", "Content"))
            .thenReturn(cardId);
        when(cardGateway.sendCardMessage(message, cardId, null))
            .thenReturn(SendResult.success("msg_123"));
        when(cardGateway.updateCard(cardId, "Update 1", 2))
            .thenReturn(true);
        when(cardGateway.updateCard(cardId, "Update 2", 3))
            .thenReturn(true);
        
        manager.createAndSend(message, "Title", "Content", null);

        boolean result1 = manager.update(cardId, "Update 1");
        boolean result2 = manager.update(cardId, "Update 2");

        assertTrue(result1);
        assertTrue(result2);
        assertEquals(3, manager.getSequence(cardId));
        verify(cardGateway).updateCard(cardId, "Update 1", 2);
        verify(cardGateway).updateCard(cardId, "Update 2", 3);
    }

    @Test
    void should_notIncrementSequence_when_updateFails() {
        Message message = createTestMessage();
        String cardId = "card_123";
        
        when(cardGateway.createCard("Title", "Content"))
            .thenReturn(cardId);
        when(cardGateway.sendCardMessage(message, cardId, null))
            .thenReturn(SendResult.success("msg_123"));
        when(cardGateway.updateCard(cardId, "Failed Update", 2))
            .thenReturn(false);

        manager.createAndSend(message, "Title", "Content", null);
        boolean result = manager.update(cardId, "Failed Update");

        assertFalse(result);
        assertEquals(1, manager.getSequence(cardId));
    }

    @Test
    void should_startFromSequence1_when_cardNotInitialized() {
        String cardId = "new_card";
        
        when(cardGateway.updateCard(cardId, "Content", 1))
            .thenReturn(true);

        boolean result = manager.update(cardId, "Content");

        assertTrue(result);
        assertEquals(1, manager.getSequence(cardId));
    }

    @Test
    void should_removeSequence_when_cleanupCalled() {
        Message message = createTestMessage();
        String cardId = "card_123";
        
        when(cardGateway.createCard("Title", "Content"))
            .thenReturn(cardId);
        when(cardGateway.sendCardMessage(message, cardId, null))
            .thenReturn(SendResult.success("msg_123"));
        
        manager.createAndSend(message, "Title", "Content", null);
        
        assertTrue(manager.exists(cardId));
        
        manager.cleanup(cardId);
        
        assertFalse(manager.exists(cardId));
        assertEquals(0, manager.getSequence(cardId));
    }

    @Test
    void should_handleCleanupForNonExistentCard() {
        manager.cleanup("nonexistent_card");
        
        assertFalse(manager.exists("nonexistent_card"));
        assertEquals(0, manager.getSequence("nonexistent_card"));
    }

    @Test
    void should_trackMultipleCardsIndependently() {
        Message message = createTestMessage();
        String cardId1 = "card_1";
        String cardId2 = "card_2";
        
        when(cardGateway.createCard("Title 1", "Content 1"))
            .thenReturn(cardId1);
        when(cardGateway.createCard("Title 2", "Content 2"))
            .thenReturn(cardId2);
        when(cardGateway.sendCardMessage(any(), anyString(), any()))
            .thenReturn(SendResult.success("msg_123"));
        when(cardGateway.updateCard(eq(cardId1), anyString(), anyInt()))
            .thenReturn(true);
        when(cardGateway.updateCard(eq(cardId2), anyString(), anyInt()))
            .thenReturn(true);

        manager.createAndSend(message, "Title 1", "Content 1", null);
        manager.createAndSend(message, "Title 2", "Content 2", null);
        
        manager.update(cardId1, "Update 1");
        manager.update(cardId1, "Update 2");
        manager.update(cardId2, "Update A");

        assertEquals(3, manager.getSequence(cardId1));
        assertEquals(2, manager.getSequence(cardId2));
    }

    private Message createTestMessage() {
        Message message = new Message();
        message.setMessageId("msg_test");
        message.setContent("/test command");
        message.setSender(new Sender("user_123", "Test User"));
        return message;
    }
}

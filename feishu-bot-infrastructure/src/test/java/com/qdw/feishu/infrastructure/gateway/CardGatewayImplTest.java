package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.cardkit.CardkitService;
import com.lark.oapi.service.cardkit.v1.V1;
import com.lark.oapi.service.cardkit.v1.model.*;
import com.lark.oapi.service.cardkit.v1.resource.Card;
import com.qdw.feishu.domain.config.FeishuConfig;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardGatewayImplTest {

    @Mock
    private Client mockClient;

    @Mock
    private CardkitService mockCardkit;

    @Mock
    private V1 mockV1;

    @Mock
    private Card mockCardResource;

    private CardGatewayImpl cardGateway;

    @BeforeEach
    void setUp() {
        cardGateway = new CardGatewayImpl(mockClient);
    }

    @Test
    void createCard_shouldReturnCardId_whenSuccess() throws Exception {
        String expectedCardId = "card_123456";
        
        CreateCardResp mockResp = mock(CreateCardResp.class);
        when(mockResp.success()).thenReturn(true);
        
        CreateCardRespBody mockBody = mock(CreateCardRespBody.class);
        when(mockBody.getCardId()).thenReturn(expectedCardId);
        when(mockResp.getData()).thenReturn(mockBody);

        when(mockClient.cardkit()).thenReturn(mockCardkit);
        when(mockCardkit.v1()).thenReturn(mockV1);
        when(mockV1.card()).thenReturn(mockCardResource);
        when(mockCardResource.create(any(CreateCardReq.class))).thenReturn(mockResp);

        String result = cardGateway.createCard("Test Title", "Test Content");

        assertEquals(expectedCardId, result);
        verify(mockCardResource).create(any(CreateCardReq.class));
    }

    @Test
    void createCard_shouldReturnNull_whenFailed() throws Exception {
        CreateCardResp mockResp = mock(CreateCardResp.class);
        when(mockResp.success()).thenReturn(false);
        when(mockResp.getCode()).thenReturn(400);
        when(mockResp.getMsg()).thenReturn("Bad Request");

        when(mockClient.cardkit()).thenReturn(mockCardkit);
        when(mockCardkit.v1()).thenReturn(mockV1);
        when(mockV1.card()).thenReturn(mockCardResource);
        when(mockCardResource.create(any(CreateCardReq.class))).thenReturn(mockResp);

        String result = cardGateway.createCard("Test Title", "Test Content");

        assertNull(result);
    }

    @Test
    void createCard_shouldReturnNull_whenException() throws Exception {
        when(mockClient.cardkit()).thenThrow(new RuntimeException("Network error"));

        String result = cardGateway.createCard("Test Title", "Test Content");

        assertNull(result);
    }

    @Test
    void updateCard_shouldReturnTrue_whenSuccess() throws Exception {
        String cardId = "card_123456";
        
        UpdateCardResp mockResp = mock(UpdateCardResp.class);
        when(mockResp.success()).thenReturn(true);

        when(mockClient.cardkit()).thenReturn(mockCardkit);
        when(mockCardkit.v1()).thenReturn(mockV1);
        when(mockV1.card()).thenReturn(mockCardResource);
        when(mockCardResource.update(any(UpdateCardReq.class))).thenReturn(mockResp);

        boolean result = cardGateway.updateCard(cardId, "Updated Content", 1);

        assertTrue(result);
        verify(mockCardResource).update(any(UpdateCardReq.class));
    }

    @Test
    void updateCard_shouldReturnFalse_whenFailed() throws Exception {
        String cardId = "card_123456";
        
        UpdateCardResp mockResp = mock(UpdateCardResp.class);
        when(mockResp.success()).thenReturn(false);
        when(mockResp.getCode()).thenReturn(404);
        when(mockResp.getMsg()).thenReturn("Card not found");

        when(mockClient.cardkit()).thenReturn(mockCardkit);
        when(mockCardkit.v1()).thenReturn(mockV1);
        when(mockV1.card()).thenReturn(mockCardResource);
        when(mockCardResource.update(any(UpdateCardReq.class))).thenReturn(mockResp);

        boolean result = cardGateway.updateCard(cardId, "Updated Content", 1);

        assertFalse(result);
    }

    @Test
    void updateCard_shouldReturnFalse_whenException() throws Exception {
        when(mockClient.cardkit()).thenThrow(new RuntimeException("Network error"));

        boolean result = cardGateway.updateCard("card_123", "Content", 1);

        assertFalse(result);
    }

    @Test
    void buildCardJson_shouldContainTitle_whenTitleProvided() throws Exception {
        Method method = CardGatewayImpl.class.getDeclaredMethod("buildCardJson", String.class, String.class);
        method.setAccessible(true);
        
        String json = (String) method.invoke(cardGateway, "Test Title", "Test Content");
        
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        
        assertEquals("2.0", node.get("schema").asText());
        assertTrue(node.has("header"));
        assertEquals("Test Title", node.get("header").get("title").get("content").asText());
        assertTrue(node.has("elements"));
    }

    @Test
    void buildCardJson_shouldNotContainTitle_whenTitleNull() throws Exception {
        Method method = CardGatewayImpl.class.getDeclaredMethod("buildCardJson", String.class, String.class);
        method.setAccessible(true);
        
        String json = (String) method.invoke(cardGateway, null, "Test Content");
        
        ObjectMapper mapper = new ObjectMapper();
        var node = mapper.readTree(json);
        
        assertEquals("2.0", node.get("schema").asText());
        assertFalse(node.has("header"));
        assertTrue(node.has("elements"));
    }

    @Test
    void sendCardMessage_shouldHandleException() {
        Message message = createTestMessage();
        
        when(mockClient.im()).thenThrow(new RuntimeException("IM service unavailable"));

        assertThrows(Exception.class, () -> {
            cardGateway.sendCardMessage(message, "card_123", null);
        });
    }

    private Message createTestMessage() {
        Message message = new Message();
        message.setMessageId("msg_001");
        message.setChatId("chat_001");
        message.setContent("test message");
        
        Sender sender = new Sender();
        sender.setOpenId("ou_123456");
        message.setSender(sender);
        
        return message;
    }
}

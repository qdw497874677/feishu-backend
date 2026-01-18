package com.qdw.feishu.app.listener;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.MessageType;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.BotMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiveMessageListenerExeTest {

    @Mock
    private BotMessageService mockBotMessageService;

    private ReceiveMessageListenerExe executor;

    @BeforeEach
    void setUp() {
        executor = new ReceiveMessageListenerExe(mockBotMessageService);
    }

    @Test
    void shouldDelegateToBotMessageService() {
        Message message = Message.builder()
                .content("test message")
                .type(MessageType.TEXT)
                .sender(Sender.builder().openId("user123").build())
                .build();

        executor.execute(message);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockBotMessageService).handleMessage(captor.capture());
        assertEquals("test message", captor.getValue().getContent());
    }

    @Test
    void shouldHandleBusinessExceptionGracefully() {
        Message message = Message.builder()
                .content("test message")
                .type(MessageType.TEXT)
                .sender(Sender.builder().openId("user123").build())
                .build();

        doThrow(new com.qdw.feishu.domain.exception.MessageBizException("Test error"))
                .when(mockBotMessageService).handleMessage(any());

        assertDoesNotThrow(() -> executor.execute(message));
    }
}

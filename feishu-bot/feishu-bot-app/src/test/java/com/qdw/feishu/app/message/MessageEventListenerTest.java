package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.event.MessageReceivedEvent;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.BotMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageEventListener tests")
class MessageEventListenerTest {

    @Mock
    private BotMessageService botMessageService;

    private MessageEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new MessageEventListener();
        eventListener.setBotMessageService(botMessageService);
    }

    @Test
    @DisplayName("Should handle message event successfully")
    void shouldHandleMessageEventSuccessfully() {
        Sender sender = new Sender("open_123", "user_456", "Test User");
        Map<String, String> metadata = new HashMap<>();
        
        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content("Hello Bot")
            .sender(sender)
            .metadata(metadata)
            .timestamp(Instant.now())
            .build();

        SendResult mockResult = SendResult.success("reply_999");
        when(botMessageService.handleMessage(any(Message.class))).thenReturn(mockResult);

        eventListener.handleMessage(event);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(botMessageService).handleMessage(messageCaptor.capture());

        Message capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getMessageId()).isEqualTo("msg_789");
        assertThat(capturedMessage.getContent()).isEqualTo("Hello Bot");
        assertThat(capturedMessage.getSender().getOpenId()).isEqualTo("open_123");
        assertThat(capturedMessage.getSender().getUserId()).isEqualTo("user_456");
        assertThat(capturedMessage.getSender().getName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should handle message with null metadata")
    void shouldHandleMessageWithNullMetadata() {
        Sender sender = new Sender("open_123", "user_456", "Test User");
        
        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content("Hello Bot")
            .sender(sender)
            .metadata(null)
            .build();

        SendResult mockResult = SendResult.success("reply_999");
        when(botMessageService.handleMessage(any(Message.class))).thenReturn(mockResult);

        eventListener.handleMessage(event);

        verify(botMessageService, times(1)).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should log error when botMessageService throws exception")
    void shouldLogErrorWhenServiceThrowsException() {
        Sender sender = new Sender("open_123", "user_456", "Test User");
        
        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content("Hello Bot")
            .sender(sender)
            .build();

        when(botMessageService.handleMessage(any(Message.class)))
            .thenThrow(new RuntimeException("Service unavailable"));

        eventListener.handleMessage(event);

        verify(botMessageService, times(1)).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should handle failed message processing")
    void shouldHandleFailedMessageProcessing() {
        Sender sender = new Sender("open_123", "user_456", "Test User");
        
        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content("Hello Bot")
            .sender(sender)
            .build();

        SendResult failedResult = SendResult.fail("Send failed");
        when(botMessageService.handleMessage(any(Message.class))).thenReturn(failedResult);

        eventListener.handleMessage(event);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(botMessageService).handleMessage(messageCaptor.capture());

        Message capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getMessageId()).isEqualTo("msg_789");
    }

    @Test
    @DisplayName("Should propagate message content correctly")
    void shouldPropagateMessageContentCorrectly() {
        Sender sender = new Sender("open_123", "user_456", "Test User");
        String longContent = "This is a longer message content to test content propagation";

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content(longContent)
            .sender(sender)
            .build();

        SendResult mockResult = SendResult.success("reply_999");
        when(botMessageService.handleMessage(any(Message.class))).thenReturn(mockResult);

        eventListener.handleMessage(event);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(botMessageService).handleMessage(messageCaptor.capture());

        assertThat(messageCaptor.getValue().getContent()).isEqualTo(longContent);
    }

    @Test
    @DisplayName("Should not process message when sender is null")
    void shouldNotProcessMessageWhenSenderIsNull() {
        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content("Hello Bot")
            .sender(null)
            .build();

        eventListener.handleMessage(event);

        verify(botMessageService, never()).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should not process message when messageId is null")
    void shouldNotProcessMessageWhenMessageIdIsNull() {
        Sender sender = new Sender("open_123", "user_456", "Test User");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId(null)
            .content("Hello Bot")
            .sender(sender)
            .build();

        eventListener.handleMessage(event);

        verify(botMessageService, never()).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should not process message when content is null")
    void shouldNotProcessMessageWhenContentIsNull() {
        Sender sender = new Sender("open_123", "user_456", "Test User");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content(null)
            .sender(sender)
            .build();

        eventListener.handleMessage(event);

        verify(botMessageService, never()).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should not process message when messageId is empty")
    void shouldNotProcessMessageWhenMessageIdIsEmpty() {
        Sender sender = new Sender("open_123", "user_456", "Test User");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("   ")
            .content("Hello Bot")
            .sender(sender)
            .build();

        eventListener.handleMessage(event);

        verify(botMessageService, never()).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should not process message when content is empty")
    void shouldNotProcessMessageWhenContentIsEmpty() {
        Sender sender = new Sender("open_123", "user_456", "Test User");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_789")
            .content("")
            .sender(sender)
            .build();

        eventListener.handleMessage(event);

        verify(botMessageService, never()).handleMessage(any(Message.class));
    }
}

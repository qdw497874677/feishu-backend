package com.qdw.feishu.domain.event;

import com.qdw.feishu.domain.message.Sender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MessageReceivedEvent domain event tests")
class MessageReceivedEventTest {

    @Test
    @DisplayName("Should create event with all fields")
    void shouldCreateEventWithAllFields() {
        Sender sender = new Sender("open_id_123", "user_id_456", "John Doe");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "webhook");
        metadata.put("timestamp", "2024-01-17T10:00:00Z");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_001")
            .content("Hello, world!")
            .sender(sender)
            .metadata(metadata)
            .build();

        assertThat(event.getMessageId()).isEqualTo("msg_001");
        assertThat(event.getContent()).isEqualTo("Hello, world!");
        assertThat(event.getSender()).isEqualTo(sender);
        assertThat(event.getMetadata()).isEqualTo(metadata);
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create event with null metadata")
    void shouldCreateEventWithNullMetadata() {
        Sender sender = new Sender("open_id", "user_id", "Jane Doe");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_002")
            .content("Test message")
            .sender(sender)
            .metadata(null)
            .build();

        assertThat(event.getMessageId()).isEqualTo("msg_002");
        assertThat(event.getContent()).isEqualTo("Test message");
        assertThat(event.getSender()).isEqualTo(sender);
        assertThat(event.getMetadata()).isNull();
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when messageId is null")
    void shouldThrowExceptionWhenMessageIdIsNull() {
        Sender sender = new Sender("open_id", "user_id", "Alice");

        assertThatThrownBy(() -> MessageReceivedEvent.builder()
            .messageId(null)
            .content("Test")
            .sender(sender)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("messageId");
    }

    @Test
    @DisplayName("Should throw exception when content is null")
    void shouldThrowExceptionWhenContentIsNull() {
        Sender sender = new Sender("open_id", "user_id", "Bob");

        assertThatThrownBy(() -> MessageReceivedEvent.builder()
            .messageId("msg_001")
            .content(null)
            .sender(sender)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("content");
    }

    @Test
    @DisplayName("Should throw exception when sender is null")
    void shouldThrowExceptionWhenSenderIsNull() {
        assertThatThrownBy(() -> MessageReceivedEvent.builder()
            .messageId("msg_001")
            .content("Test")
            .sender(null)
            .build())
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("sender");
    }

    @Test
    @DisplayName("Should create event with default timestamp")
    void shouldCreateEventWithDefaultTimestamp() {
        Sender sender = new Sender("open_id", "user_id", "Charlie");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_001")
            .content("Test message")
            .sender(sender)
            .build();

        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should create event with custom timestamp")
    void shouldCreateEventWithCustomTimestamp() {
        Sender sender = new Sender("open_id", "user_id", "Dave");
        Instant customTimestamp = Instant.parse("2024-01-17T10:00:00Z");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_001")
            .content("Test message")
            .sender(sender)
            .timestamp(customTimestamp)
            .build();

        assertThat(event.getTimestamp()).isEqualTo(customTimestamp);
    }

    @Test
    @DisplayName("Should create event via no-args constructor for serialization")
    void shouldCreateEventViaNoArgsConstructor() {
        MessageReceivedEvent event = new MessageReceivedEvent();

        assertThat(event.getMessageId()).isNull();
        assertThat(event.getContent()).isNull();
        assertThat(event.getSender()).isNull();
        assertThat(event.getMetadata()).isNull();
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should be immutable")
    void shouldBeImmutable() {
        Sender sender = new Sender("open_id", "user_id", "Eve");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");

        MessageReceivedEvent event = MessageReceivedEvent.builder()
            .messageId("msg_001")
            .content("Original")
            .sender(sender)
            .metadata(metadata)
            .build();

        assertThat(event.getMessageId()).isEqualTo("msg_001");
        assertThat(event.getContent()).isEqualTo("Original");
        assertThat(event.getSender()).isEqualTo(sender);
        assertThat(event.getMetadata()).isEqualTo(metadata);
        assertThat(event.getTimestamp()).isNotNull();

        assertThat(event.getClass().getDeclaredMethods())
            .extracting("name")
            .doesNotContain("setMessageId", "setContent", "setSender", "setMetadata", "setTimestamp");
    }
}

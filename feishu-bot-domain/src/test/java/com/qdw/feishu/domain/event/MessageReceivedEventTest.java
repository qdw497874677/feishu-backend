package com.qdw.feishu.domain.event;

import com.qdw.feishu.domain.message.Sender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageReceivedEvent domain event tests")
class MessageReceivedEventTest {

    @Test
    @DisplayName("Should create event with all fields")
    void shouldCreateEventWithAllFields() {
        Sender sender = new Sender("open_id_123", "user_id_456", "John Doe");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "webhook");
        metadata.put("timestamp", "2024-01-17T10:00:00Z");

        MessageReceivedEvent event = new MessageReceivedEvent(
            "msg_001",
            "Hello, world!",
            sender,
            metadata
        );

        assertThat(event.getMessageId()).isEqualTo("msg_001");
        assertThat(event.getContent()).isEqualTo("Hello, world!");
        assertThat(event.getSender()).isEqualTo(sender);
        assertThat(event.getMetadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("Should create event with null metadata")
    void shouldCreateEventWithNullMetadata() {
        Sender sender = new Sender("open_id", "user_id", "Jane Doe");

        MessageReceivedEvent event = new MessageReceivedEvent(
            "msg_002",
            "Test message",
            sender,
            null
        );

        assertThat(event.getMessageId()).isEqualTo("msg_002");
        assertThat(event.getContent()).isEqualTo("Test message");
        assertThat(event.getSender()).isEqualTo(sender);
        assertThat(event.getMetadata()).isNull();
    }

    @Test
    @DisplayName("Should support setters via @Data")
    void shouldSupportSetters() {
        MessageReceivedEvent event = new MessageReceivedEvent(null, null, null, null);
        
        Sender sender = new Sender("open_id", "user_id", "Alice");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");

        event.setMessageId("msg_003");
        event.setContent("Updated content");
        event.setSender(sender);
        event.setMetadata(metadata);

        assertThat(event.getMessageId()).isEqualTo("msg_003");
        assertThat(event.getContent()).isEqualTo("Updated content");
        assertThat(event.getSender()).isEqualTo(sender);
        assertThat(event.getMetadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("Should support equals and hashCode via @Data")
    void shouldSupportEqualsAndHashCode() {
        Sender sender = new Sender("open_id", "user_id", "Bob");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");

        MessageReceivedEvent event1 = new MessageReceivedEvent("msg_004", "Content", sender, metadata);
        MessageReceivedEvent event2 = new MessageReceivedEvent("msg_004", "Content", sender, metadata);
        MessageReceivedEvent event3 = new MessageReceivedEvent("msg_005", "Different", sender, metadata);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }

    @Test
    @DisplayName("Should support toString via @Data")
    void shouldSupportToString() {
        Sender sender = new Sender("open_id", "user_id", "Charlie");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("key", "value");

        MessageReceivedEvent event = new MessageReceivedEvent("msg_005", "Content", sender, metadata);

        String toString = event.toString();
        assertThat(toString).contains("MessageReceivedEvent");
        assertThat(toString).contains("msg_005");
        assertThat(toString).contains("Content");
    }
}

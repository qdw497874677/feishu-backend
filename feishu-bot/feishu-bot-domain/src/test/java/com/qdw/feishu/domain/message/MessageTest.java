package com.qdw.feishu.domain.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Message entity tests")
class MessageTest {

    @Test
    @DisplayName("Should generate reply from content")
    void shouldGenerateReply() {
        Sender sender = new Sender("open_id", "user_id", "Test User");
        Message message = new Message("msg_123", "Hello", sender);

        String reply = message.generateReply();

        assertThat(reply).isNotNull();
        assertThat(reply).isNotEmpty();
    }

    @Test
    @DisplayName("Should mark message as processed")
    void shouldMarkAsProcessed() {
        Sender sender = new Sender("open_id", "user_id", "Test User");
        Message message = new Message("msg_123", "Hello", sender);

        assertThat(message.getStatus()).isEqualTo(MessageStatus.RECEIVED);

        message.markProcessed();

        assertThat(message.getStatus()).isEqualTo(MessageStatus.PROCESSED);
    }

    @Test
    @DisplayName("Should get message properties")
    void shouldGetProperties() {
        Sender sender = new Sender("open_id", "user_id", "Test User");
        Message message = new Message("msg_123", "Hello World", sender);

        assertThat(message.getMessageId()).isEqualTo("msg_123");
        assertThat(message.getContent()).isEqualTo("Hello World");
        assertThat(message.getSender()).isEqualTo(sender);
        assertThat(message.getStatus()).isEqualTo(MessageStatus.RECEIVED);
        assertThat(message.getReceiveTime()).isNotNull();
    }
}

package com.qdw.feishu.domain.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sender value object tests")
class SenderTest {

    @Test
    @DisplayName("Should create sender with all properties")
    void shouldCreateSender() {
        Sender sender = new Sender("open_id_123", "user_id_456", "John Doe");

        assertThat(sender.getOpenId()).isEqualTo("open_id_123");
        assertThat(sender.getUserId()).isEqualTo("user_id_456");
        assertThat(sender.getName()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should create sender with null name")
    void shouldCreateSenderWithNullName() {
        Sender sender = new Sender("open_id", "user_id", null);

        assertThat(sender.getName()).isNull();
    }

    @Test
    @DisplayName("Should support equals and hashCode")
    void shouldSupportEqualsAndHashCode() {
        Sender sender1 = new Sender("open_id", "user_id", "Name");
        Sender sender2 = new Sender("open_id", "user_id", "Name");
        Sender sender3 = new Sender("open_id_2", "user_id", "Name");

        assertThat(sender1).isEqualTo(sender2);
        assertThat(sender1).isNotEqualTo(sender3);
        assertThat(sender1.hashCode()).isEqualTo(sender2.hashCode());
    }
}

package com.qdw.feishu.integration;

import com.qdw.feishu.app.listener.ReceiveMessageListenerExe;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.MessageType;
import com.qdw.feishu.domain.message.Sender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "feishu.mode=listener",
    "feishu.listener.enabled=false"
})
class FeishuListenerIntegrationTest {

    @Autowired
    private MessageListenerGateway messageListenerGateway;

    @Autowired
    private ReceiveMessageListenerExe receiveMessageListenerExe;

    @Test
    void shouldReceiveAndProcessMessage() throws InterruptedException {
        // Given
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> receivedMessage = new AtomicReference<>();

        Consumer<Message> handler = msg -> {
            receiveMessageListenerExe.execute(msg);
            latch.countDown();
        };

        Message testMessage = Message.builder()
                .content("integration test")
                .type(MessageType.TEXT)
                .sender(Sender.builder().openId("test-user").build())
                .build();

        // When
        messageListenerGateway.startListening(handler);

        // Simulate receiving message
        handler.accept(testMessage);

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be processed within 5 seconds");

        messageListenerGateway.stopListening();
    }
}

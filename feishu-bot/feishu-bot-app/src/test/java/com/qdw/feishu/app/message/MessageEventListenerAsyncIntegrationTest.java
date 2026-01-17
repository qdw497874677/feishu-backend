package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.event.MessageReceivedEvent;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.BotMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = MessageEventListener.class)
@TestPropertySource(properties = {
    "spring.main.allow-bean-definition-overriding=true"
})
@DisplayName("MessageEventListener async integration tests")
class MessageEventListenerAsyncIntegrationTest {

    @Autowired
    private MessageEventListener eventListener;

    @MockBean
    private BotMessageService botMessageService;

    @Test
    @DisplayName("Should verify async execution with integration test")
    void shouldVerifyAsyncExecutionWithIntegrationTest() throws InterruptedException {
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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean asyncExecuted = new AtomicBoolean(false);
        
        doAnswer(invocation -> {
            asyncExecuted.set(true);
            latch.countDown();
            return mockResult;
        }).when(botMessageService).handleMessage(any(Message.class));

        eventListener.handleMessage(event);
        
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        
        assertThat(completed).as("Async execution should complete within timeout").isTrue();
        assertThat(asyncExecuted.get()).as("Service should be called asynchronously").isTrue();
        
        verify(botMessageService, timeout(2000).times(1)).handleMessage(any(Message.class));
    }

    @Test
    @DisplayName("Should verify async handles validation before service call")
    void shouldVerifyAsyncHandlesValidationBeforeServiceCall() throws InterruptedException {
        MessageReceivedEvent invalidEvent = MessageReceivedEvent.builder()
            .messageId(null)
            .content("Hello Bot")
            .sender(new Sender("open_123", "user_456", "Test User"))
            .build();

        CountDownLatch latch = new CountDownLatch(1);
        
        eventListener.handleMessage(invalidEvent);
        
        boolean completed = latch.await(1, TimeUnit.SECONDS);
        
        verify(botMessageService, never()).handleMessage(any(Message.class));
    }
}

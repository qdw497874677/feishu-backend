package com.qdw.feishu.app.context;

import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Behavioral invariant tests for MessageContextResolver.
 *
 * Tests A and D from Phase 01 Task 6 behavioral invariant test suite.
 */
@ExtendWith(MockitoExtension.class)
class MessageContextResolverTest {

    @Mock
    private ImContextBindingGateway bindingGateway;

    @InjectMocks
    private MessageContextResolver resolver;

    /**
     * Test A — Single binding lookup in routing path.
     *
     * Verifies that findBinding() is called exactly once during context resolution.
     * This is the foundation of the "resolve once, thread everywhere" pattern.
     */
    @Test
    void should_callFindBindingExactlyOnce_when_resolvingContextForNormalMessage() {
        Message message = createTopicMessage("/opencode projects", "omt_test");
        ImContextRef expectedRef = ImContextRef.feishuThread("omt_test");
        ImContextBinding binding = ImContextBinding.create(expectedRef, "opencode", null);

        when(bindingGateway.findBinding(expectedRef)).thenReturn(Optional.of(binding));

        MessageContext context = resolver.resolve(message);

        verify(bindingGateway, times(1)).findBinding(any());
        assertTrue(context.isResolved());
        assertTrue(context.isBound());
        assertEquals("opencode", context.getBoundAppId().orElse(null));
    }

    /**
     * Test D — Unresolved context (card events without chatId).
     *
     * Verifies that messages without chatId/topicId return unresolved context
     * without calling bindingGateway at all.
     */
    @Test
    void should_returnUnresolvedContext_when_messageHasNoChatIdOrTopicId() {
        Message message = new Message();
        message.setContent("/opencode projects");
        message.setMessageId("msg-card");
        // No chatId, no topicId — simulates a card event

        MessageContext context = resolver.resolve(message);

        assertFalse(context.isResolved());
        assertFalse(context.isBound());
        assertFalse(context.getBoundSessionId().isPresent());
        verifyNoInteractions(bindingGateway);
    }

    @Test
    void should_returnUnboundContext_when_noBindingExists() {
        Message message = createChatMessage("/help", "chat_new");
        ImContextRef expectedRef = ImContextRef.feishuChat("chat_new");

        when(bindingGateway.findBinding(expectedRef)).thenReturn(Optional.empty());

        MessageContext context = resolver.resolve(message);

        assertTrue(context.isResolved());
        assertFalse(context.isBound());
        assertTrue(context.isChatContext());
        assertFalse(context.isThreadContext());
    }

    @Test
    void should_resolveThreadContext_when_topicIdPresent() {
        Message message = createTopicMessage("hello", "omt_thread");
        ImContextRef expectedRef = ImContextRef.feishuThread("omt_thread");
        ImContextBinding binding = ImContextBinding.create(expectedRef, "opencode", "ses_123");

        when(bindingGateway.findBinding(expectedRef)).thenReturn(Optional.of(binding));

        MessageContext context = resolver.resolve(message);

        assertTrue(context.isResolved());
        assertTrue(context.isBound());
        assertTrue(context.isThreadContext());
        assertEquals("ses_123", context.getBoundSessionId().orElse(null));
    }

    private Message createTopicMessage(String content, String topicId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(topicId);
        message.setChatId("chat_test");
        message.setMessageId("msg_" + topicId);
        message.setSender(new Sender("ou_test", "tester"));
        return message;
    }

    private Message createChatMessage(String content, String chatId) {
        Message message = new Message();
        message.setContent(content);
        message.setChatId(chatId);
        message.setMessageId("msg_" + chatId);
        message.setSender(new Sender("ou_test", "tester"));
        return message;
    }
}

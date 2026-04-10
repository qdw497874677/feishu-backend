package com.qdw.feishu.app.message;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.BotRoutingDecision;
import com.qdw.feishu.domain.message.HandledMessageResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.service.BotMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotMessageAppServiceTest {

    @Mock
    private BotMessageService botMessageService;

    @Mock
    private FeishuGateway feishuGateway;

    @Mock
    private ImContextBindingGateway bindingGateway;

    @Mock
    private FishuAppI helpApp;

    @Mock
    private FishuAppI openCodeApp;

    private BotMessageAppService appService;

    @BeforeEach
    void setUp() {
        ReplyStrategy replyStrategy = new ReplyStrategy() {
            @Override
            public ReplyMode getReplyMode() {
                return ReplyMode.DEFAULT;
            }

            @Override
            public SendResult reply(Message message, String replyContent, String topicId) {
                return feishuGateway.sendMessage(message, replyContent, topicId);
            }
        };
        appService = new BotMessageAppService(
                botMessageService,
                feishuGateway,
                bindingGateway,
                new ReplyStrategyFactory(List.of(replyStrategy))
        );
    }

    @Test
    void should_executeStatelessRouteWithoutPersistingBinding() {
        Message message = createMessage("/help", null, "chat_help");
        SendResult expected = SendResult.success("msg_help");

        when(botMessageService.routeMessage(eq(message), any(MessageContext.class))).thenReturn(new BotRoutingDecision("help", helpApp, false));
        when(helpApp.execute(any(Message.class), any(MessageContext.class))).thenReturn(AppExecutionResult.text("help text"));
        when(helpApp.getReplyMode()).thenReturn(ReplyMode.DEFAULT);
        when(feishuGateway.sendMessage(message, "help text", null)).thenReturn(expected);

        HandledMessageResult result = appService.handleMessage(message);

        assertEquals(expected, result.getSendResult());
        assertEquals("help", result.getAppId());
        verify(bindingGateway, never()).bind(any(), org.mockito.ArgumentMatchers.anyString(), any());
    }

    @Test
    void should_persistOpenCodeBindingUsingReturnedThreadId_when_routeRequestsPersistence() {
        Message message = createMessage("/opencode projects", "omt_route", "chat_route");
        SendResult expected = SendResult.success("msg_open", "omt_actual");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_actual");

        when(botMessageService.routeMessage(eq(message), any(MessageContext.class))).thenReturn(new BotRoutingDecision("opencode", openCodeApp, true));
        when(openCodeApp.execute(any(Message.class), any(MessageContext.class))).thenReturn(AppExecutionResult.text("project list"));
        when(openCodeApp.getReplyMode()).thenReturn(ReplyMode.DEFAULT);
        when(feishuGateway.sendMessage(message, "project list", "omt_route")).thenReturn(expected);
        when(bindingGateway.bind(contextRef, "opencode", null))
                .thenReturn(BindingResult.created(ImContextBinding.create(contextRef, "opencode", null)));

        HandledMessageResult result = appService.handleMessage(message);

        assertEquals(expected, result.getSendResult());
        assertEquals("opencode", result.getAppId());
        verify(bindingGateway).bind(contextRef, "opencode", null);
    }

    /**
     * Test C — Thread propagation for newly created topic.
     *
     * Verifies that when a reply creates a new thread (SendResult contains threadId),
     * the binding is propagated to the new thread context, and the original
     * chat binding is not deleted.
     */
    @Test
    void should_bindToNewThread_when_replyCreatesNewTopic() {
        // Message from chat context (no topicId)
        Message message = createMessage("/opencode projects", null, "chat_propagate");
        // SendResult indicates a new thread was created
        SendResult expected = SendResult.success("msg_prop", "omt_new_thread");
        ImContextRef newThreadRef = ImContextRef.feishuThread("omt_new_thread");

        // Pre-resolved context: chat context with existing opencode binding
        ImContextRef chatRef = ImContextRef.feishuChat("chat_propagate");
        ImContextBinding chatBinding = ImContextBinding.create(chatRef, "opencode", "internal_ses_123");
        MessageContext messageContext = MessageContext.of(chatRef, chatBinding);

        when(botMessageService.routeMessage(eq(message), any(MessageContext.class)))
                .thenReturn(new BotRoutingDecision("opencode", openCodeApp, true));
        when(openCodeApp.execute(any(Message.class), any(MessageContext.class))).thenReturn(AppExecutionResult.text("project list"));
        when(openCodeApp.getReplyMode()).thenReturn(ReplyMode.DEFAULT);
        when(feishuGateway.sendMessage(message, "project list", null)).thenReturn(expected);
        when(bindingGateway.bind(newThreadRef, "opencode", "internal_ses_123"))
                .thenReturn(BindingResult.created(ImContextBinding.create(newThreadRef, "opencode", "internal_ses_123")));

        HandledMessageResult result = appService.handleMessage(message, messageContext);

        assertEquals(expected, result.getSendResult());
        // Verify binding was propagated to the NEW thread with full session ID
        verify(bindingGateway).bind(newThreadRef, "opencode", "internal_ses_123");
        // Original chat binding is NOT deleted (duplicate, not migrate)
        verify(bindingGateway, never()).clearBinding(chatRef);
    }

    private Message createMessage(String content, String topicId, String chatId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(topicId);
        message.setChatId(chatId);
        message.setMessageId("msg_" + chatId);
        message.setSender(new Sender("ou_test", "tester"));
        return message;
    }
}

package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.BotRoutingDecision;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotMessageServiceTest {

    @Mock
    private AppRegistry appRegistry;

    @Mock
    private ImContextBindingGateway bindingGateway;

    @Mock
    private FishuAppI helpApp;

    @Mock
    private FishuAppI openCodeApp;

    private BotMessageService botMessageService;

    @BeforeEach
    void setUp() {
        botMessageService = new BotMessageService(appRegistry, bindingGateway);
    }

    @Test
    void should_routePlainTextToHelp_when_contextIsUnbound() {
        Message message = createTopicMessage("继续这个问题", "omt_help_fallback");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_help_fallback");

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());
        when(appRegistry.getApp("help")).thenReturn(Optional.of(helpApp));
        when(helpApp.getAppId()).thenReturn("help");

        BotRoutingDecision decision = botMessageService.routeMessage(message);

        assertEquals("help", decision.getAppId());
        assertFalse(decision.shouldPersistBinding());
        verify(bindingGateway, never()).bind(contextRef, "help", null);
    }

    @Test
    void should_not_persistBinding_forStatelessAppCommand() {
        Message message = createChatMessage("/help", "chat_help");

        when(appRegistry.getAllApps()).thenReturn(List.of(helpApp));
        when(helpApp.getAppId()).thenReturn("help");

        BotRoutingDecision decision = botMessageService.routeMessage(message);

        assertEquals("help", decision.getAppId());
        assertFalse(decision.shouldPersistBinding());
        verify(bindingGateway, never()).bind(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_routeBoundContextToOpenCode_when_bindingExists() {
        Message message = createTopicMessage("继续", "omt_opencode");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_opencode");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appRegistry.getApp("opencode")).thenReturn(Optional.of(openCodeApp));

        BotRoutingDecision decision = botMessageService.routeMessage(message);

        assertEquals("opencode", decision.getAppId());
        assertFalse(decision.shouldPersistBinding());
        verify(bindingGateway, never()).touchBinding(contextRef);
    }

    @Test
    void should_rejectOtherAppCommand_when_contextBoundToOpenCode() {
        Message message = createTopicMessage("/help", "omt_reject_other_app");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_reject_other_app");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appRegistry.getAllApps()).thenReturn(List.of(helpApp, openCodeApp));
        when(helpApp.getAppId()).thenReturn("help");

        assertThrows(MessageBizException.class, () -> botMessageService.routeMessage(message));
        verify(bindingGateway, never()).bind(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private Message createTopicMessage(String content, String topicId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(topicId);
        message.setChatId("chat-test");
        message.setMessageId("msg-" + topicId);
        message.setSender(new Sender("ou_test", "tester"));
        return message;
    }

    private Message createChatMessage(String content, String chatId) {
        Message message = new Message();
        message.setContent(content);
        message.setChatId(chatId);
        message.setMessageId("msg-" + chatId);
        message.setSender(new Sender("ou_test", "tester"));
        return message;
    }
}

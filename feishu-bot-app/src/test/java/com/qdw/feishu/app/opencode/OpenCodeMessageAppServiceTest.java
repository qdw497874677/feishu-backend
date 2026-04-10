package com.qdw.feishu.app.opencode;

import com.qdw.feishu.app.message.BotMessageAppService;
import com.qdw.feishu.app.session.ContextSessionOrchestrator;
import com.qdw.feishu.app.session.ContextSessionStatus;
import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.HandledMessageResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.model.opencode.OpenCodeSessionData;
import com.qdw.feishu.domain.opencode.OpenCodeApp;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OpenCodeMessageAppServiceTest {

    @Mock
    private ContextSessionOrchestrator contextSessionOrchestrator;

    @Mock
    private BotMessageAppService botMessageAppService;

    @Mock
    private FeishuGateway feishuGateway;

    @Mock
    private OpenCodeSessionManager openCodeSessionManager;

    @Mock
    private OpenCodeApp openCodeApp;

    private OpenCodeMessageAppService appService;

    @BeforeEach
    void setUp() {
        appService = new OpenCodeMessageAppService(
                contextSessionOrchestrator,
                botMessageAppService,
                feishuGateway,
                openCodeSessionManager,
                openCodeApp
        );
    }

    @Test
    void should_repairBindingToNull_when_bindingSessionIsDangling() {
        Message message = createTopicMessage("/opencode session status", "omt_dangling");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_dangling");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", "ses_missing");
        SendResult expected = SendResult.success("msg_1", "omt_dangling");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.dangling(binding));
        when(openCodeSessionManager.getCurrentSessionStatus(message)).thenReturn("status guidance");
        when(feishuGateway.sendMessage(message, "⚠️ 检测到当前 OpenCode 会话已失效，已自动修复为未激活会话状态。\n\nstatus guidance", "omt_dangling"))
                .thenReturn(expected);

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verify(contextSessionOrchestrator).repairDanglingSessionBinding(contextRef, "opencode");
        verify(botMessageAppService, never()).handleMessage(message);
    }

    @Test
    void should_routeThroughAppLayerOrchestrator_inRealOpenCodeFlow() {
        Message message = createTopicMessage("/opencode projects", "omt_app");
        SendResult expected = SendResult.success("msg_2", "omt_app");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.unbound());
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "opencode", AppExecutionResult.text("project list")));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verify(contextSessionOrchestrator).enterAppContext(ImContextRef.feishuThread("omt_app"), "opencode");
        verify(botMessageAppService).handleMessage(message);
    }

    @Test
    void should_supportPlainText_when_contextAlreadyBoundToOpenCode() {
        Message message = createTopicMessage("继续处理这个问题", "omt_bound");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_bound");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));

        assertEquals(true, appService.supports(message));
    }

    @Test
    void should_allow_statusAndProjectsCommands_when_inOpenCodeWithoutSession() {
        Message message = createTopicMessage("/opencode projects", "omt_open");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_open");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult expected = SendResult.success("msg_3", "omt_open");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "opencode", AppExecutionResult.text("project list")));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verify(botMessageAppService).handleMessage(message);
    }

    @Test
    void should_upgradeSessionInAppLayer_when_openCodeReplyContainsSessionId() {
        Message message = createTopicMessage("/opencode hello", "omt_activate");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_activate");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult expected = SendResult.success("msg_7", "omt_activate");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "opencode", AppExecutionResult.withSession("Created session", "oc_ses_123", true)));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        // progressSessionIfNeeded uses sendResult.getThreadId() to construct ImContextRef
        verify(openCodeSessionManager).saveSession(ImContextRef.feishuThread("omt_activate"), "oc_ses_123");
    }

    @Test
    void should_notUpgradeSession_when_replyDoesNotContainSessionId() {
        Message message = createTopicMessage("/opencode projects", "omt_no_upgrade");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_no_upgrade");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult expected = SendResult.success("msg_8", "omt_no_upgrade");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "opencode", AppExecutionResult.text("project list")));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verifyNoInteractions(openCodeSessionManager);
    }

    @Test
    void should_notUpgradeSession_when_sendFails() {
        Message message = createTopicMessage("/opencode hello", "omt_failed_send");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_failed_send");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult failed = SendResult.failure("send failed");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(failed, "opencode", AppExecutionResult.text("Created\nSession ID: `oc_ses_123`")));

        SendResult result = appService.handleMessage(message);

        assertEquals(failed, result);
        verifyNoInteractions(openCodeSessionManager);
    }

    @Test
    void should_notUpgradeSession_when_detailedResultIsForOtherApp() {
        Message message = createTopicMessage("/opencode hello", "omt_other_app_result");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_other_app_result");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult expected = SendResult.success("msg_9", "omt_other_app_result");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "help", AppExecutionResult.text("Created\nSession ID: `oc_ses_123`")));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verifyNoInteractions(openCodeSessionManager);
    }

    @Test
    void should_reject_chatCommand_when_inOpenCodeWithoutSession() {
        Message message = createTopicMessage("/opencode chat hello", "omt_no_session");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_no_session");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult expected = SendResult.success("msg_4", "omt_no_session");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        when(openCodeSessionManager.getCurrentSessionStatus(message)).thenReturn("need session first");
        when(feishuGateway.sendMessage(message, "need session first", "omt_no_session")).thenReturn(expected);

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verify(botMessageAppService, never()).handleMessage(message);
    }

    @Test
    void should_rejectWhen_boundToOtherApp() {
        Message message = createTopicMessage("/opencode projects", "omt_other");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_other");
        ImContextBinding binding = ImContextBinding.create(contextRef, "bash", null);
        SendResult expected = SendResult.success("msg_5", "omt_other");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.boundToOtherApp(binding));
        when(feishuGateway.sendMessage(eq(message), eq("❌ 当前上下文已绑定到其他应用：`bash`\n\n请先退出当前应用上下文，或在新的消息/话题中使用 OpenCode。"), eq("omt_other")))
                .thenReturn(expected);

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verify(botMessageAppService, never()).handleMessage(message);
    }

    @Test
    void should_delegateToBotService_when_contextCannotBeResolved() {
        Message message = new Message();
        message.setContent("/opencode projects");
        message.setMessageId("msg-resolve");
        SendResult expected = SendResult.success("msg_6");
        when(botMessageAppService.handleMessage(message)).thenReturn(new HandledMessageResult(expected, "opencode", AppExecutionResult.text("project list")));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        verify(botMessageAppService).handleMessage(message);
    }

    @Test
    void should_bypass_when_notOpenCodeCommand() {
        Message message = createTopicMessage("/help", "omt_help");
        when(openCodeApp.getAppId()).thenReturn("opencode");
        when(openCodeApp.getAppAliases()).thenReturn(java.util.List.of("oc", "code"));

        assertEquals(false, appService.supports(message));
    }

    @Test
    void should_supportExplicitOpenCodeAliasCommand() {
        Message message = createTopicMessage("/oc projects", "omt_alias");
        when(openCodeApp.getAppId()).thenReturn("opencode");
        when(openCodeApp.getAppAliases()).thenReturn(java.util.List.of("oc", "code"));

        assertEquals(true, appService.supports(message));
    }

    @Test
    void should_loadStatusOnlyOnce_when_tryHandleImplicitMessageInBoundOpenCodeContext() {
        Message message = createTopicMessage("继续", "omt_try_handle_once");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_try_handle_once");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", "ses_123");
        SendResult expected = SendResult.success("msg_10", "omt_try_handle_once");

        OpenCodeSessionData sessionData = OpenCodeSessionData.create("ses_123");
        AppSession<OpenCodeSessionData> session = new AppSession<>("ses_123", "opencode", sessionData);
        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), org.mockito.ArgumentMatchers.<TypeToken<OpenCodeSessionData>>any()))
                .thenReturn(ContextSessionStatus.inAppWithSession(binding, session));
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "opencode", AppExecutionResult.text("plain reply")));

        assertEquals(true, appService.tryHandle(message));
        verify(contextSessionOrchestrator, times(1)).loadStatus(eq(contextRef), eq("opencode"), any());
        verify(botMessageAppService).handleMessage(message);
    }

    /**
     * Test B — External vs internal session ID boundary.
     *
     * Verifies that progressSessionIfNeeded uses the structured openCodeSessionId
     * from AppExecutionResult (external ID like ses_xxx), not text parsing.
     * The internal UUID is managed by saveSession(), not exposed here.
     */
    @Test
    void should_exposeExternalSessionId_when_appExecutionResultContainsSession() {
        Message message = createTopicMessage("/opencode cn", "omt_session_boundary");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_session_boundary");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        SendResult expected = SendResult.success("msg_boundary", "omt_session_boundary");

        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"), any()))
                .thenReturn(ContextSessionStatus.inAppNoSession(binding));
        // AppExecutionResult carries the external session ID structurally
        when(botMessageAppService.handleMessage(message))
                .thenReturn(new HandledMessageResult(expected, "opencode",
                        AppExecutionResult.withSession("Session created", "ses_external_001", true)));

        SendResult result = appService.handleMessage(message);

        assertEquals(expected, result);
        // saveSession is called with the EXTERNAL openCode session ID
        verify(openCodeSessionManager).saveSession(
                ImContextRef.feishuThread("omt_session_boundary"), "ses_external_001");
    }

    /**
     * Graceful degradation: unbound thread with implicit message shows guidance.
     */
    @Test
    void should_showGuidance_when_unboundThreadReceivesImplicitMessage() {
        Message message = createTopicMessage("继续处理", "omt_old_unbound");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_old_unbound");
        SendResult expected = SendResult.success("msg_guidance", "omt_old_unbound");

        // No loadStatus stub needed — buildStatusFromContext() returns unbound() directly
        // since messageContext has no binding
        when(feishuGateway.sendMessage(eq(message),
                eq("该话题未绑定 OpenCode 会话。请在群聊中使用 /oc projects 开始绑定。"),
                eq("omt_old_unbound")))
                .thenReturn(expected);

        MessageContext messageContext = MessageContext.of(contextRef, null);
        SendResult result = appService.handleMessage(message, messageContext);

        assertEquals(expected, result);
        verify(botMessageAppService, never()).handleMessage(any(Message.class));
        verify(contextSessionOrchestrator, never()).enterAppContext(any(), any());
        // loadStatus should NOT be called since buildStatusFromContext handles it locally
        verify(contextSessionOrchestrator, never()).loadStatus(any(), any(), any());
    }

    // ========== Task 1: 纯文本合成 chat 命令测试 (UX-01) ==========

    @Test
    void should_synthesizeChatCommand_when_plainTextInInitializedTopic() {
        // Given: initialized topic + plain text (no / prefix)
        Message message = createTopicMessage("帮我写代码", "omt_synth");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_synth");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", "ses_internal");
        MessageContext messageContext = MessageContext.of(contextRef, binding);
        SendResult expected = SendResult.success("msg_synth", "omt_synth");

        when(openCodeSessionManager.detectTopicState(any(MessageContext.class)))
                .thenReturn(com.qdw.feishu.domain.topic.TopicState.INITIALIZED);

        // buildStatusFromContext needs loadStatus for binding with sessionId
        OpenCodeSessionData sessionData = OpenCodeSessionData.create("oc_ses_456");
        AppSession<OpenCodeSessionData> session = new AppSession<>("ses_internal", "opencode", sessionData);
        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"),
                org.mockito.ArgumentMatchers.<TypeToken<OpenCodeSessionData>>any()))
                .thenReturn(ContextSessionStatus.inAppWithSession(binding, session));

        // After synthesis: "/opencode chat 帮我写代码" → routes through normal command path
        when(botMessageAppService.handleMessage(any(Message.class), any(MessageContext.class)))
                .thenReturn(new HandledMessageResult(expected, "opencode",
                        AppExecutionResult.text("AI response here")));

        SendResult result = appService.handleMessage(message, messageContext);

        assertEquals(expected, result);
        // Verify botMessageAppService was called (means synthesis routed through)
        verify(botMessageAppService).handleMessage(any(Message.class), any(MessageContext.class));
    }

    @Test
    void should_notSynthesize_when_plainTextInUninitializedTopic() {
        // Given: uninitialized topic + plain text (IN_APP_NO_SESSION state)
        Message message = createTopicMessage("帮我写代码", "omt_uninit_synth");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_uninit_synth");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", null);
        MessageContext messageContext = MessageContext.of(contextRef, binding);

        when(openCodeSessionManager.detectTopicState(any(MessageContext.class)))
                .thenReturn(com.qdw.feishu.domain.topic.TopicState.UNINITIALIZED);

        // buildStatusFromContext: binding for opencode with null sessionId → inAppNoSession
        // isChatCommand("帮我写代码") → true (no /) → shows session status guidance
        SendResult expected = SendResult.success("msg_uninit_synth", "omt_uninit_synth");
        when(openCodeSessionManager.getCurrentSessionStatus(message))
                .thenReturn("initialization guidance");
        when(feishuGateway.sendMessage(eq(message), eq("initialization guidance"), eq("omt_uninit_synth")))
                .thenReturn(expected);

        SendResult result = appService.handleMessage(message, messageContext);

        // Plain text in uninitialized topic should show guidance, not be synthesized to chat
        assertEquals(expected, result);
    }

    @Test
    void should_notSynthesize_when_plainTextInNonTopic() {
        // Given: non-topic (chat context, no topicId) + plain text
        // Non-topic plain text: synthesize check fails (isThreadContext = false for chat ref)
        // Then UNBOUND status → handleMessageInternal → graceful degradation does not apply
        // (isThreadContext on message = false since topicId is null)
        // → enterAppContext → handleOpenCodeResult → delegates to botMessageAppService
        Message message = new Message();
        message.setContent("帮我写代码");
        message.setChatId("chat_non_topic");
        message.setMessageId("msg_non_topic");
        message.setSender(new Sender("ou_test", "tester"));

        ImContextRef contextRef = ImContextRef.feishuChat("chat_non_topic");
        MessageContext messageContext = MessageContext.of(contextRef, null);

        // UNBOUND status → enterAppContext → handleOpenCodeResult
        SendResult expected = SendResult.success("msg_non_topic");
        when(botMessageAppService.handleMessage(any(Message.class), any(MessageContext.class)))
                .thenReturn(new HandledMessageResult(expected, null, null));

        SendResult result = appService.handleMessage(message, messageContext);

        // Non-topic plain text: NOT synthesized, flows through normal (unbound) path
        assertEquals(expected, result);
    }

    @Test
    void should_notSynthesize_when_explicitCommandInInitializedTopic() {
        // Given: initialized topic + explicit /oc command — starts with /, no synthesis
        Message message = createTopicMessage("/oc chat test", "omt_explicit");
        ImContextRef contextRef = ImContextRef.feishuThread("omt_explicit");
        ImContextBinding binding = ImContextBinding.create(contextRef, "opencode", "ses_internal");
        MessageContext messageContext = MessageContext.of(contextRef, binding);
        SendResult expected = SendResult.success("msg_explicit", "omt_explicit");

        // buildStatusFromContext needs loadStatus for binding with sessionId
        OpenCodeSessionData sessionData = OpenCodeSessionData.create("oc_ses_789");
        AppSession<OpenCodeSessionData> session = new AppSession<>("ses_internal", "opencode", sessionData);
        when(contextSessionOrchestrator.loadStatus(any(), eq("opencode"),
                org.mockito.ArgumentMatchers.<TypeToken<OpenCodeSessionData>>any()))
                .thenReturn(ContextSessionStatus.inAppWithSession(binding, session));

        when(botMessageAppService.handleMessage(any(Message.class), any(MessageContext.class)))
                .thenReturn(new HandledMessageResult(expected, "opencode",
                        AppExecutionResult.noReply()));

        SendResult result = appService.handleMessage(message, messageContext);

        // Explicit command should NOT be synthesized, normal routing
        assertEquals(expected, result);
        verify(botMessageAppService).handleMessage(any(Message.class), any(MessageContext.class));
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
}

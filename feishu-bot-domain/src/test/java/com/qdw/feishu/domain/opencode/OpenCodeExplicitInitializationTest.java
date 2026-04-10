package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.model.opencode.OpenCodeSessionData;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.TypeToken;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for explicit initialization flag logic in OpenCode system.
 * Phase 2 - Updated to use ImContextBinding.
 */
class OpenCodeExplicitInitializationTest {

    private OpenCodeGateway openCodeGateway;
    private AppSessionGateway appSessionGateway;
    private ImContextBindingGateway bindingGateway;
    private TopicCommandValidator commandValidator;
    private OpenCodeCommandHandler commandHandler;
    private OpenCodeTaskExecutor taskExecutor;
    private OpenCodeSessionManager sessionManager;
    
    private static final TypeToken<OpenCodeSessionData> TYPE_TOKEN = new TypeToken<OpenCodeSessionData>() {};

    private Message createTestMessage(String content, String topicId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(topicId);
        message.setMessageId("msg-" + System.currentTimeMillis());
        message.setChatId("test-chat");
        message.setSender(new Sender("test-user", "Test User"));
        return message;
    }
    
    @SuppressWarnings("unchecked")
    private AppSession<OpenCodeSessionData> createMockSession(String openCodeSessionId) {
        return createMockSession(openCodeSessionId, false);
    }
    
    @SuppressWarnings("unchecked")
    private AppSession<OpenCodeSessionData> createMockSession(String openCodeSessionId, boolean explicitlyInitialized) {
        OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
        data.setExplicitlyInitialized(explicitlyInitialized);
        AppSession<OpenCodeSessionData> session = mock(AppSession.class);
        when(session.getSessionId()).thenReturn("app_ses_" + openCodeSessionId);
        when(session.getData()).thenReturn(data);
        when(session.getVersion()).thenReturn(1L);
        return session;
    }
    
    private ImContextRef createContextRef(String topicId) {
        return ImContextRef.feishuThread(topicId);
    }
    
    private ImContextBinding createBinding(ImContextRef contextRef, String sessionId) {
        return ImContextBinding.create(contextRef, "opencode", sessionId);
    }
    
    @BeforeEach
    void setUp() {
        openCodeGateway = mock(OpenCodeGateway.class);
        appSessionGateway = mock(AppSessionGateway.class);
        bindingGateway = mock(ImContextBindingGateway.class);
        commandValidator = mock(TopicCommandValidator.class);
        taskExecutor = mock(OpenCodeTaskExecutor.class);
        sessionManager = new OpenCodeSessionManager(openCodeGateway, appSessionGateway, bindingGateway);
        commandHandler = new OpenCodeCommandHandler(openCodeGateway, taskExecutor, sessionManager, commandValidator, new NextStepSuggester());
        
        // 默认设置：命令验证通过
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.allowed());
    }
    
    @Test
    void handleChatCommand_whenNotInitialized_autoCreatesSession() {
        String topicId = "test-topic-789";
        Message message = createTestMessage("/opencode chat hello", topicId);
        ImContextRef contextRef = createContextRef(topicId);

        // 设置：话题无绑定 (for the legacy Message-based path if still invoked)
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        // Pre-resolved MessageContext: resolved but no binding → UNINITIALIZED
        MessageContext messageContext = MessageContext.of(contextRef, null);

        // chat 命令在 UNINITIALIZED 状态下允许，会自动创建会话
        when(taskExecutor.executeWithNewSession(any(Message.class), eq("hello"), isNull()))
            .thenReturn(AppExecutionResult.noReply());

        AppExecutionResult response = commandHandler.handle(message, "chat",
            new String[]{"/opencode", "chat", "hello"}, CommandWhitelist.all(), messageContext);

        assertNotNull(response);
        assertNull(response.getReplyContent()); // async = noReply
        verify(taskExecutor).executeWithNewSession(any(Message.class), eq("hello"), isNull());
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void handleChatCommand_withExistingSessionIdButNotExplicit_autoCreatesSession() {
        String topicId = "test-topic-789";
        Message message = createTestMessage("/opencode chat hello", topicId);
        ImContextRef contextRef = createContextRef(topicId);
        String appSessionId = "app_ses_old_session";

        // 设置：话题有绑定但未显式初始化
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        
        AppSession<OpenCodeSessionData> session = createMockSession("opencode_ses_old", false);
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        // Pre-resolved MessageContext: resolved with binding → INITIALIZED
        MessageContext messageContext = MessageContext.of(contextRef, binding);
        
        // chat 命令在 INITIALIZED 状态下允许，使用现有会话
        when(taskExecutor.executeWithAutoSession(any(Message.class), eq("hello")))
            .thenReturn(AppExecutionResult.noReply());

        AppExecutionResult response = commandHandler.handle(message, "chat",
            new String[]{"/opencode", "chat", "hello"}, CommandWhitelist.all(), messageContext);

        // 验证使用了现有会话 (async = noReply)
        assertNotNull(response);
        assertNull(response.getReplyContent());
        verify(taskExecutor).executeWithAutoSession(any(Message.class), eq("hello"));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void handleSessionContinueCommand_withAlias_sc_setsExplicitFlag() {
        String topicId = "test-topic-alias";
        String sessionId = "ses_alias_123";
        Message message = createTestMessage("/opencode sc " + sessionId, topicId);
        ImContextRef contextRef = createContextRef(topicId);

        // Pre-resolved MessageContext: resolved but no binding → UNINITIALIZED (sc is allowed)
        MessageContext messageContext = MessageContext.of(contextRef, null);

        when(taskExecutor.executeWithSpecificSession(eq(message), isNull(), eq(sessionId)))
            .thenReturn(AppExecutionResult.withSession("✅ **会话已绑定**\n\nSession ID: " + sessionId, sessionId, false));

        AppExecutionResult response = commandHandler.handle(message, "sc",
            new String[]{"/opencode", "sc", sessionId}, CommandWhitelist.all(), messageContext);

        verify(taskExecutor).executeWithSpecificSession(eq(message), isNull(), eq(sessionId));
        assertNotNull(response);
        assertTrue(response.getReplyContent().contains("会话已绑定"));
    }
}

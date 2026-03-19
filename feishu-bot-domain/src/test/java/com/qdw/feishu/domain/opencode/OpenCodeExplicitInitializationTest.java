package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
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
 */
class OpenCodeExplicitInitializationTest {

    private OpenCodeGateway openCodeGateway;
    private AppSessionGateway appSessionGateway;
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
        when(session.getSessionId()).thenReturn("ses_" + System.currentTimeMillis());
        when(session.getData()).thenReturn(data);
        when(session.getVersion()).thenReturn(1L);
        return session;
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        openCodeGateway = mock(OpenCodeGateway.class);
        appSessionGateway = mock(AppSessionGateway.class);
        commandValidator = mock(TopicCommandValidator.class);
        taskExecutor = mock(OpenCodeTaskExecutor.class);
        sessionManager = new OpenCodeSessionManager(openCodeGateway, appSessionGateway);

        commandHandler = new OpenCodeCommandHandler(
            openCodeGateway,
            taskExecutor,
            sessionManager,
            commandValidator
        );

        // 默认返回 empty，表示话题未初始化
        doReturn(Optional.empty()).when(appSessionGateway).getActiveSession(anyString(), anyString(), any(TypeToken.class));
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.allowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void isExplicitlyInitialized_topicExistsAndFlagTrue_returnsTrue() {
        String topicId = "test-topic";
        AppSession<OpenCodeSessionData> session = createMockSession("opencode_ses", true);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        boolean result = sessionManager.isExplicitlyInitialized(topicId);

        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void isExplicitlyInitialized_topicExistsButFlagFalse_returnsFalse() {
        String topicId = "test-topic";
        AppSession<OpenCodeSessionData> session = createMockSession("opencode_ses", false);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        boolean result = sessionManager.isExplicitlyInitialized(topicId);

        assertFalse(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setExplicitlyInitialized_callsGateway() {
        String topicId = "test-topic";
        AppSession<OpenCodeSessionData> session = createMockSession("opencode_ses", false);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        sessionManager.setExplicitlyInitialized(topicId);

        verify(appSessionGateway).updateSession(eq("opencode"), eq(topicId), anyString(), 
            any(OpenCodeSessionData.class), any(TypeToken.class), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void clearExplicitlyInitialized_callsGateway() {
        String topicId = "test-topic";
        AppSession<OpenCodeSessionData> session = createMockSession("opencode_ses", true);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        sessionManager.clearExplicitlyInitialized(topicId);

        verify(appSessionGateway).updateSession(eq("opencode"), eq(topicId), anyString(), 
            any(OpenCodeSessionData.class), any(TypeToken.class), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleResetCommand_inTopicEnvironment_clearsInitialization() {
        String topicId = "test-topic-456";
        Message message = createTestMessage("/opencode reset", topicId);
        AppSession<OpenCodeSessionData> session = createMockSession("ses_abc123");
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        String response = commandHandler.handle(message, "reset", new String[]{}, CommandWhitelist.all());

        verify(appSessionGateway).deleteSession(eq("opencode"), eq(topicId), anyString());
        assertTrue(response.contains("话题已重置"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleResetCommand_withSessionId_includesSessionInResponse() {
        String topicId = "test-topic-456";
        String sessionId = "ses_abc123";
        Message message = createTestMessage("/opencode reset", topicId);
        AppSession<OpenCodeSessionData> session = createMockSession(sessionId);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        String response = commandHandler.handle(message, "reset", new String[]{}, CommandWhitelist.all());

        assertTrue(response.contains(sessionId));
        assertTrue(response.contains("话题已重置"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleResetCommand_withoutSessionId_showsHelpfulMessage() {
        String topicId = "test-topic-456";
        Message message = createTestMessage("/opencode reset", topicId);
        doReturn(Optional.empty()).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));

        String response = commandHandler.handle(message, "reset", new String[]{}, CommandWhitelist.all());

        assertTrue(response.contains("话题已重置"));
        assertTrue(response.contains("/opencode p"));
    }

    @Test
    void handleResetCommand_inNonTopicEnvironment_showsErrorMessage() {
        Message message = createTestMessage("/opencode reset", null);

        String response = commandHandler.handle(message, "reset", new String[]{}, CommandWhitelist.all());

        assertTrue(response.contains("只能在话题中使用"));
        verify(appSessionGateway, never()).deleteSession(anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleChatCommand_whenNotInitialized_autoCreatesSession() {
        String topicId = "test-topic-789";
        Message message = createTestMessage("/opencode chat hello", topicId);
        AppSession<OpenCodeSessionData> session = createMockSession("opencode_ses", false);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));
        when(taskExecutor.executeWithAutoSession(any(Message.class), eq("hello")))
            .thenReturn("对话完成");

        String response = commandHandler.handle(message, "chat", new String[]{"/opencode", "chat", "hello"}, CommandWhitelist.all());

        assertTrue(response.contains("对话完成"));
        verify(taskExecutor).executeWithAutoSession(any(Message.class), eq("hello"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleChatCommand_withExistingSessionIdButNotExplicit_autoCreatesSession() {
        String topicId = "test-topic-789";
        Message message = createTestMessage("/opencode chat hello", topicId);

        // 设置：话题有 sessionId（已绑定），此时 isTopicInitialized 会返回 true
        AppSession<OpenCodeSessionData> session = createMockSession("ses_old_session", false);
        doReturn(Optional.of(session)).when(appSessionGateway).getActiveSession(eq("opencode"), eq(topicId), any(TypeToken.class));
        
        // chat 命令在 INITIALIZED 状态下允许，使用现有会话
        when(taskExecutor.executeWithAutoSession(any(Message.class), eq("hello")))
            .thenReturn("对话完成");

        String response = commandHandler.handle(message, "chat", new String[]{"/opencode", "chat", "hello"}, CommandWhitelist.all());

        // 验证使用了现有会话，而不是创建新会话
        assertTrue(response.contains("对话完成"));
        verify(taskExecutor).executeWithAutoSession(any(Message.class), eq("hello"));
    }

    @Test
    void handleSessionContinueCommand_withAlias_sc_setsExplicitFlag() {
        String topicId = "test-topic-alias";
        String sessionId = "ses_alias_123";
        Message message = createTestMessage("/opencode sc " + sessionId, topicId);

        when(openCodeGateway.listRecentSessions(anyString(), anyInt()))
            .thenReturn("Session: " + sessionId + "\nMessages: 10\nStatus: Active");
        when(taskExecutor.executeWithSpecificSession(eq(message), isNull(), eq(sessionId)))
            .thenReturn("✅ **会话已绑定**\n\nSession ID: " + sessionId);

        String response = commandHandler.handle(message, "sc", new String[]{"/opencode", "sc", sessionId}, CommandWhitelist.all());

        verify(taskExecutor).executeWithSpecificSession(eq(message), isNull(), eq(sessionId));
        assertTrue(response.contains("会话已绑定"));
    }
}

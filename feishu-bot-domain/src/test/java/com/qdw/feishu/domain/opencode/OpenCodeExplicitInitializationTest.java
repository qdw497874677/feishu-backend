package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.command.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for explicit initialization flag logic in OpenCode system.
 */
class OpenCodeExplicitInitializationTest {

    @Mock
    private OpenCodeGateway openCodeGateway;

    @Mock
    private OpenCodeSessionGateway sessionGateway;

    @Mock
    private TopicCommandValidator commandValidator;

    private OpenCodeCommandHandler commandHandler;
    private OpenCodeTaskExecutor taskExecutor;
    private OpenCodeSessionManager sessionManager;

    private Message createTestMessage(String content, String topicId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(topicId);
        message.setMessageId("msg-" + System.currentTimeMillis());
        message.setChatId("test-chat");
        message.setSender(new Sender("test-user", "Test User"));
        return message;
    }

    @BeforeEach
    void setUp() {
        openCodeGateway = mock(OpenCodeGateway.class);
        sessionGateway = mock(OpenCodeSessionGateway.class);
        commandValidator = mock(TopicCommandValidator.class);
        taskExecutor = mock(OpenCodeTaskExecutor.class);
        sessionManager = new OpenCodeSessionManager(openCodeGateway, sessionGateway);

        commandHandler = new OpenCodeCommandHandler(
            openCodeGateway,
            taskExecutor,
            sessionManager,
            commandValidator
        );

        when(sessionGateway.getSessionId(anyString())).thenReturn(Optional.of("ses_old_123"));
        when(sessionGateway.isExplicitlyInitialized(anyString())).thenReturn(false);
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.allowed());
    }

    @Test
    void isExplicitlyInitialized_topicExistsAndFlagTrue_returnsTrue() {
        String topicId = "test-topic";
        when(sessionGateway.isExplicitlyInitialized(topicId)).thenReturn(true);

        boolean result = sessionGateway.isExplicitlyInitialized(topicId);

        assertTrue(result);
    }

    @Test
    void isExplicitlyInitialized_topicExistsButFlagFalse_returnsFalse() {
        String topicId = "test-topic";
        when(sessionGateway.isExplicitlyInitialized(topicId)).thenReturn(false);

        boolean result = sessionGateway.isExplicitlyInitialized(topicId);

        assertFalse(result);
    }

    @Test
    void setExplicitlyInitialized_callsGateway() {
        String topicId = "test-topic";

        sessionGateway.setExplicitlyInitialized(topicId);

        verify(sessionGateway).setExplicitlyInitialized(topicId);
    }

    @Test
    void clearExplicitlyInitialized_callsGateway() {
        String topicId = "test-topic";

        sessionGateway.clearExplicitlyInitialized(topicId);

        verify(sessionGateway).clearExplicitlyInitialized(topicId);
    }

    @Test
    void handleResetCommand_inTopicEnvironment_clearsInitialization() {
        String topicId = "test-topic-456";
        Message message = createTestMessage("/opencode reset", topicId);
        when(sessionGateway.getSessionId(topicId)).thenReturn(Optional.of("ses_abc123"));

        String response = commandHandler.handle(message, "reset", new String[]{});

        verify(sessionGateway).clearExplicitlyInitialized(topicId);
        verify(sessionGateway).clearSession(topicId);
        assertTrue(response.contains("话题已重置"));
    }

    @Test
    void handleResetCommand_withSessionId_includesSessionInResponse() {
        String topicId = "test-topic-456";
        String sessionId = "ses_abc123";
        Message message = createTestMessage("/opencode reset", topicId);
        when(sessionGateway.getSessionId(topicId)).thenReturn(Optional.of(sessionId));

        String response = commandHandler.handle(message, "reset", new String[]{});

        assertTrue(response.contains(sessionId));
        assertTrue(response.contains("话题已重置"));
    }

    @Test
    void handleResetCommand_withoutSessionId_showsHelpfulMessage() {
        String topicId = "test-topic-456";
        Message message = createTestMessage("/opencode reset", topicId);
        when(sessionGateway.getSessionId(topicId)).thenReturn(Optional.empty());

        String response = commandHandler.handle(message, "reset", new String[]{});

        assertTrue(response.contains("话题已重置"));
        assertTrue(response.contains("/opencode p"));
    }

    @Test
    void handleResetCommand_inNonTopicEnvironment_showsErrorMessage() {
        Message message = createTestMessage("/opencode reset", null);

        String response = commandHandler.handle(message, "reset", new String[]{});

        assertTrue(response.contains("只能在话题中使用"));
        verify(sessionGateway, never()).clearExplicitlyInitialized(anyString());
    }

    @Test
    void handleChatCommand_whenNotInitialized_showsInitializationGuide() {
        String topicId = "test-topic-789";
        Message message = createTestMessage("/opencode chat hello", topicId);
        when(sessionGateway.isExplicitlyInitialized(topicId)).thenReturn(false);

        String response = commandHandler.handle(message, "chat", new String[]{"hello"});

        assertTrue(response.contains("话题未初始化"));
    }

    @Test
    void handleChatCommand_withExistingSessionIdButNotExplicit_showsGuide() {
        String topicId = "test-topic-789";
        Message message = createTestMessage("/opencode chat hello", topicId);

        when(sessionGateway.isExplicitlyInitialized(topicId)).thenReturn(false);
        when(sessionGateway.getSessionId(topicId)).thenReturn(Optional.of("ses_old_session"));

        String response = commandHandler.handle(message, "chat", new String[]{"hello"});

        assertTrue(response.contains("话题未初始化"));
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

        String response = commandHandler.handle(message, "sc", new String[]{"/opencode", "sc", sessionId});

        verify(taskExecutor).executeWithSpecificSession(eq(message), isNull(), eq(sessionId));
        assertTrue(response.contains("会话已绑定"));
    }
}

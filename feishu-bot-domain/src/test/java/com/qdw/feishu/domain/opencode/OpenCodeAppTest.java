package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.command.CommandWhitelist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenCodeApp
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCodeApp 单元测试")
class OpenCodeAppTest {

    @Mock
    private OpenCodeGateway openCodeGateway;

    @Mock
    private OpenCodeCommandHandler commandHandler;

    @Mock
    private OpenCodeSessionManager sessionManager;

    private OpenCodeApp app;

    @BeforeEach
    void setUp() {
        app = new OpenCodeApp(openCodeGateway, commandHandler, sessionManager);
    }

    // ========== 辅助方法 ==========

    private Message createTestMessage(String content, String topicId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(topicId);
        message.setMessageId("msg-test-" + System.currentTimeMillis());
        message.setChatId("chat-test");
        message.setSender(new Sender("test-openid", "Test User"));
        return message;
    }

    // ========== 基本属性测试 ==========

    @Test
    @DisplayName("getAppId 应返回 'opencode'")
    void getAppId_returnsOpencode() {
        assertEquals("opencode", app.getAppId());
    }

    @Test
    @DisplayName("getAppName 应返回 'OpenCode 助手'")
    void getAppName_returnsCorrectName() {
        assertEquals("OpenCode 助手", app.getAppName());
    }

    @Test
    @DisplayName("getDescription 应返回描述")
    void getDescription_returnsDescription() {
        assertEquals("通过飞书对话控制 OpenCode，支持多轮对话", app.getDescription());
    }

    @Test
    @DisplayName("getAppAliases 应返回 ['oc', 'code']")
    void getAppAliases_returnsAliases() {
        List<String> aliases = app.getAppAliases();
        assertEquals(2, aliases.size());
        assertTrue(aliases.contains("oc"));
        assertTrue(aliases.contains("code"));
    }

    @Test
    @DisplayName("getReplyMode 应返回 TOPIC")
    void getReplyMode_returnsTopic() {
        assertEquals(ReplyMode.TOPIC, app.getReplyMode());
    }

    @Test
    @DisplayName("getHelp 应返回帮助文本")
    void getHelp_returnsHelpText() {
        String help = app.getHelp();

        assertNotNull(help);
        assertTrue(help.contains("OpenCode 助手"));
        assertTrue(help.contains("快速开始"));
        assertTrue(help.contains("对话命令"));
        assertTrue(help.contains("/opencode chat"));
    }

    // ========== 命令白名单测试 ==========

    @Test
    @DisplayName("NON_TOPIC 状态的白名单应只包含特定命令")
    void getCommandWhitelist_nonTopic_returnsCorrectWhitelist() {
        CommandWhitelist whitelist = app.getCommandWhitelist(com.qdw.feishu.domain.topic.TopicState.NON_TOPIC);

        assertNotNull(whitelist);
        assertTrue(whitelist.isCommandAllowed("connect", com.qdw.feishu.domain.topic.TopicState.NON_TOPIC));
        assertTrue(whitelist.isCommandAllowed("help", com.qdw.feishu.domain.topic.TopicState.NON_TOPIC));
        assertTrue(whitelist.isCommandAllowed("projects", com.qdw.feishu.domain.topic.TopicState.NON_TOPIC));
        assertFalse(whitelist.isCommandAllowed("chat", com.qdw.feishu.domain.topic.TopicState.NON_TOPIC));
    }

    @Test
    @DisplayName("UNINITIALIZED 状态的白名单应排除 chat 和 new")
    void getCommandWhitelist_uninitialized_excludesChatAndNew() {
        CommandWhitelist whitelist = app.getCommandWhitelist(com.qdw.feishu.domain.topic.TopicState.UNINITIALIZED);

        assertNotNull(whitelist);
        assertFalse(whitelist.isCommandAllowed("chat", com.qdw.feishu.domain.topic.TopicState.UNINITIALIZED));
        assertFalse(whitelist.isCommandAllowed("new", com.qdw.feishu.domain.topic.TopicState.UNINITIALIZED));
        assertTrue(whitelist.isCommandAllowed("projects", com.qdw.feishu.domain.topic.TopicState.UNINITIALIZED));
        assertTrue(whitelist.isCommandAllowed("sessions", com.qdw.feishu.domain.topic.TopicState.UNINITIALIZED));
    }

    @Test
    @DisplayName("INITIALIZED 状态的白名单应允许所有命令")
    void getCommandWhitelist_initialized_allowsAll() {
        CommandWhitelist whitelist = app.getCommandWhitelist(com.qdw.feishu.domain.topic.TopicState.INITIALIZED);

        assertNotNull(whitelist);
        assertTrue(whitelist.isCommandAllowed("chat", com.qdw.feishu.domain.topic.TopicState.INITIALIZED));
        assertTrue(whitelist.isCommandAllowed("new", com.qdw.feishu.domain.topic.TopicState.INITIALIZED));
        assertTrue(whitelist.isCommandAllowed("projects", com.qdw.feishu.domain.topic.TopicState.INITIALIZED));
    }

    // ========== isTopicInitialized 测试 ==========

    @Test
    @DisplayName("isTopicInitialized 应委托给 sessionManager")
    void isTopicInitialized_delegatesToSessionManager() {
        String topicId = "test-topic";
        Message message = createTestMessage("test", topicId);

        when(sessionManager.isTopicInitialized(message)).thenReturn(true);

        boolean result = app.isTopicInitialized(message);

        assertTrue(result);
        verify(sessionManager).isTopicInitialized(message);
    }

    // ========== execute 测试 ==========

    @Test
    @DisplayName("execute - 空命令应返回帮助")
    void execute_emptyCommand_returnsHelp() {
        Message message = createTestMessage("/opencode", "test-topic");

        String result = app.execute(message);

        assertTrue(result.contains("OpenCode 助手"));
        verify(commandHandler, never()).handle(any(), anyString(), any());
    }

    @Test
    @DisplayName("execute - help 命令应返回帮助")
    void execute_helpCommand_returnsHelp() {
        Message message = createTestMessage("/opencode help", "test-topic");

        String result = app.execute(message);

        assertTrue(result.contains("OpenCode 助手"));
        verify(commandHandler, never()).handle(any(), anyString(), any());
    }

    @Test
    @DisplayName("execute - sessions 命令应委托给 handler")
    void execute_sessionsCommand_delegatesToHandler() {
        String topicId = "test-topic";
        String expectedResponse = "会话列表";
        Message message = createTestMessage("/opencode sessions feishu-backend", topicId);

        when(commandHandler.handle(eq(message), eq("sessions"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
        verify(commandHandler).handle(eq(message), eq("sessions"), any());
    }

    @Test
    @DisplayName("execute - projects 命令应委托给 handler")
    void execute_projectsCommand_delegatesToHandler() {
        String topicId = "test-topic";
        String expectedResponse = "项目列表";
        Message message = createTestMessage("/opencode projects", topicId);

        when(commandHandler.handle(eq(message), eq("projects"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
        verify(commandHandler).handle(eq(message), eq("projects"), any());
    }

    @Test
    @DisplayName("execute - chat 命令应委托给 handler")
    void execute_chatCommand_delegatesToHandler() {
        String topicId = "test-topic";
        String expectedResponse = "对话响应";
        Message message = createTestMessage("/opencode chat 帮我写代码", topicId);

        when(commandHandler.handle(eq(message), eq("chat"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("execute - new 命令应委托给 handler")
    void execute_newCommand_delegatesToHandler() {
        String topicId = "test-topic";
        String expectedResponse = "新会话已创建";
        Message message = createTestMessage("/opencode new 重构模块", topicId);

        when(commandHandler.handle(eq(message), eq("new"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("execute - sc 别名命令应委托给 handler")
    void execute_scAlias_delegatesToHandler() {
        String topicId = "test-topic";
        String sessionId = "ses_abc123";
        String expectedResponse = "会话已绑定";
        Message message = createTestMessage("/opencode sc " + sessionId, topicId);

        when(commandHandler.handle(eq(message), eq("sc"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("execute - 未知命令应返回帮助（handler 返回 null）")
    void execute_unknownCommand_returnsHelp() {
        String topicId = "test-topic";
        Message message = createTestMessage("/opencode unknown", topicId);

        when(commandHandler.handle(any(), anyString(), any()))
            .thenReturn(null);

        String result = app.execute(message);

        assertTrue(result.contains("OpenCode 助手"));
    }

    @Test
    @DisplayName("execute - 命令大小写不敏感")
    void execute_commandCaseInsensitive() {
        String topicId = "test-topic";
        String expectedResponse = "响应";
        Message message = createTestMessage("/opencode PROJECTS", topicId);

        when(commandHandler.handle(any(Message.class), eq("projects"), any(String[].class)))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
        verify(commandHandler).handle(any(Message.class), eq("projects"), any(String[].class));
    }

    @Test
    @DisplayName("execute - 多余空格应被正确处理")
    void execute_extraSpacesHandled() {
        String topicId = "test-topic";
        String expectedResponse = "响应";
        Message message = createTestMessage("/opencode   projects   ", topicId);

        when(commandHandler.handle(eq(message), eq("projects"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("execute - 内容应被 trim")
    void execute_contentIsTrimmed() {
        String topicId = "test-topic";
        String expectedResponse = "响应";
        Message message = createTestMessage("  /opencode projects  ", topicId);

        when(commandHandler.handle(eq(message), eq("projects"), any()))
            .thenReturn(expectedResponse);

        String result = app.execute(message);

        assertEquals(expectedResponse, result);
    }
}

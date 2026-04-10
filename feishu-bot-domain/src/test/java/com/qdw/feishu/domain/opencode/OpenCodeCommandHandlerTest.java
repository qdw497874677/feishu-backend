package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.topic.TopicState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenCodeCommandHandler (Phase 2 - with ImContextBinding)
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCodeCommandHandler 单元测试 (Phase 2)")
class OpenCodeCommandHandlerTest {

    @Mock
    private OpenCodeGateway openCodeGateway;

    @Mock
    private OpenCodeTaskExecutor taskExecutor;

    @Mock
    private OpenCodeSessionManager sessionManager;

    @Mock
    private TopicCommandValidator commandValidator;

    private OpenCodeCommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        commandHandler = new OpenCodeCommandHandler(
            openCodeGateway,
            taskExecutor,
            sessionManager,
            commandValidator
        );

        // 默认 mock 设置 - 命令验证通过
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.allowed());

        // 默认 mock 设置 - detectTopicState 返回 NON_TOPIC 状态（无 topicId）
        when(sessionManager.detectTopicState(any(Message.class)))
            .thenAnswer(invocation -> {
                Message msg = invocation.getArgument(0);
                String topicId = msg.getTopicId();
                if (topicId == null || topicId.isEmpty()) {
                    return TopicState.NON_TOPIC;
                }
                return sessionManager.getSessionId(msg).isPresent() 
                    ? TopicState.INITIALIZED 
                    : TopicState.UNINITIALIZED;
            });

        // MessageContext overload — mirrors Message-based behavior
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.UNINITIALIZED);

        // 默认 mock 设置 - 话题未初始化（无sessionId）
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.empty());
        when(sessionManager.getSessionId(any(MessageContext.class)))
            .thenReturn(Optional.empty());

        // 默认 mock 设置 - 话题未显式初始化
        when(sessionManager.isExplicitlyInitialized(any(Message.class)))
            .thenReturn(false);
        when(sessionManager.isExplicitlyInitialized(any(MessageContext.class)))
            .thenReturn(false);

        when(sessionManager.isTopicInitialized(any(MessageContext.class)))
            .thenReturn(false);
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

    /** Create an unresolved MessageContext for tests that don't need binding. */
    private MessageContext unresolvedContext() {
        return MessageContext.unresolved();
    }

    // ========== connect 命令测试 ==========

    @Test
    @DisplayName("connect 命令 - 成功连接")
    void handleConnect_success() {
        when(openCodeGateway.getServerStatus())
            .thenReturn("服务运行正常");
        when(openCodeGateway.listProjects())
            .thenReturn("项目列表");

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode connect", null),
            "connect",
            new String[]{"/opencode", "connect"},
            CommandWhitelist.all()
        );

        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("连接成功"));
        assertTrue(text.contains("服务运行正常"));
        assertTrue(text.contains("项目列表"));
        verify(openCodeGateway).getServerStatus();
        verify(openCodeGateway).listProjects();
    }

    @Test
    @DisplayName("connect 命令 - 服务异常时显示错误")
    void handleConnect_serviceError() {
        when(openCodeGateway.getServerStatus())
            .thenThrow(new RuntimeException("服务不可用"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode connect", null),
            "connect",
            new String[]{"/opencode", "connect"},
            CommandWhitelist.all()
        );

        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("连接成功") || text.contains("无法获取"));
    }

    // ========== sessions 命令测试 ==========

    @Test
    @DisplayName("sessions 命令 - 缺少参数")
    void handleSessions_missingParameters() {
        // 缺少参数，"sessions" 命令应该返回帮助/错误
        when(commandValidator.validateCommand(eq("sessions"), any(), any()))
            .thenReturn(ValidationResult.restricted("用法错误"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode sessions", null),
            "sessions",
            new String[]{"/opencode", "sessions"},
            CommandWhitelist.all()
        );

        // 验证返回受限消息
        assertNotNull(result);
        assertEquals("用法错误", result.getReplyContent());
    }

    @Test
    @DisplayName("sessions 命令 - 非话题环境返回受限消息")
    void handleSessions_success() {
        // NON_TOPIC 环境下 sessions 不在白名单中
        when(commandValidator.validateCommand(eq("sessions"), any(), any()))
            .thenReturn(ValidationResult.restricted("用法错误"));

        String project = "feishu-backend";

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode sessions " + project, null),
            "sessions",
            new String[]{"/opencode", "sessions", project},
            CommandWhitelist.all()
        );

        // 验证返回受限消息
        assertNotNull(result);
        assertEquals("用法错误", result.getReplyContent());
    }

    // ========== projects 命令测试 ==========

    @Test
    @DisplayName("projects 命令 - 别名 p（非话题环境）")
    void handleProjects_aliasP() {
        String projectList = "项目列表：feishu-backend, other-project";
        when(openCodeGateway.listProjects())
            .thenReturn(projectList);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode p", null),
            "p",
            new String[]{"/opencode", "p"},
            CommandWhitelist.builder().add("p").build()
        );

        // p 命令在非话题环境允许直接执行，应调用listProjects并返回结果
        assertNotNull(result);
        assertEquals(projectList, result.getReplyContent());
        verify(openCodeGateway).listProjects();
    }

    @Test
    @DisplayName("projects 命令 - 全称")
    void handleProjects_fullName() {
        when(openCodeGateway.listProjects())
            .thenReturn("项目列表");

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode projects", null),
            "projects",
            new String[]{"/opencode", "projects"},
            CommandWhitelist.builder().add("projects").build()
        );

        assertNotNull(result);
        assertEquals("项目列表", result.getReplyContent());
    }

    // ========== new 命令测试 ==========

    @Test
    @DisplayName("new 命令 - 参数不足返回错误提示")
    void handleNew_missingPrompt() {
        // 模拟已初始化的话题，这样命令验证可以通过
        String topicId = "init-topic";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of("ses_123"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode new", topicId),
            "new",
            new String[]{"/opencode", "new"},
            CommandWhitelist.all()
        );

        // new 命令参数不足时返回包含"用法"的错误提示
        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("❌") || text.contains("用法"));
        assertTrue(text.contains("new"));
    }

    @Test
    @DisplayName("new 命令 - 成功创建新会话")
    void handleNew_success() {
        String prompt = "重构登录模块";
        AppExecutionResult expectedResult = AppExecutionResult.noReply();

        // 模拟已初始化的话题
        String topicId = "init-topic";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of("ses_123"));
        when(sessionManager.isTopicInitialized(any(Message.class))).thenReturn(true);
        when(sessionManager.isTopicInitialized(any(MessageContext.class))).thenReturn(true);
        when(sessionManager.detectTopicState(any(MessageContext.class))).thenReturn(TopicState.INITIALIZED);

        when(taskExecutor.executeWithNewSession(any(Message.class), eq(prompt), isNull()))
            .thenReturn(expectedResult);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode new " + prompt, topicId),
            "new",
            new String[]{"/opencode", "new", prompt},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        // 验证返回了正确的结果并调用了正确的方法 (async = noReply)
        assertNotNull(result);
        assertNull(result.getReplyContent());
        verify(taskExecutor).executeWithNewSession(any(Message.class), eq(prompt), isNull());
    }

    // ========== chat 命令测试 ==========

     @Test
    @DisplayName("chat 命令 - 非话题环境返回连接引导")
    void handleChat_nonTopic() {
        // NON_TOPIC 模式下，"chat" 不在白名单中，应返回受限消息
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat 帮我", null),
            "chat",
            new String[]{"/opencode", "chat", "帮我"},
            CommandWhitelist.builder().add("chat").build()
        );

        // 非话题环境，chat 不在白名单中，应返回连接引导
        assertNotNull(result);
        assertEquals("命令受限", result.getReplyContent());
    }

    @Test
    @DisplayName("chat 命令 - cn 创建新会话")
    void handleChat_uninitializedTopic() throws Exception {
        String topicId = "uninit-topic";
        when(sessionManager.isTopicInitialized(any(Message.class)))
            .thenReturn(false);
        when(taskExecutor.createSessionOnly(any(Message.class)))
            .thenReturn("✅ 会话已创建");
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of("ses_new_123"));
        
        when(commandValidator.validateCommand(eq("cn"), any(), any()))
            .thenReturn(ValidationResult.allowed());

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode cn hello", topicId),
            "cn",
            new String[]{"/opencode", "cn", "hello"},
            CommandWhitelist.builder().add("cn").build()
        );

        assertNotNull(result);
        verify(taskExecutor).createSessionOnly(any(Message.class));
    }
    
    @Test
    @DisplayName("chat 命令 - 已初始化话题，无内容时显示状态")
    void handleChat_initializedNoContent() throws Exception {
        String topicId = "init-topic";
        String sessionId = "ses_init_123";
        when(sessionManager.isExplicitlyInitialized(any(Message.class)))
            .thenReturn(true);
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of(sessionId));
        when(sessionManager.getSessionId(any(MessageContext.class)))
            .thenReturn(Optional.of(sessionId));
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.INITIALIZED);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat", topicId),
            "chat",
            new String[]{"/opencode", "chat"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("当前会话信息"));
        assertTrue(text.contains(sessionId));
    }

    @Test
    @DisplayName("chat 命令 - 成功发送对话")
    void handleChat_success() {
        String topicId = "init-topic";
        String prompt = "帮我写个排序函数";
        when(sessionManager.isTopicInitialized(any(Message.class)))
            .thenReturn(true);
        when(sessionManager.isTopicInitialized(any(MessageContext.class)))
            .thenReturn(true);
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.INITIALIZED);
        when(taskExecutor.executeWithAutoSession(any(), eq(prompt)))
            .thenReturn(AppExecutionResult.noReply());

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat " + prompt, topicId),
            "chat",
            new String[]{"/opencode", "chat", prompt},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result);
        assertNull(result.getReplyContent()); // async = noReply
        verify(taskExecutor).executeWithAutoSession(any(), eq(prompt));
    }

    // ========== session 命令测试 ==========

    @Test
    @DisplayName("session status 命令 - 有活跃会话")
    void handleSessionStatus_withActiveSession() {
        String topicId = "status-topic";
        String sessionId = "ses_status_123";
        String statusText = "📋 **当前会话信息**\n\n  🆔 Session ID: `" + sessionId + "`\n  💬 话题 ID: `" + topicId + "`\n  ✅ 状态: 活跃\n\n💡 继续对话会自动使用此会话";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of(sessionId));
        when(sessionManager.getCurrentSessionStatus(any(Message.class)))
            .thenReturn(statusText);
        when(sessionManager.getCurrentSessionStatus(any(MessageContext.class)))
            .thenReturn(statusText);
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.INITIALIZED);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode session status", topicId),
            "session",
            new String[]{"/opencode", "session", "status"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result, "session status 命令不应返回 null");
        String text = result.getReplyContent();
        assertTrue(text.contains("会话") || text.contains(sessionId));
    }

    @Test
    @DisplayName("session status 命令 - 无会话")
    void handleSessionStatus_noSession() {
        String topicId = "no-session-topic";
        String statusText = "📭 当前话题还没有 OpenCode 会话\n\n💡 发送 `/opencode <提示词>` 创建新会话";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.empty());
        when(sessionManager.getCurrentSessionStatus(any(Message.class)))
            .thenReturn(statusText);
        when(sessionManager.getCurrentSessionStatus(any(MessageContext.class)))
            .thenReturn(statusText);
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.UNINITIALIZED);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode session status", topicId),
            "session",
            new String[]{"/opencode", "session", "status"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result, "session status 命令不应返回 null");
        String text = result.getReplyContent();
        assertTrue(text.contains("话题") || text.contains("会话"));
    }

    @Test
    @DisplayName("session list 命令")
    void handleSessionList() {
        String sessionsList = "所有会话列表：ses_1, ses_2, ses_3";
        when(sessionManager.handleListSessions())
            .thenReturn(sessionsList);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode session list", "test-topic"),
            "session",
            new String[]{"/opencode", "session", "list"},
            CommandWhitelist.all()
        );

        // 验证返回了正确的会话列表
        assertNotNull(result);
        assertEquals(sessionsList, result.getReplyContent());
        verify(sessionManager).handleListSessions();
    }

    @Test
    @DisplayName("sc 别名命令 - 成功绑定会话")
    void handleScAlias_success() {
        String topicId = "sc-topic";
        String sessionId = "ses_sc_123";
        when(taskExecutor.executeWithSpecificSession(any(), isNull(), eq(sessionId)))
            .thenReturn(AppExecutionResult.withSession("✅ 会话已绑定", sessionId, false));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode sc " + sessionId, topicId),
            "sc",
            new String[]{"/opencode", "sc", sessionId},
            CommandWhitelist.all()
        );

        assertNotNull(result);
        assertTrue(result.getReplyContent().contains("会话已绑定"));
        verify(taskExecutor).executeWithSpecificSession(any(), isNull(), eq(sessionId));
    }

    @Test
    @DisplayName("sc 别名命令 - 参数不足")
    void handleScAlias_missingSessionId() {
        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode sc", "test-topic"),
            "sc",
            new String[]{"/opencode", "sc"},
            CommandWhitelist.all()
        );

        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("用法"));
        assertTrue(text.contains("/opencode sc <session_id>"));
    }

    // ========== reset 命令测试 ==========

    @Test
    @DisplayName("reset 命令 - 非话题环境")
    void handleReset_nonTopic() {
        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode reset", null),
            "reset",
            new String[]{"/opencode", "reset"},
            CommandWhitelist.all()
        );

        assertNotNull(result);
        assertTrue(result.getReplyContent().contains("只能在话题中使用"));
        verify(sessionManager, never()).clearSession(any(Message.class));
    }

    @Test
    @DisplayName("reset 命令 - 成功重置")
    void handleReset_success() {
        String topicId = "reset-topic";
        String sessionId = "ses_reset_123";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of(sessionId));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode reset", topicId),
            "reset",
            new String[]{"/opencode", "reset"},
            CommandWhitelist.all()
        );

        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("话题已重置"));
        assertTrue(text.contains(sessionId));
        verify(sessionManager).clearSession(any(Message.class));
        verify(sessionManager).clearExplicitlyInitialized(any(Message.class));
    }

    // ========== 未知命令测试 ==========

    @Test
    @DisplayName("未知命令应返回帮助消息")
    void handleUnknownCommand_returnsHelp() {
        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode unknown", "test-topic"),
            "unknown",
            new String[]{"/opencode", "unknown"},
            CommandWhitelist.all()
        );

        // 实现返回未知命令提示
        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains("未知") || text.contains("命令"));
    }

    // ========== status 快捷命令测试 ==========

    @Test
    @DisplayName("status 命令 - INITIALIZED 状态返回会话信息")
    void should_returnSessionStatus_when_statusCommandInInitializedTopic() {
        String topicId = "status-init-topic";
        String sessionId = "ses_status_456";
        String statusText = "📋 **当前会话信息**\n\n  🆔 Session ID: `" + sessionId + "`";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.INITIALIZED);
        when(sessionManager.getCurrentSessionStatus(any(MessageContext.class)))
            .thenReturn(statusText);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode status", topicId),
            "status",
            new String[]{"/opencode", "status"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result, "status 命令不应返回 null");
        assertEquals(statusText, result.getReplyContent());
        verify(sessionManager).getCurrentSessionStatus(any(MessageContext.class));
    }

    @Test
    @DisplayName("status 命令 - UNINITIALIZED 状态返回未绑定引导")
    void should_returnUnboundHint_when_statusCommandInUninitializedTopic() {
        String topicId = "status-uninit-topic";
        String statusText = "📭 当前话题还没有 OpenCode 会话\n\n💡 发送 `/opencode <提示词>` 创建新会话";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(TopicState.UNINITIALIZED);
        when(sessionManager.getCurrentSessionStatus(any(MessageContext.class)))
            .thenReturn(statusText);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode status", topicId),
            "status",
            new String[]{"/opencode", "status"},
            CommandWhitelist.builder().add("status").build(),
            unresolvedContext()
        );

        assertNotNull(result, "status 命令不应返回 null");
        assertEquals(statusText, result.getReplyContent());
        verify(sessionManager).getCurrentSessionStatus(any(MessageContext.class));
    }

    // ========== 状态检测测试 ==========

    @Test
    @DisplayName("非话题环境且非允许命令 - 应显示连接引导")
    void handle_nonTopicWithNotAllowedCommand() {
        // NON_TOPIC 模式下，"chat" 命令不在白名单中，应返回受限消息
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat help", null),
            "chat",
            new String[]{"/opencode", "chat", "help"},
            CommandWhitelist.builder().add("chat").build()
        );

        assertNotNull(result);
        assertEquals("命令受限", result.getReplyContent());
    }

    @Test
    @DisplayName("话题未初始化时 chat 命令自动创建会话")
    void handle_uninitializedTopicWithNonInitCommand() {
        String topicId = "uninit-topic";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.empty());
        when(taskExecutor.executeWithNewSession(any(Message.class), eq("help")))
            .thenReturn(AppExecutionResult.noReply());
        
        // UNINITIALIZED 模式下，"chat" 命令不在白名单中
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat help", topicId),
            "chat",
            new String[]{"/opencode", "chat", "help"},
            CommandWhitelist.builder().add("chat").build()
        );

        // 未初始化话题的 chat 命令应返回受限消息
        assertNotNull(result);
        assertTrue(result.getReplyContent().contains("受限"));
        verify(taskExecutor, never()).executeWithNewSession(any(Message.class), eq("help"));
    }

    @Test
    @DisplayName("命令验证失败 - 应返回验证消息")
    void handle_commandValidationFailed() {
        String restrictionMessage = "命令不允许";

        // 模拟已初始化的话题
        String topicId = "init-topic";
        when(sessionManager.getSessionId(any(Message.class)))
            .thenReturn(Optional.of("ses_123"));
        when(sessionManager.isExplicitlyInitialized(any(Message.class)))
            .thenReturn(true);  // 标记为已显式初始化

        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.restricted(restrictionMessage));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat help", topicId),
            "chat",
            new String[]{"/opencode", "chat", "help"},
            CommandWhitelist.all()
        );

        // 验证返回了验证失败的消息
        assertNotNull(result);
        assertEquals(restrictionMessage, result.getReplyContent());
    }
}

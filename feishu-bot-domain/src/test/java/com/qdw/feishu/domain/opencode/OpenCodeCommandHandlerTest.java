package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.qdw.feishu.domain.session.ContextSessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
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

    @Mock
    private CardRenderer cardRenderer;

    @Mock
    private FeishuGateway feishuGateway;

    @Mock
    private WizardManager wizardManager;

    private NextStepSuggester nextStepSuggester;

    private OpenCodeMessageFormatter messageFormatter;

    private OpenCodeCommandHandler commandHandler;

    @BeforeEach
    void setUp() {
        nextStepSuggester = new NextStepSuggester();
        messageFormatter = new OpenCodeMessageFormatter();
        commandHandler = new OpenCodeCommandHandler(
            openCodeGateway,
            taskExecutor,
            sessionManager,
            commandValidator,
            nextStepSuggester,
            messageFormatter,
            cardRenderer,
            feishuGateway,
            wizardManager
        );

        // 默认 mock 设置 - 命令验证通过
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.allowed());

        // 默认 mock - 向导不活跃
        when(wizardManager.isWizardActive(anyString())).thenReturn(false);

        // 默认 mock 设置 - detectTopicState 返回 NON_TOPIC 状态（无 topicId）
        when(sessionManager.detectTopicState(any(Message.class)))
            .thenAnswer(invocation -> {
                Message msg = invocation.getArgument(0);
                String topicId = msg.getTopicId();
                if (topicId == null || topicId.isEmpty()) {
                    return ContextSessionState.UNBOUND;
                }
                return sessionManager.getSessionId(msg).isPresent() 
                    ? ContextSessionState.IN_APP_WITH_SESSION 
                    : ContextSessionState.IN_APP_NO_SESSION;
            });

        // MessageContext overload — mirrors Message-based behavior
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_NO_SESSION);

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
        assertTrue(result.getReplyContent().startsWith(projectList), "应以项目列表开头");
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
        assertTrue(result.getReplyContent().startsWith("项目列表"), "应以项目列表开头");
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
        when(sessionManager.detectTopicState(any(MessageContext.class))).thenReturn(ContextSessionState.IN_APP_WITH_SESSION);

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
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);

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
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
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
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);

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
            .thenReturn(ContextSessionState.IN_APP_NO_SESSION);

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

        // 验证返回了正确的会话列表 (session command appends next-step suggestion)
        assertNotNull(result);
        assertTrue(result.getReplyContent().startsWith(sessionsList), "应以会话列表开头");
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
    @DisplayName("未知命令（话题内 UNINITIALIZED）应自动触发向导")
    void handleUnknownCommand_returnsHelp() {
        String topicId = "test-topic";
        // UNINITIALIZED + 在话题内 + 无活跃向导 → 自动触发向导（行为已变更）
        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_NO_SESSION);
        when(wizardManager.isWizardActive(topicId)).thenReturn(false);

        com.qdw.feishu.domain.card.CardContent mockCard =
            com.qdw.feishu.domain.card.CardContent.builder().headerTitle("向导").build();
        WizardManager.WizardResult mockResult =
            WizardManager.WizardResult.of(mockCard, WizardManager.WizardStep.SELECT_PROJECT);
        when(wizardManager.start(anyString(), eq(topicId))).thenReturn(mockResult);
        when(cardRenderer.render(any(), any())).thenReturn("{\"schema\":\"2.0\"}");

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode unknown", topicId),
            "unknown",
            new String[]{"/opencode", "unknown"},
            CommandWhitelist.all()
        );

        // 新行为：UNINITIALIZED + 未知命令 → 触发向导，返回 noReply
        assertNotNull(result);
        assertNull(result.getReplyContent(), "UNINITIALIZED 话题中未知命令应触发向导（noReply）");
        verify(wizardManager).start(anyString(), eq(topicId));
    }

    // ========== status 快捷命令测试 ==========

    @Test
    @DisplayName("status 命令 - INITIALIZED 状态返回会话信息")
    void should_returnSessionStatus_when_statusCommandInInitializedTopic() {
        String topicId = "status-init-topic";
        String sessionId = "ses_status_456";
        String statusText = "📋 **当前会话信息**\n\n  🆔 Session ID: `" + sessionId + "`";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
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
        assertTrue(result.getReplyContent().startsWith(statusText), "应以状态文本开头");
        assertTrue(result.getReplyContent().contains("下一步"), "应包含下一步建议");
        verify(sessionManager).getCurrentSessionStatus(any(MessageContext.class));
    }

    @Test
    @DisplayName("status 命令 - UNINITIALIZED 状态返回未绑定引导")
    void should_returnUnboundHint_when_statusCommandInUninitializedTopic() {
        String topicId = "status-uninit-topic";
        String statusText = "📭 当前话题还没有 OpenCode 会话\n\n💡 发送 `/opencode <提示词>` 创建新会话";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
                .thenReturn(ContextSessionState.IN_APP_NO_SESSION);
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
        assertTrue(result.getReplyContent().startsWith(statusText), "应以状态文本开头");
        assertTrue(result.getReplyContent().contains("下一步"), "应包含下一步建议");
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

    // ========== NextStepSuggester 集成测试 ==========

    @Test
    @DisplayName("projects 命令执行后回复包含下一步建议")
    void should_appendNextStepSuggestion_after_projects() {
        String projectList = "项目列表：feishu-backend";
        when(openCodeGateway.listProjects()).thenReturn(projectList);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode projects", null),
            "projects",
            new String[]{"/opencode", "projects"},
            CommandWhitelist.builder().add("projects").build(),
            unresolvedContext()
        );

        assertNotNull(result);
        String text = result.getReplyContent();
        assertTrue(text.contains(projectList), "应包含项目列表");
        assertTrue(text.contains("下一步"), "应包含下一步建议");
        assertTrue(text.contains("sessions"), "建议应提到 sessions");
    }

    @Test
    @DisplayName("chat 命令执行后回复不包含下一步建议")
    void should_notAppendNextStepSuggestion_after_chat() {
        String topicId = "init-topic";
        String prompt = "帮我写代码";
        when(sessionManager.isTopicInitialized(any(MessageContext.class))).thenReturn(true);
        when(sessionManager.detectTopicState(any(MessageContext.class))).thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
        when(taskExecutor.executeWithAutoSession(any(), eq(prompt)))
            .thenReturn(AppExecutionResult.noReply());

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat " + prompt, topicId),
            "chat",
            new String[]{"/opencode", "chat", prompt},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        // chat returns noReply (async), so no suggestion either
        assertNotNull(result);
        assertNull(result.getReplyContent());
    }

    // ============ 向导集成测试 ============

    @Test
    @DisplayName("向导进行中，非向导命令被拦截并提示")
    void should_interceptNonWizardCommand_when_wizardActive() {
        String topicId = "wizard-topic";
        when(wizardManager.isWizardActive(topicId)).thenReturn(true);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode projects", topicId),
            "projects",
            new String[]{"/opencode", "projects"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result);
        assertTrue(result.getReplyContent().contains("向导进行中"), "应提示向导进行中");
        verify(openCodeGateway, never()).listProjects();
    }

    @Test
    @DisplayName("向导 action 路由到 WizardManager")
    void should_routeWizardAction_to_wizardManager() {
        String topicId = "wizard-topic";
        String chatId = "chat_123";
        when(wizardManager.isWizardActive(topicId)).thenReturn(true);
        when(wizardManager.handleAction(eq("wizard_confirm"), eq(chatId), eq(topicId)))
            .thenReturn(WizardManager.WizardResult.ofText("✅ 向导完成"));

        Message message = createTestMessage("/opencode wizard_confirm", topicId);
        message.setChatId(chatId);

        AppExecutionResult result = commandHandler.handle(
            message,
            "wizard_confirm",
            new String[]{"/opencode", "wizard_confirm"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result);
        verify(wizardManager).handleAction(eq("wizard_confirm"), eq(chatId), eq(topicId));
    }

    @Test
    @DisplayName("向导 cancel 不被拦截（即使向导活跃）")
    void should_allowWizardCancel_even_when_wizardActive() {
        String topicId = "wizard-topic";
        String chatId = "chat_456";
        // cancel 是向导 action，不应被拦截
        when(wizardManager.isWizardActive(topicId)).thenReturn(true);
        when(wizardManager.handleAction(eq("wizard_cancel"), eq(chatId), eq(topicId)))
            .thenReturn(WizardManager.WizardResult.ofText("已取消向导"));

        Message message = createTestMessage("/opencode wizard_cancel", topicId);
        message.setChatId(chatId);

        AppExecutionResult result = commandHandler.handle(
            message,
            "wizard_cancel",
            new String[]{"/opencode", "wizard_cancel"},
            CommandWhitelist.all(),
            unresolvedContext()
        );

        assertNotNull(result);
        assertFalse(result.getReplyContent().contains("向导进行中"), "取消命令不应被拦截");
    }

    // ========== Task 4: 增强会话列表 (sessions card) ==========

    @Test
    @DisplayName("sessions 命令 - 话题中返回卡片（调用 sendInteractiveMessage + noReply）")
    void handleSessionsCommand_inTopic_returnsCard() throws Exception {
        String topicId = "topic-sessions-card";
        Message message = createTestMessage("/opencode sessions feishu-backend", topicId);

        com.qdw.feishu.domain.model.ImContextRef contextRef =
            com.qdw.feishu.domain.model.ImContextRef.feishuThread(topicId);
        MessageContext threadContext = MessageContext.of(contextRef, null);

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
        when(sessionManager.getSessionId(any(MessageContext.class)))
            .thenReturn(Optional.of("ses_123"));

        List<com.qdw.feishu.domain.opencode.SessionInfo> sessions = List.of(
            com.qdw.feishu.domain.opencode.SessionInfo.builder()
                .sessionId("ses_abc").title("Test session")
                .lastPrompt("some prompt").relativeTime("5分钟前").projectName("feishu-backend").build()
        );
        when(openCodeGateway.listRecentSessionsStructured(eq("feishu-backend"), anyInt()))
            .thenReturn(sessions);

        when(cardRenderer.render(any(), any())).thenReturn("{\"card\":\"json\"}");

        AppExecutionResult result = commandHandler.handle(
            message,
            "sessions",
            new String[]{"/opencode", "sessions", "feishu-backend"},
            CommandWhitelist.all(),
            threadContext
        );

        assertNotNull(result);
        assertNull(result.getReplyContent(), "话题中 sessions 应返回 noReply（卡片已通过 feishuGateway 发送）");
        verify(feishuGateway).sendInteractiveMessage(any(Message.class), anyString(), eq(topicId));
    }

    @Test
    @DisplayName("sessions 命令 - 卡片渲染失败时降级为文本")
    void handleSessionsCommand_cardRenderFails_fallbackToText() throws Exception {
        String topicId = "topic-sessions-fallback";
        Message message = createTestMessage("/opencode sessions feishu-backend", topicId);

        com.qdw.feishu.domain.model.ImContextRef contextRef =
            com.qdw.feishu.domain.model.ImContextRef.feishuThread(topicId);
        MessageContext threadContext = MessageContext.of(contextRef, null);

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
        when(sessionManager.getSessionId(any(MessageContext.class)))
            .thenReturn(Optional.of("ses_123"));

        when(openCodeGateway.listRecentSessionsStructured(anyString(), anyInt()))
            .thenThrow(new RuntimeException("network error"));

        when(sessionManager.handleSessionsCommand(any()))
            .thenReturn("📋 text session list");

        AppExecutionResult result = commandHandler.handle(
            message,
            "sessions",
            new String[]{"/opencode", "sessions", "feishu-backend"},
            CommandWhitelist.all(),
            threadContext
        );

        assertNotNull(result);
        assertNotNull(result.getReplyContent(), "卡片失败时应有文本回复");
        assertTrue(result.getReplyContent().contains("📋"), "降级结果应包含会话列表文本");
        verify(feishuGateway, never()).sendInteractiveMessage(any(), any(), any());
    }

    @Test
    @DisplayName("sessions 命令 - 会话列表卡片底部有新建会话按钮（通过卡片内容验证）")
    void handleSessionsCommand_cardIncludesNewSessionButton() throws Exception {
        String topicId = "topic-sessions-newbtn";
        Message message = createTestMessage("/opencode sessions feishu-backend", topicId);

        com.qdw.feishu.domain.model.ImContextRef contextRef =
            com.qdw.feishu.domain.model.ImContextRef.feishuThread(topicId);
        MessageContext threadContext = MessageContext.of(contextRef, null);

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
        when(sessionManager.getSessionId(any(MessageContext.class)))
            .thenReturn(Optional.of("ses_456"));

        List<com.qdw.feishu.domain.opencode.SessionInfo> sessions = List.of(
            com.qdw.feishu.domain.opencode.SessionInfo.builder()
                .sessionId("ses_xyz").title("A session").relativeTime("1小时前").projectName("feishu-backend").build()
        );
        when(openCodeGateway.listRecentSessionsStructured(eq("feishu-backend"), anyInt()))
            .thenReturn(sessions);

        // Capture the CardContent passed to renderer to verify new-session button
        org.mockito.ArgumentCaptor<com.qdw.feishu.domain.card.CardContent> cardCaptor =
            org.mockito.ArgumentCaptor.forClass(com.qdw.feishu.domain.card.CardContent.class);
        when(cardRenderer.render(cardCaptor.capture(), any())).thenReturn("{\"card\":\"ok\"}");

        commandHandler.handle(
            message,
            "sessions",
            new String[]{"/opencode", "sessions", "feishu-backend"},
            CommandWhitelist.all(),
            threadContext
        );

        com.qdw.feishu.domain.card.CardContent capturedCard = cardCaptor.getValue();
        assertNotNull(capturedCard);
        // Verify at least one element is a button group with new-session action
        boolean hasNewSessionButton = capturedCard.getElements().stream()
            .filter(e -> e.isButtonGroup())
            .flatMap(e -> e.getButtons().stream())
            .anyMatch(btn -> btn.getAction() != null && btn.getAction().contains("wizard_new_session:feishu-backend"));
        assertTrue(hasNewSessionButton, "卡片应包含新建会话按钮");
    }

    @Test
    @DisplayName("sessions 命令 - 非话题中使用文本格式")
    void handleSessionsCommand_notInTopic_fallbackToText() {
        Message message = createTestMessage("/opencode sessions feishu-backend", null);
        MessageContext chatContext = MessageContext.unresolved();

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.UNBOUND);
        when(sessionManager.handleSessionsCommand(any()))
            .thenReturn("📋 sessions text");

        AppExecutionResult result = commandHandler.handle(
            message,
            "sessions",
            new String[]{"/opencode", "sessions", "feishu-backend"},
            CommandWhitelist.all(),
            chatContext
        );

        assertNotNull(result);
        // non-topic: validator may block or fallback to text
        // ensure sendInteractiveMessage was NOT called
        verify(feishuGateway, never()).sendInteractiveMessage(any(), any(), any());
    }

    // ============ Task 1: UNINITIALIZED 自动向导触发测试 ============

    @Test
    @DisplayName("Test 1: UNINITIALIZED + 在话题内 + 非管理命令 → 自动触发向导, sendInteractiveMessage, noReply")
    void should_autoTriggerWizard_when_uninitializedTopicAndNonControlCommand() {
        String topicId = "uninit-auto-topic";
        String chatId = "chat_auto_123";
        Message message = createTestMessage("/opencode chat 帮我写代码", topicId);
        message.setChatId(chatId);

        com.qdw.feishu.domain.card.CardContent mockCard =
            com.qdw.feishu.domain.card.CardContent.builder().headerTitle("Test Wizard").build();
        WizardManager.WizardResult mockResult =
            WizardManager.WizardResult.of(mockCard, WizardManager.WizardStep.SELECT_PROJECT);

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_NO_SESSION);
        when(wizardManager.isWizardActive(topicId)).thenReturn(false);
        when(wizardManager.start(anyString(), eq(topicId))).thenReturn(mockResult);
        when(cardRenderer.render(any(), any())).thenReturn("{\"schema\":\"2.0\"}");

        AppExecutionResult result = commandHandler.handle(
            message,
            "chat",
            new String[]{"/opencode", "chat", "帮我写代码"},
            CommandWhitelist.all(),
            MessageContext.unresolved()
        );

        assertNotNull(result);
        assertNull(result.getReplyContent(), "应返回 noReply（卡片已通过 feishuGateway 发送）");
        verify(wizardManager).start(anyString(), eq(topicId));
        verify(feishuGateway).sendInteractiveMessage(any(Message.class), anyString(), eq(topicId));
    }

    @Test
    @DisplayName("Test 2: UNINITIALIZED + 明确管理命令 sc → 不触发向导，走正常路由")
    void should_notTriggerWizard_when_uninitializedTopicAndExplicitControlCommand() {
        String topicId = "uninit-sc-topic";
        String sessionId = "ses_explicit_123";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_NO_SESSION);
        when(wizardManager.isWizardActive(topicId)).thenReturn(false);
        when(taskExecutor.executeWithSpecificSession(any(), isNull(), eq(sessionId)))
            .thenReturn(AppExecutionResult.withSession("✅ 会话已绑定", sessionId, false));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode sc " + sessionId, topicId),
            "sc",
            new String[]{"/opencode", "sc", sessionId},
            CommandWhitelist.all(),
            MessageContext.unresolved()
        );

        assertNotNull(result);
        // 验证向导 start() 从未被调用
        verify(wizardManager, never()).start(anyString(), anyString());
        // 验证 sc 命令正常路由
        verify(taskExecutor).executeWithSpecificSession(any(), isNull(), eq(sessionId));
    }

    @Test
    @DisplayName("Test 3: UNINITIALIZED + 向导已活跃 → 向导拦截正常触发，不重复调用 start()")
    void should_interceptWithWizardGuard_when_wizardAlreadyActive() {
        String topicId = "uninit-wizard-active-topic";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_NO_SESSION);
        when(wizardManager.isWizardActive(topicId)).thenReturn(true);

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat hello", topicId),
            "chat",
            new String[]{"/opencode", "chat", "hello"},
            CommandWhitelist.all(),
            MessageContext.unresolved()
        );

        assertNotNull(result);
        assertTrue(result.getReplyContent().contains("向导进行中"), "应提示向导进行中");
        // 向导已活跃，不应再次调用 start()
        verify(wizardManager, never()).start(anyString(), anyString());
    }

    @Test
    @DisplayName("Test 4: INITIALIZED + 非管理命令 → 不触发向导（仅 UNINITIALIZED 触发）")
    void should_notTriggerWizard_when_topicIsInitialized() {
        String topicId = "init-chat-topic";
        String prompt = "帮我写代码";

        when(sessionManager.detectTopicState(any(MessageContext.class)))
            .thenReturn(ContextSessionState.IN_APP_WITH_SESSION);
        when(wizardManager.isWizardActive(topicId)).thenReturn(false);
        when(sessionManager.isTopicInitialized(any(MessageContext.class))).thenReturn(true);
        when(taskExecutor.executeWithAutoSession(any(), eq(prompt)))
            .thenReturn(AppExecutionResult.noReply());

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat " + prompt, topicId),
            "chat",
            new String[]{"/opencode", "chat", prompt},
            CommandWhitelist.all(),
            MessageContext.unresolved()
        );

        assertNotNull(result);
        // INITIALIZED 不触发向导
        verify(wizardManager, never()).start(anyString(), anyString());
    }

    @Test
    @DisplayName("Test 5: UNINITIALIZED + NON_TOPIC (topicId=null) → 不触发向导（只在话题内触发）")
    void should_notTriggerWizard_when_nonTopicContext() {
        // topicId = null => NON_TOPIC, 命令不通过白名单验证
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        AppExecutionResult result = commandHandler.handle(
            createTestMessage("/opencode chat hello", null),
            "chat",
            new String[]{"/opencode", "chat", "hello"},
            CommandWhitelist.all(),
            MessageContext.unresolved()
        );

        assertNotNull(result);
        // 非话题环境，命令被白名单拦截，向导 start() 不被调用
        verify(wizardManager, never()).start(anyString(), anyString());
    }
}

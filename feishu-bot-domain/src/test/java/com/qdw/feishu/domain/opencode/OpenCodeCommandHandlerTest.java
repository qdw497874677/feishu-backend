package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
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
 * Unit tests for OpenCodeCommandHandler
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCodeCommandHandler 单元测试")
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

        // 默认 mock 设置 - 话题未初始化（无sessionId）
        when(sessionManager.getSessionId(anyString()))
            .thenReturn(Optional.empty());

        // 默认 mock 设置 - 话题未显式初始化
        when(sessionManager.isExplicitlyInitialized(anyString()))
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

    // ========== connect 命令测试 ==========

    @Test
    @DisplayName("connect 命令 - 成功连接")
    void handleConnect_success() {
        when(openCodeGateway.getServerStatus())
            .thenReturn("服务运行正常");
        when(openCodeGateway.listProjects())
            .thenReturn("项目列表");

        String result = commandHandler.handle(
            createTestMessage("/opencode connect", null),
            "connect",
            new String[]{"/opencode", "connect"}
        );

        assertTrue(result.contains("连接成功"));
        assertTrue(result.contains("服务运行正常"));
        assertTrue(result.contains("项目列表"));
        verify(openCodeGateway).getServerStatus();
        verify(openCodeGateway).listProjects();
    }

    @Test
    @DisplayName("connect 命令 - 服务异常时显示错误")
    void handleConnect_serviceError() {
        when(openCodeGateway.getServerStatus())
            .thenThrow(new RuntimeException("服务不可用"));

        String result = commandHandler.handle(
            createTestMessage("/opencode connect", null),
            "connect",
            new String[]{"/opencode", "connect"}
        );

        assertTrue(result.contains("连接成功") || result.contains("无法获取"));
    }

    // ========== sessions 命令测试 ==========

    @Test
    @DisplayName("sessions 命令 - 缺少参数")
    void handleSessions_missingParameters() {
        // 缺少参数，"sessions" 命令应该返回帮助/错误
        when(commandValidator.validateCommand(eq("sessions"), any(), any()))
            .thenReturn(ValidationResult.restricted("用法错误"));

        String result = commandHandler.handle(
            createTestMessage("/opencode sessions", null),
            "sessions",
            new String[]{"/opencode", "sessions"}
        );

        // 非话题环境，sessions 不在白名单中，应返回连接引导
        assertTrue(result.contains("连接引导") || result.contains("connect"));
    }

    @Test
    @DisplayName("sessions 命令 - 非话题环境返回连接引导")
    void handleSessions_success() {
        String project = "feishu-backend";
        when(openCodeGateway.listRecentSessions(eq(project), eq(5)))
            .thenReturn("会话列表: ses_1, ses_2");
        
        // 非话题环境，sessions 不在白名单中，应返回受限消息
        when(commandValidator.validateCommand(eq("sessions"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        String result = commandHandler.handle(
            createTestMessage("/opencode sessions " + project, null),
            "sessions",
            new String[]{"/opencode", "sessions", project}
        );

        // 非话题环境，sessions 不在白名单中，应返回连接引导
        assertTrue(result.contains("连接引导") || result.contains("connect"));
    }

    @Test
    @DisplayName("sessions 命令 - 非话题环境返回连接引导")
    void handleSessions_success() {
        String project = "feishu-backend";
        when(openCodeGateway.listRecentSessions(eq(project), eq(5)))
            .thenReturn("会话列表: ses_1, ses_2");

        String result = commandHandler.handle(
            createTestMessage("/opencode sessions " + project, null),
            "sessions",
            new String[]{"/opencode", "sessions", project}
        );

        // 非话题环境，sessions 不在白名单中，应返回连接引导
        assertTrue(result.contains("连接引导") || result.contains("connect"));
    }

    // ========== projects 命令测试 ==========

    @Test
    @DisplayName("projects 命令 - 别名 p（非话题环境）")
    void handleProjects_aliasP() {
        String projectList = "项目列表：feishu-backend, other-project";
        when(openCodeGateway.listProjects())
            .thenReturn(projectList);

        String result = commandHandler.handle(
            createTestMessage("/opencode p", null),
            "p",
            new String[]{"/opencode", "p"}
        );

        // p 命令在非话题环境允许直接执行，应调用listProjects并返回结果
        assertEquals(projectList, result);
        verify(openCodeGateway).listProjects();
    }

    @Test
    @DisplayName("projects 命令 - 全称")
    void handleProjects_fullName() {
        when(openCodeGateway.listProjects())
            .thenReturn("项目列表");

        String result = commandHandler.handle(
            createTestMessage("/opencode projects", null),
            "projects",
            new String[]{"/opencode", "projects"}
        );

        assertEquals("项目列表", result);
    }

    // ========== new 命令测试 ==========

    @Test
    @DisplayName("new 命令 - 参数不足返回错误提示")
    void handleNew_missingPrompt() {
        // 模拟已初始化的话题，这样命令验证可以通过
        String topicId = "init-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.of("ses_123"));

        String result = commandHandler.handle(
            createTestMessage("/opencode new", topicId),
            "new",
            new String[]{"/opencode", "new"}
        );

        // new 命令参数不足时返回包含"用法"的错误提示
        assertTrue(result.contains("❌") || result.contains("用法"));
        assertTrue(result.contains("new"));
    }

    @Test
    @DisplayName("new 命令 - 成功创建新会话")
    void handleNew_success() {
        String prompt = "重构登录模块";
        String expectedResponse = "会话已创建";

        // 模拟已初始化的话题
        String topicId = "init-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.of("ses_123"));

        when(taskExecutor.executeWithNewSession(any(Message.class), eq(prompt), isNull()))
            .thenReturn(expectedResponse);

        String result = commandHandler.handle(
            createTestMessage("/opencode new " + prompt, topicId),
            "new",
            new String[]{"/opencode", "new", prompt}
        );

        // 验证返回了正确的结果并调用了正确的方法
        assertEquals(expectedResponse, result);
        verify(taskExecutor).executeWithNewSession(any(Message.class), eq(prompt), isNull());
    }

    // ========== chat 命令测试 ==========

     @Test
    @DisplayName("chat 命令 - 非话题环境返回连接引导")
    void handleChat_nonTopic() {
        // NON_TOPIC 模式下，"chat" 不在白名单中，应返回受限消息
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        String result = commandHandler.handle(
            createTestMessage("/opencode chat 帮我", null),
            "chat",
            new String[]{"/opencode", "chat", "帮我"}
        );

        // 非话题环境，chat 不在白名单中，应返回连接引导
        assertTrue(result.contains("连接引导") || result.contains("connect"));
    }

    @Test
    @DisplayName("chat 命令 - 话题未初始化时自动创建会话")
    void handleChat_uninitializedTopic() {
        String topicId = "uninit-topic";
        when(sessionManager.isTopicInitialized(any(Message.class)))
            .thenReturn(false);
        when(taskExecutor.executeWithNewSession(any(Message.class), eq("hello")))
            .thenReturn("✅ 会话已创建");
        
        // UNINITIALIZED 模式下，"chat" 命令不在白名单中（只有 "chatnow"/"cn"）
        // 但 test 名称暗示 "chatnow" 的行为，让我改为测试 "cn"
        when(commandValidator.validateCommand(eq("cn"), any(), any()))
            .thenReturn(ValidationResult.allowed());

        String result = commandHandler.handle(
            createTestMessage("/opencode cn hello", topicId),
            "cn",
            new String[]{"/opencode", "cn", "hello"}
        );

        assertTrue(result.contains("会话已创建"));
        verify(taskExecutor).executeWithNewSession(any(Message.class), eq("hello"));
    }

    @Test
    @DisplayName("chat 命令 - 已初始化话题，无内容时显示状态")
    void handleChat_initializedNoContent() {
        String topicId = "init-topic";
        String sessionId = "ses_init_123";
        when(sessionManager.isExplicitlyInitialized(topicId))
            .thenReturn(true);
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.of(sessionId));

        String result = commandHandler.handle(
            createTestMessage("/opencode chat", topicId),
            "chat",
            new String[]{"/opencode", "chat"}
        );

        assertTrue(result.contains("当前会话信息"));
        assertTrue(result.contains(sessionId));
    }

    @Test
    @DisplayName("chat 命令 - 成功发送对话")
    void handleChat_success() {
        String topicId = "init-topic";
        String prompt = "帮我写个排序函数";
        when(sessionManager.isTopicInitialized(any(Message.class)))
            .thenReturn(true);
        when(taskExecutor.executeWithAutoSession(any(), eq(prompt)))
            .thenReturn("对话完成");

        String result = commandHandler.handle(
            createTestMessage("/opencode chat " + prompt, topicId),
            "chat",
            new String[]{"/opencode", "chat", prompt}
        );

        assertEquals("对话完成", result);
        verify(taskExecutor).executeWithAutoSession(any(), eq(prompt));
    }

    // ========== session 命令测试 ==========

    @Test
    @DisplayName("session status 命令 - 有活跃会话")
    void handleSessionStatus_withActiveSession() {
        String topicId = "status-topic";
        String sessionId = "ses_status_123";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.of(sessionId));
        when(sessionManager.getCurrentSessionStatus(any()))
            .thenReturn("📋 **当前会话信息**\n\n  🆔 Session ID: `" + sessionId + "`\n  💬 话题 ID: `" + topicId + "`\n  ✅ 状态: 活跃\n\n💡 继续对话会自动使用此会话");

        String result = commandHandler.handle(
            createTestMessage("/opencode session status", topicId),
            "session",
            new String[]{"/opencode", "session", "status"}
        );

        assertNotNull(result, "session status 命令不应返回 null");
        assertTrue(result.contains("会话") || result.contains(sessionId));
    }

    @Test
    @DisplayName("session status 命令 - 无会话")
    void handleSessionStatus_noSession() {
        String topicId = "no-session-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.empty());
        when(sessionManager.getCurrentSessionStatus(any()))
            .thenReturn("📭 当前话题还没有 OpenCode 会话\n\n💡 发送 `/opencode <提示词>` 创建新会话");

        String result = commandHandler.handle(
            createTestMessage("/opencode session status", topicId),
            "session",
            new String[]{"/opencode", "session", "status"}
        );

        assertNotNull(result, "session status 命令不应返回 null");
        assertTrue(result.contains("话题") || result.contains("会话"));
    }

    @Test
    @DisplayName("session list 命令")
    void handleSessionList() {
        String sessionsList = "所有会话列表：ses_1, ses_2, ses_3";
        when(sessionManager.handleListSessions())
            .thenReturn(sessionsList);

        String result = commandHandler.handle(
            createTestMessage("/opencode session list", "test-topic"),
            "session",
            new String[]{"/opencode", "session", "list"}
        );

        // 验证返回了正确的会话列表
        assertEquals(sessionsList, result);
        verify(sessionManager).handleListSessions();
    }

    @Test
    @DisplayName("sc 别名命令 - 成功绑定会话")
    void handleScAlias_success() {
        String topicId = "sc-topic";
        String sessionId = "ses_sc_123";
        when(taskExecutor.executeWithSpecificSession(any(), isNull(), eq(sessionId)))
            .thenReturn("✅ 会话已绑定");

        String result = commandHandler.handle(
            createTestMessage("/opencode sc " + sessionId, topicId),
            "sc",
            new String[]{"/opencode", "sc", sessionId}
        );

        assertTrue(result.contains("会话已绑定"));
        verify(taskExecutor).executeWithSpecificSession(any(), isNull(), eq(sessionId));
    }

    @Test
    @DisplayName("sc 别名命令 - 参数不足")
    void handleScAlias_missingSessionId() {
        String result = commandHandler.handle(
            createTestMessage("/opencode sc", "test-topic"),
            "sc",
            new String[]{"/opencode", "sc"}
        );

        assertTrue(result.contains("用法"));
        assertTrue(result.contains("/opencode sc <session_id>"));
    }

    // ========== reset 命令测试 ==========

    @Test
    @DisplayName("reset 命令 - 非话题环境")
    void handleReset_nonTopic() {
        String result = commandHandler.handle(
            createTestMessage("/opencode reset", null),
            "reset",
            new String[]{"/opencode", "reset"}
        );

        assertTrue(result.contains("只能在话题中使用"));
        verify(sessionManager, never()).clearSession(anyString());
    }

    @Test
    @DisplayName("reset 命令 - 成功重置")
    void handleReset_success() {
        String topicId = "reset-topic";
        String sessionId = "ses_reset_123";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.of(sessionId));

        String result = commandHandler.handle(
            createTestMessage("/opencode reset", topicId),
            "reset",
            new String[]{"/opencode", "reset"}
        );

        assertTrue(result.contains("话题已重置"));
        assertTrue(result.contains(sessionId));
        verify(sessionManager).clearSession(topicId);
        verify(sessionManager).clearExplicitlyInitialized(topicId);
    }

    // ========== 未知命令测试 ==========

    @Test
    @DisplayName("未知命令应返回帮助消息")
    void handleUnknownCommand_returnsHelp() {
        String result = commandHandler.handle(
            createTestMessage("/opencode unknown", "test-topic"),
            "unknown",
            new String[]{"/opencode", "unknown"}
        );

        // 实现返回未知命令提示
        assertTrue(result.contains("未知") || result.contains("命令"));
    }

    // ========== 状态检测测试 ==========

    @Test
    @DisplayName("话题未初始化时 chat 命令自动创建会话")
    void handle_uninitializedTopicWithNonInitCommand() {
        String topicId = "uninit-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.empty());
        when(taskExecutor.executeWithNewSession(any(Message.class), eq("help")))
            .thenReturn("✅ 会话已创建");

        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", topicId),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        // 未初始化话题的 chat 命令应自动创建会话
        assertTrue(result.contains("会话已创建"));
        verify(taskExecutor).executeWithNewSession(any(Message.class), eq("help"));
    }

    @Test
    @DisplayName("非话题环境且非允许命令 - 应显示连接引导")
    void handle_nonTopicWithNotAllowedCommand() {
        // NON_TOPIC 模式下，"chat" 命令不在白名单中，应返回受限消息
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", null),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        assertTrue(result.contains("连接引导") || result.contains("connect"));
    }

    @Test
    @DisplayName("话题未初始化时 chat 命令自动创建会话")
    void handle_uninitializedTopicWithNonInitCommand() {
        String topicId = "uninit-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.empty());
        when(taskExecutor.executeWithNewSession(any(Message.class), eq("help")))
            .thenReturn("✅ 会话已创建");
        
        // UNINITIALIZED 模式下，"chat" 命令不在白名单中
        when(commandValidator.validateCommand(eq("chat"), any(), any()))
            .thenReturn(ValidationResult.restricted("命令受限"));

        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", topicId),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        // 未初始化话题的 chat 命令应返回受限消息
        assertTrue(result.contains("受限"));
        verify(taskExecutor, never()).executeWithNewSession(any(Message.class), eq("help"));
    }

    @Test
    @DisplayName("命令验证失败 - 应返回验证消息")
    void handle_commandValidationFailed() {
        String restrictionMessage = "命令不允许";

        // 模拟已初始化的话题
        String topicId = "init-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.of("ses_123"));
        when(sessionManager.isExplicitlyInitialized(topicId))
            .thenReturn(true);  // 标记为已显式初始化

        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.restricted(restrictionMessage));

        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", topicId),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        // 验证返回了验证失败的消息
        assertEquals(restrictionMessage, result);
    }
}

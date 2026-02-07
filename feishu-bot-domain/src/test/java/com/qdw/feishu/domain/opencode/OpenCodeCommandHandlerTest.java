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

        // 默认 mock 设置
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.allowed());
        when(sessionManager.getSessionId(anyString()))
            .thenReturn(Optional.empty());
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
    @DisplayName("sessions 命令 - 参数不足时返回用法")
    void handleSessions_missingParameters() {
        String result = commandHandler.handle(
            createTestMessage("/opencode sessions", null),
            "sessions",
            new String[]{"/opencode", "sessions"}
        );

        assertTrue(result.contains("用法"));
        assertTrue(result.contains("/opencode sessions <项目名称>"));
    }

    @Test
    @DisplayName("sessions 命令 - 成功查询")
    void handleSessions_success() {
        String project = "feishu-backend";
        when(openCodeGateway.listRecentSessions(eq(project), eq(5)))
            .thenReturn("会话列表: ses_1, ses_2");

        String result = commandHandler.handle(
            createTestMessage("/opencode sessions " + project, null),
            "sessions",
            new String[]{"/opencode", "sessions", project}
        );

        assertTrue(result.contains("会话列表"));
    }

    // ========== projects 命令测试 ==========

    @Test
    @DisplayName("projects 命令 - 别名 p")
    void handleProjects_aliasP() {
        when(openCodeGateway.listProjects())
            .thenReturn("项目列表");

        String result = commandHandler.handle(
            createTestMessage("/opencode p", null),
            "p",
            new String[]{"/opencode", "p"}
        );

        assertEquals("项目列表", result);
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
    @DisplayName("new 命令 - 参数不足时返回用法")
    void handleNew_missingPrompt() {
        String result = commandHandler.handle(
            createTestMessage("/opencode new", "test-topic"),
            "new",
            new String[]{"/opencode", "new"}
        );

        assertTrue(result.contains("用法"));
        assertTrue(result.contains("/opencode new <提示词>"));
    }

    @Test
    @DisplayName("new 命令 - 成功创建新会话")
    void handleNew_success() {
        String prompt = "重构登录模块";
        when(taskExecutor.executeWithNewSession(any(), eq(prompt)))
            .thenReturn("会话已创建并开始对话");

        String result = commandHandler.handle(
            createTestMessage("/opencode new " + prompt, "test-topic"),
            "new",
            new String[]{"/opencode", "new", prompt}
        );

        assertTrue(result.contains("会话已创建"));
        verify(taskExecutor).executeWithNewSession(any(), eq(prompt));
    }

    // ========== chat 命令测试 ==========

    @Test
    @DisplayName("chat 命令 - 非话题环境")
    void handleChat_nonTopic() {
        String result = commandHandler.handle(
            createTestMessage("/opencode chat 帮我", null),
            "chat",
            new String[]{"/opencode", "chat", "帮我"}
        );

        assertTrue(result.contains("用法"));
        assertTrue(result.contains("/opencode chat <对话内容>"));
    }

    @Test
    @DisplayName("chat 命令 - 话题未初始化")
    void handleChat_uninitializedTopic() {
        String topicId = "uninit-topic";
        when(sessionManager.isExplicitlyInitialized(topicId))
            .thenReturn(false);

        String result = commandHandler.handle(
            createTestMessage("/opencode chat hello", topicId),
            "chat",
            new String[]{"/opencode", "chat", "hello"}
        );

        assertTrue(result.contains("话题未初始化"));
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
        when(sessionManager.isExplicitlyInitialized(topicId))
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

        String result = commandHandler.handle(
            createTestMessage("/opencode session status", topicId),
            "session",
            new String[]{"/opencode", "session", "status"}
        );

        assertTrue(result.contains("当前会话信息"));
        assertTrue(result.contains(sessionId));
    }

    @Test
    @DisplayName("session status 命令 - 无会话")
    void handleSessionStatus_noSession() {
        String topicId = "no-session-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.empty());

        String result = commandHandler.handle(
            createTestMessage("/opencode session status", topicId),
            "session",
            new String[]{"/opencode", "session", "status"}
        );

        assertTrue(result.contains("当前话题还没有 OpenCode 会话"));
    }

    @Test
    @DisplayName("session list 命令")
    void handleSessionList() {
        when(openCodeGateway.listSessions())
            .thenReturn("所有会话列表");

        String result = commandHandler.handle(
            createTestMessage("/opencode session list", "test-topic"),
            "session",
            new String[]{"/opencode", "session", "list"}
        );

        assertEquals("所有会话列表", result);
        verify(openCodeGateway).listSessions();
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

        assertTrue(result.contains("未知的子命令"));
        assertTrue(result.contains("可用子命令"));
    }

    // ========== 状态检测测试 ==========

    @Test
    @DisplayName("话题未初始化且非初始化命令 - 应显示引导")
    void handle_uninitializedTopicWithNonInitCommand() {
        String topicId = "uninit-topic";
        when(sessionManager.getSessionId(topicId))
            .thenReturn(Optional.empty());

        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", topicId),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        assertTrue(result.contains("欢迎来到 OpenCode 助手"));
        assertTrue(result.contains("初始化步骤"));
    }

    @Test
    @DisplayName("非话题环境且非允许命令 - 应显示连接引导")
    void handle_nonTopicWithNotAllowedCommand() {
        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", null),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        assertTrue(result.contains("连接引导"));
    }

    @Test
    @DisplayName("命令验证失败 - 应返回验证消息")
    void handle_commandValidationFailed() {
        when(commandValidator.validateCommand(anyString(), any(), any()))
            .thenReturn(ValidationResult.restricted("命令不允许"));

        String result = commandHandler.handle(
            createTestMessage("/opencode chat help", "test-topic"),
            "chat",
            new String[]{"/opencode", "chat", "help"}
        );

        assertTrue(result.contains("命令不允许"));
    }
}

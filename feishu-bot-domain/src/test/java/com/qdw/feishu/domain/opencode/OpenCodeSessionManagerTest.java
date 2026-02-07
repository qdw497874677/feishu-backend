package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenCodeSessionManager
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCodeSessionManager 单元测试")
class OpenCodeSessionManagerTest {

    @Mock
    private OpenCodeGateway openCodeGateway;

    @Mock
    private OpenCodeSessionGateway sessionGateway;

    private OpenCodeSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new OpenCodeSessionManager(openCodeGateway, sessionGateway);
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

    // ========== isTopicInitialized 测试 ==========

    @Test
    @DisplayName("话题已绑定会话时，应返回 true")
    void isTopicInitialized_withSession_returnsTrue() {
        String topicId = "test-topic-123";
        Message message = createTestMessage("test content", topicId);

        when(sessionGateway.getSessionId(topicId))
            .thenReturn(Optional.of("ses_abc123"));

        boolean result = sessionManager.isTopicInitialized(message);

        assertTrue(result);
        verify(sessionGateway).getSessionId(topicId);
    }

    @Test
    @DisplayName("话题未绑定会话时，应返回 false")
    void isTopicInitialized_withoutSession_returnsFalse() {
        String topicId = "test-topic-456";
        Message message = createTestMessage("test content", topicId);

        when(sessionGateway.getSessionId(topicId))
            .thenReturn(Optional.empty());

        boolean result = sessionManager.isTopicInitialized(message);

        assertFalse(result);
        verify(sessionGateway).getSessionId(topicId);
    }

    @Test
    @DisplayName("非话题环境（topicId 为 null）时，应返回 false")
    void isTopicInitialized_nullTopicId_returnsFalse() {
        Message message = createTestMessage("test content", null);

        boolean result = sessionManager.isTopicInitialized(message);

        assertFalse(result);
        verify(sessionGateway, never()).getSessionId(anyString());
    }

    @Test
    @DisplayName("非话题环境（topicId 为空字符串）时，应返回 false")
    void isTopicInitialized_emptyTopicId_returnsFalse() {
        Message message = createTestMessage("test content", "");

        boolean result = sessionManager.isTopicInitialized(message);

        assertFalse(result);
        verify(sessionGateway, never()).getSessionId(anyString());
    }

    // ========== getCurrentSessionStatus 测试 ==========

    @Test
    @DisplayName("获取会话状态 - 有活跃会话")
    void getCurrentSessionStatus_withActiveSession_returnsStatus() {
        String topicId = "test-topic-789";
        String sessionId = "ses_active_123";
        Message message = createTestMessage("test", topicId);

        when(sessionGateway.getSessionId(topicId))
            .thenReturn(Optional.of(sessionId));

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("当前会话信息"));
        assertTrue(result.contains(sessionId));
        assertTrue(result.contains(topicId));
        assertTrue(result.contains("活跃"));
    }

    @Test
    @DisplayName("获取会话状态 - 话题无会话")
    void getCurrentSessionStatus_withoutSession_returnsHelpMessage() {
        String topicId = "test-topic-no-session";
        Message message = createTestMessage("test", topicId);

        when(sessionGateway.getSessionId(topicId))
            .thenReturn(Optional.empty());

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("当前话题还没有 OpenCode 会话"));
        assertTrue(result.contains("/opencode"));
    }

    @Test
    @DisplayName("获取会话状态 - 非话题环境")
    void getCurrentSessionStatus_nullTopicId_returnsErrorMessage() {
        Message message = createTestMessage("test", null);

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("当前不在话题中"));
        assertTrue(result.contains("无法查看会话状态"));
    }

    // ========== handleSessionsCommand 测试 ==========

    @Test
    @DisplayName("查询会话列表 - 参数不足时返回用法说明")
    void handleSessionsCommand_missingParameters_returnsUsage() {
        String[] parts = {"/opencode", "sessions"};

        String result = sessionManager.handleSessionsCommand(parts);

        assertTrue(result.contains("用法"));
        assertTrue(result.contains("/opencode sessions <项目名称>"));
        assertTrue(result.contains("示例"));
    }

    @Test
    @DisplayName("查询会话列表 - 空项目名称时返回用法说明")
    void handleSessionsCommand_emptyProjectName_returnsUsage() {
        String[] parts = {"/opencode", "sessions", "   "};

        String result = sessionManager.handleSessionsCommand(parts);

        assertTrue(result.contains("用法"));
    }

    @Test
    @DisplayName("查询会话列表 - 成功调用 gateway")
    void handleSessionsCommand_validProject_callsGateway() {
        String project = "feishu-backend";
        String[] parts = {"/opencode", "sessions", project};
        String expectedResponse = "📋 项目 **feishu-backend** 的最近 5 个会话:\n\n" +
                                  "1. Session One\n2. Session Two";

        when(openCodeGateway.listRecentSessions(project, 5))
            .thenReturn(expectedResponse);

        String result = sessionManager.handleSessionsCommand(parts);

        assertEquals(expectedResponse, result);
        verify(openCodeGateway).listRecentSessions(project, 5);
    }

    @Test
    @DisplayName("查询会话列表 - 自定义数量")
    void handleSessionsCommand_withCustomLimit() {
        String project = "my-project";
        int limit = 10;
        String[] parts = {"/opencode", "sessions", project, String.valueOf(limit)};

        when(openCodeGateway.listRecentSessions(project, limit))
            .thenReturn("会话列表");

        sessionManager.handleSessionsCommand(parts);

        verify(openCodeGateway).listRecentSessions(project, limit);
    }

    @Test
    @DisplayName("查询会话列表 - 数量超出范围时返回错误")
    void handleSessionsCommand_limitTooLarge_returnsError() {
        String project = "my-project";
        String[] parts = {"/opencode", "sessions", project, "100"};

        String result = sessionManager.handleSessionsCommand(parts);

        assertTrue(result.contains("数量必须在 1-20 之间"));
        verify(openCodeGateway, never()).listRecentSessions(anyString(), anyInt());
    }

    @Test
    @DisplayName("查询会话列表 - 数量小于 1 时返回错误")
    void handleSessionsCommand_limitTooSmall_returnsError() {
        String project = "my-project";
        String[] parts = {"/opencode", "sessions", project, "0"};

        String result = sessionManager.handleSessionsCommand(parts);

        assertTrue(result.contains("数量必须在 1-20 之间"));
    }

    @Test
    @DisplayName("查询会话列表 - 无效数量时使用默认值")
    void handleSessionsCommand_invalidLimit_usesDefault() {
        String project = "my-project";
        String[] parts = {"/opencode", "sessions", project, "invalid"};

        when(openCodeGateway.listRecentSessions(project, 5))
            .thenReturn("会话列表");

        sessionManager.handleSessionsCommand(parts);

        verify(openCodeGateway).listRecentSessions(project, 5);
    }

    // ========== saveSession 测试 ==========

    @Test
    @DisplayName("保存会话映射 - 成功保存")
    void saveSession_validTopicId_savesToGateway() {
        String topicId = "topic-save-123";
        String sessionId = "ses_save_456";

        sessionManager.saveSession(topicId, sessionId);

        verify(sessionGateway).saveSession(topicId, sessionId);
    }

    @Test
    @DisplayName("保存会话映射 - topicId 为 null 时不保存")
    void saveSession_nullTopicId_doesNotSave() {
        String sessionId = "ses_save_789";

        sessionManager.saveSession(null, sessionId);

        verify(sessionGateway, never()).saveSession(anyString(), anyString());
    }

    @Test
    @DisplayName("保存会话映射 - topicId 为空字符串时不保存")
    void saveSession_emptyTopicId_doesNotSave() {
        String sessionId = "ses_save_abc";

        sessionManager.saveSession("", sessionId);

        verify(sessionGateway, never()).saveSession(anyString(), anyString());
    }

    // ========== clearSession 测试 ==========

    @Test
    @DisplayName("清除会话映射 - 成功清除")
    void clearSession_validTopicId_clearsFromGateway() {
        String topicId = "topic-clear-123";

        sessionManager.clearSession(topicId);

        verify(sessionGateway).clearSession(topicId);
    }

    @Test
    @DisplayName("清除会话映射 - topicId 为 null 时不清除")
    void clearSession_nullTopicId_doesNotClear() {
        sessionManager.clearSession(null);

        verify(sessionGateway, never()).clearSession(anyString());
    }

    // ========== getSessionId 测试 ==========

    @Test
    @DisplayName("获取会话 ID - 委托给 gateway")
    void getSessionId_delegatesToGateway() {
        String topicId = "topic-get-123";
        String expectedSessionId = "ses_get_456";

        when(sessionGateway.getSessionId(topicId))
            .thenReturn(Optional.of(expectedSessionId));

        Optional<String> result = sessionManager.getSessionId(topicId);

        assertTrue(result.isPresent());
        assertEquals(expectedSessionId, result.get());
        verify(sessionGateway).getSessionId(topicId);
    }

    // ========== 显式初始化标记测试 ==========

    @Test
    @DisplayName("检查显式初始化 - 委托给 gateway")
    void isExplicitlyInitialized_delegatesToGateway() {
        String topicId = "topic-init-123";

        sessionManager.isExplicitlyInitialized(topicId);

        verify(sessionGateway).isExplicitlyInitialized(topicId);
    }

    @Test
    @DisplayName("设置显式初始化标记 - 委托给 gateway")
    void setExplicitlyInitialized_delegatesToGateway() {
        String topicId = "topic-set-123";

        sessionManager.setExplicitlyInitialized(topicId);

        verify(sessionGateway).setExplicitlyInitialized(topicId);
    }

    @Test
    @DisplayName("清除显式初始化标记 - 委托给 gateway")
    void clearExplicitlyInitialized_delegatesToGateway() {
        String topicId = "topic-clear-123";

        sessionManager.clearExplicitlyInitialized(topicId);

        verify(sessionGateway).clearExplicitlyInitialized(topicId);
    }

    // ========== handleListSessions 测试 ==========

    @Test
    @DisplayName("列出所有会话 - 委托给 gateway")
    void handleListSessions_delegatesToGateway() {
        String expectedResponse = "所有会话列表";
        when(openCodeGateway.listSessions())
            .thenReturn(expectedResponse);

        String result = sessionManager.handleListSessions();

        assertEquals(expectedResponse, result);
        verify(openCodeGateway).listSessions();
    }
}

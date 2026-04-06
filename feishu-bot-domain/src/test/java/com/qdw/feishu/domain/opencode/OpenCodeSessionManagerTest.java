package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.opencode.OpenCodeSessionData;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.TypeToken;
import com.qdw.feishu.domain.topic.TopicState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenCodeSessionManager (Phase 2 - with ImContextBinding)
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCodeSessionManager 单元测试 (Phase 2)")
class OpenCodeSessionManagerTest {

    private OpenCodeGateway openCodeGateway;
    private AppSessionGateway appSessionGateway;
    private ImContextBindingGateway bindingGateway;
    private OpenCodeSessionManager sessionManager;
    
    private static final TypeToken<OpenCodeSessionData> TYPE_TOKEN = new TypeToken<OpenCodeSessionData>() {};

    @BeforeEach
    void setUp() {
        openCodeGateway = mock(OpenCodeGateway.class);
        appSessionGateway = mock(AppSessionGateway.class);
        bindingGateway = mock(ImContextBindingGateway.class);
        sessionManager = new OpenCodeSessionManager(openCodeGateway, appSessionGateway, bindingGateway);
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
    
    private Message createTestMessageWithChat(String content, String chatId) {
        Message message = new Message();
        message.setContent(content);
        message.setTopicId(null);
        message.setMessageId("msg-test-" + System.currentTimeMillis());
        message.setChatId(chatId);
        message.setSender(new Sender("test-openid", "Test User"));
        return message;
    }
    
    @SuppressWarnings("unchecked")
    private AppSession<OpenCodeSessionData> createMockSession(String sessionId, String openCodeSessionId) {
        OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
        AppSession<OpenCodeSessionData> session = mock(AppSession.class);
        when(session.getSessionId()).thenReturn(sessionId);
        when(session.getData()).thenReturn(data);
        when(session.getVersion()).thenReturn(1L);
        return session;
    }
    
    @SuppressWarnings("unchecked")
    private AppSession<OpenCodeSessionData> createMockSession(String sessionId, String openCodeSessionId, boolean explicitlyInitialized) {
        OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
        data.setExplicitlyInitialized(explicitlyInitialized);
        AppSession<OpenCodeSessionData> session = mock(AppSession.class);
        when(session.getSessionId()).thenReturn(sessionId);
        when(session.getData()).thenReturn(data);
        when(session.getVersion()).thenReturn(1L);
        return session;
    }
    
    private ImContextBinding createBinding(ImContextRef contextRef, String sessionId) {
        return ImContextBinding.create(contextRef, "opencode", sessionId);
    }

    // ========== detectTopicState 测试 ==========

    @Test
    @DisplayName("话题已绑定会话时，应返回 INITIALIZED")
    @SuppressWarnings("unchecked")
    void detectTopicState_withBinding_returnsInitialized() {
        String topicId = "test-topic-123";
        Message message = createTestMessage("test content", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, "ses_123");

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));

        TopicState result = sessionManager.detectTopicState(message);

        assertEquals(TopicState.INITIALIZED, result);
    }

    @Test
    @DisplayName("话题未绑定会话时，应返回 UNINITIALIZED")
    void detectTopicState_withoutBinding_returnsUninitialized() {
        String topicId = "test-topic-456";
        Message message = createTestMessage("test content", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        TopicState result = sessionManager.detectTopicState(message);

        assertEquals(TopicState.UNINITIALIZED, result);
    }

    @Test
    @DisplayName("绑定到 OpenCode 但 sessionId 为空时，应返回 UNINITIALIZED")
    void should_detectUninitialized_when_boundToOpenCodeWithoutSession() {
        String topicId = "topic-null-session";
        Message message = createTestMessage("test content", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));

        TopicState result = sessionManager.detectTopicState(message);

        assertEquals(TopicState.UNINITIALIZED, result);
    }

    @Test
    @DisplayName("绑定到 OpenCode 且 sessionId 存在时，应返回 INITIALIZED")
    void should_detectInitialized_when_boundToOpenCodeWithSession() {
        String topicId = "topic-with-session";
        Message message = createTestMessage("test content", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, "ses_123");

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));

        TopicState result = sessionManager.detectTopicState(message);

        assertEquals(TopicState.INITIALIZED, result);
    }

    @Test
    @DisplayName("非话题环境（topicId 和 chatId 都为 null）时，应返回 NON_TOPIC")
    void detectTopicState_noContext_returnsNonTopic() {
        Message message = new Message();
        message.setContent("test");
        message.setTopicId(null);
        message.setChatId(null);

        TopicState result = sessionManager.detectTopicState(message);

        assertEquals(TopicState.NON_TOPIC, result);
        verify(bindingGateway, never()).findBinding(any());
    }

    @Test
    @DisplayName("非话题环境但有用 chatId 时，应返回 UNINITIALIZED")
    void detectTopicState_withChatId_returnsUninitialized() {
        String chatId = "chat-789";
        Message message = createTestMessageWithChat("test content", chatId);
        ImContextRef contextRef = ImContextRef.feishuChat(chatId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        TopicState result = sessionManager.detectTopicState(message);

        assertEquals(TopicState.UNINITIALIZED, result);
    }

    // ========== getCurrentSessionStatus 测试 ==========

    @Test
    @DisplayName("获取会话状态 - 有活跃会话")
    @SuppressWarnings("unchecked")
    void getCurrentSessionStatus_withActiveSession_returnsStatus() {
        String topicId = "test-topic-789";
        String openCodeSessionId = "opencode_ses_active_123";
        String appSessionId = "ses_789";
        Message message = createTestMessage("test", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(appSessionId, openCodeSessionId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("当前会话信息"));
        assertTrue(result.contains(appSessionId));
        assertTrue(result.contains("活跃"));
    }

    @Test
    @DisplayName("dangling binding 时应返回 OpenCode 引导")
    void should_returnOpenCodeGuidance_when_bindingSessionIsDangling() {
        String topicId = "topic-dangling";
        String appSessionId = "ses_missing";
        Message message = createTestMessage("test", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.empty());

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("OpenCode 上下文"));
        assertTrue(result.contains("还没有激活会话"));
    }

    @Test
    @DisplayName("获取会话状态 - 话题无会话")
    void getCurrentSessionStatus_withoutSession_returnsHelpMessage() {
        String topicId = "test-topic-no-session";
        Message message = createTestMessage("test", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("还没有 OpenCode 会话"));
        assertTrue(result.contains("/opencode"));
    }

    @Test
    @DisplayName("获取会话状态 - 非话题环境")
    void getCurrentSessionStatus_nullContext_returnsErrorMessage() {
        Message message = new Message();
        message.setContent("test");
        message.setTopicId(null);
        message.setChatId(null);

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("不在话题中"));
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
    @DisplayName("保存会话 - 创建新会话并绑定")
    @SuppressWarnings("unchecked")
    void saveSession_newContext_createsAndBinds() {
        String topicId = "topic-save-123";
        String openCodeSessionId = "ses_save_456";
        Message message = createTestMessage("test", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());
        when(appSessionGateway.createSession(eq("opencode"), any(OpenCodeSessionData.class), any(TypeToken.class)))
            .thenReturn("app_ses_123");
        
        ImContextBinding newBinding = ImContextBinding.create(contextRef, "opencode", "app_ses_123");
        when(bindingGateway.bind(contextRef, "opencode", "app_ses_123"))
            .thenReturn(BindingResult.created(newBinding));

        sessionManager.saveSession(message, openCodeSessionId);

        verify(appSessionGateway).createSession(eq("opencode"), any(OpenCodeSessionData.class), any(TypeToken.class));
        verify(bindingGateway).bind(contextRef, "opencode", "app_ses_123");
    }

    @Test
    @DisplayName("已有 null session binding 时，显式创建/选择会话应升级 binding")
    void should_upgradeBinding_when_sessionIsExplicitlyCreatedOrSelected() {
        ImContextRef contextRef = ImContextRef.feishuThread("topic-upgrade");
        ImContextBinding existingBinding = createBinding(contextRef, null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(existingBinding));
        when(appSessionGateway.createSession(eq("opencode"), any(OpenCodeSessionData.class), any(TypeToken.class)))
            .thenReturn("app_ses_upgraded");
        when(bindingGateway.bind(contextRef, "opencode", "app_ses_upgraded"))
            .thenReturn(BindingResult.updated(ImContextBinding.create(contextRef, "opencode", "app_ses_upgraded")));

        sessionManager.saveSession(contextRef, "oc_session_001");

        verify(appSessionGateway).createSession(eq("opencode"), any(OpenCodeSessionData.class), any(TypeToken.class));
        verify(bindingGateway).bind(contextRef, "opencode", "app_ses_upgraded");
    }

    @Test
    @DisplayName("保存会话 - topicId 为 null 时不保存")
    void saveSession_nullTopicId_doesNotSave() {
        String sessionId = "ses_save_789";
        Message message = new Message();
        message.setContent("test");
        message.setTopicId(null);
        message.setChatId(null);

        sessionManager.saveSession(message, sessionId);

        verify(appSessionGateway, never()).createSession(anyString(), any(), any());
        verify(bindingGateway, never()).bind(any(), anyString(), anyString());
    }

    // ========== clearSession 测试 ==========

    @Test
    @DisplayName("清除会话 - 成功清除")
    @SuppressWarnings("unchecked")
    void clearSession_validContext_clearsFromGateway() {
        String topicId = "topic-clear-123";
        String appSessionId = "ses_clear";
        Message message = createTestMessage("test", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));

        sessionManager.clearSession(message);

        verify(appSessionGateway).deleteSession("opencode", appSessionId);
        verify(bindingGateway).clearBinding(contextRef);
    }

    @Test
    @DisplayName("清除会话 - 无上下文时不清除")
    void clearSession_nullContext_doesNotClear() {
        Message message = new Message();
        message.setContent("test");
        message.setTopicId(null);
        message.setChatId(null);

        sessionManager.clearSession(message);

        verify(appSessionGateway, never()).deleteSession(anyString(), anyString());
        verify(bindingGateway, never()).clearBinding(any());
    }

    // ========== getSessionId 测试 ==========

    @Test
    @DisplayName("获取会话 ID - 返回 OpenCode 会话 ID")
    @SuppressWarnings("unchecked")
    void getSessionId_returnsOpenCodeSessionId() {
        String topicId = "topic-get-123";
        String expectedSessionId = "opencode_ses_get_456";
        String appSessionId = "ses_123";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(appSessionId, expectedSessionId);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        Optional<String> result = sessionManager.getSessionId(contextRef);

        assertTrue(result.isPresent());
        assertEquals(expectedSessionId, result.get());
    }

    @Test
    @DisplayName("获取会话 ID - 无绑定时返回空")
    void getSessionId_noBinding_returnsEmpty() {
        String topicId = "topic-no-session";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        Optional<String> result = sessionManager.getSessionId(contextRef);

        assertFalse(result.isPresent());
    }

    // ========== 显式初始化标记测试 ==========

    @Test
    @DisplayName("检查显式初始化 - 返回 true")
    @SuppressWarnings("unchecked")
    void isExplicitlyInitialized_returnsTrue() {
        String topicId = "topic-init-123";
        String appSessionId = "ses_123";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(appSessionId, "opencode_ses", true);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        boolean result = sessionManager.isExplicitlyInitialized(contextRef);

        assertTrue(result);
    }

    @Test
    @DisplayName("检查显式初始化 - 返回 false")
    @SuppressWarnings("unchecked")
    void isExplicitlyInitialized_returnsFalse() {
        String topicId = "topic-init-456";
        String appSessionId = "ses_456";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(appSessionId, "opencode_ses", false);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        boolean result = sessionManager.isExplicitlyInitialized(contextRef);

        assertFalse(result);
    }

    @Test
    @DisplayName("检查显式初始化 - 无绑定时返回 false")
    void isExplicitlyInitialized_noBinding_returnsFalse() {
        String topicId = "topic-no-session";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        boolean result = sessionManager.isExplicitlyInitialized(contextRef);

        assertFalse(result);
    }

    @Test
    @DisplayName("设置显式初始化标记 - 成功设置")
    @SuppressWarnings("unchecked")
    void setExplicitlyInitialized_updatesSession() {
        String topicId = "topic-set-123";
        String appSessionId = "ses_123";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(appSessionId, "opencode_ses", false);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        sessionManager.setExplicitlyInitialized(contextRef);

        verify(appSessionGateway).updateSession(eq("opencode"), eq(appSessionId), 
            any(OpenCodeSessionData.class), any(TypeToken.class), eq(1L));
    }

    @Test
    @DisplayName("清除显式初始化标记 - 成功清除")
    @SuppressWarnings("unchecked")
    void clearExplicitlyInitialized_updatesSession() {
        String topicId = "topic-clear-123";
        String appSessionId = "ses_123";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding binding = createBinding(contextRef, appSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(appSessionId, "opencode_ses", true);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(appSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        sessionManager.clearExplicitlyInitialized(contextRef);

        verify(appSessionGateway).updateSession(eq("opencode"), eq(appSessionId), 
            any(OpenCodeSessionData.class), any(TypeToken.class), eq(1L));
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

    // ========== Blocker Fix: null sessionId upgrade 测试 ==========

    @Test
    @DisplayName("保存会话 - 现有绑定 sessionId 为 null 时，应创建新会话并重新绑定")
    @SuppressWarnings("unchecked")
    void saveSession_existingBindingWithNullSessionId_createsNewSessionAndRebinds() {
        // Given: 上下文已绑定到 opencode，但 sessionId 为 null
        String topicId = "topic-null-session";
        String newOpenCodeSessionId = "new_oc_ses_123";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        
        // 创建一个 sessionId 为 null 的绑定
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));
        when(appSessionGateway.createSession(eq("opencode"), any(OpenCodeSessionData.class), any(TypeToken.class)))
            .thenReturn("new_app_ses_456");
        when(bindingGateway.bind(contextRef, "opencode", "new_app_ses_456"))
            .thenReturn(BindingResult.updated(ImContextBinding.create(contextRef, "opencode", "new_app_ses_456")));

        // When: 调用 saveSession
        sessionManager.saveSession(contextRef, newOpenCodeSessionId);

        // Then: 应创建新会话，且数据中包含正确的 openCodeSessionId
        ArgumentCaptor<OpenCodeSessionData> dataCaptor = ArgumentCaptor.forClass(OpenCodeSessionData.class);
        verify(appSessionGateway).createSession(eq("opencode"), dataCaptor.capture(), any(TypeToken.class));
        assertEquals(newOpenCodeSessionId, dataCaptor.getValue().getOpenCodeSessionId());
        
        // And: 应重新绑定到新会话 ID
        verify(bindingGateway).bind(contextRef, "opencode", "new_app_ses_456");
        
        // And: 不应调用旧路径的方法
        verify(appSessionGateway, never()).getSession(anyString(), any(), any(TypeToken.class));
        verify(appSessionGateway, never()).updateSession(anyString(), anyString(), any(), any(TypeToken.class), anyLong());
    }

    @Test
    @DisplayName("保存会话 - 现有绑定有具体 sessionId 时，应更新数据而非创建新会话")
    @SuppressWarnings("unchecked")
    void saveSession_existingBindingWithConcreteSessionId_updatesDataNotCreates() {
        // Given: 上下文已绑定到 opencode，有具体的 sessionId
        String topicId = "topic-concrete-session";
        String existingAppSessionId = "existing_ses_123";
        String existingOpenCodeSessionId = "old_oc_ses";
        String newOpenCodeSessionId = "new_oc_ses_456";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        
        ImContextBinding binding = createBinding(contextRef, existingAppSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(existingAppSessionId, existingOpenCodeSessionId);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(existingAppSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        // When: 调用 saveSession 保存新的 openCodeSessionId
        sessionManager.saveSession(contextRef, newOpenCodeSessionId);

        // Then: 不应创建新会话
        verify(appSessionGateway, never()).createSession(anyString(), any(), any());
        // And: 不应重新绑定
        verify(bindingGateway, never()).bind(any(), anyString(), anyString());
        // And: 应更新现有会话数据，且数据中包含新的 openCodeSessionId
        ArgumentCaptor<OpenCodeSessionData> dataCaptor = ArgumentCaptor.forClass(OpenCodeSessionData.class);
        verify(appSessionGateway).updateSession(
            eq("opencode"), eq(existingAppSessionId), 
            dataCaptor.capture(), any(TypeToken.class), eq(1L));
        assertEquals(newOpenCodeSessionId, dataCaptor.getValue().getOpenCodeSessionId());
    }

    @Test
    @DisplayName("保存会话 - 现有绑定 + 相同 openCodeSessionId 时，不应有任何操作")
    @SuppressWarnings("unchecked")
    void saveSession_existingBindingWithSameOpenCodeSessionId_noChange() {
        // Given: 上下文已绑定到 opencode，且 openCodeSessionId 相同
        String topicId = "topic-same-session";
        String existingAppSessionId = "existing_ses_789";
        String sameOpenCodeSessionId = "same_oc_ses";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        
        ImContextBinding binding = createBinding(contextRef, existingAppSessionId);
        AppSession<OpenCodeSessionData> session = createMockSession(existingAppSessionId, sameOpenCodeSessionId);
        
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq("opencode"), eq(existingAppSessionId), any(TypeToken.class)))
            .thenReturn(Optional.of(session));

        // When: 调用 saveSession 保存相同的 openCodeSessionId
        sessionManager.saveSession(contextRef, sameOpenCodeSessionId);

        // Then: 不应创建新会话
        verify(appSessionGateway, never()).createSession(anyString(), any(), any());
        // And: 不应重新绑定
        verify(bindingGateway, never()).bind(any(), anyString(), anyString());
        // And: 不应更新会话数据（因为 openCodeSessionId 相同）
        verify(appSessionGateway, never()).updateSession(anyString(), anyString(), any(), any(TypeToken.class), anyLong());
    }

    // ========== Null SessionId Handling 测试 ==========

    @Test
    @DisplayName("获取会话状态 - 绑定存在但 sessionId 为 null 时，显示未激活会话状态")
    void getCurrentSessionStatus_nullSessionId_returnsAppContextMessage() {
        String topicId = "topic-null-status";
        Message message = createTestMessage("test", topicId);
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));

        String result = sessionManager.getCurrentSessionStatus(message);

        assertTrue(result.contains("还没有激活会话"));
        assertTrue(result.contains("OpenCode 上下文"));
        verify(appSessionGateway, never()).getSession(anyString(), any(), any(TypeToken.class));
    }

    @Test
    @DisplayName("清除会话 - 绑定存在但 sessionId 为 null 时，只清除绑定，不调用 deleteSession")
    void clearSession_nullSessionId_onlyClearsBinding() {
        String topicId = "topic-null-clear";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));

        sessionManager.clearSession(contextRef);

        verify(bindingGateway).clearBinding(contextRef);
        verify(appSessionGateway, never()).deleteSession(anyString(), any());
    }

    @Test
    @DisplayName("获取会话 ID - 绑定存在但 sessionId 为 null 时，返回 empty 且不调用 session gateway")
    void getSessionId_nullSessionId_returnsEmpty() {
        String topicId = "topic-null-getid";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));

        Optional<String> result = sessionManager.getSessionId(contextRef);

        assertFalse(result.isPresent());
        verify(appSessionGateway, never()).getSession(anyString(), any(), any(TypeToken.class));
    }

    @Test
    @DisplayName("检查显式初始化 - 绑定存在但 sessionId 为 null 时，返回 false 且不调用 session gateway")
    void isExplicitlyInitialized_nullSessionId_returnsFalse() {
        String topicId = "topic-null-init";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));

        boolean result = sessionManager.isExplicitlyInitialized(contextRef);

        assertFalse(result);
        verify(appSessionGateway, never()).getSession(anyString(), any(), any(TypeToken.class));
    }

    @Test
    @DisplayName("设置显式初始化标记 - 绑定存在但 sessionId 为 null 时，应安全跳过")
    void setExplicitlyInitialized_nullSessionId_noOp() {
        String topicId = "topic-null-setinit";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));

        sessionManager.setExplicitlyInitialized(contextRef);

        verify(appSessionGateway, never()).getSession(anyString(), any(), any(TypeToken.class));
        verify(appSessionGateway, never()).updateSession(anyString(), anyString(), any(), any(TypeToken.class), anyLong());
    }

    @Test
    @DisplayName("清除显式初始化标记 - 绑定存在但 sessionId 为 null 时，应安全跳过")
    void clearExplicitlyInitialized_nullSessionId_noOp() {
        String topicId = "topic-null-clearinit";
        ImContextRef contextRef = ImContextRef.feishuThread(topicId);
        ImContextBinding nullSessionBinding = ImContextBinding.create(contextRef, "opencode", null);

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(nullSessionBinding));

        sessionManager.clearExplicitlyInitialized(contextRef);

        verify(appSessionGateway, never()).getSession(anyString(), any(), any(TypeToken.class));
        verify(appSessionGateway, never()).updateSession(anyString(), anyString(), any(), any(TypeToken.class), anyLong());
    }
}

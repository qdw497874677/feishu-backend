package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.model.ImContextRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WizardManager 向导状态机测试
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WizardManager 向导状态机测试")
class WizardManagerTest {

    @Mock
    private OpenCodeGateway openCodeGateway;

    @Mock
    private OpenCodeSessionManager sessionManager;

    @Mock
    private CardRenderer cardRenderer;

    @Mock
    private FeishuGateway feishuGateway;

    private WizardManager wizardManager;

    private static final String CHAT_ID = "chat_test_123";
    private static final String TOPIC_ID = "topic_test_456";
    private static final String SESSION_ID = "ses_test_789";
    private static final String PROJECT_NAME = "feishu-backend";

    @BeforeEach
    void setUp() {
        wizardManager = new WizardManager(openCodeGateway, sessionManager, cardRenderer);
        when(openCodeGateway.listProjects()).thenReturn(
            "📁 OpenCode 项目列表:\n\n1. **feishu-backend**\n   路径: /root/workspace/feishu-backend\n\n"
        );
        when(openCodeGateway.listRecentSessions(anyString(), anyInt())).thenReturn(
            "📋 项目 **feishu-backend** 的最近 5 个会话:\n\n1. 重构登录模块\n   ID: `ses_abc123`\n\n"
        );
    }

    // ============ 初始状态测试 ============

    @Test
    @DisplayName("初始状态下 isWizardActive 返回 false")
    void should_returnInactive_when_noWizard() {
        assertFalse(wizardManager.isWizardActive(TOPIC_ID));
    }

    @Test
    @DisplayName("无向导时 handleAction 返回 null")
    void should_returnNull_when_noActiveWizard() {
        WizardManager.WizardResult result = wizardManager.handleAction(
            "wizard_confirm", CHAT_ID, TOPIC_ID);
        assertNull(result);
    }

    // ============ 向导启动测试 ============

    @Test
    @DisplayName("start() 返回步骤1卡片并激活向导")
    void should_startWizard_andReturnProjectList() {
        WizardManager.WizardResult result = wizardManager.start(CHAT_ID, TOPIC_ID);

        assertNotNull(result);
        assertNotNull(result.getCardContent());
        assertEquals(WizardManager.WizardStep.SELECT_PROJECT, result.getStep());
        assertFalse(result.isCompleted());
        assertTrue(wizardManager.isWizardActive(TOPIC_ID));
    }

    @Test
    @DisplayName("start() 调用 openCodeGateway.listProjects()")
    void should_callListProjects_on_start() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        verify(openCodeGateway).listProjects();
    }

    @Test
    @DisplayName("start() 后 isWizardActive 返回 true")
    void should_returnActive_after_start() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        assertTrue(wizardManager.isWizardActive(TOPIC_ID));
    }

    // ============ 步骤1 → 步骤2 测试 ============

    @Test
    @DisplayName("wizard_select_project 触发步骤2（会话列表卡片）")
    void should_transitionToSessionList_when_projectSelected() {
        wizardManager.start(CHAT_ID, TOPIC_ID);

        WizardManager.WizardResult result = wizardManager.handleAction(
            "wizard_select_project:" + PROJECT_NAME, CHAT_ID, TOPIC_ID);

        assertNotNull(result);
        assertNotNull(result.getCardContent());
        assertEquals(WizardManager.WizardStep.SELECT_SESSION, result.getStep());
        assertFalse(result.isCompleted());
    }

    @Test
    @DisplayName("wizard_select_project 调用 listRecentSessions")
    void should_callListRecentSessions_when_projectSelected() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_select_project:" + PROJECT_NAME, CHAT_ID, TOPIC_ID);
        verify(openCodeGateway).listRecentSessions(eq(PROJECT_NAME), anyInt());
    }

    // ============ 步骤2 → 步骤3 测试 ============

    @Test
    @DisplayName("wizard_select_session 触发步骤3（确认卡片）")
    void should_transitionToConfirm_when_sessionSelected() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_select_project:" + PROJECT_NAME, CHAT_ID, TOPIC_ID);

        WizardManager.WizardResult result = wizardManager.handleAction(
            "wizard_select_session:" + SESSION_ID, CHAT_ID, TOPIC_ID);

        assertNotNull(result);
        assertNotNull(result.getCardContent());
        assertEquals(WizardManager.WizardStep.CONFIRM, result.getStep());
        assertFalse(result.isCompleted());
    }

    // ============ 步骤3 → 完成 测试 ============

    @Test
    @DisplayName("wizard_confirm 完成向导并调用 saveSession")
    void should_completeBinding_when_confirmed() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_select_project:" + PROJECT_NAME, CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_select_session:" + SESSION_ID, CHAT_ID, TOPIC_ID);

        WizardManager.WizardResult result = wizardManager.handleAction(
            "wizard_confirm", CHAT_ID, TOPIC_ID);

        assertNotNull(result);
        assertTrue(result.isCompleted());
        assertEquals(SESSION_ID, result.getOpenCodeSessionId());
        verify(sessionManager).saveSession(any(ImContextRef.class), eq(SESSION_ID));
    }

    @Test
    @DisplayName("wizard_confirm 后向导不再活跃")
    void should_becomeInactive_after_confirm() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_select_project:" + PROJECT_NAME, CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_select_session:" + SESSION_ID, CHAT_ID, TOPIC_ID);
        wizardManager.handleAction("wizard_confirm", CHAT_ID, TOPIC_ID);

        assertFalse(wizardManager.isWizardActive(TOPIC_ID));
    }

    // ============ 取消测试 ============

    @Test
    @DisplayName("wizard_cancel 清理向导状态")
    void should_cancelWizard_when_cancelAction() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        assertTrue(wizardManager.isWizardActive(TOPIC_ID));

        WizardManager.WizardResult result = wizardManager.handleAction(
            "wizard_cancel", CHAT_ID, TOPIC_ID);

        assertNotNull(result);
        assertNotNull(result.getTextContent());
        assertFalse(wizardManager.isWizardActive(TOPIC_ID));
    }

    // ============ clearWizard 测试 ============

    @Test
    @DisplayName("clearWizard 清理向导状态")
    void should_clearWizard_manually() {
        wizardManager.start(CHAT_ID, TOPIC_ID);
        assertTrue(wizardManager.isWizardActive(TOPIC_ID));

        wizardManager.clearWizard(TOPIC_ID);
        assertFalse(wizardManager.isWizardActive(TOPIC_ID));
    }

    // ============ TTL 过期测试 ============

    @Test
    @DisplayName("向导 10 分钟后自动过期（TTL 验证通过模拟 expireAfterWrite 时间戳）")
    void should_expireWizard_after_ttl() throws InterruptedException {
        // 创建一个 TTL=100ms 的测试专用 WizardManager
        WizardManager shortTtlManager = new WizardManager(openCodeGateway, sessionManager, cardRenderer, 100L);
        shortTtlManager.start(CHAT_ID, TOPIC_ID);
        assertTrue(shortTtlManager.isWizardActive(TOPIC_ID));

        Thread.sleep(200);

        assertFalse(shortTtlManager.isWizardActive(TOPIC_ID), "向导应在 TTL 过期后失效");
    }
}

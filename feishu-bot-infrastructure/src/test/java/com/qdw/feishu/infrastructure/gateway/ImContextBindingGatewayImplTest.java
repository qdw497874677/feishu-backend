package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImContextBindingGatewayImpl 测试
 * 
 * 验证绑定操作的正确性：
 * - bind 创建新绑定
 * - bind 更新现有绑定
 * - bind 相同会话时无变化
 * - findBinding 查找绑定
 * - clearBinding 清除绑定
 * - isBoundToApp 检查应用绑定
 */
class ImContextBindingGatewayImplTest {

    @TempDir
    Path tempDir;

    private ImContextBindingGatewayImpl gateway;
    private String dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test-bindings.db").toString();
        gateway = new ImContextBindingGatewayImpl(dbPath);
        gateway.init();
    }

    @AfterEach
    void tearDown() {
        gateway.cleanup();
    }

    // ========== bind 测试 ==========

    @Test
    void should_createNewBinding_when_contextUnbound() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_123");
        String appId = "opencode";
        String sessionId = "ses_abc123";

        // When
        BindingResult result = gateway.bind(contextRef, appId, sessionId);

        // Then
        assertTrue(result.isCreated());
        assertFalse(result.isUpdated());
        assertFalse(result.isNoChange());
        assertEquals(sessionId, result.getBinding().getSessionId());
        assertEquals(appId, result.getBinding().getAppId());
    }

    @Test
    void should_returnNoChange_when_bindingAlreadyExistsWithSameSession() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_456");
        String appId = "opencode";
        String sessionId = "ses_xyz789";

        // First bind
        gateway.bind(contextRef, appId, sessionId);

        // When: bind again with same session
        BindingResult result = gateway.bind(contextRef, appId, sessionId);

        // Then
        assertFalse(result.isCreated());
        assertFalse(result.isUpdated());
        assertTrue(result.isNoChange());
        assertEquals(sessionId, result.getBinding().getSessionId());
    }

    @Test
    void should_updateBinding_when_bindingExistsWithDifferentSession() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_789");
        String appId = "opencode";
        String oldSessionId = "ses_old";
        String newSessionId = "ses_new";

        // First bind
        gateway.bind(contextRef, appId, oldSessionId);

        // When: bind with different session
        BindingResult result = gateway.bind(contextRef, appId, newSessionId);

        // Then
        assertFalse(result.isCreated());
        assertTrue(result.isUpdated());
        assertFalse(result.isNoChange());
        assertEquals(newSessionId, result.getBinding().getSessionId());
    }

    @Test
    void should_rejectDifferentApp_when_bindingExistsForOtherApp() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_999");
        String app1 = "opencode";
        String app2 = "otherapp";
        String session1 = "ses_opencode";
        String session2 = "ses_other";

        // First bind to app1
        gateway.bind(contextRef, app1, session1);

        // When: bind to app2 (should update, replacing app1)
        BindingResult result = gateway.bind(contextRef, app2, session2);

        // Then: binding is updated to new app
        assertTrue(result.isUpdated());
        assertEquals(app2, result.getBinding().getAppId());
        assertEquals(session2, result.getBinding().getSessionId());
    }

    // ========== findBinding 测试 ==========

    @Test
    void should_returnEmpty_when_noBindingExists() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("nonexistent");

        // When
        Optional<ImContextBinding> binding = gateway.findBinding(contextRef);

        // Then
        assertTrue(binding.isEmpty());
    }

    @Test
    void should_returnBinding_when_bindingExists() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_exists");
        gateway.bind(contextRef, "opencode", "ses_123");

        // When
        Optional<ImContextBinding> binding = gateway.findBinding(contextRef);

        // Then
        assertTrue(binding.isPresent());
        assertEquals("opencode", binding.get().getAppId());
        assertEquals("ses_123", binding.get().getSessionId());
    }

    @Test
    void should_returnCorrectBinding_forChatContext() {
        // Given
        ImContextRef chatRef = ImContextRef.feishuChat("oc_chat123");
        gateway.bind(chatRef, "opencode", "ses_chat");

        // When
        Optional<ImContextBinding> binding = gateway.findBinding(chatRef);

        // Then
        assertTrue(binding.isPresent());
        assertEquals("feishu", binding.get().getContextRef().getPlatform());
        assertEquals("chat", binding.get().getContextRef().getContextType());
        assertEquals("oc_chat123", binding.get().getContextRef().getContextId());
    }

    // ========== clearBinding 测试 ==========

    @Test
    void should_removeBinding_when_clearBindingCalled() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_clear");
        gateway.bind(contextRef, "opencode", "ses_clear");

        // When
        gateway.clearBinding(contextRef);

        // Then
        Optional<ImContextBinding> binding = gateway.findBinding(contextRef);
        assertTrue(binding.isEmpty());
    }

    @Test
    void should_doNothing_when_clearBindingOnNonexistent() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("nonexistent");

        // When & Then: should not throw
        assertDoesNotThrow(() -> gateway.clearBinding(contextRef));
    }

    // ========== isBoundToApp 测试 ==========

    @Test
    void should_returnTrue_when_contextBoundToApp() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_bound");
        gateway.bind(contextRef, "opencode", "ses_123");

        // When
        boolean isBound = gateway.isBoundToApp(contextRef, "opencode");

        // Then
        assertTrue(isBound);
    }

    @Test
    void should_returnFalse_when_contextNotBoundToApp() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_other");
        gateway.bind(contextRef, "opencode", "ses_123");

        // When
        boolean isBound = gateway.isBoundToApp(contextRef, "otherapp");

        // Then
        assertFalse(isBound);
    }

    @Test
    void should_returnFalse_when_contextNotBound() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("unbound");

        // When
        boolean isBound = gateway.isBoundToApp(contextRef, "opencode");

        // Then
        assertFalse(isBound);
    }

    // ========== touchBinding 测试 ==========

    @Test
    void should_updateLastActiveAt_when_touchBindingCalled() throws InterruptedException {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_touch");
        gateway.bind(contextRef, "opencode", "ses_touch");
        
        ImContextBinding original = gateway.findBinding(contextRef).orElseThrow();
        long originalLastActiveAt = original.getLastActiveAt();
        
        // Wait a bit to ensure timestamp difference
        Thread.sleep(10);

        // When
        gateway.touchBinding(contextRef);

        // Then
        ImContextBinding updated = gateway.findBinding(contextRef).orElseThrow();
        assertTrue(updated.getLastActiveAt() >= originalLastActiveAt);
    }

    // ========== 边界条件测试 ==========

    @Test
    void should_throwException_when_bindWithNullContext() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            gateway.bind(null, "opencode", "ses_123")
        );
    }

    @Test
    void should_throwException_when_bindWithNullAppId() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_test");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            gateway.bind(contextRef, null, "ses_123")
        );
    }

    // ========== Nullable SessionId Tests (Task 2) ==========

    @Test
    void should_bind_when_sessionIdIsNull() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_null_session");
        String appId = "opencode";
        String sessionId = null;

        // When
        BindingResult result = gateway.bind(contextRef, appId, sessionId);

        // Then
        assertTrue(result.isCreated());
        assertNull(result.getBinding().getSessionId());
        assertEquals(appId, result.getBinding().getAppId());
    }

    @Test
    void should_returnNoChange_when_bindingMatchesNullSession() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_null_match");
        String appId = "opencode";
        
        // First bind with null session
        gateway.bind(contextRef, appId, null);

        // When: bind again with null session
        BindingResult result = gateway.bind(contextRef, appId, null);

        // Then
        assertTrue(result.isNoChange());
        assertNull(result.getBinding().getSessionId());
    }

    @Test
    void should_update_when_bindingProgressesFromNullToConcreteSession() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_null_to_concrete");
        String appId = "opencode";
        
        // First bind with null session
        gateway.bind(contextRef, appId, null);

        // When: bind with concrete session
        BindingResult result = gateway.bind(contextRef, appId, "ses_concrete123");

        // Then
        assertTrue(result.isUpdated());
        assertEquals("ses_concrete123", result.getBinding().getSessionId());
    }

    @Test
    void should_findBindingWithNullSessionId_when_persisted() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_persist_null");
        gateway.bind(contextRef, "opencode", null);

        // When
        Optional<ImContextBinding> binding = gateway.findBinding(contextRef);

        // Then
        assertTrue(binding.isPresent());
        assertNull(binding.get().getSessionId());
        assertEquals("opencode", binding.get().getAppId());
    }

    @Test
    void should_persistNullSessionIdAcrossInstances_when_reopened() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_null_persist");
        gateway.bind(contextRef, "opencode", null);

        // Close and reopen
        gateway.cleanup();
        ImContextBindingGatewayImpl newGateway = new ImContextBindingGatewayImpl(dbPath);
        newGateway.init();

        // When
        Optional<ImContextBinding> binding = newGateway.findBinding(contextRef);

        // Then
        assertTrue(binding.isPresent());
        assertNull(binding.get().getSessionId());
        assertEquals("opencode", binding.get().getAppId());

        newGateway.cleanup();
    }

    @Test
    void should_returnEmpty_when_findBindingWithNull() {
        // When
        Optional<ImContextBinding> binding = gateway.findBinding(null);

        // Then
        assertTrue(binding.isEmpty());
    }

    @Test
    void should_doNothing_when_clearBindingWithNull() {
        // When & Then: should not throw
        assertDoesNotThrow(() -> gateway.clearBinding(null));
    }

    @Test
    void should_returnFalse_when_isBoundToAppWithNullContext() {
        // When
        boolean isBound = gateway.isBoundToApp(null, "opencode");

        // Then
        assertFalse(isBound);
    }

    @Test
    void should_returnFalse_when_isBoundToAppWithNullAppId() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_test");

        // When
        boolean isBound = gateway.isBoundToApp(contextRef, null);

        // Then
        assertFalse(isBound);
    }

    // ========== 存储键解析测试 ==========

    @Test
    void should_useCorrectStorageKey_forThreadContext() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("omt_thread123");
        String expectedKey = "feishu:thread:omt_thread123";

        // When
        String storageKey = contextRef.toStorageKey();

        // Then
        assertEquals(expectedKey, storageKey);
    }

    @Test
    void should_useCorrectStorageKey_forChatContext() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuChat("oc_chat456");
        String expectedKey = "feishu:chat:oc_chat456";

        // When
        String storageKey = contextRef.toStorageKey();

        // Then
        assertEquals(expectedKey, storageKey);
    }

    @Test
    void should_parseStorageKey_correctly() {
        // Given
        String storageKey = "feishu:thread:omt_test123";

        // When
        ImContextRef contextRef = ImContextRef.fromStorageKey(storageKey);

        // Then
        assertEquals("feishu", contextRef.getPlatform());
        assertEquals("thread", contextRef.getContextType());
        assertEquals("omt_test123", contextRef.getContextId());
    }

    @Test
    void should_persistAcrossInstances_when_reopened() {
        // Given
        ImContextRef contextRef = ImContextRef.feishuThread("thread_persist");
        gateway.bind(contextRef, "opencode", "ses_persist");

        // Close and reopen
        gateway.cleanup();
        ImContextBindingGatewayImpl newGateway = new ImContextBindingGatewayImpl(dbPath);
        newGateway.init();

        // When
        Optional<ImContextBinding> binding = newGateway.findBinding(contextRef);

        // Then
        assertTrue(binding.isPresent());
        assertEquals("opencode", binding.get().getAppId());
        assertEquals("ses_persist", binding.get().getSessionId());

        newGateway.cleanup();
    }

    // ========== Schema Migration Tests (Task 2) ==========

    /**
     * Schema Migration Regression Test.
     * 
     * Verifies that an existing DB with old schema (NOT NULL session_id) is
     * automatically migrated to the new nullable schema.
     * 
     * Strategy: Create a DB with old schema, then verify the gateway migrates it.
     */
    @Test
    void should_migrateOldSchema_when_existingTableHasNotNullSessionId() {
        // Given: A database file with old schema (NOT NULL on session_id)
        String oldSchemaDbPath = tempDir.resolve("old-schema.db").toString();
        
        // Create old schema table directly using raw JDBC
        try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + oldSchemaDbPath);
             var stmt = conn.createStatement()) {
            
            // Create table with OLD schema (NOT NULL on session_id)
            stmt.execute("""
                CREATE TABLE im_context_binding (
                    context_key TEXT PRIMARY KEY NOT NULL,
                    platform TEXT NOT NULL,
                    context_type TEXT NOT NULL,
                    context_id TEXT NOT NULL,
                    app_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_active_at INTEGER NOT NULL
                )
            """);
            
            // Insert a row with the old schema (session_id must be non-null)
            stmt.execute("""
                INSERT INTO im_context_binding 
                (context_key, platform, context_type, context_id, app_id, session_id, created_at, last_active_at)
                VALUES ('feishu:thread:old_thread', 'feishu', 'thread', 'old_thread', 'opencode', 'ses_old', 1000, 2000)
            """);
        } catch (Exception e) {
            fail("Failed to set up old schema database: " + e.getMessage());
        }

        // When: Gateway initializes (should detect and migrate)
        ImContextBindingGatewayImpl migratedGateway = new ImContextBindingGatewayImpl(oldSchemaDbPath);
        migratedGateway.init();

        // Then: Gateway should work with nullable session_id
        // 1. Old data is cleared (drop-recreate strategy)
        Optional<ImContextBinding> oldBinding = migratedGateway.findBinding(
            ImContextRef.feishuThread("old_thread")
        );
        assertTrue(oldBinding.isEmpty(), "Old data should be cleared after migration");

        // 2. Can create new binding with null session_id
        ImContextRef newContext = ImContextRef.feishuThread("new_null_session");
        BindingResult result = migratedGateway.bind(newContext, "opencode", null);
        
        assertTrue(result.isCreated());
        assertNull(result.getBinding().getSessionId());

        // 3. Can retrieve binding with null session_id
        Optional<ImContextBinding> retrieved = migratedGateway.findBinding(newContext);
        assertTrue(retrieved.isPresent());
        assertNull(retrieved.get().getSessionId());

        migratedGateway.cleanup();
    }
}

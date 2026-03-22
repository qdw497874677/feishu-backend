package com.qdw.feishu.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ImContextBinding null-session semantics.
 * 
 * The sessionId field is explicitly nullable and valid persisted state.
 * - null sessionId = app context without active session
 * - non-null sessionId = app context with active session
 */
class ImContextBindingTest {

    private static final ImContextRef TEST_CONTEXT = ImContextRef.feishuThread("test-thread-123");
    private static final String APP_ID = "opencode";

    @Test
    void should_match_when_sameAppAndBothSessionIdsAreNull() {
        // given
        ImContextBinding binding = ImContextBinding.create(TEST_CONTEXT, APP_ID, null);

        // when
        boolean matches = binding.matches(APP_ID, null);

        // then
        assertTrue(matches, "Binding should match when both sessionIds are null");
    }

    @Test
    void should_match_when_sameAppAndSameSessionId() {
        // given
        String sessionId = "ses_abc123";
        ImContextBinding binding = ImContextBinding.create(TEST_CONTEXT, APP_ID, sessionId);

        // when
        boolean matches = binding.matches(APP_ID, sessionId);

        // then
        assertTrue(matches, "Binding should match when sessionIds are equal");
    }

    @Test
    void should_notMatch_when_sameAppButDifferentSessionId() {
        // given
        String bindingSessionId = "ses_abc123";
        String querySessionId = "ses_xyz789";
        ImContextBinding binding = ImContextBinding.create(TEST_CONTEXT, APP_ID, bindingSessionId);

        // when
        boolean matches = binding.matches(APP_ID, querySessionId);

        // then
        assertFalse(matches, "Binding should not match when sessionIds differ");
    }

    @Test
    void should_notMatch_when_bindingHasNullSessionAndQueryHasConcreteSession() {
        // given
        ImContextBinding binding = ImContextBinding.create(TEST_CONTEXT, APP_ID, null);
        String querySessionId = "ses_abc123";

        // when
        boolean matches = binding.matches(APP_ID, querySessionId);

        // then
        assertFalse(matches, "Binding with null session should not match concrete session query");
    }

    @Test
    void should_notMatch_when_bindingHasConcreteSessionAndQueryIsNull() {
        // given
        String bindingSessionId = "ses_abc123";
        ImContextBinding binding = ImContextBinding.create(TEST_CONTEXT, APP_ID, bindingSessionId);

        // when
        boolean matches = binding.matches(APP_ID, null);

        // then
        assertFalse(matches, "Binding with concrete session should not match null session query");
    }

    @Test
    void should_rebindWithinSameApp_when_newSessionIdProvided() {
        // given
        String originalSessionId = "ses_original";
        String newSessionId = "ses_new";
        ImContextBinding original = ImContextBinding.create(TEST_CONTEXT, APP_ID, originalSessionId);

        // when
        ImContextBinding rebound = original.rebind(newSessionId);

        // then
        assertEquals(APP_ID, rebound.getAppId(), "AppId should remain unchanged after rebind");
        assertEquals(newSessionId, rebound.getSessionId(), "SessionId should be updated");
        assertEquals(original.getContextRef(), rebound.getContextRef(), "ContextRef should remain unchanged");
        assertEquals(original.getCreatedAt(), rebound.getCreatedAt(), "CreatedAt should be preserved");
        assertTrue(rebound.getLastActiveAt() >= original.getLastActiveAt(), 
                "LastActiveAt should be updated");
    }

    @Test
    void should_rebindToNullSession_when_nullProvided() {
        // given
        String originalSessionId = "ses_original";
        ImContextBinding original = ImContextBinding.create(TEST_CONTEXT, APP_ID, originalSessionId);

        // when
        ImContextBinding rebound = original.rebind(null);

        // then
        assertNull(rebound.getSessionId(), "SessionId should be null after rebind to null");
        assertEquals(APP_ID, rebound.getAppId(), "AppId should remain unchanged");
    }

    @Test
    void should_allow_nullableSessionId_asValidPersistedState() {
        // given & when
        ImContextBinding bindingWithNullSession = ImContextBinding.create(TEST_CONTEXT, APP_ID, null);

        // then
        assertNull(bindingWithNullSession.getSessionId(), "SessionId can be null");
        assertNotNull(bindingWithNullSession.getAppId(), "AppId should not be null");
        assertNotNull(bindingWithNullSession.getContextRef(), "ContextRef should not be null");
        assertTrue(bindingWithNullSession.getCreatedAt() > 0, "CreatedAt should be valid");
        assertTrue(bindingWithNullSession.getLastActiveAt() > 0, "LastActiveAt should be valid");
    }

    @Test
    void should_matchOnlyAppId_when_isForAppCalled() {
        // given
        ImContextBinding binding = ImContextBinding.create(TEST_CONTEXT, APP_ID, null);

        // when & then
        assertTrue(binding.isForApp(APP_ID), "isForApp should match by appId only");
        assertFalse(binding.isForApp("other-app"), "isForApp should return false for different app");
    }

    @Test
    void should_preserveFields_when_touchCalled() {
        // given
        String sessionId = "ses_test";
        ImContextBinding original = ImContextBinding.create(TEST_CONTEXT, APP_ID, sessionId);
        long originalLastActive = original.getLastActiveAt();

        // Simulate passage of time
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // when
        ImContextBinding touched = original.touch();

        // then
        assertEquals(original.getAppId(), touched.getAppId(), "AppId should be preserved");
        assertEquals(original.getSessionId(), touched.getSessionId(), "SessionId should be preserved");
        assertEquals(original.getCreatedAt(), touched.getCreatedAt(), "CreatedAt should be preserved");
        assertTrue(touched.getLastActiveAt() > originalLastActive, 
                "LastActiveAt should be updated after touch");
    }
}

package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.exception.OptimisticLockException;
import com.qdw.feishu.domain.gateway.SessionContextGateway;
import com.qdw.feishu.domain.model.SessionContext;
import com.qdw.feishu.domain.session.SessionIdGenerator;
import com.qdw.feishu.domain.session.SessionState;
import com.qdw.feishu.domain.session.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AppSessionGatewayImpl 并发测试
 * 
 * 验证乐观锁机制和状态转换的正确性
 */
@ExtendWith(MockitoExtension.class)
class AppSessionGatewayImplTest {

    @Mock
    private SessionContextGateway sessionContextGateway;
    
    @Mock
    private SessionIdGenerator sessionIdGenerator;
    
    private AppSessionGatewayImpl gateway;
    
    private static final String APP_ID = "test-app";
    private static final String TOPIC_ID = "topic-123";
    private static final String SESSION_ID = "ses_abc123";
    
    @BeforeEach
    void setUp() {
        gateway = new AppSessionGatewayImpl(sessionContextGateway, sessionIdGenerator);
    }

    // ========== 乐观锁测试 ==========

    @Test
    void should_throwOptimisticLockException_when_concurrentUpdate() {
        // Given: 创建 version=1 的会话，模拟第一次读取
        String metadataV1 = buildMetadataWithSession(SESSION_ID, 1L, SessionState.ACTIVE, Map.of("data", "v1"));
        SessionContext contextV1 = new SessionContext(TOPIC_ID, APP_ID, metadataV1);
        
        // 第一次读取返回 version=1
        when(sessionContextGateway.findByTopicId(TOPIC_ID))
            .thenReturn(Optional.of(contextV1));
        
        // When: 用错误的 version=999 更新（期望抛出乐观锁异常）
        OptimisticLockException exception = assertThrows(
            OptimisticLockException.class,
            () -> gateway.updateSession(
                APP_ID, TOPIC_ID, SESSION_ID, 
                Map.of("data", "v2"), 
                new TypeToken<Map<String, String>>() {},
                999L  // 期望 version=999，但实际是 1
            )
        );
        
        // Then: 验证异常信息
        assertEquals(999L, exception.getExpectedVersion());
        assertEquals(1L, exception.getActualVersion());
    }

    @Test
    void should_allowSequentialUpdates_when_versionMatches() {
        // Given: 创建 version=1 的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.ACTIVE, Map.of("data", "v1"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When: 第一次更新（version 1 -> 2）
        assertDoesNotThrow(() -> 
            gateway.updateSession(APP_ID, TOPIC_ID, SESSION_ID, 
                Map.of("data", "v2"), new TypeToken<Map<String, String>>() {}, 1L)
        );
        
        // Then: 验证保存被调用
        verify(sessionContextGateway, times(1)).save(any(SessionContext.class));
    }

    @Test
    void should_throwOptimisticLockException_when_updateStateWithWrongVersion() {
        // Given: 创建 version=2 的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 2L, SessionState.ACTIVE, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: 用错误的 version=1 更新状态
        OptimisticLockException exception = assertThrows(
            OptimisticLockException.class,
            () -> gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.IDLE, 1L)
        );
        
        assertEquals(1L, exception.getExpectedVersion());
        assertEquals(2L, exception.getActualVersion());
    }

    // ========== 状态转换测试 ==========

    @Test
    void should_allowValidStateTransition_when_createdToActive() {
        // Given: CREATED 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.CREATED, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: CREATED -> ACTIVE 应该成功
        assertDoesNotThrow(() -> 
            gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.ACTIVE, 1L)
        );
        
        verify(sessionContextGateway, times(1)).save(any(SessionContext.class));
    }

    @Test
    void should_allowValidStateTransition_when_activeToIdle() {
        // Given: ACTIVE 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.ACTIVE, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: ACTIVE -> IDLE 应该成功
        assertDoesNotThrow(() -> 
            gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.IDLE, 1L)
        );
        
        verify(sessionContextGateway, times(1)).save(any(SessionContext.class));
    }

    @Test
    void should_allowValidStateTransition_when_idleToActive() {
        // Given: IDLE 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.IDLE, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: IDLE -> ACTIVE 应该成功
        assertDoesNotThrow(() -> 
            gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.ACTIVE, 1L)
        );
        
        verify(sessionContextGateway, times(1)).save(any(SessionContext.class));
    }

    @Test
    void should_allowValidStateTransition_when_activeToTerminated() {
        // Given: ACTIVE 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.ACTIVE, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: ACTIVE -> TERMINATED 应该成功
        assertDoesNotThrow(() -> 
            gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.TERMINATED, 1L)
        );
        
        verify(sessionContextGateway, times(1)).save(any(SessionContext.class));
    }

    @Test
    void should_throwIllegalStateException_when_terminatedToActive() {
        // Given: TERMINATED 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.TERMINATED, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: TERMINATED -> ACTIVE 应该失败
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.ACTIVE, 1L)
        );
        
        assertTrue(exception.getMessage().contains("Invalid state transition"));
        assertTrue(exception.getMessage().contains("TERMINATED"));
        assertTrue(exception.getMessage().contains("ACTIVE"));
    }

    @Test
    void should_throwIllegalStateException_when_terminatedToIdle() {
        // Given: TERMINATED 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.TERMINATED, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: TERMINATED -> IDLE 应该失败
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.IDLE, 1L)
        );
        
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    @Test
    void should_throwIllegalStateException_when_expiredToActive() {
        // Given: EXPIRED 状态的会话
        String metadata = buildMetadataWithSession(SESSION_ID, 1L, SessionState.EXPIRED, Map.of("data", "test"));
        SessionContext context = new SessionContext(TOPIC_ID, APP_ID, metadata);
        when(sessionContextGateway.findByTopicId(TOPIC_ID)).thenReturn(Optional.of(context));
        
        // When & Then: EXPIRED -> ACTIVE 应该失败（只能转到 TERMINATED）
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> gateway.updateState(APP_ID, TOPIC_ID, SESSION_ID, SessionState.ACTIVE, 1L)
        );
        
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }

    // ========== 辅助方法 ==========

    /**
     * 构建包含指定会话的元数据（使用 appId 作为命名空间）
     * 
     * 格式: {"appId": {"activeSessionId": "...", "sessions": [...]}}
     */
    private String buildMetadataWithSession(String sessionId, long version, SessionState state, Object data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String dataJson = mapper.writeValueAsString(data);
            long now = System.currentTimeMillis();
            
            String sessionJson = String.format(
                "{\"sessionId\":\"%s\",\"appId\":\"%s\",\"topicId\":\"%s\",\"state\":\"%s\",\"createdAt\":%d,\"lastActiveAt\":%d,\"version\":%d,\"data\":%s}",
                sessionId, APP_ID, TOPIC_ID, state.name(), now, now, version, dataJson
            );
            
            // 使用 appId 作为命名空间
            return String.format(
                "{\"%s\":{\"activeSessionId\":\"%s\",\"sessions\":[%s]}}",
                APP_ID, sessionId, sessionJson
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to build metadata", e);
        }
    }
}

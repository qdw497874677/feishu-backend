package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.exception.OptimisticLockException;
import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.SessionContextGateway;
import com.qdw.feishu.domain.model.SessionContext;
import com.qdw.feishu.domain.model.SessionMetadata;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.AppSessionInfo;
import com.qdw.feishu.domain.session.SessionIdGenerator;
import com.qdw.feishu.domain.session.SessionState;
import com.qdw.feishu.domain.session.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通用会话管理实现（基于 SessionContext.metadata）
 * 
 * 支持多会话、乐观锁、状态机
 */
@Slf4j
@Component
public class AppSessionGatewayImpl implements AppSessionGateway {

    private final SessionContextGateway sessionContextGateway;
    private final SessionIdGenerator sessionIdGenerator;
    private final ObjectMapper objectMapper;

    // metadata 中的 key
    private static final String KEY_SESSIONS = "sessions";
    private static final String KEY_ACTIVE_SESSION_ID = "activeSessionId";
    
    // session 对象中的 key
    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_STATE = "state";
    private static final String KEY_CREATED_AT = "createdAt";
    private static final String KEY_LAST_ACTIVE_AT = "lastActiveAt";
    private static final String KEY_EXPIRES_AT = "expiresAt";
    private static final String KEY_VERSION = "version";
    private static final String KEY_DATA = "data";

    public AppSessionGatewayImpl(SessionContextGateway sessionContextGateway,
                                  SessionIdGenerator sessionIdGenerator) {
        this.sessionContextGateway = sessionContextGateway;
        this.sessionIdGenerator = sessionIdGenerator;
        this.objectMapper = new ObjectMapper();
    }

    // ========== 会话创建 ==========

    @Override
    public <T> String createSession(String appId, String topicId, T data, TypeToken<T> typeToken) {
        String sessionId = sessionIdGenerator.generate(appId);
        return createSession(appId, topicId, sessionId, data, typeToken);
    }

    @Override
    public <T> String createSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken) {
        SessionContext context = getOrCreateContext(topicId, appId);
        SessionMetadata metadata = SessionMetadata.of(context);
        
        // 获取应用的会话列表
        List<JsonNode> sessions = getSessionsList(metadata);
        
        // 创建新会话
        long now = System.currentTimeMillis();
        AppSessionInfo newSession = new AppSessionInfo();
        newSession.setSessionId(sessionId);
        newSession.setAppId(appId);
        newSession.setTopicId(topicId);
        newSession.setState(SessionState.CREATED);
        newSession.setCreatedAt(now);
        newSession.setLastActiveAt(now);
        newSession.setVersion(1L);
        
        // 序列化 data
        try {
            JsonNode sessionNode = objectMapper.valueToTree(newSession);
            ((com.fasterxml.jackson.databind.node.ObjectNode) sessionNode)
                .set(KEY_DATA, objectMapper.valueToTree(data));
            sessions.add(sessionNode);
        } catch (Exception e) {
            log.error("Failed to serialize session data", e);
            throw new RuntimeException("Failed to create session", e);
        }
        
        // 更新 sessions 和 activeSessionId
        metadata.set(KEY_SESSIONS, sessions);
        metadata.set(KEY_ACTIVE_SESSION_ID, sessionId);
        
        // 保存
        sessionContextGateway.save(metadata.save());
        
        log.info("创建会话: appId={}, topicId={}, sessionId={}", appId, topicId, sessionId);
        return sessionId;
    }

    // ========== 会话查询 ==========

    @Override
    public <T> Optional<AppSession<T>> getActiveSession(String appId, String topicId, TypeToken<T> typeToken) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        String activeSessionId = metadata.getString(KEY_ACTIVE_SESSION_ID).orElse(null);
        
        if (activeSessionId == null) {
            return Optional.empty();
        }
        
        return getSession(appId, topicId, activeSessionId, typeToken);
    }

    @Override
    public <T> Optional<AppSession<T>> getSession(String appId, String topicId, String sessionId, TypeToken<T> typeToken) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return Optional.empty();
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        for (JsonNode sessionNode : sessions) {
            if (sessionId.equals(sessionNode.path(KEY_SESSION_ID).asText())) {
                return Optional.of(deserializeSession(sessionNode, typeToken));
            }
        }
        
        return Optional.empty();
    }

    @Override
    public List<AppSessionInfo> listSessions(String appId, String topicId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return List.of();
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        return sessions.stream()
            .map(this::deserializeSessionInfo)
            .collect(Collectors.toList());
    }

    @Override
    public int countActiveSessions(String appId, String topicId) {
        return (int) listSessions(appId, topicId).stream()
            .filter(s -> s.getState() == SessionState.ACTIVE || s.getState() == SessionState.IDLE)
            .count();
    }

    // ========== 会话更新 ==========

    @Override
    public <T> void updateSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken, long version) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            log.warn("会话上下文不存在: topicId={}", topicId);
            return;
        }
        
        SessionContext context = contextOpt.get();
        SessionMetadata metadata = SessionMetadata.of(context);
        List<JsonNode> sessions = getSessionsList(metadata);
        
        for (int i = 0; i < sessions.size(); i++) {
            JsonNode sessionNode = sessions.get(i);
            if (sessionId.equals(sessionNode.path(KEY_SESSION_ID).asText())) {
                // 乐观锁检查
                long actualVersion = sessionNode.path(KEY_VERSION).asLong();
                if (actualVersion != version) {
                    throw new OptimisticLockException(version, actualVersion);
                }
                
                // 更新会话
                try {
                    AppSessionInfo info = deserializeSessionInfo(sessionNode);
                    info.setVersion(version + 1);
                    info.setLastActiveAt(System.currentTimeMillis());
                    
                    JsonNode updatedNode = objectMapper.valueToTree(info);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) updatedNode)
                        .set(KEY_DATA, objectMapper.valueToTree(data));
                    sessions.set(i, updatedNode);
                    
                    metadata.set(KEY_SESSIONS, sessions);
                    sessionContextGateway.save(metadata.save());
                    
                    log.info("更新会话: sessionId={}, version={}", sessionId, version + 1);
                } catch (OptimisticLockException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Failed to update session", e);
                    throw new RuntimeException("Failed to update session", e);
                }
                return;
            }
        }
        
        log.warn("会话不存在: sessionId={}", sessionId);
    }

    @Override
    public void updateState(String appId, String topicId, String sessionId, SessionState state, long version) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        for (int i = 0; i < sessions.size(); i++) {
            JsonNode sessionNode = sessions.get(i);
            if (sessionId.equals(sessionNode.path(KEY_SESSION_ID).asText())) {
                long actualVersion = sessionNode.path(KEY_VERSION).asLong();
                if (actualVersion != version) {
                    throw new OptimisticLockException(version, actualVersion);
                }
                
                SessionState oldState = SessionState.valueOf(sessionNode.path(KEY_STATE).asText());
                
                // 状态转换验证
                if (!oldState.canTransitionTo(state)) {
                    throw new IllegalStateException(
                        String.format("Invalid state transition: %s -> %s", oldState, state)
                    );
                }
                
                // 更新状态
                ((com.fasterxml.jackson.databind.node.ObjectNode) sessionNode)
                    .put(KEY_STATE, state.name())
                    .put(KEY_VERSION, version + 1)
                    .put(KEY_LAST_ACTIVE_AT, System.currentTimeMillis());
                
                metadata.set(KEY_SESSIONS, sessions);
                sessionContextGateway.save(metadata.save());
                
                log.info("更新会话状态: sessionId={}, {} -> {}", sessionId, oldState, state);
                return;
            }
        }
    }

    @Override
    public void setActiveSession(String appId, String topicId, String sessionId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        metadata.set(KEY_ACTIVE_SESSION_ID, sessionId);
        sessionContextGateway.save(metadata.save());
        
        log.info("设置活跃会话: topicId={}, sessionId={}", topicId, sessionId);
    }

    @Override
    public void activateSession(String appId, String topicId, String sessionId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        for (JsonNode sessionNode : sessions) {
            if (sessionId.equals(sessionNode.path(KEY_SESSION_ID).asText())) {
                SessionState oldState = SessionState.valueOf(sessionNode.path(KEY_STATE).asText());
                long version = sessionNode.path(KEY_VERSION).asLong();
                updateState(appId, topicId, sessionId, SessionState.ACTIVE, version);
                return;
            }
        }
    }

    @Override
    public void idleSession(String appId, String topicId, String sessionId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        for (JsonNode sessionNode : sessions) {
            if (sessionId.equals(sessionNode.path(KEY_SESSION_ID).asText())) {
                SessionState oldState = SessionState.valueOf(sessionNode.path(KEY_STATE).asText());
                long version = sessionNode.path(KEY_VERSION).asLong();
                updateState(appId, topicId, sessionId, SessionState.IDLE, version);
                return;
            }
        }
    }

    // ========== 会话删除 ==========

    @Override
    public void deleteSession(String appId, String topicId, String sessionId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        boolean removed = sessions.removeIf(
            node -> sessionId.equals(node.path(KEY_SESSION_ID).asText())
        );
        
        if (removed) {
            metadata.set(KEY_SESSIONS, sessions);
            
            // 如果删除的是活跃会话，清除 activeSessionId
            String activeSessionId = metadata.getString(KEY_ACTIVE_SESSION_ID).orElse(null);
            if (sessionId.equals(activeSessionId)) {
                metadata.remove(KEY_ACTIVE_SESSION_ID);
            }
            
            sessionContextGateway.save(metadata.save());
            log.info("删除会话: sessionId={}", sessionId);
        }
    }

    @Override
    public void terminateSession(String appId, String topicId, String sessionId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        for (JsonNode sessionNode : sessions) {
            if (sessionId.equals(sessionNode.path(KEY_SESSION_ID).asText())) {
                long version = sessionNode.path(KEY_VERSION).asLong();
                try {
                    updateState(appId, topicId, sessionId, SessionState.TERMINATED, version);
                } catch (IllegalStateException e) {
                    log.warn("无法终止会话: {}", e.getMessage());
                }
                return;
            }
        }
    }

    @Override
    public int cleanupSessions(String appId, String topicId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isEmpty()) {
            return 0;
        }
        
        SessionMetadata metadata = SessionMetadata.of(contextOpt.get());
        List<JsonNode> sessions = getSessionsList(metadata);
        
        int originalSize = sessions.size();
        sessions.removeIf(node -> {
            SessionState state = SessionState.valueOf(node.path(KEY_STATE).asText());
            return state == SessionState.TERMINATED || state == SessionState.EXPIRED;
        });
        
        int removed = originalSize - sessions.size();
        if (removed > 0) {
            metadata.set(KEY_SESSIONS, sessions);
            sessionContextGateway.save(metadata.save());
            log.info("清理会话: topicId={}, removed={}", topicId, removed);
        }
        
        return removed;
    }

    // ========== 私有方法 ==========

    private SessionContext getOrCreateContext(String topicId, String appId) {
        Optional<SessionContext> contextOpt = sessionContextGateway.findByTopicId(topicId);
        if (contextOpt.isPresent()) {
            return contextOpt.get();
        }
        
        SessionContext context = new SessionContext(topicId, appId);
        sessionContextGateway.save(context);
        return context;
    }

    @SuppressWarnings("unchecked")
    private List<JsonNode> getSessionsList(SessionMetadata metadata) {
        Optional<Object> sessionsOpt = metadata.getObject(KEY_SESSIONS, Object.class);
        if (sessionsOpt.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // 重新序列化再反序列化为 List<JsonNode>
            String json = objectMapper.writeValueAsString(sessionsOpt.get());
            return objectMapper.readValue(json, new TypeReference<List<JsonNode>>() {});
        } catch (Exception e) {
            log.error("Failed to parse sessions list", e);
            return new ArrayList<>();
        }
    }

    private <T> AppSession<T> deserializeSession(JsonNode node, TypeToken<T> typeToken) {
        AppSessionInfo info = deserializeSessionInfo(node);
        
        try {
            JsonNode dataNode = node.path(KEY_DATA);
            T data = objectMapper.treeToValue(dataNode, 
                (Class<T>) typeToken.getRawType());
            return AppSession.fromInfo(info, data);
        } catch (Exception e) {
            log.error("Failed to deserialize session data", e);
            throw new RuntimeException("Failed to deserialize session data", e);
        }
    }

    private AppSessionInfo deserializeSessionInfo(JsonNode node) {
        AppSessionInfo info = new AppSessionInfo();
        info.setSessionId(node.path(KEY_SESSION_ID).asText());
        info.setAppId(node.path("appId").asText());
        info.setTopicId(node.path("topicId").asText());
        info.setState(SessionState.valueOf(node.path(KEY_STATE).asText()));
        info.setCreatedAt(node.path(KEY_CREATED_AT).asLong());
        info.setLastActiveAt(node.path(KEY_LAST_ACTIVE_AT).asLong());
        info.setVersion(node.path(KEY_VERSION).asLong());
        
        if (node.has(KEY_EXPIRES_AT) && !node.path(KEY_EXPIRES_AT).isNull()) {
            info.setExpiresAt(node.path(KEY_EXPIRES_AT).asLong());
        }
        
        return info;
    }
}

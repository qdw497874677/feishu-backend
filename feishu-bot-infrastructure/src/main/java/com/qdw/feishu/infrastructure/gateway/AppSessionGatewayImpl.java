package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.exception.OptimisticLockException;
import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.AppSessionInfo;
import com.qdw.feishu.domain.session.SessionIdGenerator;
import com.qdw.feishu.domain.session.SessionState;
import com.qdw.feishu.domain.session.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通用会话管理实现（基于独立 SQLite 存储）
 * 
 * Phase 2 重构：移除对 SessionContextGateway 的依赖，使用独立的 app_session 表。
 * 会话与 IM 上下文的绑定由 ImContextBindingGateway 管理。
 * 
 * 支持多会话、乐观锁、状态机。
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "feishu.topic-mapping.storage-type",
    havingValue = "sqlite",
    matchIfMissing = true
)
public class AppSessionGatewayImpl implements AppSessionGateway {

    private final JdbcTemplate jdbcTemplate;
    private final SessionIdGenerator sessionIdGenerator;
    private final ObjectMapper objectMapper;
    private final String dbFilePath;

    public AppSessionGatewayImpl(
            SessionIdGenerator sessionIdGenerator,
            @Value("${feishu.topic-mapping.sqlite.path:feishu-topic-mappings.db}") String dbFilePath) {
        this.sessionIdGenerator = sessionIdGenerator;
        this.dbFilePath = dbFilePath;
        this.jdbcTemplate = new JdbcTemplate(createDataSource());
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        try {
            ensureDbDirectoryExists();
            createTableIfNotExists();
            log.info("SQLite App Session table initialized: {}", dbFilePath);
            log.info("Current session count: {}", count());
        } catch (Exception e) {
            log.error("SQLite session table initialization failed", e);
            throw new RuntimeException("Failed to initialize SQLite session table", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("SQLite App Session connection closed");
    }

    private DataSource createDataSource() {
        String connectionString = "jdbc:sqlite:" + dbFilePath;
        log.info("SQLite connection string: {}", connectionString);

        return DataSourceBuilder.create()
                .url(connectionString)
                .driverClassName("org.sqlite.JDBC")
                .build();
    }

    private void ensureDbDirectoryExists() {
        try {
            Path dbPath = Paths.get(dbFilePath);
            if (dbPath.getParent() != null) {
                Files.createDirectories(dbPath.getParent());
            }
        } catch (Exception e) {
            log.warn("Could not create db directory: {}", e.getMessage());
        }
    }

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS app_session (
                app_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                state TEXT NOT NULL,
                data TEXT,
                version INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                last_active_at INTEGER NOT NULL,
                expires_at INTEGER,
                PRIMARY KEY (app_id, session_id)
            )
        """;

        jdbcTemplate.execute(sql);
        log.info("App Session table ready");

        createIndexesIfNotExist();
    }

    private void createIndexesIfNotExist() {
        String[] indexSqls = {
            "CREATE INDEX IF NOT EXISTS idx_session_app_id ON app_session(app_id)",
            "CREATE INDEX IF NOT EXISTS idx_session_state ON app_session(state)",
            "CREATE INDEX IF NOT EXISTS idx_session_created ON app_session(created_at)"
        };

        for (String sql : indexSqls) {
            jdbcTemplate.execute(sql);
        }
        log.info("App Session indexes ready");
    }

    private int count() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_session", Integer.class);
        return count != null ? count : 0;
    }

    // ========== 会话创建 ==========

    @Override
    public <T> String createSession(String appId, T data, TypeToken<T> typeToken) {
        String sessionId = sessionIdGenerator.generate(appId);
        return createSession(appId, sessionId, data, typeToken);
    }

    @Override
    public <T> String createSession(String appId, String sessionId, T data, TypeToken<T> typeToken) {
        long now = System.currentTimeMillis();
        
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize session data", e);
            throw new RuntimeException("Failed to create session", e);
        }

        String sql = """
            INSERT INTO app_session (app_id, session_id, state, data, version, created_at, last_active_at)
            VALUES (?, ?, ?, ?, 1, ?, ?)
        """;

        try {
            jdbcTemplate.update(sql, appId, sessionId, SessionState.CREATED.name(), dataJson, now, now);
            log.info("创建会话: appId={}, sessionId={}", appId, sessionId);
            return sessionId;
        } catch (Exception e) {
            log.error("Failed to create session: appId={}, sessionId={}", appId, sessionId, e);
            throw new RuntimeException("Failed to create session", e);
        }
    }

    // ========== 会话查询 ==========

    @Override
    public <T> Optional<AppSession<T>> getSession(String appId, String sessionId, TypeToken<T> typeToken) {
        String sql = """
            SELECT app_id, session_id, state, data, version, created_at, last_active_at, expires_at
            FROM app_session
            WHERE app_id = ? AND session_id = ?
        """;

        try {
            AppSession<T> session = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                AppSessionInfo info = new AppSessionInfo();
                info.setAppId(rs.getString("app_id"));
                info.setSessionId(rs.getString("session_id"));
                info.setState(SessionState.valueOf(rs.getString("state")));
                info.setVersion(rs.getLong("version"));
                info.setCreatedAt(rs.getLong("created_at"));
                info.setLastActiveAt(rs.getLong("last_active_at"));
                
                long expiresAt = rs.getLong("expires_at");
                if (!rs.wasNull()) {
                    info.setExpiresAt(expiresAt);
                }

                // Deserialize data
                T data = null;
                String dataJson = rs.getString("data");
                if (dataJson != null && !dataJson.isEmpty()) {
                    try {
                        data = objectMapper.readValue(dataJson, 
                            objectMapper.constructType(typeToken.getType()));
                    } catch (Exception e) {
                        log.error("Failed to deserialize session data: appId={}, sessionId={}", 
                            appId, sessionId, e);
                    }
                }

                return AppSession.fromInfo(info, data);
            }, appId, sessionId);

            return Optional.ofNullable(session);
        } catch (Exception e) {
            log.debug("Session not found: appId={}, sessionId={}", appId, sessionId);
            return Optional.empty();
        }
    }

    @Override
    public List<AppSessionInfo> listSessions(String appId) {
        String sql = """
            SELECT app_id, session_id, state, version, created_at, last_active_at, expires_at
            FROM app_session
            WHERE app_id = ?
            ORDER BY last_active_at DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            AppSessionInfo info = new AppSessionInfo();
            info.setAppId(rs.getString("app_id"));
            info.setSessionId(rs.getString("session_id"));
            info.setState(SessionState.valueOf(rs.getString("state")));
            info.setVersion(rs.getLong("version"));
            info.setCreatedAt(rs.getLong("created_at"));
            info.setLastActiveAt(rs.getLong("last_active_at"));
            
            long expiresAt = rs.getLong("expires_at");
            if (!rs.wasNull()) {
                info.setExpiresAt(expiresAt);
            }
            
            return info;
        }, appId);
    }

    @Override
    public int countActiveSessions(String appId) {
        String sql = """
            SELECT COUNT(*) FROM app_session
            WHERE app_id = ? AND state IN (?, ?)
        """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
            appId, SessionState.ACTIVE.name(), SessionState.IDLE.name());
        return count != null ? count : 0;
    }

    // ========== 会话更新 ==========

    @Override
    public <T> void updateSession(String appId, String sessionId, T data, TypeToken<T> typeToken, long version) {
        // First check version
        Long currentVersion = getVersion(appId, sessionId);
        if (currentVersion == null) {
            log.warn("会话不存在: appId={}, sessionId={}", appId, sessionId);
            return;
        }
        
        if (currentVersion != version) {
            throw new OptimisticLockException(version, currentVersion);
        }

        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize session data", e);
            throw new RuntimeException("Failed to update session", e);
        }

        long now = System.currentTimeMillis();
        String sql = """
            UPDATE app_session
            SET data = ?, version = version + 1, last_active_at = ?
            WHERE app_id = ? AND session_id = ? AND version = ?
        """;

        int updated = jdbcTemplate.update(sql, dataJson, now, appId, sessionId, version);
        
        if (updated == 0) {
            throw new OptimisticLockException(version, currentVersion);
        }
        
        log.info("更新会话: sessionId={}, version={}", sessionId, version + 1);
    }

    @Override
    public void updateState(String appId, String sessionId, SessionState state, long version) {
        Long currentVersion = getVersion(appId, sessionId);
        if (currentVersion == null) {
            log.warn("会话不存在: appId={}, sessionId={}", appId, sessionId);
            return;
        }
        
        if (currentVersion != version) {
            throw new OptimisticLockException(version, currentVersion);
        }

        // Get current state for validation
        SessionState oldState = getState(appId, sessionId);
        if (oldState != null && !oldState.canTransitionTo(state)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s -> %s", oldState, state)
            );
        }

        long now = System.currentTimeMillis();
        String sql = """
            UPDATE app_session
            SET state = ?, version = version + 1, last_active_at = ?
            WHERE app_id = ? AND session_id = ? AND version = ?
        """;

        int updated = jdbcTemplate.update(sql, state.name(), now, appId, sessionId, version);
        
        if (updated == 0) {
            throw new OptimisticLockException(version, currentVersion);
        }
        
        log.info("更新会话状态: sessionId={}, {} -> {}", sessionId, oldState, state);
    }

    @Override
    public void activateSession(String appId, String sessionId) {
        Long version = getVersion(appId, sessionId);
        if (version != null) {
            updateState(appId, sessionId, SessionState.ACTIVE, version);
        }
    }

    @Override
    public void idleSession(String appId, String sessionId) {
        Long version = getVersion(appId, sessionId);
        if (version != null) {
            updateState(appId, sessionId, SessionState.IDLE, version);
        }
    }

    // ========== 会话删除 ==========

    @Override
    public void deleteSession(String appId, String sessionId) {
        String sql = "DELETE FROM app_session WHERE app_id = ? AND session_id = ?";
        int deleted = jdbcTemplate.update(sql, appId, sessionId);
        
        if (deleted > 0) {
            log.info("删除会话: sessionId={}", sessionId);
        }
    }

    @Override
    public void terminateSession(String appId, String sessionId) {
        Long version = getVersion(appId, sessionId);
        if (version != null) {
            try {
                updateState(appId, sessionId, SessionState.TERMINATED, version);
            } catch (IllegalStateException e) {
                log.warn("无法终止会话: {}", e.getMessage());
            }
        }
    }

    @Override
    public int cleanupSessions(String appId) {
        String sql = """
            DELETE FROM app_session
            WHERE app_id = ? AND state IN (?, ?)
        """;
        
        int deleted = jdbcTemplate.update(sql, appId, 
            SessionState.TERMINATED.name(), SessionState.EXPIRED.name());
        
        if (deleted > 0) {
            log.info("清理会话: appId={}, removed={}", appId, deleted);
        }
        
        return deleted;
    }

    // ========== 私有方法 ==========

    private Long getVersion(String appId, String sessionId) {
        String sql = "SELECT version FROM app_session WHERE app_id = ? AND session_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, appId, sessionId);
        } catch (Exception e) {
            return null;
        }
    }

    private SessionState getState(String appId, String sessionId) {
        String sql = "SELECT state FROM app_session WHERE app_id = ? AND session_id = ?";
        try {
            String stateName = jdbcTemplate.queryForObject(sql, String.class, appId, sessionId);
            return stateName != null ? SessionState.valueOf(stateName) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public String getDbFilePath() {
        return new File(dbFilePath).getAbsolutePath();
    }
}

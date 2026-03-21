package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
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
import java.util.Optional;

/**
 * IM Context Binding SQLite Gateway Implementation.
 * 
 * Persists IM context to app session bindings using SQLite.
 * Uses the same database file as SessionContextSqliteGateway for consistency.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "feishu.topic-mapping.storage-type",
    havingValue = "sqlite",
    matchIfMissing = true
)
public class ImContextBindingGatewayImpl implements ImContextBindingGateway {

    private final JdbcTemplate jdbcTemplate;
    private final String dbFilePath;

    public ImContextBindingGatewayImpl(
            @Value("${feishu.topic-mapping.sqlite.path:feishu-topic-mappings.db}") String dbFilePath) {
        this.dbFilePath = dbFilePath;
        this.jdbcTemplate = new JdbcTemplate(createDataSource());
    }

    @PostConstruct
    public void init() {
        try {
            ensureDbDirectoryExists();
            createTableIfNotExists();
            log.info("SQLite IM Context Binding table initialized: {}", dbFilePath);
            log.info("Current binding count: {}", count());
        } catch (Exception e) {
            log.error("SQLite binding table initialization failed", e);
            throw new RuntimeException("Failed to initialize SQLite binding table", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("SQLite IM Context Binding connection closed");
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
            Path parentDir = dbPath.getParent();

            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("Created database directory: {}", parentDir);
            }
        } catch (Exception e) {
            log.error("Failed to create database directory", e);
            throw new RuntimeException("Failed to create database directory", e);
        }
    }

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS im_context_binding (
                context_key TEXT PRIMARY KEY NOT NULL,
                platform TEXT NOT NULL,
                context_type TEXT NOT NULL,
                context_id TEXT NOT NULL,
                app_id TEXT NOT NULL,
                session_id TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_active_at INTEGER NOT NULL
            )
        """;

        jdbcTemplate.execute(sql);
        log.info("IM Context Binding table ready");

        createIndexesIfNotExist();
    }

    private void createIndexesIfNotExist() {
        String[] indexSqls = {
            "CREATE INDEX IF NOT EXISTS idx_binding_app_session ON im_context_binding(app_id, session_id)",
            "CREATE INDEX IF NOT EXISTS idx_binding_platform ON im_context_binding(platform)",
            "CREATE INDEX IF NOT EXISTS idx_binding_app_id ON im_context_binding(app_id)"
        };

        for (String sql : indexSqls) {
            jdbcTemplate.execute(sql);
        }
        log.info("IM Context Binding indexes ready");
    }

    private int count() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_context_binding", Integer.class);
        return count != null ? count : 0;
    }

    // ========== ImContextBindingGateway Implementation ==========

    @Override
    public BindingResult bind(ImContextRef contextRef, String appId, String sessionId) {
        if (contextRef == null || appId == null || sessionId == null) {
            throw new IllegalArgumentException("contextRef, appId, and sessionId cannot be null");
        }

        String contextKey = contextRef.toStorageKey();

        // Check existing binding
        Optional<ImContextBinding> existing = findBinding(contextRef);

        if (existing.isPresent()) {
            ImContextBinding current = existing.get();

            // If already bound to same app+session, no change needed
            if (current.matches(appId, sessionId)) {
                log.debug("Binding already exists for context={}, appId={}, sessionId={}",
                    contextKey, appId, sessionId);
                return BindingResult.noChange(current);
            }

            // Update existing binding to new session
            long now = System.currentTimeMillis();
            String sql = """
                UPDATE im_context_binding
                SET app_id = ?, session_id = ?, last_active_at = ?
                WHERE context_key = ?
            """;

            int updated = jdbcTemplate.update(sql, appId, sessionId, now, contextKey);

            if (updated > 0) {
                ImContextBinding newBinding = new ImContextBinding(
                    contextRef, appId, sessionId, current.getCreatedAt(), now
                );
                log.info("Binding updated: context={}, oldSession={}, newSession={}",
                    contextKey, current.getSessionId(), sessionId);
                return BindingResult.updated(newBinding);
            }
        }

        // Create new binding
        long now = System.currentTimeMillis();
        String sql = """
            INSERT INTO im_context_binding
            (context_key, platform, context_type, context_id, app_id, session_id, created_at, last_active_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(sql,
            contextKey,
            contextRef.getPlatform(),
            contextRef.getContextType(),
            contextRef.getContextId(),
            appId,
            sessionId,
            now,
            now
        );

        ImContextBinding newBinding = ImContextBinding.create(contextRef, appId, sessionId);
        log.info("Binding created: context={}, appId={}, sessionId={}", contextKey, appId, sessionId);
        return BindingResult.created(newBinding);
    }

    @Override
    public Optional<ImContextBinding> findBinding(ImContextRef contextRef) {
        if (contextRef == null) {
            return Optional.empty();
        }

        String contextKey = contextRef.toStorageKey();
        String sql = """
            SELECT context_key, platform, context_type, context_id, app_id, session_id, created_at, last_active_at
            FROM im_context_binding
            WHERE context_key = ?
        """;

        try {
            ImContextBinding binding = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                ImContextRef ref = ImContextRef.fromStorageKey(rs.getString("context_key"));
                return new ImContextBinding(
                    ref,
                    rs.getString("app_id"),
                    rs.getString("session_id"),
                    rs.getLong("created_at"),
                    rs.getLong("last_active_at")
                );
            }, contextKey);

            return Optional.ofNullable(binding);
        } catch (Exception e) {
            log.debug("Binding not found: contextKey={}", contextKey);
            return Optional.empty();
        }
    }

    @Override
    public void clearBinding(ImContextRef contextRef) {
        if (contextRef == null) {
            return;
        }

        String contextKey = contextRef.toStorageKey();
        String sql = "DELETE FROM im_context_binding WHERE context_key = ?";

        int deleted = jdbcTemplate.update(sql, contextKey);

        if (deleted > 0) {
            log.info("Binding cleared: context={}", contextKey);
        }
    }

    @Override
    public boolean isBoundToApp(ImContextRef contextRef, String appId) {
        if (contextRef == null || appId == null) {
            return false;
        }

        String contextKey = contextRef.toStorageKey();
        String sql = "SELECT COUNT(*) FROM im_context_binding WHERE context_key = ? AND app_id = ?";

        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, contextKey, appId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("Failed to check binding: contextKey={}, appId={}", contextKey, appId);
            return false;
        }
    }

    @Override
    public void touchBinding(ImContextRef contextRef) {
        if (contextRef == null) {
            return;
        }

        String contextKey = contextRef.toStorageKey();
        long now = System.currentTimeMillis();
        String sql = "UPDATE im_context_binding SET last_active_at = ? WHERE context_key = ?";

        jdbcTemplate.update(sql, now, contextKey);
        log.debug("Binding touched: context={}", contextKey);
    }

    public String getDbFilePath() {
        return new File(dbFilePath).getAbsolutePath();
    }
}

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IM Context Binding SQLite Gateway Implementation.
 * 
 * Persists IM context to app session bindings using SQLite.
 * Uses the same database file as SessionContextSqliteGateway for consistency.
 * 
 * Schema Migration Strategy (Task 2):
 * Uses create-copy-swap pattern to migrate from NOT NULL to nullable session_id:
 * 1. Create new table with nullable schema
 * 2. Copy existing data to new table
 * 3. Drop old table
 * 
 * This preserves all existing bindings during migration.
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
    private final DataSource dataSource;

    public ImContextBindingGatewayImpl(
            @Value("${feishu.topic-mapping.sqlite.path:feishu-topic-mappings.db}") String dbFilePath) {
        this.dbFilePath = dbFilePath;
        this.dataSource = createDataSource();
        this.jdbcTemplate = new JdbcTemplate(dataSource);
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
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
                log.info("SQLite IM Context Binding connection closed");
            } catch (Exception e) {
                log.warn("Failed to close SQLite datasource", e);
            }
        }
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
        // Migration strategy: Check for old schema and migrate if needed
        // See class-level documentation for rationale
        if (needsSchemaMigration()) {
            log.info("Detected old schema with NOT NULL session_id, migrating to nullable schema");
            migrateTableToNullableSessionId();
        } else {
            createTable();
        }
        createIndexesIfNotExist();
    }
    
    /**
     * Check if the table exists with the old NOT NULL schema.
     * Uses PRAGMA table_info to structurally detect column nullability.
     * Returns true if table exists but has NOT NULL constraint on session_id.
     */
    private boolean needsSchemaMigration() {
        try {
            // Use PRAGMA table_info which returns: cid, name, type, notnull, dflt_value, pk
            // notnull = 1 means NOT NULL constraint, 0 means nullable
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "PRAGMA table_info(im_context_binding)");
            
            for (Map<String, Object> column : columns) {
                String columnName = (String) column.get("name");
                if ("session_id".equals(columnName)) {
                    Object notNullObj = column.get("notnull");
                    // Handle Number types robustly (SQLite may return Long or Integer)
                    if (notNullObj instanceof Number) {
                        int notNull = ((Number) notNullObj).intValue();
                        // If notnull = 1, the column has NOT NULL constraint
                        if (notNull == 1) {
                            return true;
                        }
                    }
                }
            }
            // Table exists but session_id is already nullable (or column not found)
            return false;
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // Table doesn't exist - no migration needed
            log.debug("Table im_context_binding not found, no migration needed");
            return false;
        }
        // Note: Other unexpected DB errors will propagate up and fail initialization
        // This is intentional - we should not silently swallow real DB problems
    }
    
    /**
     * Migrate table using create-copy-swap pattern.
     * Preserves all existing data while changing session_id from NOT NULL to nullable.
     */
    private void migrateTableToNullableSessionId() {
        log.info("Starting create-copy-swap migration for im_context_binding");
        
        // Step 1: Create new table with nullable session_id
        jdbcTemplate.execute("""
            CREATE TABLE im_context_binding_new (
                context_key TEXT PRIMARY KEY NOT NULL,
                platform TEXT NOT NULL,
                context_type TEXT NOT NULL,
                context_id TEXT NOT NULL,
                app_id TEXT NOT NULL,
                session_id TEXT,
                created_at INTEGER NOT NULL,
                last_active_at INTEGER NOT NULL
            )
        """);
        log.debug("Created new table with nullable session_id");
        
        // Step 2: Copy existing data to new table
        int copiedRows = jdbcTemplate.update("""
            INSERT INTO im_context_binding_new
            SELECT context_key, platform, context_type, context_id, app_id, session_id, created_at, last_active_at
            FROM im_context_binding
        """);
        log.info("Copied {} existing bindings to new table", copiedRows);
        
        // Step 3: Drop old table
        jdbcTemplate.execute("DROP TABLE im_context_binding");
        log.debug("Dropped old table");
        
        // Step 4: Rename new table to final name
        jdbcTemplate.execute("ALTER TABLE im_context_binding_new RENAME TO im_context_binding");
        log.info("Migration completed: im_context_binding now has nullable session_id");
    }
    
    /**
     * Create the table with current schema (nullable session_id).
     */
    private void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS im_context_binding (
                context_key TEXT PRIMARY KEY NOT NULL,
                platform TEXT NOT NULL,
                context_type TEXT NOT NULL,
                context_id TEXT NOT NULL,
                app_id TEXT NOT NULL,
                session_id TEXT,
                created_at INTEGER NOT NULL,
                last_active_at INTEGER NOT NULL
            )
        """;

        jdbcTemplate.execute(sql);
        log.info("IM Context Binding table ready (with nullable session_id)");
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
        if (contextRef == null || appId == null) {
            throw new IllegalArgumentException("contextRef and appId cannot be null");
        }
        // sessionId is nullable - null means app context without active session

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
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // No binding found for this context - expected case
            log.debug("Binding not found: contextKey={}", contextKey);
            return Optional.empty();
        }
        // Note: Other DataAccessExceptions (connection issues, SQL errors) will propagate up
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
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // No rows returned - shouldn't happen for COUNT(*), but handle defensively
            log.debug("No result for binding check: contextKey={}, appId={}", contextKey, appId);
            return false;
        }
        // Note: Other DataAccessExceptions (connection issues, SQL errors) will propagate up
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

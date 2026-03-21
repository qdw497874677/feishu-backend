package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.TopicAppBindingGateway;
import com.qdw.feishu.domain.model.TopicAppBinding;
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
 * 话题-应用绑定 SQLite Gateway 实现
 *
 * 使用 SQLite 数据库持久化话题与应用的绑定关系
 * 用于消息路由：决定话题消息应由哪个应用处理
 *
 * 注意：此网关与 ImContextBindingGateway 职责不同：
 * - 本网关：通用路由（topicId → appId）
 * - ImContextBindingGateway：会话管理（contextRef → sessionId + app session data）
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "feishu.topic-mapping.storage-type",
    havingValue = "sqlite",
    matchIfMissing = true
)
public class TopicAppBindingSqliteGateway implements TopicAppBindingGateway {

    private final String dbFilePath;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    public TopicAppBindingSqliteGateway(
            @Value("${feishu.topic-mapping.sqlite.path:data/feishu-topic-mappings.db}") String dbFilePath) {
        this.dbFilePath = dbFilePath;
    }

    @PostConstruct
    public void init() {
        try {
            // 确保数据目录存在
            Path dbPath = Paths.get(dbFilePath);
            Path parentDir = dbPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.info("创建数据库目录: {}", parentDir.toAbsolutePath());
            }

            // 创建 SQLite 数据源
            String jdbcUrl = "jdbc:sqlite:" + dbFilePath;
            this.dataSource = DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .build();
            this.jdbcTemplate = new JdbcTemplate(dataSource);

            // 初始化表结构
            createTableIfNotExists();

            log.info("话题-应用绑定 SQLite Gateway 初始化成功: dbFile={}, recordCount={}",
                    new File(dbFilePath).getAbsolutePath(), count());
        } catch (Exception e) {
            log.error("初始化话题-应用绑定 SQLite Gateway 失败", e);
            throw new RuntimeException("Failed to initialize topic-app binding SQLite gateway", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception e) {
                log.warn("关闭数据源失败", e);
            }
        }
    }

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS topic_mapping (
                topic_id TEXT PRIMARY KEY,
                app_id TEXT NOT NULL,
                metadata TEXT,
                created_at INTEGER NOT NULL,
                last_active_at INTEGER NOT NULL
            )
        """;

        jdbcTemplate.execute(sql);
        log.info("话题映射表已就绪");

        createIndexIfNotExists();
    }

    private void createIndexIfNotExists() {
        String sql = """
            CREATE INDEX IF NOT EXISTS idx_topic_mapping_app_id
            ON topic_mapping(app_id)
        """;

        jdbcTemplate.execute(sql);
        log.info("话题映射索引已就绪");
    }

    private int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM topic_mapping", Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public void save(TopicAppBinding binding) {
        String sql = """
            INSERT OR REPLACE INTO topic_mapping (topic_id, app_id, metadata, created_at, last_active_at)
            VALUES (?, ?, ?, ?, ?)
        """;

        int updated = jdbcTemplate.update(sql,
                binding.getTopicId(),
                binding.getAppId(),
                binding.getMetadata(),
                binding.getCreatedAt(),
                binding.getLastActiveAt()
        );

        if (updated > 0) {
            log.info("话题-应用绑定已保存到 SQLite: topicId={}, appId={}, dbFile={}",
                    binding.getTopicId(), binding.getAppId(), dbFilePath);
        }
    }

    @Override
    public Optional<TopicAppBinding> findByTopicId(String topicId) {
        String sql = "SELECT topic_id, app_id, metadata, created_at, last_active_at FROM topic_mapping WHERE topic_id = ?";

        try {
            TopicAppBinding binding = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                TopicAppBinding b = new TopicAppBinding(
                        rs.getString("topic_id"),
                        rs.getString("app_id"),
                        rs.getString("metadata")
                );
                // 设置时间戳
                b.setCreatedAt(rs.getLong("created_at"));
                b.setLastActiveAt(rs.getLong("last_active_at"));
                return b;
            }, topicId);

            return Optional.ofNullable(binding);
        } catch (Exception e) {
            log.debug("话题-应用绑定未找到: topicId={}", topicId);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String topicId) {
        String sql = "DELETE FROM topic_mapping WHERE topic_id = ?";

        int deleted = jdbcTemplate.update(sql, topicId);

        if (deleted > 0) {
            log.info("话题-应用绑定已从 SQLite 删除: topicId={}, dbFile={}", topicId, dbFilePath);
        }
    }

    public String getDbFilePath() {
        return new File(dbFilePath).getAbsolutePath();
    }
}

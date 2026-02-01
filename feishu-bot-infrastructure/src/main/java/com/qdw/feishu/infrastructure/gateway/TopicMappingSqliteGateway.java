package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.model.TopicMapping;
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
 * 话题映射 SQLite Gateway 实现
 *
 * 使用 SQLite 数据库持久化话题映射，数据文件存储在项目目录中，可以加入版本控制
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "feishu.topic-mapping.storage-type",
    havingValue = "sqlite",
    matchIfMissing = true
)
public class TopicMappingSqliteGateway implements TopicMappingGateway {

    private final JdbcTemplate jdbcTemplate;
    private final String dbFilePath;

    public TopicMappingSqliteGateway(
            @Value("${feishu.topic-mapping.sqlite.path:feishu-topic-mappings.db}") String dbFilePath) {
        this.dbFilePath = dbFilePath;
        this.jdbcTemplate = new JdbcTemplate(createDataSource());
    }

    @PostConstruct
    public void init() {
        try {
            ensureDbDirectoryExists();
            createTableIfNotExists();
            log.info("SQLite 话题映射数据库初始化成功: {}", dbFilePath);
            log.info("当前话题映射数量: {}", count());
        } catch (Exception e) {
            log.error("SQLite 数据库初始化失败", e);
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("SQLite 话题映射数据库连接关闭");
    }

    private DataSource createDataSource() {
        String connectionString = "jdbc:sqlite:" + dbFilePath;
        log.info("SQLite 连接字符串: {}", connectionString);

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
                log.info("创建数据库目录: {}", parentDir);
            }
        } catch (Exception e) {
            log.error("创建数据库目录失败", e);
            throw new RuntimeException("Failed to create database directory", e);
        }
    }

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS topic_mapping (
                topic_id TEXT PRIMARY KEY NOT NULL,
                app_id TEXT NOT NULL,
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
    public void save(TopicMapping mapping) {
        String sql = """
            INSERT OR REPLACE INTO topic_mapping (topic_id, app_id, created_at, last_active_at)
            VALUES (?, ?, ?, ?)
        """;

        int updated = jdbcTemplate.update(sql,
                mapping.getTopicId(),
                mapping.getAppId(),
                mapping.getCreatedAt(),
                mapping.getLastActiveAt()
        );

        if (updated > 0) {
            log.info("话题映射已保存到 SQLite: topicId={}, appId={}, dbFile={}",
                    mapping.getTopicId(), mapping.getAppId(), dbFilePath);
        }
    }

    @Override
    public Optional<TopicMapping> findByTopicId(String topicId) {
        String sql = "SELECT topic_id, app_id, created_at, last_active_at FROM topic_mapping WHERE topic_id = ?";

        try {
            TopicMapping mapping = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                TopicMapping m = new TopicMapping(
                        rs.getString("topic_id"),
                        rs.getString("app_id")
                );
                return m;
            }, topicId);

            return Optional.ofNullable(mapping);
        } catch (Exception e) {
            log.debug("话题映射未找到: topicId={}", topicId);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String topicId) {
        String sql = "DELETE FROM topic_mapping WHERE topic_id = ?";

        int deleted = jdbcTemplate.update(sql, topicId);

        if (deleted > 0) {
            log.info("话题映射已从 SQLite 删除: topicId={}, dbFile={}", topicId, dbFilePath);
        }
    }

    public String getDbFilePath() {
        return new File(dbFilePath).getAbsolutePath();
    }
}

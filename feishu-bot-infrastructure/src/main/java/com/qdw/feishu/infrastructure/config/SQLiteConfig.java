package com.qdw.feishu.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Shared SQLite DataSource configuration.
 *
 * Provides a single DataSource bean for both ImContextBindingGatewayImpl
 * and AppSessionGatewayImpl, avoiding duplicate connection pools to the same DB.
 */
@Slf4j
@Configuration
public class SQLiteConfig {

    @Bean
    public DataSource sqliteDataSource(
            @Value("${feishu.topic-mapping.sqlite.path:feishu-topic-mappings.db}") String dbFilePath) {
        String connectionString = "jdbc:sqlite:" + dbFilePath;
        log.info("Shared SQLite DataSource: {}", connectionString);

        return DataSourceBuilder.create()
                .url(connectionString)
                .driverClassName("org.sqlite.JDBC")
                .build();
    }
}

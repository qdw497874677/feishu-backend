package com.qdw.feishu.infrastructure.gateway;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.model.TopicMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "feishu.topic-mapping.storage-type",
    havingValue = "file",
    matchIfMissing = false
)
public class TopicMappingGatewayImpl implements TopicMappingGateway {

    private final Map<String, TopicMapping> mappings = new ConcurrentHashMap<>();
    private static final String STORAGE_FILE = "/tmp/feishu-topic-mappings.json";
    private final Gson gson = new Gson();

    @PostConstruct
    public void loadMappings() {
        try {
            File file = new File(STORAGE_FILE);
            if (file.exists()) {
                String json = Files.readString(file.toPath());
                Type type = new TypeToken<Map<String, TopicMapping>>(){}.getType();
                Map<String, TopicMapping> loaded = gson.fromJson(json, type);
                if (loaded != null) {
                    mappings.putAll(loaded);
                    log.info("从文件加载话题映射: {} 个", mappings.size());
                }
            } else {
                log.info("话题映射文件不存在，使用空映射");
            }
        } catch (Exception e) {
            log.warn("加载话题映射失败: {}，使用空映射", e.getMessage());
        }
    }

    @PreDestroy
    public void saveMappingsOnShutdown() {
        try {
            saveMappingsToFile();
            log.info("应用关闭，话题映射已保存到文件");
        } catch (Exception e) {
            log.error("保存话题映射失败", e);
        }
    }

    private void saveMappingsToFile() {
        try {
            String json = gson.toJson(mappings);
            Files.writeString(Path.of(STORAGE_FILE), json);
            log.debug("话题映射已持久化到文件: {} 个", mappings.size());
        } catch (Exception e) {
            log.error("保存话题映射到文件失败", e);
        }
    }

    @Override
    public void save(TopicMapping mapping) {
        mappings.put(mapping.getTopicId(), mapping);
        log.info("保存话题映射: topicId={}, appId={}", mapping.getTopicId(), mapping.getAppId());

        // 每次保存时都持久化到文件
        saveMappingsToFile();
    }

    @Override
    public Optional<TopicMapping> findByTopicId(String topicId) {
        log.debug("查找话题映射: topicId={}", topicId);
        return Optional.ofNullable(mappings.get(topicId));
    }

    @Override
    public void delete(String topicId) {
        mappings.remove(topicId);
        log.info("删除话题映射: topicId={}", topicId);

        // 删除后也持久化
        saveMappingsToFile();
    }
}

package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.model.TopicMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TopicMappingGatewayImpl implements TopicMappingGateway {

    private final Map<String, TopicMapping> mappings = new ConcurrentHashMap<>();

    @Override
    public void save(TopicMapping mapping) {
        mappings.put(mapping.getTopicId(), mapping);
        log.info("保存话题映射: topicId={}, appId={}", mapping.getTopicId(), mapping.getAppId());
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
    }
}

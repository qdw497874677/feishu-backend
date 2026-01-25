package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.app.TopicMapping;

import java.util.Optional;

public interface TopicMappingGateway {

    void save(TopicMapping mapping);

    Optional<TopicMapping> findByTopicId(String topicId);

    void updateLastActiveTime(String topicId);

    void delete(String topicId);
}

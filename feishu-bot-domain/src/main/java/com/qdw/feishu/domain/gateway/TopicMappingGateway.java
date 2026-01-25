package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.model.TopicMapping;

import java.util.Optional;

/**
 * 话题映射 Gateway 接口
 * 
 * 定义话题映射数据持久化的抽象，符合 COLA 的 Gateway 模式
 * 将外部依赖隔离在领域层
 */
public interface TopicMappingGateway {

    /**
     * 保存话题映射
     * 
     * @param mapping 话题映射实体
     */
    void save(TopicMapping mapping);

    /**
     * 根据话题 ID 查找映射
     * 
     * @param topicId 话题 ID
     * @return 话题映射，如果不存在则返回 Optional.empty()
     */
    Optional<TopicMapping> findByTopicId(String topicId);

    /**
     * 删除话题映射
     * 
     * @param topicId 话题 ID
     */
    void delete(String topicId);
}

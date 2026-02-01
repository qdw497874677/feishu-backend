package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.model.opencode.OpenCodeMetadata;

import java.util.Optional;

/**
 * OpenCode 会话管理 Gateway 接口
 *
 * 定义会话持久化的抽象，基于 TopicMapping.metadata 实现
 */
public interface OpenCodeSessionGateway {

    /**
     * 保存会话 ID
     *
     * @param topicId 话题 ID
     * @param sessionId OpenCode 会话 ID
     */
    void saveSession(String topicId, String sessionId);

    /**
     * 获取会话 ID
     *
     * @param topicId 话题 ID
     * @return 会话 ID，如果不存在返回 Optional.empty()
     */
    Optional<String> getSessionId(String topicId);

    /**
     * 更新会话 ID
     *
     * @param topicId 话题 ID
     * @param sessionId 新的会话 ID
     */
    void updateSession(String topicId, String sessionId);

    /**
     * 删除会话
     *
     * @param topicId 话题 ID
     */
    void deleteSession(String topicId);

    /**
     * 清除会话（创建新会话时使用）
     *
     * @param topicId 话题 ID
     */
    void clearSession(String topicId);

    /**
     * 获取完整的元数据
     *
     * @param topicId 话题 ID
     * @return 元数据对象
     */
    Optional<OpenCodeMetadata> getMetadata(String topicId);

    /**
     * 保存完整元数据
     *
     * @param topicId 话题 ID
     * @param metadata 元数据对象
     */
    void saveMetadata(String topicId, OpenCodeMetadata metadata);
}

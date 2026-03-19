package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.model.SessionContext;

import java.util.Optional;

/**
 * 会话上下文 Gateway 接口
 * 
 * 定义会话上下文数据持久化的抽象，符合 COLA 的 Gateway 模式
 * 将外部依赖隔离在领域层
 */
public interface SessionContextGateway {

    /**
     * 保存会话上下文
     * 
     * @param context 会话上下文实体
     */
    void save(SessionContext context);

    /**
     * 根据话题 ID 查找会话上下文
     * 
     * @param topicId 话题 ID
     * @return 会话上下文，如果不存在则返回 Optional.empty()
     */
    Optional<SessionContext> findByTopicId(String topicId);

    /**
     * 删除会话上下文
     * 
     * @param topicId 话题 ID
     */
    void delete(String topicId);
}

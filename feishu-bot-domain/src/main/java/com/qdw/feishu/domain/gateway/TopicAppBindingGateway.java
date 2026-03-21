package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.model.TopicAppBinding;

import java.util.Optional;

/**
 * 话题-应用绑定网关
 *
 * 职责：管理话题与应用的绑定关系（路由层）
 * - 哪个话题属于哪个应用
 * - 用于消息路由，决定消息应由哪个应用处理
 *
 * 与 ImContextBindingGateway 的区别：
 * - TopicAppBindingGateway: 通用路由（topicId → appId）
 * - ImContextBindingGateway: 会话管理（contextRef → sessionId + app session data）
 */
public interface TopicAppBindingGateway {

    /**
     * 保存话题-应用绑定
     *
     * @param binding 绑定信息
     */
    void save(TopicAppBinding binding);

    /**
     * 根据话题 ID 查找绑定
     *
     * @param topicId 话题 ID
     * @return 绑定信息，如果不存在则返回 empty
     */
    Optional<TopicAppBinding> findByTopicId(String topicId);

    /**
     * 删除话题-应用绑定
     *
     * @param topicId 话题 ID
     */
    void delete(String topicId);
}

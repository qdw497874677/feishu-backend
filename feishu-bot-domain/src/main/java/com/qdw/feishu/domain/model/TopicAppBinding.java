package com.qdw.feishu.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 话题-应用绑定领域实体
 *
 * 用于通用的话题级别应用路由：确定哪个应用应该处理该话题的消息
 * 与 ImContextBinding 的区别：
 * - TopicAppBinding: 通用路由，只存储 topicId → appId 的映射
 * - ImContextBinding: 会话绑定，存储 contextRef → (appId, sessionId) 的完整映射
 *
 * 每个话题只能绑定到一个应用
 */
@Data
@NoArgsConstructor
public class TopicAppBinding {

    /** 话题 ID */
    private String topicId;

    /** 当前绑定的应用 ID */
    private String appId;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;

    /** 最后活跃时间（毫秒时间戳） */
    private long lastActiveAt;

    /** 元数据（JSON 字符串，用于扩展） */
    private String metadata;

    /**
     * 创建话题-应用绑定（不含元数据）
     *
     * @param topicId 话题 ID
     * @param appId 应用 ID
     */
    public TopicAppBinding(String topicId, String appId) {
        this(topicId, appId, null);
    }

    /**
     * 创建话题-应用绑定（含元数据）
     *
     * @param topicId 话题 ID
     * @param appId 应用 ID
     * @param metadata 元数据（JSON 字符串）
     */
    public TopicAppBinding(String topicId, String appId, String metadata) {
        this.topicId = topicId;
        this.appId = appId;
        this.metadata = metadata;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = this.createdAt;
    }

    /**
     * 激活话题（更新最后活跃时间）
     */
    public void activate() {
        this.lastActiveAt = System.currentTimeMillis();
    }
}

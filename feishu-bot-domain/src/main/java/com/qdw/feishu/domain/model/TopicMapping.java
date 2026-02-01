package com.qdw.feishu.domain.model;

import com.alibaba.cola.exception.BizException;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 话题映射领域实体
 *
 * 封装话题 ID 与应用 ID 的映射关系，用于持久化话题上下文
 */
@Data
@NoArgsConstructor
public class TopicMapping {

    /** 话题 ID */
    private String topicId;

    /** 应用 ID */
    private String appId;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;

    /** 最后活跃时间（毫秒时间戳） */
    private long lastActiveAt;

    /** 元数据（JSON 字符串，存储应用特定的任意数据） */
    private String metadata;

    /**
     * 创建话题映射（不含元数据）
     *
     * @param topicId 话题 ID
     * @param appId 应用 ID
     */
    public TopicMapping(String topicId, String appId) {
        this(topicId, appId, null);
    }

    /**
     * 创建话题映射（含元数据）
     *
     * @param topicId 话题 ID
     * @param appId 应用 ID
     * @param metadata 元数据（JSON 字符串）
     */
    public TopicMapping(String topicId, String appId, String metadata) {
        this.topicId = topicId;
        this.appId = appId;
        this.metadata = metadata;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
        this.validate();
    }

    /**
     * 领域业务逻辑：验证话题映射
     */
    public void validate() {
        if (topicId == null || topicId.trim().isEmpty()) {
            throw new BizException("TOPIC_ID_EMPTY", "话题 ID 不能为空");
        }
        if (appId == null || appId.trim().isEmpty()) {
            throw new BizException("APP_ID_EMPTY", "应用 ID 不能为空");
        }
    }

    /**
     * 领域业务逻辑：激活话题映射
     *
     * 更新最后活跃时间为当前时间
     */
    public void activate() {
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * 检查是否有元数据
     *
     * @return 如果 metadata 不为空且不为空字符串，返回 true
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}

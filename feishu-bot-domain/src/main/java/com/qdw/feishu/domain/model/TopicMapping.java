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

    /**
     * 创建话题映射
     *
     * @param topicId 话题 ID
     * @param appId 应用 ID
     */
    public TopicMapping(String topicId, String appId) {
        this.topicId = topicId;
        this.appId = appId;
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
}

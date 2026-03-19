package com.qdw.feishu.domain.session;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话基础信息（不含泛型 data，用于列表查询）
 */
@Data
@NoArgsConstructor
public class AppSessionInfo {
    
    /** 会话唯一标识 */
    private String sessionId;
    
    /** 所属应用 */
    private String appId;
    
    /** 绑定的话题 */
    private String topicId;
    
    /** 会话状态 */
    private SessionState state;
    
    /** 创建时间 */
    private long createdAt;
    
    /** 最后活跃时间 */
    private long lastActiveAt;
    
    /** 过期时间（可选） */
    private Long expiresAt;
    
    /** 乐观锁版本号 */
    private long version;
    
    public AppSessionInfo(String sessionId, String appId, String topicId) {
        this.sessionId = sessionId;
        this.appId = appId;
        this.topicId = topicId;
        this.state = SessionState.CREATED;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
        this.version = 1;
    }
    
    /**
     * 更新活跃时间
     */
    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
    }
    
    /**
     * 递增版本号
     */
    public void incrementVersion() {
        this.version++;
    }
    
    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return System.currentTimeMillis() > expiresAt;
    }
    
    /**
     * 检查状态转换是否合法
     */
    public boolean canTransitionTo(SessionState target) {
        return state != null && state.canTransitionTo(target);
    }
}

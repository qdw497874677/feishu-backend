package com.qdw.feishu.domain.session;

/**
 * 会话状态枚举
 */
public enum SessionState {
    
    /** 已创建，未激活 */
    CREATED,
    
    /** 活跃中 */
    ACTIVE,
    
    /** 空闲（可恢复） */
    IDLE,
    
    /** 已过期 */
    EXPIRED,
    
    /** 已终止（不可恢复） */
    TERMINATED;
    
    /**
     * 检查是否可以转换到目标状态
     */
    public boolean canTransitionTo(SessionState target) {
        return switch (this) {
            case CREATED -> target == ACTIVE || target == TERMINATED || target == EXPIRED;
            case ACTIVE -> target == IDLE || target == TERMINATED || target == EXPIRED;
            case IDLE -> target == ACTIVE || target == TERMINATED || target == EXPIRED;
            case EXPIRED -> target == TERMINATED;
            case TERMINATED -> false;
        };
    }
    
    /**
     * 判断是否为活跃状态
     */
    public boolean isActive() {
        return this == ACTIVE;
    }
    
    /**
     * 判断是否可恢复
     */
    public boolean isRecoverable() {
        return this == IDLE || this == ACTIVE;
    }
    
    /**
     * 判断是否已结束
     */
    public boolean isFinished() {
        return this == TERMINATED || this == EXPIRED;
    }
}

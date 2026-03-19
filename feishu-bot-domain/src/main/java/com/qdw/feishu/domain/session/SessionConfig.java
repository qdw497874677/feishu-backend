package com.qdw.feishu.domain.session;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * 会话配置
 */
@Data
@Builder
public class SessionConfig {
    
    /**
     * 会话超时时间（毫秒），从 lastActiveAt 计算
     * 默认 30 分钟
     */
    @Builder.Default
    private long timeoutMs = 30 * 60 * 1000L;
    
    /**
     * 最大会话数（历史会话保留上限）
     * 默认 10 个
     */
    @Builder.Default
    private int maxSessions = 10;
    
    /**
     * 是否自动清理过期会话
     * 默认开启
     */
    @Builder.Default
    private boolean autoCleanup = true;
    
    /**
     * 清理间隔（毫秒）
     * 默认 1 小时
     */
    @Builder.Default
    private long cleanupIntervalMs = 60 * 60 * 1000L;
    
    /**
     * 自定义状态（如 PLAYING, PAUSED）
     */
    @Builder.Default
    private Set<String> customStates = Collections.emptySet();
    
    /**
     * 默认配置
     */
    public static SessionConfig defaultConfig() {
        return SessionConfig.builder().build();
    }
}

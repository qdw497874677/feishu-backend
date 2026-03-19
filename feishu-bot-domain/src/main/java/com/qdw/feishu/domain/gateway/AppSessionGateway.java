package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.AppSessionInfo;
import com.qdw.feishu.domain.session.SessionState;
import com.qdw.feishu.domain.session.TypeToken;

import java.util.List;
import java.util.Optional;

/**
 * 通用会话管理 Gateway 接口
 * 
 * 使用 TypeToken<T> 解决泛型类型擦除问题：
 * - 避免不同 T 类型的 Bean 注册冲突
 * - 运行时保留泛型类型信息
 * - 支持类型安全的序列化/反序列化
 */
public interface AppSessionGateway {

    // ========== 会话创建 ==========
    
    /**
     * 创建新会话（自动生成 sessionId）
     * @return 新会话的 sessionId
     */
    <T> String createSession(String appId, String topicId, T data, TypeToken<T> typeToken);
    
    /**
     * 使用自定义 sessionId 创建会话
     */
    <T> String createSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken);

    // ========== 会话查询 ==========
    
    /**
     * 获取当前活跃会话
     */
    <T> Optional<AppSession<T>> getActiveSession(String appId, String topicId, TypeToken<T> typeToken);
    
    /**
     * 获取指定会话
     */
    <T> Optional<AppSession<T>> getSession(String appId, String topicId, String sessionId, TypeToken<T> typeToken);
    
    /**
     * 获取应用在某话题下的所有会话（仅返回基础信息，不反序列化 data）
     */
    List<AppSessionInfo> listSessions(String appId, String topicId);
    
    /**
     * 获取应用在某话题下的活跃会话数量
     */
    int countActiveSessions(String appId, String topicId);

    // ========== 会话更新 ==========
    
    /**
     * 更新会话数据（带乐观锁）
     * @throws OptimisticLockException 当版本冲突时抛出
     */
    <T> void updateSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken, long version);
    
    /**
     * 更新会话状态（带乐观锁）
     */
    void updateState(String appId, String topicId, String sessionId, SessionState state, long version);
    
    /**
     * 设置活跃会话（切换当前会话）
     */
    void setActiveSession(String appId, String topicId, String sessionId);
    
    /**
     * 激活会话（IDLE → ACTIVE）
     */
    void activateSession(String appId, String topicId, String sessionId);
    
    /**
     * 休眠会话（ACTIVE → IDLE）
     */
    void idleSession(String appId, String topicId, String sessionId);

    // ========== 会话删除 ==========
    
    /**
     * 删除指定会话
     */
    void deleteSession(String appId, String topicId, String sessionId);
    
    /**
     * 终止会话（任意状态 → TERMINATED）
     */
    void terminateSession(String appId, String topicId, String sessionId);
    
    /**
     * 清除所有已过期/已终止的会话
     * @return 清除的会话数量
     */
    int cleanupSessions(String appId, String topicId);

    // ========== 生命周期钩子（由具体实现类覆盖）==========
    
    /**
     * 会话即将过期的回调
     */
    default void onSessionExpiring(AppSessionInfo session) {}
    
    /**
     * 会话已终止的回调
     */
    default void onSessionTerminated(AppSessionInfo session) {}
    
    /**
     * 会话状态变更的回调
     */
    default void onStateChanged(AppSessionInfo session, SessionState oldState, SessionState newState) {}
}

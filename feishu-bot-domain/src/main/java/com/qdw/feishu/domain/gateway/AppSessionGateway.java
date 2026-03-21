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
 * Phase 2 重构：移除 topicId 参数，实现应用会话与 IM 上下文的解耦。
 * 
 * 使用 TypeToken<T> 解决泛型类型擦除问题：
 * - 避免不同 T 类型的 Bean 注册冲突
 * - 运行时保留泛型类型信息
 * - 支持类型安全的序列化/反序列化
 * 
 * 会话与 IM 上下文的绑定由 ImContextBindingGateway 管理。
 */
public interface AppSessionGateway {

    // ========== 会话创建 ==========
    
    /**
     * 创建新会话（自动生成 sessionId）
     * @param appId 应用 ID
     * @param data 会话数据
     * @param typeToken 类型标记
     * @return 新会话的 sessionId
     */
    <T> String createSession(String appId, T data, TypeToken<T> typeToken);
    
    /**
     * 使用自定义 sessionId 创建会话
     * @param appId 应用 ID
     * @param sessionId 自定义会话 ID
     * @param data 会话数据
     * @param typeToken 类型标记
     * @return 会话 ID
     */
    <T> String createSession(String appId, String sessionId, T data, TypeToken<T> typeToken);

    // ========== 会话查询 ==========
    
    /**
     * 获取指定会话
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     * @param typeToken 类型标记
     * @return 会话对象，不存在返回 Optional.empty()
     */
    <T> Optional<AppSession<T>> getSession(String appId, String sessionId, TypeToken<T> typeToken);
    
    /**
     * 获取应用的所有会话（仅返回基础信息，不反序列化 data）
     * @param appId 应用 ID
     * @return 会话信息列表
     */
    List<AppSessionInfo> listSessions(String appId);
    
    /**
     * 获取应用的活跃会话数量
     * @param appId 应用 ID
     * @return 活跃会话数量
     */
    int countActiveSessions(String appId);

    // ========== 会话更新 ==========
    
    /**
     * 更新会话数据（带乐观锁）
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     * @param data 新的会话数据
     * @param typeToken 类型标记
     * @param version 当前版本号（乐观锁）
     * @throws com.qdw.feishu.domain.exception.OptimisticLockException 当版本冲突时抛出
     */
    <T> void updateSession(String appId, String sessionId, T data, TypeToken<T> typeToken, long version);
    
    /**
     * 更新会话状态（带乐观锁）
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     * @param state 新状态
     * @param version 当前版本号（乐观锁）
     */
    void updateState(String appId, String sessionId, SessionState state, long version);
    
    /**
     * 激活会话（IDLE → ACTIVE）
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     */
    void activateSession(String appId, String sessionId);
    
    /**
     * 休眠会话（ACTIVE → IDLE）
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     */
    void idleSession(String appId, String sessionId);

    // ========== 会话删除 ==========
    
    /**
     * 删除指定会话
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     */
    void deleteSession(String appId, String sessionId);
    
    /**
     * 终止会话（任意状态 → TERMINATED）
     * @param appId 应用 ID
     * @param sessionId 会话 ID
     */
    void terminateSession(String appId, String sessionId);
    
    /**
     * 清除应用的所有已过期/已终止的会话
     * @param appId 应用 ID
     * @return 清除的会话数量
     */
    int cleanupSessions(String appId);

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

package com.qdw.feishu.domain.session;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 完整会话实体（含泛型 data）
 * 
 * Phase 2 重构：移除 topicId，会话与 IM 上下文解耦。
 * 
 * @param <T> 应用特定的会话数据类型
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppSession<T> extends AppSessionInfo {
    
    /** 应用特定数据 */
    private T data;
    
    public AppSession(String sessionId, String appId, T data) {
        super(sessionId, appId);
        this.data = data;
    }
    
    /**
     * 从 AppSessionInfo 创建 AppSession
     */
    public static <T> AppSession<T> fromInfo(AppSessionInfo info, T data) {
        AppSession<T> session = new AppSession<>();
        session.setSessionId(info.getSessionId());
        session.setAppId(info.getAppId());
        session.setState(info.getState());
        session.setCreatedAt(info.getCreatedAt());
        session.setLastActiveAt(info.getLastActiveAt());
        session.setExpiresAt(info.getExpiresAt());
        session.setVersion(info.getVersion());
        session.setData(data);
        return session;
    }
}

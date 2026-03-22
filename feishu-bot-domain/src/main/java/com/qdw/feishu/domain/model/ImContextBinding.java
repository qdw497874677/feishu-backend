package com.qdw.feishu.domain.model;

import lombok.Value;

import java.util.Objects;

/**
 * Mapping from an IM context to an app session.
 * 
 * Represents the binding between an external IM conversation context
 * and an internal application session. This is the source of truth
 * for "which app session is active in this IM context".
 * 
 * Rules:
 * - One IM context can have at most one current binding
 * - Binding can be updated (rebound to different session)
 * - Binding targets an app session by (appId, sessionId)
 * 
 * Session ID Semantics:
 * - sessionId is explicitly nullable and valid as persisted state
 * - null sessionId = entered app context without active session
 * - non-null sessionId = app context with active session
 * - This enables two-phase binding: app context first, then session activation
 */
@Value
public class ImContextBinding {
    
    /** The IM context being bound */
    ImContextRef contextRef;
    
    /** The application ID */
    String appId;
    
    /** 
     * The internal app session ID.
     * Nullable: null means app context is active but no session is bound yet.
     */
    String sessionId;
    
    /** When this binding was created (epoch millis) */
    long createdAt;
    
    /** When this binding was last accessed/updated (epoch millis) */
    long lastActiveAt;
    
    /**
     * Create a new binding with current timestamps.
     * 
     * @param contextRef the IM context
     * @param appId the application ID
     * @param sessionId the session ID
     * @return new ImContextBinding with current timestamps
     */
    public static ImContextBinding create(ImContextRef contextRef, String appId, String sessionId) {
        long now = System.currentTimeMillis();
        return new ImContextBinding(contextRef, appId, sessionId, now, now);
    }
    
    /**
     * Create a binding with updated lastActiveAt timestamp.
     * 
     * @return new binding with updated timestamp
     */
    public ImContextBinding touch() {
        return new ImContextBinding(
            contextRef, appId, sessionId, createdAt, System.currentTimeMillis()
        );
    }
    
    /**
     * Create a binding pointing to a different session.
     * 
     * @param newSessionId the new session ID
     * @return new binding with updated session and timestamp
     */
    public ImContextBinding rebind(String newSessionId) {
        return new ImContextBinding(
            contextRef, appId, newSessionId, createdAt, System.currentTimeMillis()
        );
    }
    
    /**
     * Check if this binding matches the given app and session.
     * 
     * Uses null-safe comparison for sessionId. Returns true only when
     * both appId and sessionId match exactly (including both being null).
     * 
     * @param appId the app ID to check
     * @param sessionId the session ID to check (nullable)
     * @return true if both match
     */
    public boolean matches(String appId, String sessionId) {
        return this.appId.equals(appId) && Objects.equals(this.sessionId, sessionId);
    }
    
    /**
     * Check if this binding is for the given app.
     * 
     * @param appId the app ID to check
     * @return true if app matches
     */
    public boolean isForApp(String appId) {
        return this.appId.equals(appId);
    }
}

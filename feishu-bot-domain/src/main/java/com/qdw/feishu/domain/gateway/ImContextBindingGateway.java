package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;

import java.util.Optional;

/**
 * Gateway for managing IM context to app session bindings.
 * 
 * This gateway owns the relationship between external IM conversation contexts
 * (Feishu topics/chats, Discord channels, etc.) and internal application sessions.
 * 
 * Responsibilities:
 * - bind IM context to app session
 * - find current binding for a context
 * - clear binding
 * - validate app/session consistency
 * 
 * Rules:
 * - one IM context has at most one current binding
 * - bind operation uses upsert semantics (create or update)
 * - if binding exists for same session, no change is made
 */
public interface ImContextBindingGateway {
    
    /**
     * Bind an IM context to an app session.
     * 
     * Upsert semantics:
     * - if context is unbound: create new binding
     * - if context is bound to same (appId, sessionId): no-op, return noChange
     * - if context is bound to different session: atomically replace
     * 
     * @param contextRef the IM context to bind
     * @param appId the application ID
     * @param sessionId the internal app session ID
     * @return BindingResult indicating what happened
     */
    BindingResult bind(ImContextRef contextRef, String appId, String sessionId);
    
    /**
     * Find the current binding for an IM context.
     * 
     * @param contextRef the IM context to look up
     * @return the current binding, or empty if unbound
     */
    Optional<ImContextBinding> findBinding(ImContextRef contextRef);
    
    /**
     * Clear (remove) the binding for an IM context.
     * 
     * If no binding exists, this is a no-op.
     * 
     * @param contextRef the IM context to unbind
     */
    void clearBinding(ImContextRef contextRef);
    
    /**
     * Check if an IM context is bound to a specific app.
     * 
     * @param contextRef the IM context to check
     * @param appId the application ID to check
     * @return true if the context is bound to this app
     */
    boolean isBoundToApp(ImContextRef contextRef, String appId);
    
    /**
     * Update the lastActiveAt timestamp for a binding.
     * 
     * This should be called when a bound session is actively used.
     * 
     * @param contextRef the IM context
     */
    void touchBinding(ImContextRef contextRef);
}

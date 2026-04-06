package com.qdw.feishu.app.session;

import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.session.TypeToken;

/**
 * Orchestrator for managing context-session bindings in session-aware applications.
 * 
 * This interface lives in the app layer and coordinates between:
 * - ImContextBindingGateway (domain): for binding persistence
 * - AppSessionGateway (domain): for session existence verification
 * 
 * This orchestrator implements the two-phase binding model:
 * 1. Enter app context: bind context to app with null sessionId
 * 2. Activate session: update binding to include concrete sessionId
 * 
 * It also handles dangling bindings (binding points to non-existent session).
 */
public interface ContextSessionOrchestrator {
    
    /**
     * Load the current status of a context's session binding.
     * 
     * This method:
     * - Checks if a binding exists
     * - If bound to target app with sessionId, verifies session exists
     * - Detects dangling bindings (session missing)
     * 
     * @param contextRef the IM context to check
     * @return the current status with binding and session data (if available)
     */
    <T> ContextSessionStatus<T> loadStatus(ImContextRef contextRef, String appId, TypeToken<T> typeToken);
    
    /**
     * Enter app context without an active session.
     * 
     * Creates a binding with null sessionId, representing phase 1 of two-phase binding.
     * 
     * @param contextRef the IM context to bind
     */
    void enterAppContext(ImContextRef contextRef, String appId);
    
    /**
     * Activate a session for an already-bound context.
     * 
     * Updates the existing binding to include the concrete sessionId.
     * This represents phase 2 of two-phase binding.
     * 
     * @param contextRef the IM context
     * @param sessionId the session ID to activate
     */
    void activateSession(ImContextRef contextRef, String appId, String sessionId);
    
    /**
     * Repair a dangling binding by resetting sessionId to null.
     * 
     * This should be called when loadStatus() returns a dangling status.
     * The binding will be downgraded to app-context-only (null sessionId).
     * 
     * @param contextRef the IM context with dangling binding
     */
    void repairDanglingSessionBinding(ImContextRef contextRef, String appId);
    
    /**
     * Clear the binding for a context.
     * 
     * Removes the binding entirely, returning the context to UNBOUND state.
     * 
     * @param contextRef the IM context to unbind
     */
    void clearContext(ImContextRef contextRef, String appId);
}

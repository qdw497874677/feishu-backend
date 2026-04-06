package com.qdw.feishu.domain.session;

/**
 * Core domain enum representing the state of a context-session binding.
 * 
 * This is a pure domain concept that describes the relationship between
 * an IM context and a session-aware application.
 * 
 * States:
 * - UNBOUND: The IM context has no binding to any application.
 * - BOUND_TO_OTHER_APP: The IM context is bound to a different application.
 * - IN_APP_NO_SESSION: The IM context is bound to the target app but without an active session.
 * - IN_APP_WITH_SESSION: The IM context is bound to the target app with an active session.
 * 
 * Note: This enum lives in the domain layer as a core business concept.
 * The app layer's ContextSessionStatus uses this enum and adds additional data.
 */
public enum ContextSessionState {
    
    /**
     * The IM context has no binding to any application.
     */
    UNBOUND,
    
    /**
     * The IM context is bound to a different application than the target app.
     */
    BOUND_TO_OTHER_APP,
    
    /**
     * The IM context is bound to the target app but without an active session.
     * This represents the first phase of two-phase binding: app context without session.
     */
    IN_APP_NO_SESSION,
    
    /**
     * The IM context is bound to the target app with an active session.
     * This represents the second phase: app context with active session.
     */
    IN_APP_WITH_SESSION
}

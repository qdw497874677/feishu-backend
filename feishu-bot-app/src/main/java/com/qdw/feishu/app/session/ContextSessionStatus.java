package com.qdw.feishu.app.session;

import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.ContextSessionState;
import lombok.Getter;

import java.util.Optional;

/**
 * App-layer status object representing the complete state of a context-session binding.
 * 
 * This is a rich status object used by the app layer to make orchestration decisions.
 * It combines:
 * - The state enum (from domain layer)
 * - The binding (if any)
 * - The session data (if any)
 * - Dangling flag (if binding points to missing session)
 * 
 * @param <T> The type of session data (use Void if no session data expected)
 */
@Getter
public class ContextSessionStatus<T> {
    
    private final ContextSessionState state;
    private final Optional<ImContextBinding> binding;
    private final Optional<AppSession<T>> session;
    private final boolean dangling;
    
    private ContextSessionStatus(
            ContextSessionState state,
            Optional<ImContextBinding> binding,
            Optional<AppSession<T>> session,
            boolean dangling) {
        this.state = state;
        this.binding = binding;
        this.session = session;
        this.dangling = dangling;
    }
    
    /**
     * Create an UNBOUND status.
     */
    public static <T> ContextSessionStatus<T> unbound() {
        return new ContextSessionStatus<>(ContextSessionState.UNBOUND, Optional.empty(), Optional.empty(), false);
    }
    
    /**
     * Create a BOUND_TO_OTHER_APP status.
     */
    public static <T> ContextSessionStatus<T> boundToOtherApp(ImContextBinding binding) {
        return new ContextSessionStatus<>(ContextSessionState.BOUND_TO_OTHER_APP, Optional.of(binding), Optional.empty(), false);
    }
    
    /**
     * Create an IN_APP_NO_SESSION status (app context without active session).
     */
    public static <T> ContextSessionStatus<T> inAppNoSession(ImContextBinding binding) {
        return new ContextSessionStatus<>(ContextSessionState.IN_APP_NO_SESSION, Optional.of(binding), Optional.empty(), false);
    }
    
    /**
     * Create an IN_APP_WITH_SESSION status (app context with active session).
     */
    public static <T> ContextSessionStatus<T> inAppWithSession(ImContextBinding binding, AppSession<T> session) {
        return new ContextSessionStatus<>(ContextSessionState.IN_APP_WITH_SESSION, Optional.of(binding), Optional.of(session), false);
    }
    
    /**
     * Create a dangling IN_APP_NO_SESSION status (binding points to non-existent session).
     */
    public static <T> ContextSessionStatus<T> dangling(ImContextBinding binding) {
        return new ContextSessionStatus<>(ContextSessionState.IN_APP_NO_SESSION, Optional.of(binding), Optional.empty(), true);
    }
    
    /**
     * Check if there's an active session.
     */
    public boolean hasActiveSession() {
        return state == ContextSessionState.IN_APP_WITH_SESSION && session.isPresent();
    }
    
    /**
     * Check if the context is bound to the target app (with or without session).
     */
    public boolean isInApp() {
        return state == ContextSessionState.IN_APP_NO_SESSION || state == ContextSessionState.IN_APP_WITH_SESSION;
    }
}

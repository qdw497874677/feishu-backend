package com.qdw.feishu.app.session;

import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Implementation of ContextSessionOrchestrator.
 * 
 * This orchestrator coordinates between binding and session gateways to provide
 * a unified view of context-session state for session-aware applications.
 * 
 * Thread Safety: This class is stateless and thread-safe.
 *
 * This is a lightweight generic orchestrator, not an app-scoped service.
 * Callers must provide the target appId explicitly for each operation.
 */
@Slf4j
@Component
public class ContextSessionOrchestratorImpl implements ContextSessionOrchestrator {

    private final ImContextBindingGateway bindingGateway;
    private final AppSessionGateway sessionGateway;

    /**
     * Create a generic context/session orchestrator.
     *
     * @param bindingGateway the binding gateway
     * @param sessionGateway the session gateway
     */
    public ContextSessionOrchestratorImpl(
            ImContextBindingGateway bindingGateway,
            AppSessionGateway sessionGateway) {
        this.bindingGateway = bindingGateway;
        this.sessionGateway = sessionGateway;
    }

    @Override
    public <T> ContextSessionStatus<T> loadStatus(ImContextRef contextRef, String appId, TypeToken<T> typeToken) {
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef);
        
        // Case 1: No binding exists
        if (bindingOpt.isEmpty()) {
            return ContextSessionStatus.unbound();
        }
        
        ImContextBinding binding = bindingOpt.get();
        
        // Case 2: Bound to a different app
        if (!binding.isForApp(appId)) {
            return ContextSessionStatus.boundToOtherApp(binding);
        }
        
        // Case 3: Bound to target app with null sessionId (phase 1)
        if (binding.getSessionId() == null) {
            return ContextSessionStatus.inAppNoSession(binding);
        }
        
        // Case 4: Bound to target app with sessionId - verify session exists
        String sessionId = binding.getSessionId();
        Optional<AppSession<T>> sessionOpt = sessionGateway.getSession(
                appId,
                sessionId, 
                typeToken
        );
        
        if (sessionOpt.isPresent()) {
            return ContextSessionStatus.inAppWithSession(binding, sessionOpt.get());
        } else {
            log.warn("Dangling binding detected: context={}, sessionId={} not found", 
                    contextRef.toStorageKey(), sessionId);
            return ContextSessionStatus.dangling(binding);
        }
    }

    @Override
    public void enterAppContext(ImContextRef contextRef, String appId) {
        assertWritableForApp(contextRef, appId);
        log.info("Entering app context: context={}, app={}", contextRef.toStorageKey(), appId);
        bindingGateway.bind(contextRef, appId, null);
    }

    @Override
    public void activateSession(ImContextRef contextRef, String appId, String sessionId) {
        assertWritableForApp(contextRef, appId);
        log.info("Activating session: context={}, app={}, sessionId={}", 
                contextRef.toStorageKey(), appId, sessionId);
        bindingGateway.bind(contextRef, appId, sessionId);
    }

    @Override
    public void repairDanglingSessionBinding(ImContextRef contextRef, String appId) {
        assertWritableForApp(contextRef, appId);
        log.info("Repairing dangling binding: context={}, app={}", contextRef.toStorageKey(), appId);
        bindingGateway.bind(contextRef, appId, null);
    }

    @Override
    public void clearContext(ImContextRef contextRef, String appId) {
        assertWritableForApp(contextRef, appId);
        log.info("Clearing context binding: context={}, app={}", contextRef.toStorageKey(), appId);
        bindingGateway.clearBinding(contextRef);
    }

    private void assertWritableForApp(ImContextRef contextRef, String appId) {
        Optional<ImContextBinding> currentBinding = bindingGateway.findBinding(contextRef);
        if (currentBinding.isPresent() && !currentBinding.get().isForApp(appId)) {
            throw new IllegalStateException("Context is already bound to a different app: "
                    + currentBinding.get().getAppId());
        }
    }
}

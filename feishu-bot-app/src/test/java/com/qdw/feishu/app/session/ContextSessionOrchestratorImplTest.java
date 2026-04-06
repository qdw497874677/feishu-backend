package com.qdw.feishu.app.session;

import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.ContextSessionState;
import com.qdw.feishu.domain.session.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextSessionOrchestratorImplTest {

    private static final String APP_ID = "opencode";
    private static final TypeToken<String> STRING_TYPE = new TypeToken<String>() {};

    @Mock
    private ImContextBindingGateway bindingGateway;

    @Mock
    private AppSessionGateway appSessionGateway;

    private ContextSessionOrchestrator orchestrator;
    private ImContextRef contextRef;

    @BeforeEach
    void setUp() {
        orchestrator = new ContextSessionOrchestratorImpl(bindingGateway, appSessionGateway);
        contextRef = ImContextRef.feishuThread("omt_123");
    }

    @Test
    void should_returnUnbound_when_noBindingExists() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        ContextSessionStatus<String> status = orchestrator.loadStatus(contextRef, APP_ID, STRING_TYPE);

        assertEquals(ContextSessionState.UNBOUND, status.getState());
        assertTrue(status.getBinding().isEmpty());
        assertTrue(status.getSession().isEmpty());
        assertFalse(status.isDangling());
        verify(appSessionGateway, never()).getSession(any(), any(), any());
    }

    @Test
    void should_returnInAppNoSession_when_bindingHasNullSessionId() {
        ImContextBinding binding = ImContextBinding.create(contextRef, APP_ID, null);
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));

        ContextSessionStatus<String> status = orchestrator.loadStatus(contextRef, APP_ID, STRING_TYPE);

        assertEquals(ContextSessionState.IN_APP_NO_SESSION, status.getState());
        assertEquals(Optional.of(binding), status.getBinding());
        assertTrue(status.getSession().isEmpty());
        assertFalse(status.isDangling());
        verify(appSessionGateway, never()).getSession(any(), any(), any());
    }

    @Test
    void should_returnInAppWithSession_when_bindingAndSessionExist() {
        ImContextBinding binding = ImContextBinding.create(contextRef, APP_ID, "ses_123");
        AppSession<String> session = new AppSession<>("ses_123", APP_ID, "payload");

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq(APP_ID), eq("ses_123"), eq(STRING_TYPE)))
                .thenReturn(Optional.of(session));

        ContextSessionStatus<String> status = orchestrator.loadStatus(contextRef, APP_ID, STRING_TYPE);

        assertEquals(ContextSessionState.IN_APP_WITH_SESSION, status.getState());
        assertEquals(Optional.of(binding), status.getBinding());
        assertEquals(Optional.of(session), status.getSession());
        assertFalse(status.isDangling());
        assertTrue(status.hasActiveSession());
    }

    @Test
    void should_markDanglingBinding_when_bindingSessionMissing() {
        ImContextBinding binding = ImContextBinding.create(contextRef, APP_ID, "ses_missing");

        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));
        when(appSessionGateway.getSession(eq(APP_ID), eq("ses_missing"), eq(STRING_TYPE)))
                .thenReturn(Optional.empty());

        ContextSessionStatus<String> status = orchestrator.loadStatus(contextRef, APP_ID, STRING_TYPE);

        assertEquals(ContextSessionState.IN_APP_NO_SESSION, status.getState());
        assertEquals(Optional.of(binding), status.getBinding());
        assertTrue(status.getSession().isEmpty());
        assertTrue(status.isDangling());
    }

    @Test
    void should_returnBoundToOtherApp_when_contextBoundToDifferentApp() {
        ImContextBinding binding = ImContextBinding.create(contextRef, "bash", null);
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(binding));

        ContextSessionStatus<String> status = orchestrator.loadStatus(contextRef, APP_ID, STRING_TYPE);

        assertEquals(ContextSessionState.BOUND_TO_OTHER_APP, status.getState());
        assertEquals(Optional.of(binding), status.getBinding());
        assertTrue(status.getSession().isEmpty());
        assertFalse(status.isDangling());
        verify(appSessionGateway, never()).getSession(any(), any(), any());
    }

    @Test
    void should_repairDanglingBinding_toNullSession_when_requested() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, APP_ID, "ses_missing")));

        orchestrator.repairDanglingSessionBinding(contextRef, APP_ID);

        verify(bindingGateway).bind(contextRef, APP_ID, null);
        verify(appSessionGateway, never()).getSession(any(), any(), any());
    }

    @Test
    void should_enterAppContext_withNullSessionBinding() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.empty());

        orchestrator.enterAppContext(contextRef, APP_ID);

        verify(bindingGateway).bind(contextRef, APP_ID, null);
    }

    @Test
    void should_activateSession_withConcreteBinding() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, APP_ID, null)));

        orchestrator.activateSession(contextRef, APP_ID, "ses_123");

        verify(bindingGateway).bind(contextRef, APP_ID, "ses_123");
    }

    @Test
    void should_clearContext_binding() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, APP_ID, null)));

        orchestrator.clearContext(contextRef, APP_ID);

        verify(bindingGateway).clearBinding(contextRef);
    }

    @Test
    void should_rejectEnterAppContext_when_contextBelongsToOtherApp() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, "bash", null)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orchestrator.enterAppContext(contextRef, APP_ID));

        assertTrue(exception.getMessage().contains("different app"));
        verify(bindingGateway, never()).bind(contextRef, APP_ID, null);
    }

    @Test
    void should_rejectActivateSession_when_contextBelongsToOtherApp() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, "bash", null)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orchestrator.activateSession(contextRef, APP_ID, "ses_123"));

        assertTrue(exception.getMessage().contains("different app"));
        verify(bindingGateway, never()).bind(contextRef, APP_ID, "ses_123");
    }

    @Test
    void should_rejectRepair_when_contextBelongsToOtherApp() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, "bash", "ses_999")));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orchestrator.repairDanglingSessionBinding(contextRef, APP_ID));

        assertTrue(exception.getMessage().contains("different app"));
        verify(bindingGateway, never()).bind(contextRef, APP_ID, null);
    }

    @Test
    void should_rejectClearContext_when_contextBelongsToOtherApp() {
        when(bindingGateway.findBinding(contextRef)).thenReturn(Optional.of(ImContextBinding.create(contextRef, "bash", null)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> orchestrator.clearContext(contextRef, APP_ID));

        assertTrue(exception.getMessage().contains("different app"));
        verify(bindingGateway, never()).clearBinding(contextRef);
    }
}

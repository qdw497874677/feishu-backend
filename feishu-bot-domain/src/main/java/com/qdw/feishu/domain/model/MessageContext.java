package com.qdw.feishu.domain.model;

import java.util.Optional;

/**
 * Request-scoped context resolved once per incoming message.
 *
 * Encapsulates the IM context reference and its optional binding,
 * eliminating redundant findBinding() calls during message processing.
 *
 * <p>Lifecycle: created by {@code MessageContextResolver} at pipeline entry,
 * threaded through the full chain (listener → app service → domain service → app → handler).
 *
 * <p>Design decision: No {@code AppSession} field. App-specific session data
 * is loaded on-demand by {@code OpenCodeSessionManager} using {@code binding.getSessionId()}.
 * This keeps MessageContext simple and reusable for non-session-aware apps.
 */
public class MessageContext {

    private final ImContextRef contextRef;
    private final ImContextBinding binding;

    private MessageContext(ImContextRef contextRef, ImContextBinding binding) {
        this.contextRef = contextRef;
        this.binding = binding;
    }

    /**
     * Create a resolved context with binding info.
     *
     * @param ref     the IM context reference (non-null)
     * @param binding the binding, or null if unbound
     * @return resolved MessageContext
     */
    public static MessageContext of(ImContextRef ref, ImContextBinding binding) {
        if (ref == null) {
            throw new IllegalArgumentException("ImContextRef cannot be null; use unresolved() for missing context");
        }
        return new MessageContext(ref, binding);
    }

    /**
     * Create an unresolved context for messages without chatId/topicId
     * (e.g., card events).
     *
     * @return unresolved MessageContext with null contextRef and binding
     */
    public static MessageContext unresolved() {
        return new MessageContext(null, null);
    }

    /** @return true if contextRef is a thread/topic context */
    public boolean isThreadContext() {
        return contextRef != null && contextRef.isThread();
    }

    /** @return true if contextRef is a chat context */
    public boolean isChatContext() {
        return contextRef != null && contextRef.isChat();
    }

    /** @return true if a binding exists */
    public boolean isBound() {
        return binding != null;
    }

    /** @return true if bound to the given app */
    public boolean isBoundToApp(String appId) {
        return binding != null && binding.isForApp(appId);
    }

    /** @return the bound app ID, if any */
    public Optional<String> getBoundAppId() {
        return Optional.ofNullable(binding).map(ImContextBinding::getAppId);
    }

    /**
     * @return the internal app session ID from the binding, if present.
     *         This is the internal UUID, NOT the external OpenCode session ID.
     */
    public Optional<String> getBoundSessionId() {
        return Optional.ofNullable(binding)
                .map(ImContextBinding::getSessionId);
    }

    /** @return the IM context reference, or null if unresolved */
    public ImContextRef getContextRef() {
        return contextRef;
    }

    /** @return the binding, or null if unbound */
    public ImContextBinding getBinding() {
        return binding;
    }

    /** @return true if context was resolved (has a contextRef) */
    public boolean isResolved() {
        return contextRef != null;
    }
}

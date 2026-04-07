package com.qdw.feishu.app.context;

import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves MessageContext once per incoming message at pipeline entry.
 *
 * This is the single point where {@code findBinding()} is called for the
 * normal routing path. Encapsulating resolution avoids injecting
 * {@code ImContextBindingGateway} directly into listeners.
 *
 * The {@code unresolved()} fallback handles edge cases where context
 * cannot be determined (e.g., card events missing chatId).
 */
@Slf4j
@Component
public class MessageContextResolver {

    private final ImContextBindingGateway bindingGateway;

    public MessageContextResolver(ImContextBindingGateway bindingGateway) {
        this.bindingGateway = bindingGateway;
    }

    /**
     * Resolve MessageContext from an incoming Feishu message.
     *
     * @param message the incoming message
     * @return resolved MessageContext (never null)
     */
    public MessageContext resolve(Message message) {
        try {
            ImContextRef contextRef = FeishuContextResolver.resolve(message);
            Optional<ImContextBinding> binding = bindingGateway.findBinding(contextRef);
            return MessageContext.of(contextRef, binding.orElse(null));
        } catch (IllegalArgumentException e) {
            // Card events or messages without chatId/topicId
            log.debug("Cannot resolve IM context, returning unresolved: {}", e.getMessage());
            return MessageContext.unresolved();
        }
    }
}

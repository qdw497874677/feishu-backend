package com.qdw.feishu.infrastructure.adapter;

import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class CardCommandAdapter implements CommandAdapter {
    
    @Override
    @SuppressWarnings("unchecked")
    public UnifiedCommand adapt(Object event) {
        Map<String, Object> cardEvent = (Map<String, Object>) event;
        
        String appId = extractAppId(cardEvent);
        String action = extractAction(cardEvent);
        String[] parts = parseAction(action);
        
        UnifiedCommand.UnifiedCommandBuilder builder = UnifiedCommand.builder()
            .appId(appId)
            .subCommand(parts.length > 0 ? parts[0] : null)
            .args(parts.length > 1 ? extractArgs(parts) : new String[0])
            .source(EventSource.CARD);
        
        extractOpenId(cardEvent).ifPresent(builder::openId);
        extractMessageId(cardEvent).ifPresent(builder::messageId);
        extractCardToken(cardEvent).ifPresent(builder::cardToken);
        
        UnifiedCommand command = builder.build();
        
        log.debug("Adapted card action to command: appId={}, subCommand={}", 
            appId, parts.length > 0 ? parts[0] : null);
        return command;
    }
    
    @Override
    public boolean supports(Object event) {
        if (event instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) event;
            return map.containsKey("action") || map.containsKey("body");
        }
        String className = event.getClass().getSimpleName();
        return className.contains("Card") && className.contains("Action");
    }
    
    @SuppressWarnings("unchecked")
    private String extractAppId(Map<String, Object> event) {
        try {
            Map<String, Object> body = (Map<String, Object>) event.get("body");
            if (body != null) {
                Map<String, Object> action = (Map<String, Object>) body.get("action");
                if (action != null) {
                    Map<String, Object> extraMap = (Map<String, Object>) action.get("extraMap");
                    if (extraMap != null && extraMap.get("app_id") != null) {
                        return extraMap.get("app_id").toString();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract appId from card event: {}", e.getMessage());
        }
        return "opencode";
    }
    
    @SuppressWarnings("unchecked")
    private String extractAction(Map<String, Object> event) {
        try {
            Map<String, Object> body = (Map<String, Object>) event.get("body");
            if (body != null) {
                Map<String, Object> action = (Map<String, Object>) body.get("action");
                if (action != null && action.get("value") != null) {
                    return action.get("value").toString();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract action from card event: {}", e.getMessage());
        }
        return "";
    }
    
    @SuppressWarnings("unchecked")
    private java.util.Optional<String> extractOpenId(Map<String, Object> event) {
        try {
            Map<String, Object> body = (Map<String, Object>) event.get("body");
            if (body != null) {
                Map<String, Object> operator = (Map<String, Object>) body.get("operator");
                if (operator != null && operator.get("openId") != null) {
                    return java.util.Optional.of(operator.get("openId").toString());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract openId from card event: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }
    
    @SuppressWarnings("unchecked")
    private java.util.Optional<String> extractMessageId(Map<String, Object> event) {
        try {
            Map<String, Object> body = (Map<String, Object>) event.get("body");
            if (body != null && body.get("messageId") != null) {
                return java.util.Optional.of(body.get("messageId").toString());
            }
        } catch (Exception e) {
            log.debug("Failed to extract messageId from card event: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }
    
    @SuppressWarnings("unchecked")
    private java.util.Optional<String> extractCardToken(Map<String, Object> event) {
        try {
            Map<String, Object> body = (Map<String, Object>) event.get("body");
            if (body != null && body.get("token") != null) {
                return java.util.Optional.of(body.get("token").toString());
            }
        } catch (Exception e) {
            log.debug("Failed to extract token from card event: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }
    
    private String[] parseAction(String action) {
        if (action == null || action.isEmpty()) {
            return new String[0];
        }
        return action.split(":");
    }
    
    private String[] extractArgs(String[] parts) {
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return args;
    }
}

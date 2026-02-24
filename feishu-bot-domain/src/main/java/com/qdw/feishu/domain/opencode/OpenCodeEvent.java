package com.qdw.feishu.domain.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * OpenCode SSE 事件
 *
 * 封装从 OpenCode SSE 端点接收到的事件
 */
@Data
@Builder
public class OpenCodeEvent {

    private String type;

    private JsonNode properties;

    public String getSessionId() {
        if (properties == null) return null;
        
        if (properties.has("sessionID")) {
            return properties.get("sessionID").asText();
        }
        if (properties.has("part") && properties.get("part").has("sessionID")) {
            return properties.get("part").get("sessionID").asText();
        }
        return null;
    }

    public String getDelta() {
        if (properties != null && properties.has("delta")) {
            return properties.get("delta").asText();
        }
        return null;
    }

    public String getText() {
        if (properties != null && properties.has("part") && properties.get("part").has("text")) {
            return properties.get("part").get("text").asText();
        }
        return null;
    }

    public String getStatus() {
        if (properties != null && properties.has("status") && properties.get("status").has("type")) {
            return properties.get("status").get("type").asText();
        }
        return null;
    }

    public boolean isSessionIdle() {
        return "idle".equals(getStatus());
    }

    public boolean isSessionBusy() {
        return "busy".equals(getStatus());
    }

    public boolean isTextUpdate() {
        return "message.part.updated".equals(type);
    }

    public boolean isStatusUpdate() {
        return "session.status".equals(type);
    }

    public static OpenCodeEvent of(String type, JsonNode properties) {
        return OpenCodeEvent.builder()
                .type(type)
                .properties(properties)
                .build();
    }
}

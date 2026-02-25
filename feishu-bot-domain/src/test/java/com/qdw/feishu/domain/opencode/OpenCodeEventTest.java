package com.qdw.feishu.domain.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenCodeEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_extractSessionId_fromProperties() throws Exception {
        String json = "{\"sessionID\": \"ses_123\"}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("session.status")
                .properties(properties)
                .build();

        assertEquals("ses_123", event.getSessionId());
    }

    @Test
    void should_extractSessionId_fromPart() throws Exception {
        String json = "{\"part\": {\"sessionID\": \"ses_456\"}}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("message.part.updated")
                .properties(properties)
                .build();

        assertEquals("ses_456", event.getSessionId());
    }

    @Test
    void should_extractDelta() throws Exception {
        String json = "{\"delta\": \"新增文本\"}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("message.part.updated")
                .properties(properties)
                .build();

        assertEquals("新增文本", event.getDelta());
    }

    @Test
    void should_extractStatus() throws Exception {
        String json = "{\"status\": {\"type\": \"idle\"}}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("session.status")
                .properties(properties)
                .build();

        assertEquals("idle", event.getStatus());
        assertTrue(event.isSessionIdle());
    }

    @Test
    void should_detectTextUpdate() {
        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("message.part.updated")
                .build();

        assertTrue(event.isTextUpdate());
        assertFalse(event.isStatusUpdate());
    }

    @Test
    void should_detectStatusUpdate() {
        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("session.status")
                .build();

        assertTrue(event.isStatusUpdate());
        assertFalse(event.isTextUpdate());
    }

    @Test
    void should_returnNull_when_sessionIdNotPresent() {
        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("server.heartbeat")
                .build();

        assertNull(event.getSessionId());
    }
}

package com.qdw.feishu.domain.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardActionContext 序列化/反序列化测试
 */
class CardActionContextTest {

    @Test
    @DisplayName("toValueMap 包含 action、chatId、topicId、sessionId")
    void should_embedContextInValueMap() {
        CardActionContext context = CardActionContext.builder()
            .chatId("chat_123")
            .topicId("topic_456")
            .sessionId("ses_789")
            .build();

        Map<String, Object> map = context.toValueMap("test_action");

        assertEquals("test_action", map.get("action"));
        assertEquals("chat_123", map.get("chatId"));
        assertEquals("topic_456", map.get("topicId"));
        assertEquals("ses_789", map.get("sessionId"));
        assertEquals(4, map.size());
    }

    @Test
    @DisplayName("fromValueMap 正确还原 chatId/topicId/sessionId")
    void should_extractContextFromValueMap() {
        Map<String, Object> map = Map.of(
            "action", "wizard_select_project:feishu-backend",
            "chatId", "chat_abc",
            "topicId", "topic_def",
            "sessionId", "ses_ghi"
        );

        CardActionContext context = CardActionContext.fromValueMap(map);

        assertEquals("chat_abc", context.getChatId());
        assertEquals("topic_def", context.getTopicId());
        assertEquals("ses_ghi", context.getSessionId());
    }

    @Test
    @DisplayName("extractAction 正确提取 action 字符串")
    void should_extractActionFromValueMap() {
        Map<String, Object> map = Map.of(
            "action", "wizard_confirm",
            "chatId", "chat_123"
        );

        String action = CardActionContext.extractAction(map);
        assertEquals("wizard_confirm", action);
    }

    @Test
    @DisplayName("null 字段不嵌入 value map")
    void should_handleNullFields() {
        CardActionContext context = CardActionContext.builder()
            .chatId("chat_123")
            .build();

        Map<String, Object> map = context.toValueMap("test_action");

        assertEquals("test_action", map.get("action"));
        assertEquals("chat_123", map.get("chatId"));
        assertFalse(map.containsKey("topicId"), "topicId should not be in map when null");
        assertFalse(map.containsKey("sessionId"), "sessionId should not be in map when null");
        assertEquals(2, map.size());
    }

    @Test
    @DisplayName("只有 action 的旧格式 value map 返回 null 字段")
    void should_handleOldFormatValueMap() {
        Map<String, Object> map = Map.of("action", "help");

        CardActionContext context = CardActionContext.fromValueMap(map);

        assertNull(context.getChatId());
        assertNull(context.getTopicId());
        assertNull(context.getSessionId());
    }

    @Test
    @DisplayName("extractAction 处理 null map")
    void should_handleNullValueMap_extractAction() {
        assertNull(CardActionContext.extractAction(null));
    }

    @Test
    @DisplayName("fromValueMap 处理 null map")
    void should_handleNullValueMap_fromValueMap() {
        CardActionContext context = CardActionContext.fromValueMap(null);
        assertNull(context.getChatId());
        assertNull(context.getTopicId());
        assertNull(context.getSessionId());
    }
}

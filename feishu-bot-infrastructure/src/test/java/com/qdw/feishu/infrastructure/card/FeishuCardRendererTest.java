package com.qdw.feishu.infrastructure.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.card.*;
import com.qdw.feishu.domain.gateway.CardRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FeishuCardRenderer 测试 — 验证 schema 2.0 JSON 输出
 */
class FeishuCardRendererTest {

    private CardRenderer renderer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        renderer = new FeishuCardRenderer(objectMapper);
    }

    @Test
    @DisplayName("render 生成合法 schema 2.0 JSON，包含 header 和 markdown")
    @SuppressWarnings("unchecked")
    void should_renderValidSchema20Json() throws Exception {
        CardContent card = CardContent.builder()
            .headerTitle("测试标题")
            .headerTemplate("blue")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("测试内容"))
            .build();

        String json = renderer.render(card, null);
        assertNotNull(json);

        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        assertEquals("2.0", parsed.get("schema"));

        Map<String, Object> config = (Map<String, Object>) parsed.get("config");
        assertEquals(true, config.get("wide_screen_mode"));

        Map<String, Object> header = (Map<String, Object>) parsed.get("header");
        Map<String, Object> title = (Map<String, Object>) header.get("title");
        assertEquals("测试标题", title.get("content"));
        assertEquals("plain_text", title.get("tag"));
        assertEquals("blue", header.get("template"));

        Map<String, Object> body = (Map<String, Object>) parsed.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        assertEquals(1, elements.size());
        assertEquals("markdown", elements.get(0).get("tag"));
        assertEquals("测试内容", elements.get(0).get("content"));
    }

    @Test
    @DisplayName("render 对含按钮的 CardContent 生成 button 元素")
    @SuppressWarnings("unchecked")
    void should_renderButtons_inCardContent() throws Exception {
        CardContent card = CardContent.builder()
            .headerTitle("按钮测试")
            .wideScreenMode(true)
            .addElement(CardElement.buttonGroup(
                CardButton.primary("主按钮", "action1"),
                CardButton.defaults("默认按钮", "action2")
            ))
            .build();

        String json = renderer.render(card, null);
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        Map<String, Object> body = (Map<String, Object>) parsed.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");

        assertEquals(2, elements.size());
        assertEquals("button", elements.get(0).get("tag"));
        assertEquals("primary", elements.get(0).get("type"));
        assertEquals("button", elements.get(1).get("tag"));
        assertEquals("default", elements.get(1).get("type"));

        // 无 context 时，value 只有 action
        Map<String, Object> value0 = (Map<String, Object>) elements.get(0).get("value");
        assertEquals("action1", value0.get("action"));
        assertFalse(value0.containsKey("chatId"));
    }

    @Test
    @DisplayName("render context 嵌入按钮 value")
    @SuppressWarnings("unchecked")
    void should_embedContextInButtonValue() throws Exception {
        CardContent card = CardContent.builder()
            .headerTitle("上下文测试")
            .wideScreenMode(true)
            .addElement(CardElement.buttonGroup(
                CardButton.primary("按钮", "test_action")
            ))
            .build();

        CardActionContext context = CardActionContext.builder()
            .chatId("chat_123")
            .topicId("topic_456")
            .sessionId("ses_789")
            .build();

        String json = renderer.render(card, context);
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        Map<String, Object> body = (Map<String, Object>) parsed.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");

        Map<String, Object> value = (Map<String, Object>) elements.get(0).get("value");
        assertEquals("test_action", value.get("action"));
        assertEquals("chat_123", value.get("chatId"));
        assertEquals("topic_456", value.get("topicId"));
        assertEquals("ses_789", value.get("sessionId"));
    }

    @Test
    @DisplayName("render 含多个按钮组生成正确 layout")
    @SuppressWarnings("unchecked")
    void should_renderMultipleButtonGroups() throws Exception {
        CardContent card = CardContent.builder()
            .headerTitle("多组按钮")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("说明文字"))
            .addElement(CardElement.buttonGroup(
                CardButton.primary("A", "actionA"),
                CardButton.defaults("B", "actionB")
            ))
            .addElement(CardElement.buttonGroup(
                CardButton.primary("C", "actionC")
            ))
            .build();

        String json = renderer.render(card, null);
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        Map<String, Object> body = (Map<String, Object>) parsed.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");

        // 1 markdown + 2 buttons from group1 + 1 button from group2 = 4
        assertEquals(4, elements.size());
        assertEquals("markdown", elements.get(0).get("tag"));
        assertEquals("button", elements.get(1).get("tag"));
        assertEquals("button", elements.get(2).get("tag"));
        assertEquals("button", elements.get(3).get("tag"));
    }

    @Test
    @DisplayName("render null context 时 value 只有 action")
    @SuppressWarnings("unchecked")
    void should_renderOnlyAction_whenNullContext() throws Exception {
        CardContent card = CardContent.builder()
            .headerTitle("无上下文")
            .wideScreenMode(true)
            .addElement(CardElement.buttonGroup(CardButton.primary("btn", "my_action")))
            .build();

        String json = renderer.render(card, null);
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        Map<String, Object> body = (Map<String, Object>) parsed.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");

        Map<String, Object> value = (Map<String, Object>) elements.get(0).get("value");
        assertEquals(1, value.size());
        assertEquals("my_action", value.get("action"));
    }
}

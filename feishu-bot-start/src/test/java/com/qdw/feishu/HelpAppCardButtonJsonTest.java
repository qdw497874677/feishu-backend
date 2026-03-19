package com.qdw.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.app.HelpApp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试 - 验证卡片按钮 JSON 格式
 * 
 * 测试目标：
 * 1. button value 必须是字符串格式（修复 200671 错误）
 * 2. 验证卡片 JSON 结构正确
 */
class HelpAppCardButtonJsonTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("测试 button value 是字符串格式 - 防止 200671 错误")
    void should_useStringValue_forButtonValue() throws Exception {
        // Given: HelpApp 实例
        HelpApp helpApp = new HelpApp();
        
        // When: 调用 buildCardHelpJson 方法（使用反射）
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        // Then: 验证 JSON 不为空
        assertNotNull(cardJson, "Card JSON should not be null");
        assertFalse(cardJson.isEmpty(), "Card JSON should not be empty");

        // 验证 value 不是对象格式 {"message":"xxx"}
        assertFalse(cardJson.contains("\"value\":{\"message\":"), 
            "Button value MUST NOT be object format {\"message\":\"xxx\"}, this causes error 200671");
        
        // 解析 JSON 并验证结构
        Map<String, Object> card = objectMapper.readValue(cardJson, Map.class);
        
        // 验证 schema
        assertEquals("2.0", card.get("schema"), "Schema must be 2.0");
        
        // 验证 header
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertNotNull(header, "Header should not be null");
        assertEquals("blue", header.get("template"), "Header template must be blue");
        
        // 验证 body
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        assertNotNull(body, "Body should not be null");
        
        // 验证 column_set
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        Map<String, Object> columnSet = elements.stream()
            .filter(e -> "column_set".equals(e.get("tag")))
            .findFirst()
            .orElse(null);
        
        assertNotNull(columnSet, "Must contain column_set element");
        
        // 验证每个 button 的 value 是字符串
        List<Map<String, Object>> columns = (List<Map<String, Object>>) columnSet.get("columns");
        for (Map<String, Object> column : columns) {
            List<Map<String, Object>> columnElements = (List<Map<String, Object>>) column.get("elements");
            for (Map<String, Object> element : columnElements) {
                if ("button".equals(element.get("tag"))) {
                    Object value = element.get("value");
                    assertTrue(value instanceof String, 
                        "Button value must be String type, got: " + (value != null ? value.getClass() : "null"));
                    assertNotNull(value, "Button value cannot be null");
                    assertFalse(((String) value).isEmpty(), "Button value cannot be empty");
                    
                    System.out.println("✅ Button value verified: " + value + " (type: String)");
                }
            }
        }
    }

    @Test
    @DisplayName("测试卡片 JSON 包含正确的按钮数量")
    void should_generateCorrectNumberOfButtons() throws Exception {
        // Given: HelpApp 实例
        HelpApp helpApp = new HelpApp();
        
        // When: 生成卡片 JSON
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        // Then: 解析并验证按钮数量
        Map<String, Object> card = objectMapper.readValue(cardJson, Map.class);
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        
        Map<String, Object> columnSet = elements.stream()
            .filter(e -> "column_set".equals(e.get("tag")))
            .findFirst()
            .orElse(null);
        
        List<Map<String, Object>> columns = (List<Map<String, Object>>) columnSet.get("columns");
        
        // 验证至少有 5 个按钮（help, opencode, bash, history, time）
        assertTrue(columns.size() >= 5, 
            "Should have at least 5 buttons (help, opencode, bash, history, time), got: " + columns.size());
        
        System.out.println("✅ Total buttons: " + columns.size());
    }

    @Test
    @DisplayName("测试卡片 JSON 格式 - 打印完整 JSON 用于调试")
    void should_printCardJson_forDebugging() throws Exception {
        // Given: HelpApp 实例
        HelpApp helpApp = new HelpApp();
        
        // When: 生成卡片 JSON
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        // Then: 打印 JSON 用于调试
        System.out.println("=== Generated Card JSON ===");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsString(objectMapper.readValue(cardJson, Map.class)));
        System.out.println("===========================");
        
        // 验证 JSON 格式正确
        assertDoesNotThrow(() -> objectMapper.readValue(cardJson, Map.class), 
            "Card JSON should be valid JSON");
    }
}

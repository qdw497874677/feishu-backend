package com.qdw.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.app.HelpApp;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 单元测试 - 验证卡片按钮 JSON 格式
 * 
 * 测试目标：
 * 1. button value 必须是字符串格式（修复 200671 错误）
 * 2. 验证卡片 JSON 结构正确
 */
@ExtendWith(MockitoExtension.class)
class HelpAppCardButtonJsonTest {

    @Mock
    private AppRegistry appRegistry;

    @Mock
    private FeishuGateway feishuGateway;

    private ObjectMapper objectMapper = new ObjectMapper();

    private HelpApp helpApp;

    @BeforeEach
    void setUp() {
        helpApp = new HelpApp();
        // 使用反射注入依赖
        ReflectionTestUtils.setField(helpApp, "appRegistry", appRegistry);
        ReflectionTestUtils.setField(helpApp, "feishuGateway", feishuGateway);
        ReflectionTestUtils.setField(helpApp, "objectMapper", objectMapper);

        // Mock appRegistry 返回测试应用列表
        when(appRegistry.getAllApps()).thenReturn(createTestApps());
    }

    private List<FishuAppI> createTestApps() {
        return Arrays.asList(
            createMockApp("help", "帮助信息", "显示所有可用命令", "/help"),
            createMockApp("opencode", "OpenCode助手", "通过飞书控制OpenCode", "/opencode"),
            createMockApp("bash", "Bash命令", "执行安全的bash命令", "/bash"),
            createMockApp("history", "历史记录", "查询bash历史", "/history"),
            createMockApp("time", "时间查询", "查询系统时间", "/time")
        );
    }

    private FishuAppI createMockApp(String appId, String appName, String description, String help) {
        return new FishuAppI() {
            @Override
            public String getAppId() { return appId; }
            @Override
            public String getAppName() { return appName; }
            @Override
            public String getDescription() { return description; }
            @Override
            public String getHelp() { return help; }
            @Override
            public String execute(com.qdw.feishu.domain.message.Message message) { return null; }
            @Override
            public List<String> getAppAliases() { return Arrays.asList(); }
        };
    }

    @Test
    @DisplayName("测试 button value 是 Map 格式包含 action 字段")
    void should_useMapValueWithAction_forButtonValue() throws Exception {
        // Given: HelpApp 实例已在 setUp 中初始化
        
        // When: 调用 buildCardHelpJson 方法（使用反射）
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        // Then: 验证 JSON 不为空
        assertNotNull(cardJson, "Card JSON should not be null");
        assertFalse(cardJson.isEmpty(), "Card JSON should not be empty");
        
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
        
        // 验证 elements 中包含 button
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        assertNotNull(elements, "Elements should not be null");
        
        // 验证每个 button 的 value 是 Map 格式，包含 action 字段
        int buttonCount = 0;
        for (Map<String, Object> element : elements) {
            if ("button".equals(element.get("tag"))) {
                buttonCount++;
                Object value = element.get("value");
                assertTrue(value instanceof Map, 
                    "Button value must be Map type, got: " + (value != null ? value.getClass() : "null"));
                
                Map<String, Object> valueMap = (Map<String, Object>) value;
                assertTrue(valueMap.containsKey("action"), 
                    "Button value must contain 'action' key");
                
                Object action = valueMap.get("action");
                assertTrue(action instanceof String, 
                    "Action value must be String type");
                assertFalse(((String) action).isEmpty(), 
                    "Action value cannot be empty");
                
                System.out.println("✅ Button value verified: " + value + " (type: Map with action)");
            }
        }
        
        assertTrue(buttonCount >= 5, 
            "Should have at least 5 buttons, got: " + buttonCount);
    }

    @Test
    @DisplayName("测试卡片 JSON 包含正确的按钮数量")
    void should_generateCorrectNumberOfButtons() throws Exception {
        // Given: HelpApp 实例已在 setUp 中初始化
        
        // When: 生成卡片 JSON
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        // Then: 解析并验证按钮数量
        Map<String, Object> card = objectMapper.readValue(cardJson, Map.class);
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        
        // 统计 button 元素数量
        long buttonCount = elements.stream()
            .filter(e -> "button".equals(e.get("tag")))
            .count();
        
        // 验证至少有 5 个按钮（help, opencode, bash, history, time）
        assertTrue(buttonCount >= 5, 
            "Should have at least 5 buttons (help, opencode, bash, history, time), got: " + buttonCount);
        
        System.out.println("✅ Total buttons: " + buttonCount);
    }

    @Test
    @DisplayName("测试卡片 JSON 格式 - 打印完整 JSON 用于调试")
    void should_printCardJson_forDebugging() throws Exception {
        // Given: HelpApp 实例已在 setUp 中初始化
        
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

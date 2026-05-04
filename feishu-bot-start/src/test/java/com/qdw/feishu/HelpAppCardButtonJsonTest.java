package com.qdw.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.app.HelpApp;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.card.CardElement;
import com.qdw.feishu.domain.card.CardButton;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.infrastructure.card.FeishuCardRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 单元测试 - 验证卡片按钮 JSON 格式
 *
 * 测试目标：
 * 1. button value 必须是 Map 格式包含 action 字段
 * 2. 验证卡片 JSON 结构正确
 * 3. [金标准] 迁移到 CardRenderer 后结构等价
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
        CardRenderer cardRenderer = new FeishuCardRenderer(objectMapper);
        helpApp = new HelpApp(appRegistry, feishuGateway, cardRenderer);

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
            public AppExecutionResult execute(com.qdw.feishu.domain.message.Message message) { return AppExecutionResult.text(null); }
            @Override
            public List<String> getAppAliases() { return Arrays.asList(); }
        };
    }

    @Test
    @DisplayName("测试 button value 是 Map 格式包含 action 字段")
    @SuppressWarnings("unchecked")
    void should_useMapValueWithAction_forButtonValue() throws Exception {
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        assertNotNull(cardJson, "Card JSON should not be null");
        assertFalse(cardJson.isEmpty(), "Card JSON should not be empty");

        Map<String, Object> card = objectMapper.readValue(cardJson, Map.class);

        assertEquals("2.0", card.get("schema"), "Schema must be 2.0");

        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertNotNull(header, "Header should not be null");
        assertEquals("blue", header.get("template"), "Header template must be blue");

        Map<String, Object> body = (Map<String, Object>) card.get("body");
        assertNotNull(body, "Body should not be null");

        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        assertNotNull(elements, "Elements should not be null");

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
            }
        }

        assertTrue(buttonCount >= 5,
            "Should have at least 5 buttons, got: " + buttonCount);
    }

    @Test
    @DisplayName("测试卡片 JSON 包含正确的按钮数量")
    @SuppressWarnings("unchecked")
    void should_generateCorrectNumberOfButtons() throws Exception {
        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        Map<String, Object> card = objectMapper.readValue(cardJson, Map.class);
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");

        long buttonCount = elements.stream()
            .filter(e -> "button".equals(e.get("tag")))
            .count();

        assertTrue(buttonCount >= 5,
            "Should have at least 5 buttons (help, opencode, bash, history, time), got: " + buttonCount);
    }

    @Test
    @DisplayName("[金标准] 迁移后 JSON 结构等价：header、按钮 action、按钮数量、schema")
    @SuppressWarnings("unchecked")
    void should_matchGoldStandardStructure_afterMigration() throws Exception {
        // 金标准快照：旧实现 buildCardHelpJson() 的结构特征
        // schema = "2.0", header.template = "blue", header.title.content = "🤖 应用菜单"
        // 5 buttons with actions: "help", "opencode projects", "bash help", "history", "time"

        Method buildCardHelpJson = HelpApp.class.getDeclaredMethod("buildCardHelpJson");
        buildCardHelpJson.setAccessible(true);
        String cardJson = (String) buildCardHelpJson.invoke(helpApp);

        Map<String, Object> card = objectMapper.readValue(cardJson, Map.class);

        // 验证 schema 版本
        assertEquals("2.0", card.get("schema"), "Schema version must match gold standard");

        // 验证 header
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        Map<String, Object> title = (Map<String, Object>) header.get("title");
        assertEquals("🤖 应用菜单", title.get("content"), "Header title must match gold standard");
        assertEquals("blue", header.get("template"), "Header template must match gold standard");

        // 验证按钮 action 列表
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");

        List<String> actions = elements.stream()
            .filter(e -> "button".equals(e.get("tag")))
            .map(e -> {
                Map<String, Object> value = (Map<String, Object>) e.get("value");
                return (String) value.get("action");
            })
            .collect(Collectors.toList());

        // 金标准 action 列表（按 createTestApps 顺序）
        List<String> expectedActions = Arrays.asList(
            "help",               // help app -> default action = appId
            "opencode projects",  // opencode -> getDefaultAction returns "opencode projects"
            "bash help",          // bash -> getDefaultAction returns "bash help"
            "history",            // history -> default action = appId
            "time"                // time -> default action = appId
        );

        assertEquals(expectedActions, actions, "Button actions must match gold standard order and values");

        // 验证按钮数量
        assertEquals(5, actions.size(), "Button count must match gold standard (5 apps)");

        // 验证 markdown 提示文本存在
        boolean hasMarkdown = elements.stream().anyMatch(e -> "markdown".equals(e.get("tag")));
        assertTrue(hasMarkdown, "Must contain markdown prompt text");
    }
}

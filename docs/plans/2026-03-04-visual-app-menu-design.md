# 可视化应用菜单设计方案

**创建时间**: 2026-03-04  
**作者**: OpenCode  
**状态**: 设计完成，待实施

---

## 📋 概述

### 核心目标

为现有的命令交互方式提供**可视化卡片按钮**作为补充，提升用户体验。

### 设计原则

- ✅ **交互补充**：卡片按钮是命令输入的补充方式，不是替代
- ✅ **流程不变**：后端处理流程完全相同，只是触发方式不同
- ✅ **最小改动**：只需创建 MenuApp，无需修改现有架构
- ✅ **降级友好**：卡片失败时降级为文本菜单
- ✅ **简单易用**：用户点击按钮即可触发应用

### 核心理念

```
命令交互（原有）:
用户输入: /opencode chat 你好
         ↓
后端处理: 解析 → 路由 → 执行

卡片交互（新增）:
用户点击: [🤖 OpenCode 助手]
         ↓
飞书发送: "opencode"
         ↓
后端处理: 解析 → 路由 → 执行（与命令交互完全相同）
```

**关键点**：卡片按钮点击后，飞书会自动发送一条消息，后端将这条消息解析为命令来执行。

---

## 🏗️ 架构设计

### 系统架构（无变化）

```
┌─────────────────────────────────┐
│  用户交互层                      │
│  - 命令输入（原有）              │
│  - 卡片按钮（新增）              │  ← 唯一的变化
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  消息处理层（完全不变）          │
│  - BotMessageService            │
│  - AppRouter                    │
│  - AppRegistry                  │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  应用执行层（完全不变）          │
│  - OpenCodeApp                  │
│  - BashApp                      │
│  - HelpApp                      │
│  - MenuApp (新增)               │  ← 唯一的新增
└─────────────────────────────────┘
```

### 核心组件

#### MenuApp（应用菜单）

**位置**: `domain/app/MenuApp.java`

**职责**:
- 生成应用菜单（卡片格式优先，文本格式降级）
- 显示所有可用应用及其描述
- 提供按钮供用户点击

**触发方式**:
- 命令: `/menu` 或 `/apps` 或 `/应用`
- 别名: `menu`, `apps`, `应用`

**核心逻辑**:
```java
@Component
public class MenuApp implements FishuAppI {
    
    @Autowired
    private AppRegistry appRegistry;
    
    @Autowired
    private FeishuGateway feishuGateway;
    
    @Autowired
    private CardGateway cardGateway;  // 可选
    
    @Override
    public String execute(Message message) {
        // 1. 尝试发送交互式卡片
        if (trySendInteractiveCard(message)) {
            return null;  // 卡片发送成功，不需要返回文本
        }
        
        // 2. 降级：返回文本菜单
        return generateTextMenu();
    }
    
    private boolean trySendInteractiveCard(Message message) {
        try {
            // 构建卡片 JSON
            String cardJson = buildInteractiveCardJson();
            
            // 发送卡片消息
            feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
            
            log.info("应用菜单卡片发送成功: chatId={}", message.getChatId());
            return true;
            
        } catch (Exception e) {
            log.warn("应用菜单卡片发送失败，降级为文本: error={}", e.getMessage());
            return false;
        }
    }
    
    private String buildInteractiveCardJson() {
        // 构建飞书卡片 JSON
        // 包含所有应用的按钮
        // 每个按钮配置 value.message 字段
        return CardBuilder.create()
            .header("🤖 应用菜单")
            .actionButtons(buildAppButtons())
            .footer("点击按钮选择应用")
            .build();
    }
    
    private List<ActionButton> buildAppButtons() {
        return appRegistry.getAllApps().stream()
            .filter(app -> !app.getAppId().equals("menu"))  // 排除自己
            .map(app -> ActionButton.builder()
                .text(getAppIcon(app) + " " + app.getAppName())
                .type(getButtonType(app))
                .value(Map.of("message", app.getAppId()))  // 点击后发送的消息
                .build())
            .collect(Collectors.toList());
    }
    
    private String generateTextMenu() {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 应用菜单\n\n");
        
        List<FishuAppI> apps = appRegistry.getAllApps().stream()
            .filter(app -> !app.getAppId().equals("menu"))
            .collect(Collectors.toList());
        
        for (int i = 0; i < apps.size(); i++) {
            FishuAppI app = apps.get(i);
            sb.append(String.format("%d. %s %s\n", 
                i + 1, 
                getAppIcon(app), 
                app.getAppName()));
            sb.append(String.format("   %s\n", app.getDescription()));
            sb.append(String.format("   示例: %s\n\n", app.getHelp()));
        }
        
        sb.append("回复编号或应用名称选择");
        return sb.toString();
    }
}
```

---

## 🔄 交互流程

### 完整交互流程

```
┌─────────────────────────────────┐
│  用户输入: /menu                │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  MenuApp.execute()              │
│  - 尝试发送卡片                 │
│  - 失败则返回文本               │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  显示应用菜单卡片               │
│  ┌────────────────────┐         │
│  │ 🤖 应用菜单        │         │
│  ├────────────────────┤         │
│  │ [🤖 OpenCode 助手] │ ← 按钮  │
│  │ [💻 Bash 命令]     │ ← 按钮  │
│  │ [❓ 帮助信息]      │ ← 按钮  │
│  │ [📊 历史查询]      │ ← 按钮  │
│  │ [⏰ 时间查询]      │ ← 按钮  │
│  └────────────────────┘         │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  用户点击: [🤖 OpenCode 助手]   │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  飞书自动发送消息: "opencode"   │  ← 飞书自动发送
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  后端处理（与命令输入完全相同） │
│  - BotMessageService 接收消息   │
│  - 解析为命令: /opencode        │
│  - AppRouter 路由到 OpenCodeApp │
│  - OpenCodeApp.execute()        │
└─────────────────────────────────┘
```

### 按钮点击 → 消息转换

**关键机制**：飞书卡片按钮的 `value.message` 字段

```json
{
  "tag": "button",
  "text": {
    "content": "🤖 OpenCode 助手",
    "tag": "plain_text"
  },
  "type": "primary",
  "value": {
    "message": "opencode"  // ← 点击后，飞书自动发送这条消息
  }
}
```

**用户点击按钮后**：
1. 飞书客户端自动发送消息: "opencode"
2. 后端接收消息，内容为 "opencode"
3. 后端解析为命令: `/opencode`
4. 路由到 OpenCodeApp 执行

---

## 🎨 菜单设计

### 卡片格式（优先）

```json
{
  "schema": "2.0",
  "config": {
    "wide_screen_mode": true
  },
  "header": {
    "title": {
      "content": "🤖 应用菜单",
      "tag": "plain_text"
    },
    "template": "blue"
  },
  "elements": [
    {
      "tag": "markdown",
      "content": "点击按钮选择应用，或直接输入命令"
    },
    {
      "tag": "action",
      "actions": [
        {
          "tag": "button",
          "text": {
            "content": "🤖 OpenCode 助手",
            "tag": "plain_text"
          },
          "type": "primary",
          "value": {
            "message": "opencode"
          }
        },
        {
          "tag": "button",
          "text": {
            "content": "💻 Bash 命令",
            "tag": "plain_text"
          },
          "type": "default",
          "value": {
            "message": "bash"
          }
        },
        {
          "tag": "button",
          "text": {
            "content": "❓ 帮助信息",
            "tag": "plain_text"
          },
          "type": "default",
          "value": {
            "message": "help"
          }
        },
        {
          "tag": "button",
          "text": {
            "content": "📊 历史查询",
            "tag": "plain_text"
          },
          "type": "default",
          "value": {
            "message": "history"
          }
        },
        {
          "tag": "button",
          "text": {
            "content": "⏰ 时间查询",
            "tag": "plain_text"
          },
          "type": "default",
          "value": {
            "message": "time"
          }
        }
      ]
    }
  ]
}
```

### 文本格式（降级）

```
🤖 应用菜单

1. 🤖 OpenCode 助手
   AI编程助手，支持代码生成和问题解答
   示例: /opencode chat 帮我写代码

2. 💻 Bash 命令
   执行安全的bash命令
   示例: /bash ls -la

3. ❓ 帮助信息
   显示所有可用命令
   示例: /help

4. 📊 历史查询
   查询bash命令历史
   示例: /history

5. ⏰ 时间查询
   查询当前系统时间
   示例: /time

回复编号或应用名称选择
```

---

## ⚙️ 配置管理

### 应用配置

```yaml
# application.yml
feishu:
  menu:
    enabled: true                    # 是否启用应用菜单
    prefer-card: true                # 优先使用卡片格式
    show-icons: true                 # 显示应用图标
    card-template: "blue"            # 卡片主题色
```

### 配置类

```java
@Component
@ConfigurationProperties(prefix = "feishu.menu")
@Data
public class MenuProperties {
    private boolean enabled = true;
    private boolean preferCard = true;
    private boolean showIcons = true;
    private String cardTemplate = "blue";
}
```

---

## 🛡️ 错误处理

### 1. 卡片发送失败

```java
private boolean trySendInteractiveCard(Message message) {
    try {
        String cardJson = buildInteractiveCardJson();
        feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
        return true;
    } catch (Exception e) {
        log.warn("卡片发送失败，降级为文本: error={}", e.getMessage());
        return false;  // 返回 false，触发文本菜单降级
    }
}
```

### 2. 应用图标获取

```java
private String getAppIcon(FishuAppI app) {
    // 根据应用ID返回图标
    Map<String, String> iconMap = Map.of(
        "opencode", "🤖",
        "bash", "💻",
        "help", "❓",
        "history", "📊",
        "time", "⏰"
    );
    return iconMap.getOrDefault(app.getAppId(), "📦");
}
```

### 3. 按钮类型选择

```java
private String getButtonType(FishuAppI app) {
    // 推荐应用使用 primary，其他使用 default
    List<String> primaryApps = Arrays.asList("opencode", "bash", "help");
    return primaryApps.contains(app.getAppId()) ? "primary" : "default";
}
```

---

## 🧪 测试策略

### 单元测试

```java
@SpringBootTest
class MenuAppTest {
    
    @Autowired
    private MenuApp menuApp;
    
    @MockBean
    private AppRegistry appRegistry;
    
    @MockBean
    private FeishuGateway feishuGateway;
    
    @Test
    void should_generate_text_menu_when_card_failed() {
        // Given
        Message message = createTestMessage();
        List<FishuAppI> apps = createMockApps();
        when(appRegistry.getAllApps()).thenReturn(apps);
        when(feishuGateway.sendInteractiveMessage(any(), any(), any()))
            .thenThrow(new RuntimeException("Card failed"));
        
        // When
        String result = menuApp.execute(message);
        
        // Then
        assertThat(result).contains("🤖 应用菜单");
        assertThat(result).contains("OpenCode 助手");
        assertThat(result).contains("Bash 命令");
    }
    
    @Test
    void should_send_card_when_available() {
        // Given
        Message message = createTestMessage();
        when(appRegistry.getAllApps()).thenReturn(createMockApps());
        
        // When
        String result = menuApp.execute(message);
        
        // Then
        assertThat(result).isNull();  // 卡片发送成功，返回 null
        verify(feishuGateway).sendInteractiveMessage(any(), any(), any());
    }
    
    @Test
    void should_exclude_self_from_menu() {
        // Given
        List<FishuAppI> apps = Arrays.asList(
            createMockApp("opencode"),
            createMockApp("menu"),  // MenuApp 自己
            createMockApp("bash")
        );
        when(appRegistry.getAllApps()).thenReturn(apps);
        
        // When
        String result = menuApp.execute(createTestMessage());
        
        // Then
        assertThat(result).contains("opencode");
        assertThat(result).contains("bash");
        assertThat(result).doesNotContain("menu");  // 不包含自己
    }
}
```

### 集成测试

```java
@SpringBootTest
class MenuAppIntegrationTest {
    
    @Test
    void should_show_menu_with_command() {
        // Given
        Message message = createTestMessage();
        message.setContent("/menu");
        
        // When
        botMessageService.handleMessage(message);
        
        // Then
        verify(feishuGateway).sendMessage(
            any(Message.class),
            contains("🤖 应用菜单"),
            any()
        );
    }
}
```

---

## 📊 监控与日志

### 关键日志

```java
// 菜单显示日志
log.info("应用菜单发送成功: chatId={}, format={}", 
    message.getChatId(), "card");

log.info("应用菜单降级为文本: chatId={}, reason={}", 
    message.getChatId(), e.getMessage());

// 应用选择日志（由现有系统记录）
log.info("路由到应用: appId={}, command={}", appId, command);
```

---

## 🚀 实施计划

### Phase 1: 核心功能（2-3小时）

**任务清单**:
- [ ] 创建 MenuApp.java
- [ ] 实现 generateTextMenu()
- [ ] 实现 buildInteractiveCardJson()
- [ ] 添加配置类 MenuProperties
- [ ] 编写单元测试
- [ ] 编写集成测试

**文件清单**:
```
feishu-bot-domain/
├── app/MenuApp.java                    # 新增
├── config/MenuProperties.java          # 新增
└── util/CardBuilder.java               # 可选：卡片构建工具

feishu-bot-infrastructure/
└── gateway/FeishuGatewayImpl.java      # 扩展：sendInteractiveMessage()

feishu-bot-start/
└── application.yml                      # 扩展：menu 配置
```

### Phase 2: 测试验证（1小时）

- [ ] 本地测试：文本菜单
- [ ] 本地测试：卡片菜单（如果 CardKit 可用）
- [ ] 验证按钮点击 → 消息发送 → 命令解析流程
- [ ] 验证所有应用都能正确触发

### Phase 3: 优化迭代（可选）

- [ ] 优化卡片样式
- [ ] 添加应用图标
- [ ] 支持自定义按钮类型
- [ ] 添加使用统计

---

## ✅ 部署检查清单

### 功能检查

- [ ] MenuApp 已注册到 AppRegistry
- [ ] 菜单内容正确显示所有应用
- [ ] 文本菜单降级正常工作
- [ ] 按钮点击能触发对应应用
- [ ] 每个应用都能正确执行

### 性能检查

- [ ] 菜单生成时间 < 100ms
- [ ] 卡片发送成功率（如果可用）
- [ ] 内存占用合理

### 兼容性检查

- [ ] 不影响现有命令交互方式
- [ ] 所有现有功能正常工作
- [ ] 降级模式稳定可靠

---

## 📝 实施要点

### 遵循 COLA 架构

- **domain/app**: MenuApp.java
- **domain/config**: MenuProperties.java
- **infrastructure/gateway**: 扩展 FeishuGateway

### 代码规范

- ✅ 添加 `@Component` 注解
- ✅ 使用 `@Autowired` 构造器注入
- ✅ 关键操作记录日志
- ✅ 异常处理并降级
- ✅ 返回 `null` 表示卡片发送成功

### 扩展性

- ✅ 配置化（优先卡片/文本、图标显示）
- ✅ 卡片模板可配置
- ✅ 按钮类型可自定义

---

## 🎯 成功标准

### 用户体验

- ✅ 用户能通过 `/menu` 看到所有应用
- ✅ 点击按钮能正确触发应用
- ✅ 文本菜单降级体验良好
- ✅ 不影响现有命令交互

### 技术指标

- ✅ MenuApp 代码 < 200 行
- ✅ 菜单生成时间 < 100ms
- ✅ 单元测试覆盖率 > 80%
- ✅ 零破坏性修改

---

## 📚 参考资料

- [COLA 架构规范](../AGENTS.md)
- [应用开发指南](../docs/APP_GUIDE.md)
- [飞书卡片概述](https://open.feishu.cn/document/feishu-cards/feishu-card-overview)
- [飞书交互式卡片](https://open.feishu.cn/document/ukTMukTMukTM/ucTM5UjL3ETO24yNxkjN)

---

## 🔗 相关设计

- [CardKit 流式响应设计](./2026-02-25-cardkit-streaming-design.md) - 卡片技术探索

---

**最后更新**: 2026-03-04  
**预计实施时间**: 2-3 小时  
**预计上线时间**: 2026-03-04

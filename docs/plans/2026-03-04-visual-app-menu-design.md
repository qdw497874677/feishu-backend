# 可视化应用菜单设计方案

**创建时间**: 2026-03-04  
**作者**: OpenCode  
**状态**: 设计完成，待实施

---

## 📋 概述

### 核心目标

在默认对话中提供可视化的应用选择菜单，帮助用户快速发现和使用应用，提升用户体验。

### 设计原则

- ✅ **轻量级实现**：避开CardKit API问题，使用普通消息
- ✅ **独立功能层**：应用菜单是独立模块，不属于任何应用
- ✅ **话题绑定**：选择应用后创建话题并临时绑定（30分钟）
- ✅ **降级友好**：文本菜单 + 可选卡片增强
- ✅ **符合架构**：遵循 COLA 分层原则

---

## 🏗️ 架构设计

### 系统架构

```
┌─────────────────────────────────┐
│  默认对话（无话题）              │
│                                 │
│  用户发送消息                    │
│  BotMessageService 检测         │
│  AppMenuService 显示菜单        │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  应用菜单交互                    │
│  - 用户选择应用（编号/名称）     │
│  - 创建话题                      │
│  - 绑定话题到应用（30分钟）      │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  话题对话（已绑定应用）          │
│  - 用户输入问题/命令             │
│  - 自动路由到绑定应用            │
│  - 应用在话题中执行              │
└─────────────────────────────────┘
```

### 核心组件

#### 1. AppMenuService（应用菜单服务）

**位置**: `domain/service/AppMenuService.java`

**职责**:
- 生成应用菜单内容（详细版：名称 + 描述 + 示例）
- 处理用户选择（编号或应用名称）
- 创建话题并绑定应用

**核心方法**:
```java
public String generateMenuContent()
public void handleUserSelection(Message message, String userInput)
private Optional<FishuAppI> parseUserInput(String input)
private String createTopicAndBind(Message message, FishuAppI app)
```

#### 2. TopicBindingManager（话题绑定管理器）

**位置**: `domain/service/TopicBindingManager.java`

**职责**:
- 管理话题与应用的临时绑定
- 设置绑定超时（30分钟）
- 查询/清除绑定关系

**核心方法**:
```java
public void bindTopic(String topicId, String appId, long durationMs)
public Optional<String> getBindingApp(String topicId)
public void clearBinding(String topicId)
```

#### 3. TopicBindingGateway（话题绑定网关）

**位置**: `domain/gateway/TopicBindingGateway.java`

**职责**:
- 持久化话题绑定数据
- 提供绑定增删查接口

**实现**: `infrastructure/gateway/TopicBindingGatewayImpl.java`
- 内存实现（ConcurrentHashMap）
- 可扩展为 SQLite

#### 4. BotMessageService 扩展

**位置**: `domain/service/BotMessageService.java`

**扩展逻辑**:
```java
public void handleMessage(Message message) {
    String topicId = message.getTopicId();
    
    // 情况1: 默认对话（无话题）
    if (topicId == null || topicId.isEmpty()) {
        handleDefaultConversation(message);
        return;
    }
    
    // 情况2: 话题已绑定应用
    Optional<String> boundApp = bindingManager.getBindingApp(topicId);
    if (boundApp.isPresent()) {
        routeToApp(message, boundApp.get());
        return;
    }
    
    // 情况3: 话题未绑定，检查是否有应用前缀
    if (hasAppPrefix(message)) {
        routeToAppByPrefix(message);
    } else {
        showAppMenuInTopic(message);  // 可选
    }
}
```

---

## 🔄 工作流程

### 完整交互流程

```
1. 用户私聊机器人（默认对话）
   消息: "你好"
   topicId: null
   ↓
2. BotMessageService 检测
   → topicId == null
   → 默认对话
   ↓
3. AppMenuService 生成并发送菜单
   ┌────────────────────┐
   │ 🤖 应用菜单        │
   ├────────────────────┤
   │ 1. OpenCode 助手   │
   │    AI编程助手      │
   │    示例: /opencode chat 问题 │
   │                    │
   │ 2. Bash 命令       │
   │    执行安全的bash命令 │
   │    示例: /bash ls -la │
   │ ...                │
   │                    │
   │ 回复编号或应用名称选择 │
   └────────────────────┘
   ↓
4. 用户回复: "1" 或 "opencode"
   ↓
5. AppMenuService.handleUserSelection
   → 解析用户输入
   → 识别应用: OpenCode
   ↓
6. 创建话题并绑定
   → 创建话题: omt_xxx
   → 绑定到 OpenCode（30分钟）
   ↓
7. 发送确认消息（在新话题中）
   ┌────────────────────┐
   │ ✅ 已选择 OpenCode 助手 │
   ├────────────────────┤
   │ 话题已创建，30分钟内有效 │
   │                    │
   │ 你可以直接输入问题或命令 │
   └────────────────────┘
   ↓
8. 用户在新话题中输入: "帮我写代码"
   topicId: omt_xxx
   ↓
9. BotMessageService 路由
   → 查询绑定: OpenCode
   → 路由到 OpenCodeApp
   ↓
10. OpenCodeApp 处理并回复
```

---

## 🎨 菜单设计

### 文本格式（详细版）

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

### 卡片格式（未来增强）

如果 CardKit API 可用，可以升级为交互式卡片：

```
┌────────────────────┐
│ 🤖 应用菜单        │
├────────────────────┤
│ 1. 🤖 OpenCode     │
│ 2. 💻 Bash         │
│ 3. ❓ Help         │
│                    │
│ [选择1] [选择2]    │ ← 点击按钮
│ [选择3] [更多...]  │
└────────────────────┘
```

---

## ⚙️ 配置管理

### 应用配置

```yaml
# application.yml
feishu:
  app-menu:
    enabled: true                    # 是否启用应用菜单
    auto-show-in-default: true       # 在默认对话中自动显示
    binding-duration: 1800000        # 绑定持续时间（30分钟）
    show-expiry-warning: true        # 显示过期警告
    expiry-warning-threshold: 300000 # 过期警告阈值（5分钟前）
```

### 配置类

```java
@Component
@ConfigurationProperties(prefix = "feishu.app-menu")
@Data
public class AppMenuProperties {
    private boolean enabled = true;
    private boolean autoShowInDefault = true;
    private long bindingDuration = 30 * 60 * 1000L; // 30分钟
    private boolean showExpiryWarning = true;
    private long expiryWarningThreshold = 5 * 60 * 1000L; // 5分钟
}
```

---

## 🛡️ 错误处理

### 1. 无效输入处理

```java
public void handleUserSelection(Message message, String userInput) {
    Optional<FishuAppI> selectedApp = parseUserInput(userInput);
    
    if (selectedApp.isEmpty()) {
        log.warn("无效的应用选择: userInput={}, chatId={}", 
            userInput, message.getChatId());
        
        String errorContent = String.format(
            "❌ 无效选择\n\n" +
            "\"%s\" 不是有效的应用\n\n" +
            "%s",
            userInput,
            generateMenuContent()
        );
        
        feishuGateway.sendMessage(message, errorContent, null);
        return;
    }
    
    // 正常流程...
}
```

### 2. 话题创建失败处理

```java
private String createTopicAndBind(Message message, FishuAppI app) {
    try {
        SendResult result = feishuGateway.sendDirectReply(message, "");
        
        if (result.getThreadId() == null || result.getThreadId().isEmpty()) {
            throw new MessageSysException("TOPIC_CREATE_FAILED", 
                "Failed to create topic");
        }
        
        String topicId = result.getThreadId();
        bindingManager.bindTopic(topicId, app.getAppId(), properties.getBindingDuration());
        
        log.info("话题创建并绑定成功: topicId={}, appId={}", 
            topicId, app.getAppId());
        
        return topicId;
        
    } catch (Exception e) {
        log.error("创建话题失败: appId={}, error={}", app.getAppId(), e.getMessage());
        
        // 降级：直接使用应用，不创建话题
        String fallbackContent = String.format(
            "⚠️ 话题创建失败，将直接使用应用\n\n" +
            "✅ 已选择 %s\n\n" +
            "你可以使用命令：%s",
            app.getAppName(),
            app.getHelp()
        );
        
        feishuGateway.sendMessage(message, fallbackContent, null);
        throw new MessageSysException("TOPIC_CREATE_FAILED", e);
    }
}
```

### 3. 绑定过期处理

```java
public Optional<String> getBindingApp(String topicId) {
    Optional<TopicBinding> binding = bindingGateway.getBinding(topicId);
    
    if (binding.isEmpty()) {
        return Optional.empty();
    }
    
    // 检查是否过期
    if (binding.get().getExpiryTime() < System.currentTimeMillis()) {
        log.info("话题绑定已过期: topicId={}, appId={}",
            topicId, binding.get().getAppId());
        
        bindingGateway.removeBinding(topicId);
        return Optional.empty();
    }
    
    return Optional.of(binding.get().getAppId());
}
```

---

## 🧪 测试策略

### 单元测试

```java
@SpringBootTest
class AppMenuServiceTest {
    
    @Test
    void should_generate_menu_content_with_all_apps() {
        // Given
        List<FishuAppI> apps = Arrays.asList(
            createMockApp("opencode", "OpenCode 助手"),
            createMockApp("bash", "Bash 命令")
        );
        when(appRegistry.getAllApps()).thenReturn(apps);
        
        // When
        String content = appMenuService.generateMenuContent();
        
        // Then
        assertThat(content).contains("🤖 应用菜单");
        assertThat(content).contains("1. OpenCode 助手");
        assertThat(content).contains("2. Bash 命令");
    }
    
    @Test
    void should_parse_user_input_as_number() {
        // Given
        when(appRegistry.getAllApps()).thenReturn(apps);
        
        // When
        Optional<FishuAppI> result = appMenuService.parseUserInput("1");
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAppId()).isEqualTo("opencode");
    }
    
    @Test
    void should_handle_invalid_input() {
        // Given
        Message message = createTestMessage();
        
        // When
        appMenuService.handleUserSelection(message, "invalid");
        
        // Then
        verify(feishuGateway).sendMessage(
            eq(message),
            contains("❌ 无效选择"),
            isNull()
        );
    }
}
```

### 集成测试

```java
@SpringBootTest
class AppMenuIntegrationTest {
    
    @Test
    void should_show_menu_in_default_conversation() {
        // Given
        Message message = createTestMessage(null); // 无话题
        
        // When
        botMessageService.handleMessage(message);
        
        // Then
        verify(feishuGateway).sendMessage(
            eq(message),
            contains("🤖 应用菜单"),
            isNull()
        );
    }
    
    @Test
    void should_create_topic_when_user_selects_app() {
        // Given
        Message message = createTestMessage(null);
        message.setContent("1");
        
        when(feishuGateway.sendDirectReply(any(), any()))
            .thenReturn(SendResult.success("msg_123", "omt_456"));
        
        // When
        botMessageService.handleMessage(message);
        
        // Then
        Optional<TopicBinding> binding = bindingGateway.getBinding("omt_456");
        assertThat(binding).isPresent();
        assertThat(binding.get().getAppId()).isEqualTo("opencode");
    }
}
```

---

## 📊 监控与日志

### 关键日志

```java
// 应用选择日志
log.info("用户选择应用: chatId={}, userInput={}", message.getChatId(), userInput);
log.info("应用选择成功: appId={}, appName={}", app.getAppId(), app.getAppName());

// 话题绑定日志
log.info("绑定话题到应用: topicId={}, appId={}, duration={}分钟",
    topicId, appId, durationMs / 60000);

// 错误日志
log.warn("无效的应用选择: userInput={}, chatId={}", userInput, message.getChatId());
log.error("创建话题失败: appId={}, error={}", app.getAppId(), e.getMessage());
log.info("话题绑定已过期: topicId={}, appId={}", topicId, appId);
```

### 监控指标

```java
// 可选：集成 Micrometer
Counter menuShownCounter;           // 菜单显示次数
Counter appSelectedCounter;         // 应用选择成功次数
Counter invalidSelectionCounter;    // 无效选择次数
```

---

## 🚀 发布计划

### Phase 1: 核心功能（1-2天）

- ✅ AppMenuService 实现
- ✅ TopicBindingManager 实现
- ✅ TopicBindingGateway 实现（内存版）
- ✅ BotMessageService 集成
- ✅ 基础单元测试
- ✅ 集成测试

### Phase 2: 增强功能（可选）

- ⏳ CardKit 卡片支持（解决 200610 错误后）
- ⏳ 绑定持久化（SQLite）
- ⏳ 监控指标集成
- ⏳ 性能优化

### Phase 3: 优化迭代（未来）

- ⏳ 用户使用统计
- ⏳ 智能推荐（基于使用频率）
- ⏳ 自定义菜单配置
- ⏳ 应用分组

---

## ✅ 部署检查清单

### 功能检查

- [ ] 所有应用正确注册到 AppRegistry
- [ ] 菜单内容格式正确，包含描述和示例
- [ ] 话题创建成功，topicId 正确返回
- [ ] 绑定关系正确保存，过期时间准确
- [ ] 过期后自动清除绑定

### 性能检查

- [ ] TopicBindingGateway 使用 ConcurrentHashMap
- [ ] 支持并发访问（多用户同时选择）
- [ ] 内存占用合理

### 安全检查

- [ ] 用户输入验证和清理
- [ ] 防止注入攻击
- [ ] 权限控制（仅合法用户）

---

## 📝 实施要点

### 遵循 COLA 架构

- **domain/service**: AppMenuService, TopicBindingManager
- **domain/gateway**: TopicBindingGateway
- **domain/topic**: TopicBinding
- **infrastructure/gateway**: TopicBindingGatewayImpl
- **domain/config**: AppMenuProperties

### 代码规范

- ✅ 添加 `@Component` 注解
- ✅ 使用 `@Autowired` 构造器注入
- ✅ 关键操作记录日志
- ✅ 异常向上抛出，不吞掉
- ✅ 返回 `Optional` 而非 `null`

### 扩展性

- ✅ 配置化（绑定时长、菜单格式）
- ✅ Gateway 接口便于切换实现（内存 → SQLite）
- ✅ 预留卡片增强接口

---

## 🎯 成功标准

### 用户体验

- ✅ 新用户能在 10 秒内发现并选择应用
- ✅ 无需记忆斜杠命令即可使用应用
- ✅ 错误提示清晰友好
- ✅ 菜单永久可用，不会丢失

### 技术指标

- ✅ 菜单生成时间 < 100ms
- ✅ 话题创建成功率 > 99%
- ✅ 绑定过期自动清理
- ✅ 单元测试覆盖率 > 80%

---

## 📚 参考资料

- [COLA 架构规范](../AGENTS.md)
- [应用开发指南](../docs/APP_GUIDE.md)
- [飞书 IM SDK 文档](https://open.feishu.cn/document/serverSdk/im sdk)
- [飞书卡片概述](https://open.feishu.cn/document/feishu-cards/feishu-card-overview)

---

**最后更新**: 2026-03-04  
**预计实施时间**: 1-2 天  
**预计上线时间**: 2026-03-06

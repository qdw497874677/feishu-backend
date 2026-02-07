# 飞书机器人 - 项目规范

> **必读**：所有开发者必须遵守本文档规范。违反规范可能导致代码无法合并。

---

## ⚠️ 核心原则

### 1. 通信模式（铁律）

| 模式 | 状态 | 说明 |
|------|------|------|
| 长连接 | ✅ **唯一允许** | WebSocket 实时推送，稳定可靠 |
| WebHook | ❌ **严禁使用** | 需要公网 IP 和域名，部署复杂 |

**强制要求**：
- 所有代码必须基于长连接模式
- 禁止添加任何 WebHook 相关代码
- 消息收发统一使用 `MessageListenerGateway` 和 `FeishuGateway`

### 2. COLA 架构

严格遵循 [COLA (Clean Object-oriented and Layered Architecture)](https://github.com/alibaba/COLA)。

---

## 🏗️ COLA 架构规范

### 模块职责

| 模块 | 职责 | 代码类型 | 示例 |
|------|------|---------|------|
| **domain** | 领域模型、业务逻辑、网关接口 | `@Entity`, `DomainService`, `Gateway Interface`, `FishuAppI` | `Message.java`, `FeishuGateway.java` |
| **app** | 应用服务、用例编排 | `@AppService`, `Cmd`, `Qry`, `CmdExe`, `QryExe` | `ReceiveMessageCmdExe.java` |
| **infrastructure** | 基础设施、外部集成 | Gateway 实现, Config, Repository | `FeishuGatewayImpl.java` |
| **adapter** | 适配层、事件监听 | Controller, Listener, Event Handler | `FeishuEventListener.java` |
| **client** | DTO 对象 | `@DTO`, `@Request`, `@Response` | `ReceiveMessageCmd.java` |
| **start** | 启动配置 | `Application.java`, `application.yml` | - |

### 分层依赖图

```
┌─────────────────────────────────────┐
│         feishu-bot-start          │  ← 启动入口
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│        feishu-bot-adapter         │  ← 适配层
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         feishu-bot-app            │  ← 应用层
└──────────────┬──────────────────────┘
               │
        ┌──────┴───────┐
        │              │
┌──────▼──────┐ ┌─────▼─────┐
│  feishu-bot- │ │feishu-bot-│
│   domain     │ │  client   │  ← 领域层 + DTO 层
└──────┬───────┘ └───────────┘
       │
┌──────▼──────────────────────────┐
│  feishu-bot-infrastructure     │  ← 基础设施层
└─────────────────────────────────┘
```

### 依赖规则

1. **上层依赖下层**：app → domain + client
2. **下层定义接口**：domain 定义接口，infrastructure 实现
3. **横向隔离**：同层模块不能直接依赖

### 代码放置决策树

```
需要添加什么代码？
│
├─ 实体/值对象/领域服务/应用 → domain
├─ 命令/查询/用例执行器 → app
├─ 数据库/外部API/配置 → infrastructure
├─ Controller/Listener → adapter
├─ DTO/请求响应 → client
└─ 启动配置 → start
```

---

## 🚀 启动与部署

### 快速启动

```bash
./start-feishu.sh
```

**详细指南**：👉 [RESTART-GUIDE.md](./RESTART-GUIDE.md)

### 验证启动成功

```bash
# 查看日志
tail -50 /tmp/feishu-run.log

# 检查 WebSocket 连接
grep "connected to wss://" /tmp/feishu-run.log

# 成功标志
# ✅ Started Application in X seconds
# ✅ connected to wss://msg-frontner.feishu.cn/...
# ✅ 5个应用已注册：[help, opencode, bash, history, time]
```

---

## 🎯 应用开发规范

### 快速创建应用（3 步）

1. **创建类**：`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/YourApp.java`
2. **实现接口**：添加 `@Component` + 实现 `FishuAppI`
3. **构建重启**：`mvn clean package && ./start-feishu.sh`

### 应用模板

```java
@Component
public class YourApp implements FishuAppI {

    @Override
    public String getAppId() {
        return "yourapp";  // 唯一ID，命令前缀 /yourapp
    }

    @Override
    public String execute(Message message) {
        return "Hello from YourApp!";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("alias1", "alias2");  // 可选
    }
}
```

### 关键要点

- **位置**：必须在 `domain/app/` 目录
- **注解**：必须添加 `@Component`
- **AppId**：必须唯一

**详细指南**：👉 [APP_GUIDE.md](./APP_GUIDE.md)

---

## 🛡️ 设计模式规范

### 策略模式（回复处理）

**目的**：消除 if-else，符合开放封闭原则

**结构**：
```
domain/reply/
├── ReplyStrategy.java          # 策略接口
└── ReplyStrategyFactory.java   # 策略工厂

infrastructure/reply/
├── DirectReplyStrategy.java    # 直接回复
├── TopicReplyStrategy.java     # 话题回复
└── DefaultReplyStrategy.java   # 默认回复
```

**使用方式**：
```java
// BotMessageService.java
ReplyStrategy strategy = replyStrategyFactory.getStrategy(replyMode);
SendResult result = strategy.reply(message, replyContent, topicId);
```

**新增回复模式**：只需创建新的 `ReplyStrategy` 实现类，自动注册。

### 防腐层（外部集成）

**目的**：隔离外部 SDK 变化，保护领域层

**结构**：
```
domain/gateway/
└── MessageEventParser.java     # 防腐层接口

infrastructure/parser/
└── MessageEventParserImpl.java # 解析器实现
```

**原则**：
- 领域层不依赖飞书 SDK
- SDK 解析逻辑封装在防腐层
- 便于单元测试（可 mock）

---

## 📋 代码规范约束

### 1. 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `BotMessageService`, `MessageListenerGateway` |
| 方法名 | camelCase | `sendMessage()`, `findAppByCommand()` |
| 变量名 | camelCase | `messageId`, `topicId`, `replyContent` |
| 常量名 | UPPER_SNAKE_CASE | `MAX_RETRIES`, `DEFAULT_TIMEOUT` |
| 包名 | 全小写 | `com.qdw.feishu.domain.gateway` |

**禁止**：
- ❌ 模糊缩写（`cmd` → `command`，除非是广泛认可的如 `id`）
- ❌ 单字母变量（循环变量除外）
- ❌ 类型前缀（`strMessage`, `iCount`）

### 2. 类设计规范

**单一职责原则**：
- ✅ 一个类只负责一件事
- ✅ 类长度建议不超过 300 行
- ✅ 方法长度建议不超过 50 行

**接口隔离**：
- ✅ 接口方法尽量少（≤ 5 个）
- ✅ 优先使用小接口而非大接口
- ✅ 避免"肥接口"

**示例**：
```java
// ✅ 好：职责单一
public interface ReplyStrategy {
    SendResult reply(Message message, String content, String topicId);
}

// ❌ 不好：职责过多
public interface MessageHandler {
    void validate();
    void parse();
    void route();
    void execute();
    void reply();
}
```

### 3. 方法规范

**参数数量**：
- ✅ 理想：0-2 个参数
- ⚠️ 警告：3-4 个参数（考虑封装为对象）
- ❌ 禁止：超过 4 个参数

**返回值**：
- ✅ 优先返回具体类型而非泛型
- ✅ 使用 `Optional` 表示可能为空的值
- ✅ 返回 `void` 用于副作用操作

**异常处理**：
- ✅ 使用明确的异常类型
- ✅ 记录有意义的错误信息
- ❌ 禁止吞掉异常（`catch (Exception e) {}`）
- ❌ 禁止返回 `null` 表示错误

### 4. 注释规范

**允许的注释**：
- ✅ API 文档（public 类/方法）
- ✅ 复杂算法说明
- ✅ 安全相关逻辑
- ✅ 业务规则解释

**禁止的注释**：
- ❌ 显而易见的代码说明（`i++; // i 增加 1`）
- ❌ 注释掉的代码（删除或使用版本控制）
- ❌ TODO 长期存在（及时处理）

**示例**：
```java
/**
 * 根据回复模式执行消息回复。
 * 使用策略模式封装不同回复行为，便于扩展。
 */
public interface ReplyStrategy {
    // 复杂的正则表达式需要注释
    private static final Pattern THREAD_ID_PATTERN = 
        Pattern.compile("\"thread_id\"\\s*:\\s*\"([^\"]+)\"");
}
```

### 5. 测试规范

**要求**：
- ✅ 核心业务逻辑必须有单元测试
- ✅ 测试方法命名：`should_returnX_when_givenY`
- ✅ 每个测试用例一个验证点
- ✅ 保持测试代码与生产代码同等质量

**示例**：
```java
@Test
void should_returnDirectReply_when_modeIsDirect() {
    // given
    Message message = createTestMessage();
    ReplyStrategy strategy = new DirectReplyStrategy(feishuGateway);
    
    // when
    SendResult result = strategy.reply(message, "test", null);
    
    // then
    assertTrue(result.isSuccess());
}
```

### 6. 测试质量保证 ⚠️ **重要**

**核心原则：不要为了快速通过而降低测试质量**

#### 6.1 高质量测试的标志

✅ **必须做到**：
- 验证具体返回值（使用 `assertEquals` 而非 `assertNotNull`）
- 验证方法调用（使用 `verify()` 确认正确的方法被调用）
- 验证错误消息格式（检查包含关键错误信息）
- 模拟正确的测试场景（正确设置话题状态、初始化状态等）

❌ **禁止行为**：
- 仅检查 `assertNotNull(result)` - 这会让bug逃过检测
- 仅检查 `assertTrue(result.contains("xxx"))` 当应该精确验证时
- 省略 `verify()` 调用 - 无法确认正确的交互发生
- 为了通过测试而简化断言

#### 6.2 测试场景设置

**OpenCode 应用的三种话题状态**：

| 状态 | 条件 | 行为 |
|------|------|------|
| `NON_TOPIC` | `topicId == null` | 只允许 connect/help/projects/p/reset |
| `UNINITIALIZED` | `topicId != null` 且无 sessionId | 显示初始化引导 |
| `INITIALIZED` | 有 sessionId | 允许所有命令 |

**正确模拟已初始化话题**：
```java
String topicId = "init-topic";
when(sessionManager.getSessionId(topicId))
    .thenReturn(Optional.of("ses_123"));
when(sessionManager.isExplicitlyInitialized(topicId))
    .thenReturn(true);  // chat命令需要此设置
```

#### 6.3 命令别名完整性检查

**问题**：别名必须在所有相关检查中被包含

**示例**：
```java
// ❌ 错误：只检查全称
if (state == TopicState.NON_TOPIC && !subCommand.equals("projects")) {
    return buildConnectGuide();
}

// ✅ 正确：检查全称和别名
if (state == TopicState.NON_TOPIC && !subCommand.equals("projects")
    && !subCommand.equals("p")) {
    return buildConnectGuide();
}
```

**检查清单**：
- 白名单检查是否包含别名
- 允许命令列表是否包含别名
- 初始化命令列表是否包含别名

#### 6.4 Mockito 匹配器使用规范

**错误示例**（混合实际值和匹配器）：
```java
when(commandHandler.handle(eq(message), eq("projects"), any()))
    .thenReturn(expectedResponse);
```

**正确示例**（统一使用匹配器）：
```java
when(commandHandler.handle(any(Message.class), eq("projects"), any(String[].class)))
    .thenReturn(expectedResponse);
```

**规则**：
- 要么全部使用匹配器：`any()`, `eq()`, `any(String[].class)`
- 要么全部使用实际值
- **禁止混合使用**

#### 6.5 测试失败诊断流程

当测试失败时：

1. **查看实际返回值** - 理解实现的真实行为
2. **检查实现逻辑** - 确认是测试问题还是实现bug
3. **修复实现而非降低测试** - 如果是bug，修复代码
4. **保持断言强度** - 使用精确的验证而非宽泛的检查

**示例**：
```java
// ❌ 降低质量：为了通过而放宽断言
assertTrue(result.contains("some") || result.contains("any"));

// ✅ 保持质量：修复实现或调整测试场景
assertEquals(expectedResult, result);
verify(service).correctMethod(param);
```

### 7. 日志规范

**级别使用**：
| 级别 | 使用场景 | 示例 |
|------|---------|------|
| ERROR | 系统错误，需要关注 | `连接飞书 SDK 失败` |
| WARN | 潜在问题，可恢复 | `使用默认策略，因为未找到指定策略` |
| INFO | 关键操作，业务流程 | `消息处理完成，耗时 123ms` |
| DEBUG | 调试信息，开发时使用 | `提取到 threadId: xxx` |
| TRACE | 详细追踪，极少使用 | `SDK 原始响应: xxx` |

**禁止**：
- ❌ 在生产环境使用 `System.out.println()`
- ❌ 记录敏感信息（密码、密钥等）
- ❌ 记录完整请求/响应（使用 DEBUG 级别）

### 7. Git 规范

**提交信息**：
```
<type>(<scope>): <subject>

types: feat, fix, refactor, docs, style, test, chore
```

**示例**：
```
feat(reply): 添加话题回复策略实现
fix(parser): 修复正则表达式解析 threadId 失败问题
refactor(gateway): 重构 FeishuGateway 使用策略模式
docs(AGENTS.md): 更新规范约束章节
```

**禁止**：
- ❌ 提交信息为空或不明确
- ❌ 提交未编译通过的代码
- ❌ 提交临时文件（`.tmp`, `debug.log` 等）

---

## 🐛 常见错误排查

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `NoSuchMethodError: Sender` | 缺少 `@NoArgsConstructor` | 添加 Lombok 注解 |
| `No qualifying bean of type 'X'` | 未添加 `@Component`/`@Service` | 添加注解 |
| `app_id is invalid` | 环境变量未传递 | 使用 `./start-feishu.sh` |
| 中文显示为 `?` | 编码配置不正确 | 配置 UTF-8 |
| 话题已失效 | 未保存话题映射 | 检查 `topicMappingGateway.save()` |

**详细排查**：👉 [RESTART-GUIDE.md](./RESTART-GUIDE.md)

---

## 📚 参考资料

| 文档 | 用途 |
|------|------|
| [APP_GUIDE.md](./APP_GUIDE.md) | 应用开发指南 |
| [RESTART-GUIDE.md](./RESTART-GUIDE.md) | 重启与故障排查 |
| [COLA 框架](https://github.com/alibaba/COLA) | COLA 架构官方文档 |
| [飞书 IM SDK](https://open.feishu.cn/document/serverSdk/im sdk) | 飞书消息 API |
| [飞书 WebSocket](https://open.feishu.cn/document/serverSdk/event-sdk) | 飞书事件推送 |

---

## 📁 关键文件位置

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── app/                      # 应用系统
│   ├── FishuAppI.java        # 应用接口
│   └── *.java                # 应用实现
├── gateway/                  # 网关接口
│   ├── FeishuGateway.java
│   ├── MessageListenerGateway.java
│   └── MessageEventParser.java    # 防腐层接口
├── message/                  # 消息模型
│   └── Message.java
├── reply/                    # 策略模式
│   ├── ReplyStrategy.java
│   └── ReplyStrategyFactory.java
├── router/                   # 路由器
│   └── AppRouter.java
└── service/                  # 领域服务
    └── BotMessageService.java

feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── gateway/                  # 网关实现
│   ├── FeishuGatewayImpl.java
│   └── MessageListenerGatewayImpl.java
├── parser/                   # 防腐层实现
│   └── MessageEventParserImpl.java
├── reply/                    # 策略实现
│   ├── DirectReplyStrategy.java
│   ├── TopicReplyStrategy.java
│   └── DefaultReplyStrategy.java
└── config/                   # 配置
    ├── FeishuProperties.java
    └── DomainServiceConfig.java
```

---

**最后更新**: 2026-02-02

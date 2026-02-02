# feishu-bot-domain - 领域层知识库

**复杂度**: HIGH (85)
**文件数**: 43 Java 文件（重构后）
**最后更新**: 2026-02-02

---

## 📋 模块职责

feishu-bot-domain 是飞书机器人的**核心业务层**，包含：

- **领域模型**：消息、发送者、话题映射等实体
- **应用系统**：BashApp, TimeApp, HelpApp, HistoryApp, OpenCodeApp
- **业务逻辑**：消息路由、命令解析、别名匹配
- **领域服务**：BotMessageService（消息处理编排）
- **网关接口**：FeishuGateway, MessageListenerGateway, TopicMappingGateway
- **规则验证**：命令白名单验证

---

## 📂 目录结构

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── core/                    # 核心接口和抽象
│   ├── FishuAppI.java       # 应用接口定义
│   ├── AppRegistry.java     # 应用注册中心
│   └── ReplyMode.java       # 回复模式枚举
├── app/                     # 应用实现
│   ├── BashApp.java         # 命令执行应用
│   ├── TimeApp.java         # 时间查询应用
│   ├── HelpApp.java         # 帮助信息应用
│   ├── HistoryApp.java      # 历史查询应用
│   └── OpenCodeApp.java     # OpenCode 应用
├── message/                 # 消息领域模型
│   ├── Message.java         # 消息实体
│   ├── MessageType.java     # 消息类型枚举
│   ├── Sender.java          # 发送者信息
│   ├── SenderInfo.java      # 发送者详情
│   ├── MessageStatus.java   # 消息状态
│   ├── SendResult.java      # 发送结果
│   └── ChatHistory.java     # 聊天历史
├── topic/                   # 话题相关
│   ├── TopicMapping.java    # 话题映射实体
│   ├── TopicState.java      # 话题状态枚举
│   └── TopicCommandValidator.java
├── command/                 # 命令相关
│   ├── CommandWhitelist.java
│   ├── CommandWhitelistValidator.java
│   └── ValidationResult.java
├── service/                 # 领域服务
│   ├── BotMessageService.java    # 消息处理核心服务
│   └── MessageDeduplicator.java  # 消息去重
├── gateway/                 # 网关接口
│   ├── FeishuGateway.java            # 飞书 API 网关
│   ├── MessageListenerGateway.java   # 长连接网关
│   ├── MessageEventParser.java       # 防腐层接口（新增）
│   ├── TopicMappingGateway.java      # 话题映射网关
│   ├── UserInfo.java                 # 用户信息
│   ├── OpenCodeGateway.java          # OpenCode 网关
│   └── OpenCodeSessionGateway.java   # OpenCode 会话网关
├── reply/                   # 策略模式（新增）
│   ├── ReplyStrategy.java        # 策略接口
│   └── ReplyStrategyFactory.java # 策略工厂
├── router/                  # 路由器
│   └── AppRouter.java
├── history/                 # 历史管理
│   ├── BashHistoryManager.java
│   └── CommandExecution.java
├── exception/               # 异常定义
│   ├── MessageSysException.java
│   ├── ConnectionLostException.java
│   ├── MessageBizException.java
│   └── MessageInvalidException.java
├── config/                  # 配置类
│   ├── FeishuConfig.java
│   └── FeishuReplyProperties.java
└── model/                   # 其他领域模型
    ├── TopicMetadata.java
    └── opencode/OpenCodeMetadata.java
```

---

## 🎯 核心概念

### 1. 应用系统（App System）

**FishuAppI 接口**：所有应用必须实现此接口

```java
public interface FishuAppI {
    String getAppId();                      // 应用ID（如 "bash"）
    String getAppName();                    // 应用名称
    String getDescription();                // 应用描述
    String getHelp();                      // 帮助信息
    String execute(Message message);       // 执行逻辑
    ReplyMode getReplyMode();              // 回复模式：DIRECT/TOPIC/DEFAULT
    List<String> getAppAliases();         // 命令别名
    List<String> getAllTriggerCommands(); // 所有触发方式
}
```

**已实现的应用**：
| 应用ID | 触发命令 | 别名 | 职责 |
|--------|---------|------|------|
| `bash` | `/bash` | `/cmd`, `/shell`, `/exec` | 执行安全的bash命令 |
| `time` | `/time` | `/t`, `/now`, `/date` | 查询系统时间 |
| `help` | `/help` | `/h`, `/?`, `/man` | 显示帮助信息 |
| `history` | `/history` | 无 | 查询bash历史 |
| `opencode` | `/opencode` | `/oc`, `/code` | OpenCode 助手 |

### 2. 策略模式（Reply Strategy）

**目的**：消除 if-else，符合开放封闭原则

**结构**：
```
domain/reply/
├── ReplyStrategy.java          # 策略接口
└── ReplyStrategyFactory.java   # 策略工厂
```

**使用方式**：
```java
// BotMessageService.java
ReplyStrategy strategy = replyStrategyFactory.getStrategy(replyMode);
SendResult result = strategy.reply(message, replyContent, topicId);
```

**优势**：
- 新增回复模式只需创建新策略类
- 各策略之间相互独立
- 便于单元测试

### 3. 防腐层（Anti-Corruption Layer）

**目的**：隔离外部 SDK 变化，保护领域层

**结构**：
```
domain/gateway/
└── MessageEventParser.java     # 防腐层接口
```

**职责**：
- 将飞书 SDK 事件转换为领域模型
- 封装 SDK 特定的解析逻辑
- 领域层不依赖飞书 SDK 的具体类

### 4. 消息处理流程

```
用户消息 (飞书)
    ↓
MessageListenerGateway (接收)
    ↓
防腐层 MessageEventParser (解析)
    ↓
BotMessageService.handleMessage() (编排)
    ↓
提取命令前缀 / 别名
    ↓
AppRouter / AppRegistry (查找应用)
    ↓
FishuAppI.execute() (执行)
    ↓
ReplyStrategyFactory (策略选择)
    ↓
ReplyStrategy.reply() (回复)
```

### 5. 话题上下文机制

**TopicMapping 实体**：
```java
public class TopicMapping {
    private String topicId;      // 话题ID
    private String appId;        // 应用ID
    private long createdAt;      // 创建时间（毫秒时间戳）
    private long lastActiveAt;   // 最后活跃时间
}
```

---

## 🔑 关键约定

### 1. 新建应用规范

**3步创建新应用**：
```java
@Component
public class YourApp implements FishuAppI {
    @Override
    public String getAppId() {
        return "yourapp";  // 唯一ID
    }

    @Override
    public String execute(Message message) {
        return "result";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("alias1");
    }
}
```

**放置位置**：`domain/app/`

### 2. 网关接口模式

**接口定义在 domain**，实现在 infrastructure：
```java
// domain/gateway/FeishuGateway.java
public interface FeishuGateway {
    SendResult sendMessage(Message message, String content, String topicId);
}

// infrastructure/gateway/FeishuGatewayImpl.java
@Component
public class FeishuGatewayImpl implements FeishuGateway { }
```

### 3. 禁止模式

| 行为 | 原因 | 后果 |
|------|------|------|
| **跨层依赖** | domain 不能依赖 infrastructure | 代码无法编译 |
| **直接使用 SDK** | 必须通过 Gateway 接口 | 违反架构规范 |
| **应用ID重复** | 必须唯一 | 后注册的应用会覆盖 |
| **命令前缀冲突** | 不同应用不能有相同别名 | 导致路由混乱 |

---

## 📝 代码模式

### 消息处理模板

```java
@Override
public String execute(Message message) {
    String content = message.getContent().trim();
    String[] parts = content.split("\\s+", 2);

    if (parts.length < 2) {
        return getHelp();
    }

    String command = parts[1].trim();
    String result = doSomething(command);
    return result;
}
```

### 领域服务模式

**BotMessageService** 是核心编排服务：
- 接收消息
- 路由到应用
- 使用策略模式处理回复
- 保存话题映射

**不要在此服务中**：
- 直接使用飞书 SDK（使用 FeishuGateway）
- 实现业务逻辑（应该在应用中）
- 处理 HTTP 请求（adapter 层的职责）

---

## 🔍 调试技巧

```bash
# 查看应用注册日志
grep "AppRegistry" /tmp/feishu-run.log

# 查看消息处理日志
grep "BotMessageService" /tmp/feishu-run.log

# 查看策略选择日志
grep "ReplyStrategy" /tmp/feishu-run.log

# 查看话题映射日志
grep "话题映射" /tmp/feishu-run.log
```

---

## 📚 相关文档

- [根目录规范](../AGENTS.md) - 项目整体规范
- [基础设施层规范](../feishu-bot-infrastructure/AGENTS.md) - Gateway 实现
- [应用开发指南](../docs/APP_GUIDE.md) - 如何创建新应用

---

## ⚠️ 常见陷阱

1. **忘记添加 @Component**：应用不会被注册
2. **应用ID重复**：导致路由混乱
3. **直接返回 null**：会导致错误回复
4. **不处理异常**：异常会传播到 adapter 层
5. **别名冲突**：不同应用使用相同别名

---

**最后更新**: 2026-02-02

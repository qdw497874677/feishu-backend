# feishu-bot-domain - 领域层知识库

**复杂度**: HIGH (85)
**文件数**: 31 Java 文件
**最后更新**: 2026-02-01

---

## 📋 模块职责

feishu-bot-domain 是飞书机器人的**核心业务层**，包含：

- **领域模型**：消息、发送者、话题映射等实体
- **应用系统**：BashApp, TimeApp, HelpApp, HistoryApp
- **业务逻辑**：消息路由、命令解析、别名匹配
- **领域服务**：BotMessageService（消息处理编排）
- **网关接口**：FeishuGateway, MessageListenerGateway, TopicMappingGateway
- **规则验证**：命令白名单验证

---

## 📂 目录结构

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── app/                    # 应用系统（7个文件）
│   ├── FishuAppI.java     # 应用接口定义
│   ├── AppRegistry.java    # 应用注册中心
│   ├── AppRouter.java      # 应用路由器
│   ├── BashApp.java        # 命令执行应用
│   ├── TimeApp.java        # 时间查询应用
│   ├── HelpApp.java        # 帮助信息应用
│   └── HistoryApp.java     # 历史查询应用
├── message/                # 消息领域模型（7个文件）
│   ├── Message.java        # 消息实体
│   ├── MessageType.java    # 消息类型枚举
│   ├── Sender.java         # 发送者信息
│   ├── SenderInfo.java     # 发送者详情
│   ├── MessageStatus.java  # 消息状态
│   └── SendResult.java     # 发送结果
├── service/                # 领域服务（2个文件）
│   ├── BotMessageService.java  # 消息处理核心服务
│   └── MessageDeduplicator.java # 消息去重
├── gateway/                # 网关接口（4个文件）
│   ├── FeishuGateway.java           # 飞书 API 网关
│   ├── MessageListenerGateway.java  # 长连接网关
│   └── TopicMappingGateway.java     # 话题映射网关
├── router/                 # 路由器（1个文件）
│   └── AppRouter.java
├── model/                  # 领域模型（1个文件）
│   └── TopicMapping.java   # 话题映射实体
├── history/                # 历史管理（2个文件）
│   ├── BashHistoryManager.java   # Bash历史管理
│   └── CommandExecution.java      # 命令执行记录
├── validation/             # 验证器（2个文件）
│   └── CommandWhitelistValidator.java  # 命令白名单验证
├── exception/              # 异常定义（4个文件）
│   ├── MessageBizException.java
│   ├── MessageSysException.java
│   ├── MessageInvalidException.java
│   └── ConnectionLostException.java
└── config/                 # 配置类（2个文件）
    ├── FeishuConfig.java
    └── FeishuReplyProperties.java
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
    List<String> getAppAliases();         // 命令别名（新增）
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

**应用注册**：
- `AppRegistry`: 自动扫描 `@Component` 注解的应用类
- `AppRouter`: 根据命令前缀或别名路由到对应应用

### 2. 消息处理流程

```
用户消息 (飞书)
    ↓
MessageListenerGateway (接收)
    ↓
BotMessageService.handleMessage() (编排)
    ↓
提取命令前缀 / 别名
    ↓
AppRouter / AppRegistry (查找应用)
    ↓
FishuAppI.execute() (执行)
    ↓
FeishuGateway (回复)
```

**关键逻辑**：
1. **命令解析**：`extractAppId()` 提取命令前缀
2. **别名匹配**：`findAppByCommandOrAlias()` 支持别名查找
3. **话题映射**：通过 `topicId` 找到对应应用，支持无前缀命令
4. **消息去重**：`MessageDeduplicator` 防止重复处理

### 3. 话题上下文机制

**TopicMapping 实体**：
```java
public class TopicMapping {
    private String topicId;      // 话题ID
    private String appId;        // 应用ID
    private long createdAt;      // 创建时间（毫秒时间戳）
    private long lastActiveAt;   // 最后活跃时间
}
```

**功能**：
- 话题与应用绑定：在话题中自动路由到对应应用
- 无前缀命令：在绑定的话题中可直接输入命令（如 `pwd` 而非 `/bash pwd`）
- 持久化：使用 SQLite 存储（`feishu-bot-infrastructure` 实现）

---

## 🔑 关键约定

### 1. 新建应用规范

**3步创建新应用**：

```java
@Component  // 必须添加
public class YourApp implements FishuAppI {

    @Override
    public String getAppId() {
        return "yourapp";  // 唯一ID，决定命令前缀 /yourapp
    }

    @Override
    public String execute(Message message) {
        // 业务逻辑
        return "result";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("alias1", "alias2");  // 可选别名
    }
}
```

**放置位置**：`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/`

### 2. 网关接口模式

**接口定义在 domain**，实现在 infrastructure：

```java
// domain/gateway/FeishuGateway.java
public interface FeishuGateway {
    SendResult sendMessage(Message message, String content, String topicId);
    SendResult sendDirectReply(Message message, String content);
}

// infrastructure/gateway/FeishuGatewayImpl.java
@Component
public class FeishuGatewayImpl implements FeishuGateway {
    // 使用飞书SDK实现
}
```

**为什么这样做**：
- 领域层定义"需要什么"
- 基础设施层决定"怎么实现"
- 符合依赖倒置原则（DIP）

### 3. 命令别名机制（新增）

**接口方法**：
```java
default List<String> getAppAliases() {
    return Collections.emptyList();  // 默认无别名
}
```

**查找逻辑**：
```java
// BotMessageService.java
private FishuAppI findAppByCommandOrAlias(String command) {
    for (FishuAppI app : appRegistry.getAllApps()) {
        // 检查主命令
        if (app.getAppId().equalsIgnoreCase(command)) {
            return app;
        }
        // 检查别名
        for (String alias : app.getAppAliases()) {
            if (alias.equalsIgnoreCase(command)) {
                return app;
            }
        }
    }
    return null;
}
```

**特点**：
- 大小写不敏感：`/Bash`, `/bash`, `/BASH` 都可以
- 优先级：主命令 > 别名
- 帮助信息：自动显示所有别名

### 4. 禁止模式

| 行为 | 原因 | 后果 |
|------|------|------|
| **跨层依赖** | domain 不能依赖 infrastructure | 代码无法编译（Maven依赖限制） |
| **直接使用 SDK** | 必须通过 Gateway 接口 | 违反架构规范，代码将被拒绝 |
| **应用ID重复** | 必须唯一 | 后注册的应用会覆盖前面的 |
| **命令前缀冲突** | 不同应用不能有相同别名 | 导致路由混乱 |

---

## 📝 代码模式

### 1. 消息处理模板

```java
@Override
public String execute(Message message) {
    String content = message.getContent().trim();
    String[] parts = content.split("\\s+", 2);

    if (parts.length < 2) {
        return getHelp();  // 参数不足
    }

    String command = parts[1].trim();

    // 业务逻辑
    String result = doSomething(command);

    return result;
}
```

### 2. 领域服务模式

**BotMessageService** 是核心编排服务：
- 接收消息
- 路由到应用
- 处理异常
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

# 查看别名匹配日志
grep "通过别名找到应用" /tmp/feishu-run.log

# 查看话题映射日志
grep "话题映射" /tmp/feishu-run.log
```

---

## 📚 相关文档

- [根目录规范](../AGENTS.md) - 项目整体规范
- [基础设施层规范](../feishu-bot-infrastructure/AGENTS.md) - Gateway 实现
- [应用开发指南](../docs/APP_GUIDE.md) - 如何创建新应用
- [命令别名机制](../docs/COMMAND-ALIASES.md) - 别名功能详解

---

## ⚠️ 常见陷阱

1. **忘记添加 @Component**：应用不会被注册
2. **应用ID重复**：导致路由混乱
3. **直接返回 null**：会导致错误回复
4. **不处理异常**：异常会传播到 adapter 层
5. **别名冲突**：不同应用使用相同别名

---

**最后更新**: 2026-02-01

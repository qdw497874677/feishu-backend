# feishu-bot-infrastructure - 基础设施层知识库

**复杂度**: MODERATE (19)
**文件数**: 11 Java 文件
**最后更新**: 2026-02-02

---

## 📋 模块职责

feishu-bot-infrastructure 是飞书机器人的**外部集成层**，包含：

- **Gateway 实现**：FeishuGateway, MessageListenerGateway, TopicMappingGateway
- **策略实现**：DirectReplyStrategy, TopicReplyStrategy, DefaultReplyStrategy
- **防腐层实现**：MessageEventParserImpl（隔离飞书 SDK）
- **配置管理**：FeishuProperties（从 application.yml 读取配置）
- **持久化实现**：SQLite 数据库操作

---

## 📂 目录结构

```
feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── config/                     # 配置类
│   ├── FeishuProperties.java          # 飞书配置属性
│   ├── AsyncConfig.java               # 异步执行器配置
│   └── DomainServiceConfig.java       # 领域服务配置（含策略工厂）
├── gateway/                    # Gateway 实现
│   ├── FeishuGatewayImpl.java          # 飞书 API 实现
│   ├── MessageListenerGatewayImpl.java # 长连接实现
│   ├── TopicMappingGatewayImpl.java    # 话题映射（文件模式）
│   └── TopicMappingSqliteGateway.java  # 话题映射（SQLite模式）
├── reply/                      # 策略实现（新增）
│   ├── DirectReplyStrategy.java    # 直接回复策略
│   ├── TopicReplyStrategy.java     # 话题回复策略
│   └── DefaultReplyStrategy.java   # 默认回复策略
└── parser/                     # 防腐层实现（新增）
    └── MessageEventParserImpl.java # 消息事件解析器
```

---

## 🎯 核心概念

### 1. Gateway 模式

**接口在 domain，实现在 infrastructure**：

```java
// domain/gateway/FeishuGateway.java (接口定义)
public interface FeishuGateway {
    SendResult sendMessage(Message message, String content, String topicId);
    SendResult sendDirectReply(Message message, String content);
}

// infrastructure/gateway/FeishuGatewayImpl.java (实现)
@Component
@Slf4j
public class FeishuGatewayImpl implements FeishuGateway {
    private final Client httpClient;

    @Override
    public SendResult sendMessage(Message message, String content, String topicId) {
        // 使用飞书SDK发送消息
    }
}
```

**优势**：
- 领域层不依赖外部SDK
- 可以轻松切换SDK版本
- 便于单元测试（mock Gateway）

### 2. 策略模式实现

**结构**：
```
infrastructure/reply/
├── DirectReplyStrategy.java    # 直接回复，不创建话题
├── TopicReplyStrategy.java     # 回复到话题（创建或使用）
└── DefaultReplyStrategy.java   # 默认行为，透传 topicId
```

**示例**：
```java
@Component
@Slf4j
public class DirectReplyStrategy implements ReplyStrategy {

    private final FeishuGateway feishuGateway;

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.DIRECT;
    }

    @Override
    public SendResult reply(Message message, String replyContent, String topicId) {
        log.debug("DirectReplyStrategy: 直接回复消息");
        return feishuGateway.sendDirectReply(message, replyContent);
    }
}
```

### 3. 防腐层实现

**目的**：隔离飞书 SDK 变化，保护领域层

**结构**：
```
infrastructure/parser/
└── MessageEventParserImpl.java # 消息事件解析器
```

**职责**：
- 将飞书 SDK 的 P2MessageReceiveV1 事件转换为领域 Message 对象
- 封装 SDK 特定的解析逻辑（正则表达式提取 thread_id/root_id）
- 统一处理 content JSON 格式提取

**示例**：
```java
@Component
@Slf4j
public class MessageEventParserImpl implements MessageEventParser {

    @Override
    @SuppressWarnings("unchecked")
    public <T> Message parse(T rawEvent) {
        if (rawEvent instanceof P2MessageReceiveV1) {
            return parseMessageReceiveEvent((P2MessageReceiveV1) rawEvent);
        }
        throw new IllegalArgumentException("Unsupported event type");
    }
}
```

### 4. 策略工厂配置

**DomainServiceConfig.java** 中配置策略工厂：
```java
@Configuration
public class DomainServiceConfig {

    @Bean
    public ReplyStrategyFactory replyStrategyFactory(List<ReplyStrategy> strategies) {
        return new ReplyStrategyFactory(strategies);
    }
}
```

### 5. 长连接模式

**MessageListenerGatewayImpl** 使用防腐层解析事件：
```java
public MessageListenerGatewayImpl(FeishuProperties properties, 
                                  MessageEventParser messageEventParser) {
    this.messageEventParser = messageEventParser;
    
    this.eventDispatcher = EventDispatcher.newBuilder(...)
        .onP2MessageReceiveV1(event -> {
            if (messageHandler != null) {
                Message message = messageEventParser.parse(event);
                messageHandler.accept(message);
            }
        }).build();
}
```

---

## 🔑 关键约定

### 1. 配置管理

**FeishuProperties 映射 application.yml**：
```java
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {
    private String appid;
    private String appsecret;
    // ...
}
```

### 2. 异步执行配置

**BashApp 使用专用线程池**：
```java
@Configuration
public class AsyncConfig {

    @Bean(name = "bashExecutor")
    public Executor bashExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setThreadNamePrefix("bash-async-");
        executor.initialize();
        return executor;
    }
}
```

### 3. 禁止模式

| 行为 | 原因 | 后果 |
|------|------|------|
| **直接在 domain 中使用 SDK** | 违反分层原则 | 无法编译 |
| **硬编码配置** | 应使用 FeishuProperties | 维护困难 |
| **阻塞 WebSocket 线程** | 长时间操作使用异步 | 影响响应速度 |
| **忽略异常** | 日志记录但向上抛出 | 问题被隐藏 |

---

## 📝 代码模式

### Gateway 实现模板

```java
@Component
@Slf4j
public class SomeGatewayImpl implements SomeGateway {

    private final ExternalServiceClient client;

    public SomeGatewayImpl(FeishuProperties properties) {
        this.client = initializeClient(properties);
    }

    @Override
    public Result doSomething(Request req) {
        try {
            Response response = client.call(req);
            return convertToResult(response);
        } catch (Exception e) {
            log.error("External service call failed", e);
            throw new SystemException("Service unavailable", e);
        }
    }
}
```

### 策略实现模板

```java
@Component
@Slf4j
public class SomeReplyStrategy implements ReplyStrategy {

    private final FeishuGateway feishuGateway;

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.CUSTOM;
    }

    @Override
    public SendResult reply(Message message, String content, String topicId) {
        log.debug("CustomReplyStrategy: 处理回复");
        // 策略特定的回复逻辑
        return feishuGateway.sendMessage(message, content, topicId);
    }
}
```

---

## 🔍 调试技巧

```bash
# 查看配置加载日志
grep "FeishuProperties" /tmp/feishu-run.log

# 查看策略选择日志
grep "ReplyStrategy" /tmp/feishu-run.log

# 查看防腐层解析日志
grep "MessageEventParser" /tmp/feishu-run.log

# 查看WebSocket连接状态
grep "connected to wss://" /tmp/feishu-run.log

# 查看数据库操作日志
grep "SQLite" /tmp/feishu-run.log
```

---

## 📚 相关文档

- [根目录规范](../AGENTS.md) - 项目整体规范
- [领域层规范](../feishu-bot-domain/AGENTS.md) - 领域模型和业务逻辑
- [SQLite 持久化](../docs/SQLITE-PERSISTENCE.md) - 数据库使用指南

---

## ⚠️ 常见陷阱

1. **忘记 @Component**：Bean 不会被注册
2. **配置属性没有 Getter**：Spring 无法绑定
3. **阻塞 WebSocket 线程**：消息处理会超时
4. **不关闭资源**：导致内存泄漏
5. **忽略返回值检查**：错误被忽略

---

## 🚨 特殊注意事项

### SQLite 数据库文件

**位置**：`feishu-bot-start/data/feishu-topic-mappings.db`

**Git 管理**：
- 默认**不忽略**（可以提交到 Git）
- 如需忽略，在 `.gitignore` 中添加：`*.db`

**表结构**：
```sql
CREATE TABLE topic_mapping (
    topic_id TEXT PRIMARY KEY,
    app_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL
);

CREATE INDEX idx_topic_mapping_app_id ON topic_mapping(app_id);
```

### 飞书 SDK 版本

**当前版本**：`2.5.2`

**升级步骤**：
1. 修改 `pom.xml` 中的版本号
2. 运行 `mvn clean install`
3. 检查 API 变更日志
4. 测试关键功能

---

**最后更新**: 2026-02-02

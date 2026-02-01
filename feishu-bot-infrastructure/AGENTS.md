# feishu-bot-infrastructure - 基础设施层知识库

**复杂度**: MODERATE (19)
**文件数**: 7 Java 文件
**最后更新**: 2026-02-01

---

## 📋 模块职责

feishu-bot-infrastructure 是飞书机器人的**外部集成层**，包含：

- **Gateway 实现**：FeishuGateway, MessageListenerGateway, TopicMappingGateway
- **配置管理**：FeishuProperties（从 application.yml 读取配置）
- **外部系统集成**：飞书 SDK（oapi-sdk）
- **持久化实现**：SQLite 数据库操作
- **异步配置**：线程池配置

---

## 📂 目录结构

```
feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── config/                     # 配置类（3个文件）
│   ├── FeishuProperties.java          # 飞书配置属性
│   ├── AsyncConfig.java               # 异步执行器配置
│   └── DomainServiceConfig.java       # 领域服务配置
└── gateway/                    # Gateway 实现（4个文件）
    ├── FeishuGatewayImpl.java          # 飞书 API 实现
    ├── MessageListenerGatewayImpl.java # 长连接实现
    ├── TopicMappingGatewayImpl.java    # 话题映射（文件模式）
    └── TopicMappingSqliteGateway.java  # 话题映射（SQLite模式）
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
public class FeishuGatewayImpl implements FeishuGateway {
    private final Client httpClient;  // 飞书SDK客户端

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

### 2. 飞书 SDK 集成

**依赖**：`oapi-sdk:2.5.2`

**初始化**：
```java
// FeishuGatewayImpl.java
private final Client httpClient = Client.newBuilder()
    .appId(feishuProperties.getAppid())
    .appSecret(feishuProperties.getAppsecret())
    .build();
```

**配置来源**：
```yaml
# application.yml
feishu:
  appid: ${FEISHU_APPID:your_app_id}
  appsecret: ${FEISHU_APPSECRET:your_app_secret}
  mode: listener
  listener:
    enabled: true
```

**关键类**：
- `Client`: 飞书SDK客户端
- `MessageService`: 消息发送API
- `EventDispatcher`: 事件分发器

### 3. 长连接模式

**MessageListenerGatewayImpl** 实现 WebSocket 长连接：

```java
@Override
public void startListener() {
    Event event = Event.newBuilder()
        .messageListener(event -> {
            // 接收飞书消息事件
            handleIncomingEvent(event);
        })
        .build();

    // 启动WebSocket连接
    event.start();
}
```

**与 WebHook 的区别**：
| 特性 | 长连接（✅） | WebHook（❌） |
|------|-------------|-------------|
| 公网IP | 不需要 | 必需 |
| 域名 | 不需要 | 必需 |
| 稳定性 | 高 | 低 |
| 实时性 | 高 | 中 |

### 4. 数据持久化

**两种实现**（通过条件化配置切换）：

#### 文件模式（TopicMappingGatewayImpl）
```java
@Component
@ConditionalOnProperty(name = "feishu.topic-mapping.storage-type", havingValue = "file")
public class TopicMappingGatewayImpl implements TopicMappingGateway {
    private final Map<String, TopicMapping> mappings = new ConcurrentHashMap<>();
    private static final String STORAGE_FILE = "/tmp/feishu-topic-mappings.json";

    // 使用Gson序列化到JSON文件
}
```

#### SQLite 模式（TopicMappingSqliteGateway，默认）
```java
@Component
@ConditionalOnProperty(name = "feishu.topic-mapping.storage-type", havingValue = "sqlite", matchIfMissing = true)
public class TopicMappingSqliteGateway implements TopicMappingGateway {
    private final JdbcTemplate jdbcTemplate;

    // 使用HikariCP + SQLite
    // 数据库文件：data/feishu-topic-mappings.db
}
```

**切换方式**：
```yaml
feishu:
  topic-mapping:
    storage-type: sqlite  # 默认
    # storage-type: file  # 切换到文件模式
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
    private String encryptKey;
    private String verificationToken;
    private String mode;
    private ListenerProperties listener;
    private ReplyProperties reply;

    // Getters and Setters
}
```

**环境变量优先级**：
```
环境变量 > application-dev.yml > application.yml
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
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bash-async-");
        executor.initialize();
        return executor;
    }
}
```

**使用方式**：
```java
@Async("bashExecutor")
public void executeCommandAsync(Message message, String command) {
    // 异步执行bash命令
}
```

### 3. 禁止模式

| 行为 | 原因 | 后果 |
|------|------|------|
| **直接在 domain 中使用 SDK** | 违反分层原则 | 无法编译 |
| **硬编码配置** | 应使用 FeishuProperties | 维护困难 |
| **阻塞主线程** | 长时间操作使用异步 | 影响响应速度 |
| **忽略异常** | 日志记录但向上抛出 | 问题被隐藏 |

---

## 📝 代码模式

### 1. Gateway 实现模板

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
            // 调用外部服务
            Response response = client.call(req);
            return convertToResult(response);
        } catch (Exception e) {
            log.error("External service call failed", e);
            throw new SystemException("Service unavailable", e);
        }
    }
}
```

### 2. 配置类模板

```java
@Component
@ConfigurationProperties(prefix = "some.service")
public class SomeProperties {
    private String url;
    private int timeout;
    private boolean enabled;

    // Getters and Setters
    // 必须提供标准Getter/Setter，Spring才能绑定
}
```

---

## 🔍 调试技巧

```bash
# 查看配置加载日志
grep "FeishuProperties" /tmp/feishu-run.log

# 查看WebSocket连接状态
grep "connected to wss://" /tmp/feishu-run.log

# 查看数据库操作日志
grep "SQLite" /tmp/feishu-run.log

# 查看消息发送日志
grep "sendMessage" /tmp/feishu-run.log
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

**最后更新**: 2026-02-01

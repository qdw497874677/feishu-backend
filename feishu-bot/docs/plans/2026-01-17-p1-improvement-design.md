# P1 优先级改进设计文档

## 文档信息

- **项目**: 飞书机器人后端 (Feishu Bot Backend)
- **设计版本**: 1.0
- **创建日期**: 2026-01-17
- **设计范围**: P1 高优先级改进（异步消息处理、全局异常处理、数据持久化）

---

## 背景

P0 改进已完成（Webhook 验证、架构优化、单元测试），现在继续实施 P1 优先级改进以提升系统的生产就绪性。

**P1 改进目标**：
1. 解决 Webhook 超时问题
2. 统一错误处理，提升稳定性
3. 实现数据持久化，支持业务功能

---

## 第一部分：异步消息处理（Spring Event 模式）

### 问题分析

当前系统使用同步处理 Webhook 请求，飞书要求 Webhook 在 5 秒内返回响应。如果消息处理逻辑耗时较长（如调用外部 API、复杂计算），会导致超时。

**影响**：
- Webhook 请求超时
- 消息处理失败
- 用户体验差

### 设计方案

使用 Spring Event 实现异步消息处理：

1. **事件定义**（Domain 层）
   ```java
   public class MessageReceivedEvent {
       private String messageId;
       private String content;
       private Sender sender;
       private Map<String, String> metadata;
   }
   ```

2. **事件监听器**（App 层）
   ```java
   @Component
   public class MessageEventListener {

       @Autowired
       private BotMessageService botMessageService;

       @EventListener
       @Async("webhookTaskExecutor")
       public void handleMessage(MessageReceivedEvent event) {
           Message message = new Message(
               event.getMessageId(),
               event.getContent(),
               event.getSender()
           );
           botMessageService.handleMessage(message);
       }
   }
   ```

3. **Webhook Controller 改造**（Adapter 层）
   ```java
   @PostMapping("/webhook")
   public ResponseEntity<String> handleWebhook(@RequestBody String body) {
       // 1. 验证签名
       WebhookValidationResult result = webhookValidator.validate(headers, body);
       if (!result.isValid()) {
           return ResponseEntity.status(401).body("Invalid signature");
       }

       // 2. 解析事件
       FeishuEvent event = parseEvent(body);

       // 3. 发布事件（立即返回）
       MessageReceivedEvent msgEvent = new MessageReceivedEvent(
           event.getMessageId(),
           event.getContent(),
           event.getSender()
       );
       applicationEventPublisher.publishEvent(msgEvent);

       // 4. 立即返回 200
       return ResponseEntity.ok("OK");
   }
   ```

4. **线程池配置**（application.yml）
   ```yaml
   spring:
     task:
       execution:
         pool:
           core-size: 5
           max-size: 20
           queue-capacity: 100
           thread-name-prefix: webhook-async-
   ```

### 优势

- ✅ 解决 Webhook 5 秒超时问题
- ✅ 轻量级实现，无需额外中间件
- ✅ 易于扩展（可添加多个监听器）
- ✅ 解耦 Webhook 接收和消息处理逻辑

### 技术决策

| 选项 | 优势 | 劣势 | 选择 |
|------|------|------|------|
| 简单 @Async | 实现简单 | 缺少监控 | ❌ |
| 消息队列（Kafka/RabbitMQ） | 持久化、可靠 | 需要额外基础设施 | ❌ |
| Spring Event | 轻量级、易扩展 | 无持久化 | ✅ |
| 混合模式 | 灵活 | 复杂 | ❌ |

---

## 第二部分：全局异常处理器（环境区分模式）

### 问题分析

当前异常处理散落在各处，缺少统一的错误处理机制。生产环境和开发环境需要不同级别的错误信息展示。

**影响**：
- 错误处理不一致
- 生产环境暴露敏感信息
- 难以追踪和排查问题

### 设计方案

使用 `@ControllerAdvice` 创建全局异常处理器：

1. **统一错误响应格式**
   ```java
   @Data
   @AllArgsConstructor
   public class ErrorResponse {
       private String code;
       private String message;
       private Object details;
       private Instant timestamp;
   }
   ```

2. **全局异常处理器**
   ```java
   @ControllerAdvice
   @Slf4j
   public class GlobalExceptionHandler {

       @ExceptionHandler(MessageBizException.class)
       public ResponseEntity<ErrorResponse> handleBizException(
           MessageBizException e,
           WebRequest request
       ) {
           log.error("Business exception: {}", e.getErrMsg(), e);
           ErrorResponse error = new ErrorResponse(
               e.getErrCode(),
               e.getErrMsg(),
               null,
               Instant.now()
           );
           return ResponseEntity.badRequest().body(error);
       }

       @ExceptionHandler(Exception.class)
       public ResponseEntity<ErrorResponse> handleSystemException(
           Exception e,
           WebRequest request
       ) {
           log.error("System error", e);

           boolean isDev = isDevelopmentEnvironment();
           ErrorResponse error = new ErrorResponse(
               "SYSTEM_ERROR",
               "系统异常，请稍后重试",
               isDev ? getStackTrace(e) : null,
               Instant.now()
           );
           return ResponseEntity.internalServerError().body(error);
       }

       @ExceptionHandler(MethodArgumentNotValidException.class)
       public ResponseEntity<ErrorResponse> handleValidationException(
           MethodArgumentNotValidException e
       ) {
           Map<String, String> details = new HashMap<>();
           e.getBindingResult().getFieldErrors().forEach(error ->
               details.put(error.getField(), error.getDefaultMessage())
           );

           ErrorResponse error = new ErrorResponse(
               "VALIDATION_ERROR",
               "参数校验失败",
               details,
               Instant.now()
           );
           return ResponseEntity.badRequest().body(error);
       }
   }
   ```

3. **环境区分**
   ```java
   private boolean isDevelopmentEnvironment() {
       String[] activeProfiles = environment.getActiveProfiles();
       return Arrays.stream(activeProfiles)
           .anyMatch(profile -> profile.equals("dev") || profile.equals("test"));
   }
   ```

### 错误响应示例

**生产环境**：
```json
{
  "code": "CONTENT_EMPTY",
  "message": "消息内容不能为空",
  "timestamp": "2026-01-17T17:00:00Z"
}
```

**开发环境**：
```json
{
  "code": "SYSTEM_ERROR",
  "message": "系统异常，请稍后重试",
  "details": {
    "exception": "NullPointerException",
    "message": "Sender cannot be null",
    "stackTrace": "at BotMessageService.handleMessage:45\n  at MessageEventListener.handleMessage:28"
  },
  "timestamp": "2026-01-17T17:00:00Z"
}
```

### 优势

- ✅ 统一错误处理逻辑
- ✅ 生产环境不暴露敏感信息
- ✅ 开发环境提供详细调试信息
- ✅ 支持多种异常类型处理

---

## 第三部分：数据持久化（完整模式 + MongoDB）

### 问题分析

当前系统无数据持久化能力，无法存储消息历史、用户上下文等关键数据。

**影响**：
- 无法查询历史消息
- 无法维护用户会话
- 无法进行数据分析和审计

### 设计方案

使用 MongoDB 存储完整的业务数据，包括消息历史、用户上下文、扩展点配置和审计日志。

#### 1. 消息历史表（MessageHistory）

**实体设计**：
```java
@Document(collection = "message_history")
@Data
public class MessageHistory {
    @Id
    private String id;
    private String messageId;
    private String senderOpenId;
    private String senderUserId;
    private String senderName;
    private String content;
    private MessageType messageType;
    private MessageStatus status;
    private String replyContent;
    private Instant createdAt;
    private Instant processedAt;
    private Map<String, Object> metadata;
}
```

**索引**：
- `senderOpenId` - 支持按用户查询
- `createdAt` - 支持时间范围查询
- `messageId` - 唯一索引，防止重复

**用途**：记录所有消息交互，支持历史查询和审计

#### 2. 用户上下文表（UserContext）

**实体设计**：
```java
@Document(collection = "user_context")
@Data
public class UserContext {
    @Id
    private String id;
    private String userId;
    private Map<String, Object> sessionData;
    private Map<String, Object> preferences;
    private Instant lastInteraction;
    private List<Map<String, Object>> conversationHistory;
    private Instant createdAt;
    private Instant updatedAt;
}
```

**索引**：
- `userId` - 唯一索引

**用途**：存储用户会话数据、偏好设置、对话历史

#### 3. 扩展点配置表（ExtensionConfig）

**实体设计**：
```java
@Document(collection = "extension_config")
@Data
public class ExtensionConfig {
    @Id
    private String id;
    private String extensionId;
    private String extensionName;
    private String bizId;
    private String useCase;
    private Boolean enabled;
    private Map<String, Object> config;
    private Integer priority;
    private Instant createdAt;
    private Instant updatedAt;
}
```

**索引**：
- `extensionId` - 唯一索引
- `bizId` + `useCase` - 复合索引

**用途**：动态配置和管理扩展点

#### 4. 审计日志表（AuditLog）

**实体设计**：
```java
@Document(collection = "audit_log")
@Data
public class AuditLog {
    @Id
    private String id;
    private String userId;
    private String action;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> beforeState;
    private Map<String, Object> afterState;
    private String ipAddress;
    private String userAgent;
    private Instant timestamp;
}
```

**索引**：
- `userId` - 支持按用户查询
- `action` - 支持按操作类型查询
- `timestamp` - 支持时间范围查询

**用途**：记录关键操作，支持安全审计

### 技术实现

1. **依赖配置**（pom.xml）
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-mongodb</artifactId>
   </dependency>
   ```

2. **Repository 接口**
   ```java
   public interface MessageHistoryRepository extends MongoRepository<MessageHistory, String> {
       List<MessageHistory> findBySenderOpenIdOrderByCreatedAtDesc(String senderOpenId);
       List<MessageHistory> findByCreatedAtBetween(Instant start, Instant end);
   }

   public interface UserContextRepository extends MongoRepository<UserContext, String> {
       Optional<UserContext> findByUserId(String userId);
   }

   public interface ExtensionConfigRepository extends MongoRepository<ExtensionConfig, String> {
       List<ExtensionConfig> findByBizIdAndUseCaseAndEnabled(
           String bizId, String useCase, Boolean enabled
       );
   }

   public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
       List<AuditLog> findByUserIdOrderByTimestampDesc(String userId);
       List<AuditLog> findByActionAndTimestampBetween(
           String action, Instant start, Instant end
       );
   }
   ```

3. **MongoDB 配置**（application.yml）
   ```yaml
   spring:
     data:
       mongodb:
         host: localhost
         port: 27017
         database: feishu_bot
         username: ${MONGODB_USERNAME}
         password: ${MONGODB_PASSWORD}
   ```

4. **软删除和版本控制**
   ```java
   @Document(collection = "message_history")
   @Data
   public class MessageHistory {
       private Boolean deleted = false;
       private Integer version = 0;

       public void incrementVersion() {
           this.version++;
       }

       public void markDeleted() {
           this.deleted = true;
           this.incrementVersion();
       }
   }
   ```

### 优势

- ✅ 完整的数据持久化能力
- ✅ 支持灵活的查询和索引
- ✅ JSON 数据类型适合非结构化数据
- ✅ 易于扩展和维护

---

## 架构集成

### 层次结构

```
Adapter Layer (适配器层)
    ↓ FeishuWebhookController
    ↓ 发布 MessageReceivedEvent

App Layer (应用层)
    ↓ MessageEventListener (异步)
    ↓ GlobalExceptionHandler (异常处理)

Domain Layer (领域层)
    ↓ MessageReceivedEvent (事件定义)
    ↓ 实体定义（MessageHistory, UserContext 等）

Infrastructure Layer (基础设施层)
    ↓ MongoDB Repository 实现
    ↓ Spring Data MongoDB
```

### 数据流

1. **Webhook 接收流程**：
   Webhook → FeishuWebhookController → 验证签名 → 发布事件 → 立即返回 200

2. **异步处理流程**：
   MessageReceivedEvent → MessageEventListener → BotMessageService → 处理消息 → 保存到 MongoDB

3. **异常处理流程**：
   异常发生 → GlobalExceptionHandler → 根据异常类型处理 → 返回标准错误响应

4. **数据持久化流程**：
   消息处理 → 调用 Repository → 保存到 MongoDB → 记录审计日志

---

## 实施计划

### 阶段 1：异步消息处理（1-2 天）

1. 创建 MessageReceivedEvent
2. 创建 MessageEventListener
3. 修改 FeishuWebhookController
4. 配置线程池
5. 编写单元测试

### 阶段 2：全局异常处理（2-3 天）

1. 创建 ErrorResponse
2. 创建 GlobalExceptionHandler
3. 配置环境区分
4. 编写单元测试

### 阶段 3：数据持久化（3-5 天）

1. 配置 MongoDB 依赖
2. 创建实体类（4 个表）
3. 创建 Repository 接口
4. 实现数据访问逻辑
5. 集成到业务流程
6. 编写集成测试

### 阶段 4：集成测试和文档（1-2 天）

1. 端到端测试
2. 性能测试
3. 编写部署文档
4. 更新 API 文档

**总预估工作量**：7-12 天

---

## 技术栈

- **Spring Event**: 3.2.1
- **Spring Data MongoDB**: 3.2.1
- **MongoDB**: 6.0+
- **JUnit 5**: 5.10.1
- **Mockito**: 5.8.0
- **AssertJ**: 3.24.2

---

## 风险和缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 异步处理失败 | 消息丢失 | 实现重试机制 + 死信队列 |
| MongoDB 连接失败 | 无法持久化 | 实现降级策略 + 监控告警 |
| 异常处理不当 | 敏感信息泄露 | 严格环境区分 + 代码审查 |
| 性能瓶颈 | 响应延迟 | 线程池调优 + 压力测试 |

---

## 成功标准

1. ✅ Webhook 响应时间 < 500ms
2. ✅ 异常处理覆盖所有异常类型
3. ✅ 生产环境不暴露敏感信息
4. ✅ MongoDB 持久化成功率 > 99%
5. ✅ 单元测试覆盖率 > 80%
6. ✅ 集成测试通过

---

## 参考资料

- Spring Event 文档: https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#context-functionality-events
- Spring Data MongoDB: https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/
- @ControllerAdvice: https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-customer-arguments
- MongoDB 最佳实践: https://www.mongodb.com/docs/manual/administration/production-notes/

---

**文档版本**: 1.0
**最后更新**: 2026-01-17
**状态**: 设计完成，待审核

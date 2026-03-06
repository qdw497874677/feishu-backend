# 架构重构设计文档 v2 - 平台抽象化与服务拆分

**日期**: 2026-03-06  
**作者**: OpenCode  
**状态**: 已评审，待实施  
**版本**: v2（基于评审反馈修订）

---

## 📋 概述

本文档描述了飞书机器人项目的架构重构方案，核心目标是：
1. **平台抽象化** - 应用层不依赖特定平台（飞书）
2. **服务拆分** - 解决 BotMessageService 职责过重问题
3. **统一路由** - 移除双路由系统，统一到新架构

**预期收益**：
- ✅ 易于适配新平台（钉钉、企微）
- ✅ 代码职责清晰，可维护性提升
- ✅ 消除技术债，为未来扩展打好基础

**工作量**: 4-5 天（基于评审后重新评估）  
**风险**: 低（无数据迁移，简化设计）

---

## 🎯 核心设计决策

### 决策 #1：扩展现有 UnifiedCommand（不引入新概念）

**问题**: 评审指出 `AppExecutionContext` 与现有 `UnifiedCommand` 职责重复

**决策**: **合并设计** - 扩展 `UnifiedCommand` 而非引入新概念

```java
/**
 * 统一命令模型（扩展版）
 * 
 * 新增字段：
 * - platform: 平台标识（feishu/dingtalk/wework）
 * - context: 上下文数据（Map<String, Object>）
 */
public class UnifiedCommand {
    // 现有字段
    private String appId;
    private String subCommand;
    private String[] args;
    private String openId;      // 用户标识
    private String topicId;     // 上下文标识（已存在）
    private String messageId;
    private EventSource source;
    
    // 新增字段
    private String platform;    // 平台标识：feishu/dingtalk/wework
    private Map<String, Object> context;  // 上下文数据（不可变）
    
    // ============ 新增便捷方法 ============
    
    /**
     * 获取上下文标识（topicId 的别名）
     */
    public String getContextId() {
        return topicId;
    }
    
    /**
     * 获取用户标识（openId 的别名）
     */
    public String getUserId() {
        return openId;
    }
    
    /**
     * 获取平台标识
     */
    public String getPlatform() {
        return platform != null ? platform : "feishu";  // 默认飞书
    }
    
    /**
     * 获取上下文数据（不可变副本）
     */
    public Map<String, Object> getContext() {
        return context != null 
            ? Collections.unmodifiableMap(context)
            : Collections.emptyMap();
    }
    
    /**
     * 安全更新上下文（返回新对象）
     * 
     * 使用示例：
     * UnifiedCommand updated = command.withContext("sessionId", "ses_123");
     */
    public UnifiedCommand withContext(String key, Object value) {
        Map<String, Object> newContext = new HashMap<>(this.context != null ? this.context : Map.of());
        newContext.put(key, value);
        
        return UnifiedCommand.builder()
            .appId(this.appId)
            .subCommand(this.subCommand)
            .args(this.args)
            .openId(this.openId)
            .topicId(this.topicId)
            .messageId(this.messageId)
            .source(this.source)
            .platform(this.platform)
            .context(Collections.unmodifiableMap(newContext))  // 不可变
            .build();
    }
    
    // Builder 模式（使用 Lombok）
    @Builder
    public static UnifiedCommand of(...) { ... }
}
```

**关键改进**：
- ✅ 不引入新概念，复用 `UnifiedCommand`
- ✅ 不可变设计，线程安全
- ✅ 最小化改动，风险低
- ✅ 向后兼容（旧代码仍可使用）

---

### 决策 #2：不可变设计保证并发安全

**问题**: 评审指出 `metadata` 修改非线程安全

**决策**: **不可变设计 + withContext() 方法**

```java
// ❌ 错误用法（编译期阻止）
Map<String, Object> ctx = command.getContext();
ctx.put("key", value);  // UnsupportedOperationException（不可修改）

// ✅ 正确用法（函数式风格）
UnifiedCommand updated = command.withContext("sessionId", "ses_123");

// ✅ 链式更新
UnifiedCommand updated = command
    .withContext("sessionId", "ses_123")
    .withContext("project", "feishu-backend");
```

**线程安全保证**：
1. `context` 字段是 `final` 且不可变
2. `getContext()` 返回不可修改的副本
3. `withContext()` 创建新对象（无共享状态）

---

### 决策 #3：分层错误处理 + 恢复策略

**问题**: 评审指出缺少完整的错误处理策略

**决策**: **分层错误 + 恢复策略**

```java
// 1. 错误类型枚举
public enum ContextError {
    // 平台层错误
    PLATFORM_EVENT_INVALID("平台事件格式无效"),
    PLATFORM_ADAPTER_NOT_FOUND("未找到平台适配器"),
    
    // 上下文错误
    CONTEXT_NOT_FOUND("上下文不存在"),
    CONTEXT_EXPIRED("上下文已过期"),
    
    // 应用层错误
    APP_NOT_FOUND("应用不存在"),
    APP_EXECUTION_FAILED("应用执行失败"),
    APP_TIMEOUT("应用执行超时"),
    
    // 回复层错误
    REPLY_FAILED("回复发送失败");
    
    private final String description;
}

// 2. 统一异常类
public class ContextException extends RuntimeException {
    private final ContextError error;
    private final String contextId;
    private final RecoveryStrategy recovery;
    private final boolean retryable;
    
    public ContextException(ContextError error, String contextId, 
                           RecoveryStrategy recovery, Throwable cause) {
        super(String.format("%s [contextId=%s]", error.getDescription(), contextId), cause);
        this.error = error;
        this.contextId = contextId;
        this.recovery = recovery;
        this.retryable = recovery == RecoveryStrategy.RETRY;
    }
    
    // Getters
    public ContextError getError() { return error; }
    public String getContextId() { return contextId; }
    public RecoveryStrategy getRecovery() { return recovery; }
    public boolean isRetryable() { return retryable; }
}

// 3. 恢复策略
public enum RecoveryStrategy {
    NONE,           // 不可恢复，记录日志并告警
    RETRY,          // 自动重试（3次）
    FALLBACK,       // 降级处理（返回友好提示）
    USER_ACTION     // 需要用户操作（显示引导）
}

// 4. 全局异常处理器
@Slf4j
@ControllerAdvice
public class ContextExceptionHandler {
    
    private final ReplyCoordinator replyCoordinator;
    private final AlertService alertService;
    
    @ExceptionHandler(ContextException.class)
    public void handle(ContextException e, UnifiedCommand command) {
        // 结构化日志
        log.error("Context error: error={}, contextId={}, recovery={}", 
            e.getError(), e.getContextId(), e.getRecovery(), e);
        
        // 根据恢复策略处理
        switch (e.getRecovery()) {
            case RETRY -> retryWithBackoff(command, e);
            case FALLBACK -> sendFallbackMessage(command);
            case USER_ACTION -> sendUserGuidance(command, e);
            case NONE -> logAndAlert(e);
        }
    }
    
    private void sendFallbackMessage(UnifiedCommand command) {
        String fallbackMsg = "⚠️ 服务暂时不可用，请稍后重试\n\n" +
                            "如果问题持续，请联系管理员";
        replyCoordinator.sendReply(command, BizResult.failure(fallbackMsg));
    }
    
    private void sendUserGuidance(UnifiedCommand command, ContextException e) {
        String guidance = switch (e.getError()) {
            case CONTEXT_EXPIRED -> 
                "⏰ 会话已过期\n\n" +
                "请重新初始化：\n" +
                "1. `/opencode p` 查看项目\n" +
                "2. `/opencode s <项目>` 查看会话\n" +
                "3. `/opencode sc <sessionId>` 绑定会话";
            
            case APP_NOT_FOUND ->
                "❓ 未找到应用\n\n" +
                "使用 `/help` 查看可用命令";
            
            default -> 
                "⚠️ 操作失败：" + e.getError().getDescription();
        };
        
        replyCoordinator.sendReply(command, BizResult.failure(guidance));
    }
    
    private void logAndAlert(ContextException e) {
        alertService.sendAlert(
            "Context Error",
            String.format("Error: %s\nContextId: %s\nMessage: %s",
                e.getError(), e.getContextId(), e.getMessage())
        );
    }
    
    private void retryWithBackoff(UnifiedCommand command, ContextException e) {
        // 指数退避重试逻辑
        // 实现略...
    }
}
```

**使用示例**：
```java
@Service
public class AppContextManager {
    
    public Optional<UnifiedCommand> loadContext(String contextId) {
        try {
            return contextGateway.findById(contextId)
                .map(this::toCommand);
        } catch (DataAccessException e) {
            throw new ContextException(
                ContextError.CONTEXT_NOT_FOUND,
                contextId,
                RecoveryStrategy.FALLBACK,  // 降级处理
                e
            );
        }
    }
}
```

---

### 决策 #4：不迁移数据，完全忽略旧数据

**问题**: 评审指出数据迁移方案复杂且风险高

**决策**: **不迁移数据，完全忽略旧数据**

**实施方案**：
```
1. 部署新版本
   ├─ 创建新表 app_execution_context
   └─ 删除旧表 topic_mapping（无需备份）

2. 用户影响
   ├─ OpenCodeApp: 会话绑定丢失，需重新执行 /opencode sc
   ├─ BashApp: 工作目录和历史丢失，需重新开始
   └─ 其他应用: 无影响（无状态）

3. 用户通知
   └─ 不主动通知（静默处理）
      └─ 旧话题当作新话题处理
      └─ 用户自然发现需要重新初始化
```

**优势**：
- ✅ 简化实施（省去3-5天迁移工作）
- ✅ 零风险（无数据不一致）
- ✅ 代码更简单
- ✅ 数据更干净

**影响范围**：
- 仅影响有状态应用（OpenCodeApp、BashApp）
- 无状态应用（TimeApp、HelpApp）无影响
- 用户重新初始化成本很低（1-2分钟）

---

## 🏗️ 架构设计

### 服务层拆分

将 BotMessageService（362行）拆分为职责单一的组件：

```java
// 1. 消息处理协调器 - 轻量级协调者
@Slf4j
@Service
public class MessageProcessingCoordinator {
    
    private final PlatformContextAdapterRegistry adapterRegistry;
    private final UnifiedCommandRouter commandRouter;
    private final ReplyCoordinator replyCoordinator;
    private final AppContextManager contextManager;
    
    /**
     * 处理平台事件
     */
    public void process(Object platformEvent) {
        try {
            // 1. 查找平台适配器
            PlatformContextAdapter adapter = adapterRegistry.findAdapter(platformEvent)
                .orElseThrow(() -> new ContextException(
                    ContextError.PLATFORM_ADAPTER_NOT_FOUND,
                    null,
                    RecoveryStrategy.NONE,
                    null
                ));
            
            // 2. 转换为统一命令
            UnifiedCommand command = adapter.adapt(platformEvent);
            
            // 3. 加载上下文（如果存在）
            if (command.getContextId() != null) {
                command = contextManager.loadContext(command.getContextId())
                    .orElse(command);  // 无上下文则使用原始命令
            }
            
            // 4. 路由到应用执行
            BizResult result = commandRouter.route(command);
            
            // 5. 协调回复
            replyCoordinator.sendReply(command, result);
            
            // 6. 保存上下文（如果有变化）
            if (command.getContext() != null && !command.getContext().isEmpty()) {
                contextManager.saveContext(command);
            }
            
        } catch (ContextException e) {
            throw e;  // 由全局处理器处理
        } catch (Exception e) {
            throw new ContextException(
                ContextError.APP_EXECUTION_FAILED,
                null,
                RecoveryStrategy.FALLBACK,
                e
            );
        }
    }
}

// 2. 上下文管理器 - 负责持久化
@Slf4j
@Service
public class AppContextManager {
    
    private final AppContextGateway contextGateway;
    
    /**
     * 加载上下文
     */
    public Optional<UnifiedCommand> loadContext(String contextId) {
        return contextGateway.findById(contextId)
            .map(this::enrichCommand);
    }
    
    /**
     * 保存上下文
     */
    public void saveContext(UnifiedCommand command) {
        if (command.getContextId() == null) {
            log.warn("Cannot save context: contextId is null");
            return;
        }
        
        try {
            ContextData data = ContextData.builder()
                .contextId(command.getContextId())
                .appId(command.getAppId())
                .userId(command.getUserId())
                .platform(command.getPlatform())
                .contextData(command.getContext())
                .lastActiveAt(System.currentTimeMillis())
                .build();
            
            contextGateway.save(data);
            
            log.info("Context saved: contextId={}, appId={}", 
                command.getContextId(), command.getAppId());
            
        } catch (Exception e) {
            log.error("Failed to save context: contextId={}", command.getContextId(), e);
            // 不抛异常，避免影响主流程
        }
    }
    
    private UnifiedCommand enrichCommand(ContextData data) {
        return UnifiedCommand.builder()
            .appId(data.getAppId())
            .openId(data.getUserId())
            .topicId(data.getContextId())
            .platform(data.getPlatform())
            .context(data.getContextData())
            .build();
    }
}

// 3. 回复协调器 - 统一回复逻辑
@Slf4j
@Service
public class ReplyCoordinator {
    
    private final PlatformContextAdapterRegistry adapterRegistry;
    
    /**
     * 发送回复
     */
    public void sendReply(UnifiedCommand command, BizResult result) {
        try {
            PlatformContextAdapter adapter = adapterRegistry.getAdapter(command.getPlatform());
            
            adapter.reply(command, result);
            
            log.debug("Reply sent: platform={}, contextId={}, success={}", 
                command.getPlatform(), command.getContextId(), result.isSuccess());
            
        } catch (Exception e) {
            log.error("Failed to send reply: platform={}, contextId={}", 
                command.getPlatform(), command.getContextId(), e);
            
            // 不抛异常，回复失败不应中断流程
        }
    }
}
```

**关键改进**：
- 每个组件职责单一
- 完全基于 `UnifiedCommand` 工作
- 易于测试和替换
- 错误处理完善

---

## 🔌 平台适配器设计

### 核心接口

```java
/**
 * 平台适配器 - 负责平台事件和统一命令的双向转换
 */
public interface PlatformContextAdapter<T> {
    
    /**
     * 将平台事件转换为统一命令
     */
    UnifiedCommand adapt(T platformEvent);
    
    /**
     * 发送回复到平台
     */
    void reply(UnifiedCommand command, BizResult result);
    
    /**
     * 确定回复模式（由平台决定）
     */
    ReplyMode determineReplyMode(UnifiedCommand command);
    
    /**
     * 是否支持该类型的事件
     */
    boolean supports(T platformEvent);
    
    /**
     * 获取平台标识
     */
    String getPlatformId();
}
```

### 飞书适配器实现

```java
@Slf4j
@Component
public class FeishuContextAdapter implements PlatformContextAdapter<P2MessageReceiveV1> {
    
    private final FeishuGateway feishuGateway;
    private final MessageEventParser eventParser;
    
    @Override
    public UnifiedCommand adapt(P2MessageReceiveV1 event) {
        // 1. 解析消息
        Message message = eventParser.parse(event);
        
        // 2. 构建统一命令
        return UnifiedCommand.builder()
            .appId(extractAppId(message))  // 从内容中提取
            .subCommand(extractSubCommand(message))
            .args(extractArgs(message))
            .openId(message.getSender().getOpenId())
            .topicId(message.getTopicId())
            .messageId(message.getMessageId())
            .source(EventSource.MESSAGE)
            .platform("feishu")
            .context(Map.of(
                "chatId", message.getChatId(),
                "rootId", message.getRootId() != null ? message.getRootId() : ""
            ))
            .build();
    }
    
    @Override
    public void reply(UnifiedCommand command, BizResult result) {
        ReplyMode mode = determineReplyMode(command);
        
        String messageId = command.getMessageId();
        String content = result.getContent();
        String topicId = command.getContextId();
        
        try {
            switch (mode) {
                case TOPIC -> feishuGateway.sendReply(messageId, content, topicId);
                case DIRECT -> feishuGateway.sendDirectReply(messageId, content);
                default -> feishuGateway.sendReply(messageId, content, topicId);
            }
            
            log.debug("Feishu reply sent: mode={}, topicId={}", mode, topicId);
            
        } catch (Exception e) {
            throw new ContextException(
                ContextError.REPLY_FAILED,
                command.getContextId(),
                RecoveryStrategy.NONE,
                e
            );
        }
    }
    
    @Override
    public ReplyMode determineReplyMode(UnifiedCommand command) {
        // 飞书逻辑：有 topicId 用 TOPIC，否则用 DIRECT
        return command.getContextId() != null && !command.getContextId().isEmpty()
            ? ReplyMode.TOPIC
            : ReplyMode.DIRECT;
    }
    
    @Override
    public boolean supports(P2MessageReceiveV1 event) {
        return event != null && event.getEvent() != null;
    }
    
    @Override
    public String getPlatformId() {
        return "feishu";
    }
    
    // 私有辅助方法
    private String extractAppId(Message message) { ... }
    private String extractSubCommand(Message message) { ... }
    private String[] extractArgs(Message message) { ... }
}
```

**关键设计点**：
- 每个平台一个适配器
- 适配器负责双向转换（事件 → 命令，结果 → 回复）
- 回复模式由平台决定
- 错误统一包装为 ContextException

---

## 💾 数据持久化设计

### 数据库表结构

```sql
-- 上下文数据表
CREATE TABLE app_execution_context (
    context_id TEXT PRIMARY KEY NOT NULL,
    app_id TEXT NOT NULL,
    user_id TEXT,
    platform TEXT NOT NULL DEFAULT 'feishu',
    context_data TEXT,  -- JSON 格式存储应用自定义数据
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL,
    version INTEGER DEFAULT 1
);

CREATE INDEX idx_context_app ON app_execution_context(app_id);
CREATE INDEX idx_context_platform ON app_execution_context(platform);
CREATE INDEX idx_context_user ON app_execution_context(user_id);
CREATE INDEX idx_context_active ON app_execution_context(last_active_at);
```

### Gateway 接口

```java
/**
 * 上下文数据访问接口
 */
public interface AppContextGateway {
    
    /**
     * 根据上下文ID查找
     */
    Optional<ContextData> findById(String contextId);
    
    /**
     * 保存上下文
     */
    void save(ContextData data);
    
    /**
     * 删除上下文
     */
    void deleteById(String contextId);
    
    /**
     * 清理过期上下文
     */
    int deleteExpiredBefore(long timestamp);
}

/**
 * 上下文数据实体
 */
@Data
@Builder
public class ContextData {
    private String contextId;
    private String appId;
    private String userId;
    private String platform;
    private Map<String, Object> contextData;
    private long createdAt;
    private long lastActiveAt;
    private int version;
}
```

### SQLite 实现

```java
@Slf4j
@Component
public class SqliteAppContextGateway implements AppContextGateway {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void save(ContextData data) {
        String sql = """
            INSERT OR REPLACE INTO app_execution_context 
            (context_id, app_id, user_id, platform, context_data, created_at, last_active_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try {
            String contextJson = objectMapper.writeValueAsString(data.getContextData());
            
            jdbcTemplate.update(sql,
                data.getContextId(),
                data.getAppId(),
                data.getUserId(),
                data.getPlatform(),
                contextJson,
                data.getCreatedAt(),
                data.getLastActiveAt(),
                data.getVersion()
            );
            
            log.debug("Context saved: contextId={}", data.getContextId());
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize context data", e);
        }
    }
    
    @Override
    public Optional<ContextData> findById(String contextId) {
        String sql = "SELECT * FROM app_execution_context WHERE context_id = ?";
        
        try {
            ContextData data = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> contextData = parseContextJson(rs.getString("context_data"));
                
                return ContextData.builder()
                    .contextId(rs.getString("context_id"))
                    .appId(rs.getString("app_id"))
                    .userId(rs.getString("user_id"))
                    .platform(rs.getString("platform"))
                    .contextData(contextData)
                    .createdAt(rs.getLong("created_at"))
                    .lastActiveAt(rs.getLong("last_active_at"))
                    .version(rs.getInt("version"))
                    .build();
            }, contextId);
            
            return Optional.ofNullable(data);
            
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to load context: contextId={}", contextId, e);
            return Optional.empty();
        }
    }
    
    private Map<String, Object> parseContextJson(String json) {
        if (json == null || json.isEmpty()) {
            return Map.of();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse context JSON: {}", json, e);
            return Map.of();
        }
    }
    
    @Override
    public int deleteExpiredBefore(long timestamp) {
        String sql = "DELETE FROM app_execution_context WHERE last_active_at < ?";
        int deleted = jdbcTemplate.update(sql, timestamp);
        
        if (deleted > 0) {
            log.info("Deleted {} expired contexts", deleted);
        }
        
        return deleted;
    }
}
```

---

## 📦 应用迁移策略

### FishuAppI 接口变更

```java
public interface FishuAppI {
    
    /**
     * 应用 ID
     */
    String getAppId();
    
    /**
     * 应用名称
     */
    String getAppName();
    
    /**
     * 执行应用（新版）
     */
    BizResult execute(UnifiedCommand command);
    
    /**
     * 执行应用（旧版 - 已废弃）
     * @deprecated 请使用 {@link #execute(UnifiedCommand)} 代替
     */
    @Deprecated
    default String execute(Message message) {
        return null;
    }
    
    // 其他方法保持不变...
}
```

### 迁移示例

#### 简单应用（TimeApp）

```java
@Slf4j
@Component
public class TimeApp implements FishuAppI {
    
    @Override
    public String getAppId() {
        return "time";
    }
    
    @Override
    public String getAppName() {
        return "时间查询";
    }
    
    @Override
    public BizResult execute(UnifiedCommand command) {
        // 不需要上下文，直接返回当前时间
        return BizResult.success("当前时间: " + LocalDateTime.now());
    }
    
    // 工作量：10 分钟
}
```

#### 复杂应用（OpenCodeApp）

```java
@Slf4j
@Component
public class OpenCodeApp implements FishuAppI {
    
    private final OpenCodeGateway openCodeGateway;
    private final AppContextManager contextManager;
    
    @Override
    public BizResult execute(UnifiedCommand command) {
        String contextId = command.getContextId();
        
        // 1. 从上下文获取 sessionId
        String sessionId = (String) command.getContext().get("sessionId");
        
        if (sessionId == null) {
            // 没有会话，提示用户初始化
            return BizResult.failure(
                "❌ 话题未初始化\n\n" +
                "请先绑定会话：\n" +
                "1. `/opencode p` 查看项目\n" +
                "2. `/opencode s <项目>` 查看会话\n" +
                "3. `/opencode sc <sessionId>` 绑定会话"
            );
        }
        
        // 2. 执行对话
        String prompt = extractPrompt(command);
        String result = openCodeGateway.executeCommand(prompt, sessionId, 60);
        
        // 3. 更新上下文（如果需要）
        // sessionId 不变，无需更新
        
        return BizResult.success(result);
    }
    
    // 工作量：1-2 小时
}
```

#### 状态应用（BashApp）

```java
@Slf4j
@Component
public class BashApp implements FishuAppI {
    
    private final CommandWhitelistValidator validator;
    private final FeishuGateway feishuGateway;
    private final AppContextManager contextManager;
    
    @Override
    public BizResult execute(UnifiedCommand command) {
        String contextId = command.getContextId();
        String bashCommand = extractBashCommand(command);
        
        // 1. 验证命令
        if (!validator.isValid(bashCommand)) {
            return BizResult.failure("❌ 命令不在白名单中: " + bashCommand);
        }
        
        // 2. 获取或创建工作目录
        String workDir = (String) command.getContext().get("workDir");
        if (workDir == null) {
            workDir = createWorkspace(contextId);
            
            // 更新上下文
            command = command.withContext("workDir", workDir);
            contextManager.saveContext(command);
        }
        
        // 3. 执行命令
        String result = executeCommand(workDir, bashCommand);
        
        // 4. 保存命令历史（可选）
        List<String> history = (List<String>) command.getContext().getOrDefault("history", List.of());
        history = appendHistory(history, bashCommand);
        command = command.withContext("history", history);
        contextManager.saveContext(command);
        
        return BizResult.success(result);
    }
    
    // 工作量：2-3 小时
}
```

**迁移工作量汇总**：

| 应用 | 工作量 | 难度 | 主要改动 |
|------|--------|------|---------|
| TimeApp | 10分钟 | 简单 | 接口签名变更 |
| HelpApp | 10分钟 | 简单 | 接口签名变更 |
| HistoryApp | 10分钟 | 简单 | 接口签名变更 |
| BashApp | 2-3小时 | 中等 | 状态管理重构 |
| OpenCodeApp | 1-2小时 | 中等 | 会话管理适配 |
| **总计** | **4-5小时** | | |

---

## 🗺️ 实施路径

### 阶段一：基础设施准备（1天）

```
上午（3小时）：
  1. 创建核心接口和类
     ✓ 扩展 UnifiedCommand（新增 platform/context 字段）
     ✓ 实现 withContext() 方法
     ✓ 实现 AppContextManager
     ✓ 实现 ReplyCoordinator
     ✓ 实现 PlatformContextAdapterRegistry
  
  2. 创建数据表
     ✓ app_execution_context 表
     ✓ 索引

下午（4小时）：
  3. 实现数据访问层
     ✓ AppContextGateway 接口
     ✓ SqliteAppContextGateway 实现
     ✓ 单元测试
  
  4. 实现错误处理
     ✓ ContextError 枚举
     ✓ ContextException 异常类
     ✓ ContextExceptionHandler 全局处理器
     ✓ 单元测试
```

### 阶段二：适配器实现（1天）

```
上午（3小时）：
  1. 实现 FeishuContextAdapter
     ✓ adapt() 方法（事件 → 命令）
     ✓ reply() 方法（结果 → 回复）
     ✓ determineReplyMode() 方法
     ✓ 单元测试

下午（4小时）：
  2. 实现适配器注册表
     ✓ 自动扫描 @Component
     ✓ 根据平台标识查找
     ✓ 单元测试
  
  3. 集成测试
     ✓ 端到端测试（事件 → 回复）
     ✓ 错误场景测试
```

### 阶段三：应用迁移（1天）

```
上午（2小时）：
  1. 简单应用迁移
     ✓ TimeApp
     ✓ HelpApp
     ✓ HistoryApp
     ✓ 更新测试

下午（5小时）：
  2. 复杂应用迁移
     ✓ BashApp（状态管理）
     ✓ OpenCodeApp（会话管理）
     ✓ 更新测试
  
  3. 集成验证
     ✓ 所有应用端到端测试
```

### 阶段四：切换和清理（1天）

```
上午（3小时）：
  1. 更新 MessageProcessingCoordinator
     ✓ 使用新的服务层
     ✓ 完全切换到新路由
     ✓ 删除旧代码引用
  
  2. 删除旧代码
     ✓ BotMessageService（362行）
     ✓ TopicMappingGateway
     ✓ topic_mapping 表（DROP）

下午（4小时）：
  3. 回归测试
     ✓ 所有应用功能测试
     ✓ 性能测试（基准对比）
     ✓ 错误场景测试
  
  4. 文档更新
     ✓ 更新 AGENTS.md
     ✓ 更新 APP_GUIDE.md
     ✓ 更新架构文档
```

**总工作量：4天**

---

## ⚠️ 风险管理

### 风险评估

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| **应用迁移遗漏** | 高 | 低 | ✅ 编译期检查（@Deprecated）<br/>✅ 单元测试覆盖所有应用 |
| **并发安全问题** | 高 | 低 | ✅ 不可变设计<br/>✅ 代码审查 |
| **性能下降** | 中 | 低 | ✅ 性能基准测试<br/>✅ 上下文缓存（未来） |
| **用户体验影响** | 中 | 高 | ✅ 提前通知用户<br/>✅ 提供初始化引导 |

### 回滚方案

```bash
# 快速回滚（5分钟）
git revert <migration-commit>
./deploy.sh

# 数据回滚
# 无需回滚（不迁移数据，新表可丢弃重建）

# 验证回滚
./run-tests.sh
```

---

## ✅ 成功标准

### 功能完整性
- ✅ 所有应用正常工作
- ✅ 回复到正确的位置（话题/直接）
- ✅ 上下文状态正确持久化
- ✅ 错误处理完善

### 代码质量
- ✅ 无编译错误和警告
- ✅ 单元测试覆盖率 > 80%
- ✅ 集成测试通过
- ✅ 无并发安全问题

### 性能指标
- ✅ 响应时间 < 2秒（95分位）
- ✅ 内存使用无明显增加
- ✅ 数据库查询优化

### 可维护性
- ✅ BotMessageService 已删除（或 < 100行）
- ✅ 每个服务职责单一
- ✅ 易于添加新平台
- ✅ 错误处理统一

---

## 🚀 后续优化方向

### 1. 上下文生命周期管理
- 自动清理过期上下文（定时任务）
- 上下文快照和恢复
- 上下文继承（子上下文）

### 2. 性能优化
- 上下文缓存（Caffeine/Redis）
- 异步持久化
- 批量操作优化

### 3. 监控和可观测性
- 上下文创建/销毁指标
- 应用执行时长统计
- 平台适配器性能监控
- 错误率告警

### 4. 新平台支持
- 钉钉适配器
- 企业微信适配器
- Slack 适配器

---

## 📊 工作量对比

| 阶段 | 原估算 | 新估算 | 变化 |
|------|--------|--------|------|
| 基础设施 | 1天 | 1天 | - |
| 适配器 | 1天 | 1天 | - |
| 应用迁移 | 1-2天 | 1天 | **-1天** |
| 数据迁移 | 3-5天 | **0天** | **-5天** |
| 切换清理 | 1天 | 1天 | - |
| **总计** | **7-10天** | **4天** | **-60%** |

**节省原因**：
- ✅ 不迁移数据（-5天）
- ✅ 合并概念（-1天）
- ✅ 简化设计（整体效率提升）

---

## 📚 参考资料

- [COLA 架构规范](https://github.com/alibaba/COLA)
- [领域驱动设计（DDD）](https://domain-driven-design.org/)
- [适配器模式](https://refactoring.guru/design-patterns/adapter)
- [不可变对象](https://docs.oracle.com/javase/tutorial/essential/concurrency/immutable.html)

---

**最后更新**: 2026-03-06  
**版本**: v2（基于评审反馈修订）  
**状态**: 已评审，待实施

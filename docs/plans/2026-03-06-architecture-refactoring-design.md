# 架构重构设计文档 - 平台抽象化与服务拆分

**日期**: 2026-03-06  
**作者**: OpenCode  
**状态**: 待实施  

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

**工作量**: 4-5 天  
**风险**: 低-中（有回滚方案）

---

## 🎯 核心抽象：AppExecutionContext

### 设计理念

**上下文 = 应用实例的执行环境容器**

- 框架层只提供"容器"，不定义语义
- 应用自己决定上下文代表什么
- 平台适配器负责映射（飞书 topicId → contextId）

```
应用层（只关心上下文）
  ↓
抽象层（AppExecutionContext）
  ↓  
平台适配层（飞书/钉钉/企微）
```

### 核心接口定义

```java
/**
 * 应用执行上下文 - 应用实例的上下文容器
 * 
 * 框架层只提供容器，不定义语义
 * 具体含义由应用自己定义
 */
public interface AppExecutionContext {
    
    /**
     * 上下文唯一标识
     * 
     * 应用自己决定含义：
     * - OpenCodeApp: contextId = 对话会话ID
     * - BashApp: contextId = 工作空间ID
     * - TimeApp: 可能没有上下文
     */
    String getContextId();
    
    /**
     * 所属应用 ID
     */
    String getAppId();
    
    /**
     * 用户标识（可选）
     */
    String getUserId();
    
    /**
     * 应用自定义数据
     * 
     * 示例：
     * - OpenCodeApp: {"sessionId": "ses_123", "project": "feishu-backend"}
     * - BashApp: {"workDir": "/tmp/workspace", "history": [...]}
     */
    Map<String, Object> getMetadata();
    
    /**
     * 上下文创建时间
     */
    long getCreatedAt();
    
    /**
     * 最后活跃时间
     */
    long getLastActiveAt();
}
```

### 上下文语义示例

| 应用 | contextId 含义 | metadata 存储内容 |
|------|---------------|------------------|
| OpenCodeApp | AI 对话会话 | sessionId, project, language |
| BashApp | Shell 工作空间 | workDir, history, env |
| ProjectApp | 项目配置环境 | projectPath, config |
| GameApp | 游戏房间 | players, score, level |

---

## 🏗️ 架构设计

### 服务层拆分

将 BotMessageService（362行）拆分为职责单一的组件：

```java
// 1. 消息处理协调器 - 轻量级协调者
@Service
public class MessageProcessingCoordinator {
    
    private final PlatformContextAdapterRegistry adapterRegistry;
    private final UnifiedCommandRouter commandRouter;
    private final ReplyCoordinator replyCoordinator;
    
    public void process(Object platformEvent) {
        // 1. 查找平台适配器
        PlatformContextAdapter adapter = adapterRegistry.findAdapter(platformEvent);
        
        // 2. 转换为上下文
        AppExecutionContext context = adapter.adapt(platformEvent);
        
        // 3. 路由到应用
        BizResult result = commandRouter.route(context);
        
        // 4. 协调回复
        replyCoordinator.sendReply(context, result);
    }
}

// 2. 上下文管理器 - 负责持久化
@Service
public class AppContextManager {
    
    private final AppContextGateway contextGateway;
    
    public Optional<AppExecutionContext> getContext(String contextId) {
        return contextGateway.findById(contextId);
    }
    
    public void saveContext(AppExecutionContext context) {
        contextGateway.save(context);
    }
    
    public void clearContext(String contextId) {
        contextGateway.deleteById(contextId);
    }
}

// 3. 回复协调器 - 统一回复逻辑
@Service
public class ReplyCoordinator {
    
    private final PlatformContextAdapterRegistry adapterRegistry;
    
    public void sendReply(AppExecutionContext context, BizResult result) {
        PlatformContextAdapter adapter = adapterRegistry.getAdapter(context.getPlatform());
        adapter.reply(context, result);
    }
}
```

**关键改进：**
- 每个组件职责单一
- 完全基于抽象接口工作
- 易于测试和替换
- 上下文贯穿整个流程

---

## 🔌 平台适配器设计

### 核心接口

```java
/**
 * 平台适配器 - 负责平台事件和统一上下文的双向转换
 */
public interface PlatformContextAdapter<T> {
    
    /**
     * 将平台事件转换为执行上下文
     */
    AppExecutionContext adapt(T platformEvent);
    
    /**
     * 发送回复到平台
     */
    void reply(AppExecutionContext context, BizResult result);
    
    /**
     * 确定回复模式（由平台决定）
     */
    ReplyMode determineReplyMode(AppExecutionContext context);
    
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

### 飞书适配器实现示例

```java
@Component
public class FeishuContextAdapter implements PlatformContextAdapter<P2MessageReceiveV1> {
    
    private final FeishuGateway feishuGateway;
    private final AppContextManager contextManager;
    
    @Override
    public AppExecutionContext adapt(P2MessageReceiveV1 event) {
        // 1. 提取平台特定字段
        String topicId = extractTopicId(event);
        String messageId = event.getEvent().getMessage().getMessageId();
        String userId = event.getEvent().getSender().getSenderId().getOpenId();
        
        // 2. 确定上下文 ID
        String contextId = topicId != null ? topicId : messageId;
        
        // 3. 创建上下文（平台无关）
        return new GenericAppExecutionContext(
            contextId,
            null,  // appId 由路由器决定
            userId,
            Map.of(
                "messageId", messageId,
                "chatId", event.getEvent().getMessage().getChatId(),
                "platform", "feishu"
            )
        );
    }
    
    @Override
    public void reply(AppExecutionContext context, BizResult result) {
        ReplyMode mode = determineReplyMode(context);
        
        String messageId = (String) context.getMetadata().get("messageId");
        String content = result.getContent();
        
        switch (mode) {
            case TOPIC -> feishuGateway.sendReply(messageId, content, context.getContextId());
            case DIRECT -> feishuGateway.sendDirectReply(messageId, content);
        }
    }
    
    @Override
    public ReplyMode determineReplyMode(AppExecutionContext context) {
        // 飞书逻辑：有 topicId 用 TOPIC，否则用 DIRECT
        return context.getContextId().startsWith("msg_") 
            ? ReplyMode.DIRECT 
            : ReplyMode.TOPIC;
    }
    
    @Override
    public String getPlatformId() {
        return "feishu";
    }
}
```

**关键设计点：**
- 每个平台一个适配器
- 适配器负责双向转换（事件 → 上下文，结果 → 回复）
- 回复模式由平台决定，应用不关心

---

## 💾 数据持久化设计

### 上下文管理器接口

```java
public interface AppContextManager {
    
    /**
     * 获取上下文
     */
    Optional<AppExecutionContext> getContext(String contextId);
    
    /**
     * 保存上下文（包括 metadata）
     */
    void saveContext(AppExecutionContext context);
    
    /**
     * 清除上下文
     */
    void clearContext(String contextId);
    
    /**
     * 更新最后活跃时间
     */
    AppExecutionContext updateLastActive(String contextId);
}
```

### SQLite 实现

```java
@Component
public class SqliteAppContextManager implements AppContextManager {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void saveContext(AppExecutionContext context) {
        String sql = """
            INSERT OR REPLACE INTO app_execution_context 
            (context_id, app_id, user_id, metadata, created_at, last_active_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        
        String metadataJson = objectMapper.writeValueAsString(context.getMetadata());
        
        jdbcTemplate.update(sql,
            context.getContextId(),
            context.getAppId(),
            context.getUserId(),
            metadataJson,
            context.getCreatedAt(),
            context.getLastActiveAt()
        );
    }
    
    @Override
    public Optional<AppExecutionContext> getContext(String contextId) {
        String sql = "SELECT * FROM app_execution_context WHERE context_id = ?";
        
        try {
            GenericAppExecutionContext context = jdbcTemplate.queryForObject(
                sql, 
                (rs, rowNum) -> {
                    Map<String, Object> metadata = objectMapper.readValue(
                        rs.getString("metadata"),
                        new TypeReference<Map<String, Object>>() {}
                    );
                    
                    return new GenericAppExecutionContext(
                        rs.getString("context_id"),
                        rs.getString("app_id"),
                        rs.getString("user_id"),
                        metadata,
                        rs.getLong("created_at"),
                        rs.getLong("last_active_at")
                    );
                },
                contextId
            );
            
            return Optional.ofNullable(context);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```

### 数据库表结构

```sql
CREATE TABLE app_execution_context (
    context_id TEXT PRIMARY KEY NOT NULL,
    app_id TEXT NOT NULL,
    user_id TEXT,
    metadata TEXT,  -- JSON 格式存储应用自定义数据
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL
);

CREATE INDEX idx_app_context_app_id ON app_execution_context(app_id);
CREATE INDEX idx_app_context_user_id ON app_execution_context(user_id);
```

**设计要点：**
- 复用现有 SQLite 基础设施
- metadata 使用 JSON 存储，支持任意数据结构
- 保留时间戳用于清理过期上下文
- 可以平滑迁移现有 TopicMapping 数据

---

## 📦 应用迁移策略

### 简单应用（TimeApp、HelpApp）

```java
// 改动前：依赖 Message 对象
@Override
public String execute(Message message) {
    return "当前时间: " + LocalDateTime.now();
}

// 改动后：使用上下文
@Override
public BizResult execute(AppExecutionContext context) {
    return BizResult.success("当前时间: " + LocalDateTime.now());
}

// 工作量：每个应用 10 分钟
```

### 复杂应用（OpenCodeApp）

```java
// 改动前：通过 message.getTopicId() 获取话题
String topicId = message.getTopicId();
Optional<String> sessionId = sessionGateway.getSessionId(topicId);

// 改动后：使用上下文
String contextId = context.getContextId();
Map<String, Object> metadata = context.getMetadata();
String sessionId = (String) metadata.get("sessionId");

// 或者使用上下文管理器
Optional<AppExecutionContext> saved = contextManager.getContext(contextId);
String sessionId = (String) saved.get().getMetadata().get("sessionId");

// 工作量：1-2 小时
```

### BashApp（需要持久化状态）

```java
// 改动前：自己管理工作目录
historyManager.saveExecution(topicId, command, result);

// 改动后：使用上下文 metadata
Map<String, Object> metadata = context.getMetadata();
metadata.put("lastCommand", command);
metadata.put("workDir", workDir);
contextManager.saveContext(context);

// 工作量：2-3 小时
```

**迁移关键点：**
- 所有应用改为 `execute(AppExecutionContext)`
- 不再依赖 `Message` 对象
- 通过 `context.getMetadata()` 获取平台特定信息
- 状态保存在上下文中

---

## 🗺️ 迁移路径

### 阶段一：基础设施准备（1天）

```
1. 创建核心接口
   ✓ AppExecutionContext
   ✓ AppContextManager
   ✓ PlatformContextAdapter
   ✓ ReplyCoordinator

2. 实现通用基础设施
   ✓ GenericAppExecutionContext
   ✓ SqliteAppContextManager
   ✓ PlatformContextAdapterRegistry

3. 创建数据库表
   ✓ app_execution_context 表
```

### 阶段二：适配器实现（1天）

```
1. 实现 FeishuContextAdapter
   ✓ 事件转上下文
   ✓ 回复逻辑
   ✓ ReplyMode 决策

2. 注册到适配器注册表
   ✓ 自动扫描 @Component

3. 编写适配器测试
   ✓ 单元测试
   ✓ 集成测试
```

### 阶段三：应用迁移（1-2天）

```
1. 简单应用迁移（2小时）
   ✓ TimeApp
   ✓ HelpApp
   ✓ HistoryApp

2. 复杂应用迁移（4小时）
   ✓ BashApp（状态持久化）
   ✓ OpenCodeApp（会话管理）

3. 更新测试
   ✓ 修改测试使用 Mock 上下文
```

### 阶段四：切换和清理（1天）

```
1. 更新 MessageProcessingCoordinator
   ✓ 使用新的服务层
   ✓ 完全切换到新路由

2. 删除旧代码
   ✓ BotMessageService
   ✓ MessageListenerGateway 中的旧逻辑
   ✓ TopicMapping 表（迁移数据后）

3. 回归测试
   ✓ 端到端测试
   ✓ 手动验证
```

**总工作量：4-5天**

---

## ⚠️ 风险管理

### 风险评估

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| **应用迁移遗漏** | 高 | 中 | ✅ 编译期检查（旧接口标记 @Deprecated）<br/>✅ 单元测试覆盖所有应用 |
| **上下文丢失** | 高 | 低 | ✅ 数据迁移脚本<br/>✅ 保留 TopicMapping 表作为备份 |
| **性能下降** | 中 | 低 | ✅ 上下文缓存机制<br/>✅ 异步持久化 |
| **平台兼容性** | 高 | 低 | ✅ 平台适配器充分测试<br/>✅ 集成测试覆盖 |

### 回滚计划

```bash
# 方案 A：快速回滚（5分钟）
git revert <migration-commit>
./deploy.sh

# 方案 B：渐进回滚
1. 禁用新路由
2. 启用旧路由（保留但未删除）
3. 逐步恢复旧应用代码
```

---

## ✅ 成功标准

### 功能完整性
- ✅ 所有应用正常工作
- ✅ 回复到正确的位置（话题/直接）
- ✅ 上下文状态正确持久化

### 代码质量
- ✅ 无编译错误和警告
- ✅ 单元测试覆盖率 > 80%
- ✅ 集成测试通过

### 性能指标
- ✅ 响应时间 < 2秒（95分位）
- ✅ 内存使用无明显增加
- ✅ 数据库查询优化

### 可维护性
- ✅ BotMessageService 代码行数 < 150（或已删除）
- ✅ 每个服务职责单一
- ✅ 易于添加新平台

---

## 🚀 后续优化方向

### 1. 上下文生命周期管理
- 自动清理过期上下文
- 上下文快照和恢复

### 2. 监控和可观测性
- 上下文创建/销毁指标
- 应用执行时长统计
- 平台适配器性能监控

### 3. 高级特性
- 上下文继承（子上下文）
- 上下文共享（多应用协作）
- 分布式上下文存储（Redis）

### 4. 新平台支持
- 钉钉适配器
- 企业微信适配器
- Slack 适配器

---

## 📚 参考资料

- [COLA 架构规范](https://github.com/alibaba/COLA)
- [领域驱动设计（DDD）](https://domain-driven-design.org/)
- [适配器模式](https://refactoring.guru/design-patterns/adapter)
- [策略模式](https://refactoring.guru/design-patterns/strategy)

---

**最后更新**: 2026-03-06

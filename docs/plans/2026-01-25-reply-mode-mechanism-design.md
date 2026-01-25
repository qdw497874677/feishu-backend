# 应用回复模式机制设计

**日期**: 2026-01-25
**作者**: OpenCode
**状态**: 已设计

---

## 概述

为应用系统添加回复模式配置机制，支持应用选择默认回复或创建话题回复，并支持话题上下文持久化，使话题中的后续对话能自动触发对应应用。

---

## 设计目标

1. 支持全局配置默认回复模式
2. 支持应用单独配置回复模式（覆盖全局配置）
3. 支持话题上下文持久化
4. 话题中的后续消息自动触发对应应用
5. 遵循 COLA 架构规范

---

## 架构设计

### 1. 配置层

#### FeishuReplyProperties（infrastructure）
```java
@ConfigurationProperties(prefix = "feishu.reply")
public class FeishuReplyProperties {
    private ReplyMode mode = ReplyMode.DEFAULT;
}
```

配置位置：`application.yml`
```yaml
feishu:
  reply:
    mode: DEFAULT  # 或 TOPIC
```

### 2. 领域层

#### ReplyMode 枚举
```java
public enum ReplyMode {
    DEFAULT,  // 直接回复
    TOPIC     // 创建话题回复
}
```

#### FishuAppI 接口扩展
```java
public interface FishuAppI {
    // ... 其他方法

    default ReplyMode getReplyMode() {
        return ReplyMode.DEFAULT;  // 默认值
    }
}
```

#### TopicMapping 实体
```java
@Entity
public class TopicMapping {
    private String topicId;
    private String appId;
    private Instant createdAt;
    private Instant lastActiveAt;
}
```

#### TopicMappingGateway 接口
```java
public interface TopicMappingGateway {
    void save(TopicMapping mapping);
    Optional<TopicMapping> findByTopicId(String topicId);
    void updateLastActiveTime(String topicId);
    void delete(String topicId);
}
```

#### AppRegistry 扩展
```java
@Component
public class AppRegistry {
    @Lazy
    private FeishuReplyProperties replyProperties;

    public ReplyMode getDefaultReplyMode() {
        return replyProperties.getMode();
    }
}
```

### 3. 基础设施层

#### TopicMappingGatewayImpl
- 实现话题映射存储（内存或数据库）
- 使用 `@Component` 注册为 Bean

### 4. 应用层

#### BotMessageService 更新
```java
public void processMessage(Message message) {
    String topicId = message.getTopicId();
    FishuAppI app;

    if (topicId != null) {
        // 话题中的消息
        Optional<TopicMapping> mapping = topicMappingGateway.findByTopicId(topicId);
        if (mapping.isPresent()) {
            app = findAppById(mapping.get().getAppId());
        } else {
            // 话题映射不存在，降级处理
            handleUnknownTopic(message);
            return;
        }
    } else {
        // 新消息，正常路由
        app = appRouter.route(message);
    }

    String reply = app.execute(message);
    ReplyMode mode = app.getReplyMode();

    if (mode == ReplyMode.TOPIC && topicId == null) {
        // 创建话题
        String newTopicId = createTopic();
        topicMappingGateway.save(new TopicMapping(newTopicId, app.getAppId()));
        feishuGateway.sendMessage(message, reply, newTopicId);
    } else {
        // 使用现有话题或默认回复
        feishuGateway.sendMessage(message, reply, topicId);
    }
}
```

---

## 数据流

### 场景1：新消息触发应用（话题模式）

```
用户发送 "/time"
  ↓
AppRouter 路由到 TimeApp
  ↓
TimeApp.execute() 返回 "当前时间：10:30"
  ↓
TimeApp.getReplyMode() 返回 TOPIC
  ↓
创建话题（topicId = "topic_123"）
  ↓
保存映射：topic_123 → "time"
  ↓
FeishuGateway 发送回复到 topic_123
```

### 场景2：话题中的后续消息

```
用户在话题 topic_123 中发送 "几点了？"
  ↓
检测 topicId = "topic_123"
  ↓
TopicMappingGateway 查询：topic_123 → "time"
  ↓
路由到 TimeApp.execute()
  ↓
TimeApp 返回 "当前时间：10:35"
  ↓
FeishuGateway 发送回复到 topic_123
```

---

## 边界情况处理

1. **话题映射不存在**
   - 降级为默认回复模式
   - 提示用户重新触发命令

2. **应用被禁用或不存在**
   - 返回错误提示：应用不可用
   - 记录日志

3. **应用未配置回复模式**
   - 使用 AppRegistry.getDefaultReplyMode()
   - 全局配置默认为 DEFAULT

4. **话题过期**
   - 可添加话题TTL机制（后续优化）
   - 超时自动清理映射

---

## 实现步骤

### 第1步：创建配置类
- [ ] 在 infrastructure 创建 `FeishuReplyProperties`
- [ ] 在 `application.yml` 添加配置示例

### 第2步：扩展领域模型
- [ ] 创建 `TopicMapping` 实体
- [ ] 创建 `TopicMappingGateway` 接口
- [ ] 扩展 `FishuAppI` 接口（已有 `getReplyMode()`）

### 第3步：实现基础设施
- [ ] 创建 `TopicMappingGatewayImpl`（内存存储）
- [ ] 实现 CRUD 操作

### 第4步：更新 AppRegistry
- [ ] 注入 `FeishuReplyProperties`（使用 `@Lazy`）
- [ ] 添加 `getDefaultReplyMode()` 方法

### 第5步：更新消息处理逻辑
- [ ] 修改 `BotMessageService.processMessage()`
- [ ] 添加话题检测逻辑
- [ ] 添加话题映射查询
- [ ] 添加话题创建和映射保存

### 第6步：更新 FeishuGateway
- [ ] 修改 `sendMessage()` 方法支持话题ID参数
- [ ] 根据 topicId 选择发送方式

### 第7步：测试
- [ ] 测试默认回复模式
- [ ] 测试话题创建模式
- [ ] 测试话题中后续消息
- [ ] 测试边界情况

---

## 扩展性考虑

1. **数据库持久化**
   - 当前使用内存存储
   - 后续可升级为数据库（MySQL/Redis）

2. **话题TTL**
   - 添加话题过期机制
   - 定时清理不活跃话题

3. **多话题支持**
   - 一个应用可以创建多个话题
   - 话题生命周期管理

4. **话题元数据**
   - 存储话题标题、描述
   - 支持话题分类

---

## COLA 架构合规性

| 模块 | 新增内容 | 职责 |
|------|---------|------|
| domain | `TopicMapping`, `TopicMappingGateway` | 领域模型、网关接口 |
| infrastructure | `FeishuReplyProperties`, `TopicMappingGatewayImpl` | 配置、网关实现 |
| app | `BotMessageService` 更新 | 用例编排 |
| adapter | 无变更 | - |
| client | 无变更 | - |

所有依赖关系符合 COLA 分层原则。

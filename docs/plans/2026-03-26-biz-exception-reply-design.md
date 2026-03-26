# 业务异常回复设计

## 日期
2026-03-26

## 问题描述

### 现状
- `BotMessageService` 抛出 `MessageBizException`，包含格式化的用户错误回复
- `ReceiveMessageListenerExe` 捕获异常但只记录日志
- 用户看不到业务错误，机器人看起来像"静默挂起"

### 影响范围
- 跨应用命令被拒绝时无反馈
- 其他业务校验失败时无反馈

## 设计决策

### 1. 错误回复发送位置
**决策**: 在 Listener 层直接发送

**理由**:
- 改动范围最小
- 不需要改变现有服务接口
- 符合 YAGNI 原则

### 2. 错误回复目标
**决策**: 回复到原消息的位置

**理由**:
- 保持上下文一致性
- 话题中的错误仍在话题中显示

### 3. 异常处理范围
**决策**: 统一在 Listener 处理

**理由**:
- `OpenCodeMessageAppService` 和 `BotMessageAppService` 都可能抛出异常
- 统一处理逻辑，代码集中

### 4. 日志级别
**决策**: 降级为 INFO

**理由**:
- 业务异常是正常的用户交互流程，不是系统警告
- 例如"跨应用命令被拒绝"是预期行为

## 架构

### 数据流

```
用户消息
    ↓
Listener.execute()
    ↓
调用服务处理消息
    ↓
[抛出 MessageBizException]
    ↓
Listener 捕获异常
    ↓
调用 feishuGateway.sendMessage() 发送错误回复
    ↓
用户收到错误提示
```

### 改动范围

**文件**: `ReceiveMessageListenerExe.java`

**改动内容**:
1. 新增依赖: `FeishuGateway`
2. 修改 `catch (MessageBizException e)` 块

### 不改动的部分
- `MessageBizException` 本身不变
- `BotMessageAppService` 不变
- `OpenCodeMessageAppService` 不变

## 代码改动

### 1. 新增 import

```java
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.SendResult;
```

### 2. 新增依赖注入

```java
private final FeishuGateway feishuGateway;

public ReceiveMessageListenerExe(BotMessageAppService botMessageAppService,
                                 OpenCodeMessageAppService openCodeMessageAppService,
                                 MessageDeduplicator messageDeduplicator,
                                 FeishuGateway feishuGateway) {
    this.botMessageAppService = botMessageAppService;
    this.openCodeMessageAppService = openCodeMessageAppService;
    this.messageDeduplicator = messageDeduplicator;
    this.feishuGateway = feishuGateway;
}
```

### 3. 修改异常处理块

```java
} catch (MessageBizException e) {
    String errorReply = e.getMessage();
    if (errorReply == null || errorReply.isEmpty()) {
        errorReply = "操作失败，请稍后重试";
    }
    SendResult result = feishuGateway.sendMessage(message, errorReply, message.getTopicId());
    if (result.isSuccess()) {
        log.info("业务异常已回复给用户: {}", errorReply);
    } else {
        log.warn("业务异常回复发送失败: {}", result.getErrorMessage());
    }
}
```

## 边界情况

| 场景 | 处理方式 |
|------|----------|
| `message.getTopicId()` 为 null | 直接回复到群聊 |
| `feishuGateway.sendMessage()` 失败 | 记录 warn 日志，不抛出异常 |
| `e.getMessage()` 返回 null/空 | 使用默认消息 "操作失败，请稍后重试" |

## 测试策略

### 新增测试用例

**文件**: `ReceiveMessageListenerExeTest.java`

1. **`should_sendBizExceptionMessageToUser_when_routingThrowsBizException()`**
   - 模拟 `BotMessageAppService.handleMessage()` 抛出 `MessageBizException`
   - 验证 `feishuGateway.sendMessage()` 被调用
   - 验证发送的内容是异常消息
   - 验证 topicId 正确传递

2. **`should_sendBizExceptionMessageToTopic_when_openCodeThrowsBizException()`**
   - 模拟 `OpenCodeMessageAppService.tryHandle()` 抛出 `MessageBizException`
   - 验证错误回复发送到正确的话题

3. **`should_sendDefaultErrorMessage_when_bizExceptionMessageIsNull()`**
   - 模拟 `MessageBizException` 的 message 为 null
   - 验证发送默认错误消息

## 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 错误回复发送失败 | 低 | 低 | 记录日志，不影响主流程 |
| 异常消息过长 | 低 | 低 | 飞书 API 会截断，无需额外处理 |

## 实现清单

- [ ] 修改 `ReceiveMessageListenerExe.java`
- [ ] 添加测试用例到 `ReceiveMessageListenerExeTest.java`
- [ ] 运行测试验证
- [ ] 手动测试跨应用命令拒绝场景

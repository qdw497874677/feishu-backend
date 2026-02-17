# 飞书事件统一架构设计

> 创建日期: 2026-02-17
> 状态: 设计完成，待实施

---

## 1. 设计目标

### 1.1 当前问题

```
消息事件 → BotMessageService.handleMessage() → AppRouter → App
卡片事件 → CardActionDispatcher.dispatch() → CardActionHandler (独立路径)
```

- 消息和卡片走两条完全不同的路径
- 应用需要分别处理两种入口
- 代码重复，难以扩展新事件类型

### 1.2 目标架构

```
消息事件 ─┐
          ├─→ 统一 Command ─→ CommandRouter ─→ App ─→ BizResult
卡片事件 ─┘                                            ↓
                                          ResponseAdapter 决定响应方式
```

### 1.3 核心原则

- **单一职责**: 应用只关心业务逻辑，适配层处理输入输出多样性
- **开放封闭**: 新增事件类型只需添加适配器，无需修改核心代码
- **依赖倒置**: 领域层定义适配器接口，基础设施层实现

---

## 2. 核心领域模型

### 2.1 UnifiedCommand (统一命令)

```java
package com.qdw.feishu.domain.command;

public class UnifiedCommand {
    private String appId;           // 目标应用ID
    private String subCommand;      // 子命令
    private String[] args;          // 参数列表
    private String openId;          // 用户ID
    private String topicId;         // 话题ID (可能为空)
    private String messageId;       // 原始消息ID
    private String cardToken;       // 卡片token (仅卡片事件)
    private EventSource source;     // 事件来源: MESSAGE / CARD
}
```

### 2.2 EventSource (事件来源)

```java
public enum EventSource {
    MESSAGE,    // 消息事件
    CARD        // 卡片交互事件
}
```

### 2.3 BizResult (业务结果)

```java
public class BizResult {
    private boolean success;        // 是否成功
    private Object data;            // 业务数据对象
    private String message;         // 用户提示信息
}
```

### 2.4 FishuAppI (应用接口)

```java
public interface FishuAppI {
    // 核心方法：接收统一命令，返回业务结果
    BizResult execute(UnifiedCommand command);
    
    // 其他方法保持不变
    String getAppId();
    String getAppName();
    default List<String> getAppAliases() { ... }
}
```

---

## 3. 适配层架构

```
┌─────────────────────────────────────────────────────────────┐
│                     飞书事件                                 │
│          P2MessageReceiveV1 / P2CardActionTrigger           │
└────────────────────────────┬────────────────────────────────┘
                             ↓
┌────────────────────────────────────────────────────────────┐
│                  EventProcessor                             │
│                  (统一事件处理入口)                          │
└────────────────────────────┬───────────────────────────────┘
                             │
     ┌───────────────────────┼───────────────────────┐
     ↓                       ↓                       ↓
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│CommandAdapter│      │CommandRouter│      │ResponseAdapter│
│  Factory    │ ───→ │             │ ───→ │   Factory   │
└──────┬──────┘      └──────┬──────┘      └──────┬──────┘
       ↓                    ↓                    ↓
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│MessageAdapter│      │  FishuAppI  │      │MessageAdapter│
│ CardAdapter │      │  .execute() │      │ CardAdapter │
└─────────────┘      └─────────────┘      └─────────────┘
```

---

## 4. 适配器接口

### 4.1 CommandAdapter (输入)

```java
public interface CommandAdapter {
    UnifiedCommand adapt(Object event);
    boolean supports(Object event);
}
```

### 4.2 ResponseAdapter (输出)

```java
public interface ResponseAdapter {
    void respond(UnifiedCommand command, BizResult result);
    boolean supports(EventSource source, UnifiedCommand command);
}
```

---

## 5. 文件结构

```
feishu-bot-domain/
├── command/
│   ├── UnifiedCommand.java
│   └── EventSource.java
├── result/
│   └── BizResult.java
├── adapter/
│   ├── CommandAdapter.java
│   ├── ResponseAdapter.java
│   ├── CommandAdapterFactory.java
│   └── ResponseAdapterFactory.java
├── router/
│   └── CommandRouter.java
└── app/
    └── FishuAppI.java

feishu-bot-infrastructure/
└── adapter/
    ├── MessageCommandAdapter.java
    ├── CardCommandAdapter.java
    ├── MessageResponseAdapter.java
    └── CardResponseAdapter.java

feishu-bot-app/
└── processor/
    └── EventProcessor.java
```

---

## 6. 迁移策略

| Phase | 内容 | 影响范围 |
|-------|------|---------|
| 1 | 基础模型 + 接口定义 | domain 层新增 |
| 2 | 适配器实现 | infrastructure 层新增 |
| 3 | 核心重构 + 应用迁移 | domain + app 层修改 |
| 4 | 清理旧代码 + 测试 | 全局 |

### 现有代码映射

| 现有组件 | 新架构 | 处理方式 |
|---------|--------|---------|
| BotMessageService | EventProcessor | 重构 |
| AppRouter | CommandRouter | 重构 |
| MessageEventParser | MessageCommandAdapter | 重构 |
| CardActionGateway | CardCommandAdapter | 合并 |
| ReplyStrategy | ResponseAdapter | 重构 |

---

## 7. 扩展点

新增事件类型只需添加新的适配器实现：

```java
@Component
public class FileCommandAdapter implements CommandAdapter {
    // 文件上传事件 → UnifiedCommand
}
```

# 状态感知命令路由器 - 使用指南

## 📋 概述

`StateAwareCommandRouter` 是一个通用能力，用于解决"命令根据状态选择不同执行逻辑"的架构问题。

### 核心价值

**问题**：每个命令在 handle 方法中硬编码状态判断
```java
// ❌ 糟糕的实践
private String handleNewCommand(String[] parts, Message message) {
    boolean isInitialized = sessionManager.isTopicInitialized(message);
    if (isInitialized) {
        // 逻辑 A
    } else {
        // 逻辑 B
    }
}
```

**解决**：通用状态路由能力
```java
// ✅ 最佳实践
router.register(
    TopicStateMatcher.exactState(TopicState.INITIALIZED),
    parts -> executeInCurrentProject(parts, message)
);
router.register(
    TopicStateMatcher.anyOf(TopicState.NON_TOPIC, TopicState.UNINITIALIZED),
    parts -> executeInSpecifiedProject(parts, message)
);
```

---

## 🎯 核心组件

### 1. TopicStateMatcher - 状态匹配器

```java
@FunctionalInterface
public interface TopicStateMatcher {
    boolean matches(TopicState state, Message message);
    
    // 静态工厂方法
    static TopicStateMatcher exactState(TopicState targetState) { ... }
    static TopicStateMatcher anyOf(TopicState... states) { ... }
    static TopicStateMatcher not(TopicState excludedState) { ... }
    static TopicStateMatcher and(TopicStateMatcher m1, TopicStateMatcher m2) { ... }
    static TopicStateMatcher or(TopicStateMatcher m1, TopicStateMatcher m2) { ... }
}
```

### 2. ICommandExecutor - 命令执行器

```java
@FunctionalInterface
public interface ICommandExecutor {
    String execute(String[] parts, Message message);
    
    // 静态工厂方法
    static ICommandExecutor withValidation(int minParts, ICommandExecutor executor) { ... }
}
```

### 3. StateAwareCommandRouter - 路由器

```java
public class StateAwareCommandRouter {
    public StateAwareCommandRouter register(TopicStateMatcher matcher, ICommandExecutor executor) { ... }
    public String route(String[] parts, Message message, TopicState currentState) { ... }
}
```

---

## 💡 使用示例

### 示例1：new 命令重构

**原始代码**（硬编码状态判断）：
```java
private String handleNewCommand(String[] parts, Message message) {
    boolean isInitialized = sessionManager.isTopicInitialized(message);
    
    if (parts.length < 3) {
        return buildNewCommandUsage(isInitialized);
    }
    
    String project = null;
    String prompt;
    
    if (parts.length >= 4) {
        project = parts[2].trim();
        prompt = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
    } else {
        prompt = parts[2].trim();
        
        if (!isInitialized) {
            return buildNewCommandUsage(false);
        }
    }
    
    return taskExecutor.executeWithNewSession(message, prompt, project);
}
```

**重构后代码**（使用通用路由）：
```java
// 初始化路由器
private final StateAwareCommandRouter newCommandRouter = new StateAwareCommandRouter();

@PostConstruct
public void initNewCommandRouter() {
    // 话题已绑定：在当前项目创建新会话
    newCommandRouter.register(
        TopicStateMatcher.exactState(TopicState.INITIALIZED),
        parts -> {
            String prompt = parts[2].trim();
            return taskExecutor.executeWithNewSession(message, prompt, null);
        }
    );
    
    // 话题未绑定：必须指定项目
    newCommandRouter.register(
        TopicStateMatcher.anyOf(TopicState.NON_TOPIC, TopicState.UNINITIALIZED),
        ICommandExecutor.withValidation(4, parts -> {
            String project = parts[2].trim();
            String prompt = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
            return taskExecutor.executeWithNewSession(message, prompt, project);
        })
    );
}

// 处理命令
private String handleNewCommand(String[] parts, Message message) {
    TopicState currentState = detectTopicState(message);
    return newCommandRouter.route(parts, message, currentState);
}
```

**优势**：
- ✅ 状态判断逻辑被抽象为匹配器
- ✅ 每个场景的执行逻辑独立且可测试
- ✅ 添加新状态无需修改现有代码

### 示例2：chatnow 命令

```java
@PostConstruct
public void initChatNowCommandRouter() {
    // chatnow/cn 命令：所有场景都创建新会话
    chatNowCommandRouter.register(
        TopicStateMatcher.anyOf(TopicState.values()),  // 所有状态
        parts -> {
            String prompt = extractChatContent(parts, message);
            return taskExecutor.executeWithNewSession(message, prompt, null);
        }
    );
}
```

### 示例3：chat 命令

```java
@PostConstruct
public void initChatCommandRouter() {
    // chat 命令：仅话题已初始化时可用
    chatCommandRouter.register(
        TopicStateMatcher.exactState(TopicState.INITIALIZED),
        parts -> {
            String prompt = extractChatContent(parts, message);
            return taskExecutor.executeWithAutoSession(message, prompt);
        }
    );
}
```

---

## 📐 最佳实践

### 1. 命名规范

```java
// ✅ 推荐：为每个命令创建独立的路由器
private final StateAwareCommandRouter newCommandRouter = new StateAwareCommandRouter();
private final StateAwareCommandRouter chatCommandRouter = new StateAwareCommandRouter();
private final StateAwareCommandRouter chatNowCommandRouter = new StateAwareCommandRouter();

// ❌ 避免：所有命令共享一个路由器
private final StateAwareCommandRouter router = new StateAwareCommandRouter();
```

### 2. 初始化时机

```java
@Component
public class OpenCodeCommandHandler {
    
    private final StateAwareCommandRouter newCommandRouter = new StateAwareCommandRouter();
    
    @PostConstruct
    public void init() {
        // 在 @PostConstruct 中注册所有处理器
        initNewCommandRouter();
        initChatCommandRouter();
        // ...
    }
}
```

### 3. 错误处理

```java
// ✅ 推荐：在执行器中处理具体错误
router.register(
    TopicStateMatcher.exactState(TopicState.INITIALIZED),
    parts -> {
        try {
            return executeBusinessLogic(parts);
        } catch (BusinessException e) {
            return "❌ " + e.getMessage();
        }
    }
);

// ❌ 避免：让异常传播到路由器
```

---

## 🔧 高级用法

### 自定义状态匹配器

```java
// 复杂条件：话题已初始化且在特定日期之前
TopicStateMatcher customMatcher = (state, message) -> {
    if (state != TopicState.INITIALIZED) {
        return false;
    }
    long createdAt = sessionManager.getSessionCreationTime(message.getTopicId());
    return createdAt < cutoffTime;
};

router.register(customMatcher, executor);
```

### 组合匹配器

```java
// 已初始化 或 话题外
TopicStateMatcher initializedOrNonTopic = TopicStateMatcher.or(
    TopicStateMatcher.exactState(TopicState.INITIALIZED),
    TopicStateMatcher.exactState(TopicState.NON_TOPIC)
);

router.register(initializedOrNonTopic, executor);
```

---

## 📊 对比分析

| 方面 | 硬编码状态判断 | 通用路由能力 |
|------|---------------|------------|
| 代码复杂度 | 高（if-else 嵌套） | 低（声明式注册） |
| 可测试性 | 差（需要模拟状态） | 好（独立测试每个执行器） |
| 可扩展性 | 差（修改现有代码） | 好（添加新注册） |
| 职责分离 | 混乱（状态+业务） | 清晰（分离关注点） |
| 重复代码 | 多（每个命令重复判断） | 少（复用路由逻辑） |

---

## 🚀 迁移指南

### 步骤1：创建路由器

```java
private final StateAwareCommandRouter commandRouter = new StateAwareCommandRouter();
```

### 步骤2：注册处理器

```java
@PostConstruct
public void init() {
    // 为每种状态注册对应的处理器
    commandRouter.register(matcher, executor);
}
```

### 步骤3：重构 handle 方法

```java
private String handleCommand(String[] parts, Message message) {
    TopicState currentState = detectTopicState(message);
    return commandRouter.route(parts, message, currentState);
}
```

### 步骤4：删除旧的状态判断代码

```java
// 删除类似这样的代码
if (state == X) {
    // ...
} else if (state == Y) {
    // ...
}
```

---

## 📚 总结

**通用状态路由能力**提供了：
1. ✅ **解耦**：状态判断与业务逻辑分离
2. ✅ **复用**：所有命令共享路由机制
3. ✅ **可测试**：独立的执行器易于测试
4. ✅ **可扩展**：添加新状态无需修改现有代码
5. ✅ **声明式**：通过注册表定义命令行为

这是一个符合**开闭原则**的架构设计。

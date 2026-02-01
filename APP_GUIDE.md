# 飞书机器人 - 应用开发完整指南

**最后更新**: 2026-02-01

---

## 📋 概述

本文档提供创建飞书机器人应用的**完整教程**，包括基础步骤、高级功能、最佳实践和故障排查。

**核心原则**：
- ✅ 遵循 COLA 架构
- ✅ 自动注册，无需配置
- ✅ 3步完成基础应用
- ✅ 支持命令别名、话题映射等高级功能

**预计时间**: 20-40 分钟（含测试）

---

## 📋 快速开始（3 步法）

### 步骤 1: 创建应用类

在 `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/` 创建 Java 类：

```java
package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class YourApp implements FishuAppI {

    @Override
    public String getAppId() {
        return "yourapp";  // 命令前缀：/yourapp
    }

    @Override
    public String getAppName() {
        return "应用名称";
    }

    @Override
    public String getDescription() {
        return "应用描述";
    }

    @Override
    public String execute(Message message) {
        return "Hello from YourApp!";
    }
}
```

### 步骤 2: 构建项目

```bash
cd /root/workspace/feishu-backend
mvn clean install -DskipTests
```

**预期输出**: `BUILD SUCCESS`

### 步骤 3: 重启应用

```bash
./start-feishu.sh
```

**完成！** 应用会自动注册，立即可用。

**测试命令**: 在飞书中发送 `/yourapp`

---

## 📐 必须遵循的规则

### ✅ DO（必须做）

| 规则 | 说明 | 示例 |
|------|------|------|
| **位置** | 必须在 `feishu-bot-domain` 的 `app/` 目录 | `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/YourApp.java` |
| **注解** | 必须添加 `@Component` | `@Component public class YourApp` |
| **接口** | 必须实现 `FishuAppI` | `implements FishuAppI` |
| **AppId** | 必须唯一，小写英文，使用连字符分隔 | `"weather-forecast"`, `"my-app"` |
| **日志** | 建议使用 `@Slf4j` | `@Slf4j public class YourApp` |
| **返回值** | `execute()` 必须返回 `String` | `return "result";` |

### ❌ DON'T（禁止做）

| 禁止项 | 原因 | 正确做法 |
|---------|------|----------|
| 不要在其他模块创建应用 | 领域层应该在 `domain` 模块 | 放在 `feishu-bot-domain/domain/app/` |
| 不要手动注册应用 | Spring 自动扫描并注册 | 添加 `@Component` 注解即可 |
| 不要修改 `AppRegistry` 或 `AppRouter` | 无需手动修改 | 自动注册机制处理 |
| 不要修改配置文件 | 应用会自动发现 | 无需修改 application.yml |
| 不要使用 WebHook | 项目只允许长连接模式 | 使用 MessageListenerGateway |
| 不要在构造函数直接注入 AppRegistry | 会造成循环依赖 | 使用 `@Lazy` 注解 |
| 不要使用 `as any` 抑错 | 违反代码规范 | 正确处理异常 |

---

## 🎯 FishuAppI 接口详解

### 完整接口定义

```java
public interface FishuAppI {
    
    // ========== 必须实现的方法 ==========
    
    /**
     * 应用唯一标识
     * @return 应用ID（小写英文，如 "bash", "time"）
     */
    String getAppId();
    
    /**
     * 应用显示名称
     * @return 中文名称（如 "命令执行"）
     */
    String getAppName();
    
    /**
     * 功能描述
     * @return 一句话描述
     */
    String getDescription();
    
    /**
     * 执行逻辑
     * @param message 收到的消息对象
     * @return 返回给用户的内容（返回 null 表示不回复）
     */
    String execute(Message message);
    
    // ========== 可选方法（有默认实现） ==========
    
    /**
     * 帮助信息（默认：显示触发命令）
     * @return 帮助文本
     */
    default String getHelp() {
        return "用法：" + getTriggerCommand();
    }
    
    /**
     * 触发命令（默认："/" + appId）
     * @return 命令前缀
     */
    default String getTriggerCommand() {
        return "/" + getAppId();
    }
    
    /**
     * 回复模式（默认：DEFAULT）
     * @return 回复模式
     */
    default ReplyMode getReplyMode() {
        return ReplyMode.DEFAULT;
    }
    
    /**
     * 命令别名列表（默认：空列表）
     * @return 别名列表（不含 "/"）
     */
    default List<String> getAppAliases() {
        return Collections.emptyList();
    }
    
    /**
     * 所有触发方式（包括主命令和别名）
     * @return 命令列表
     */
    default List<String> getAllTriggerCommands() {
        List<String> commands = new ArrayList<>();
        commands.add(getTriggerCommand());
        getAppAliases().forEach(alias -> commands.add("/" + alias));
        return commands;
    }
}
```

### ReplyMode 枚举

| 模式 | 行为 | 使用场景 |
|------|------|----------|
| **DIRECT** | 直接回复，不创建话题 | 简单查询、帮助信息 |
| **TOPIC** | 创建话题，所有消息在话题中 | 需要上下文的交互 |
| **DEFAULT** | 智能选择（通常创建话题） | 大多数应用 |

---

## 📁 完整实例：带别名的高级应用

### 示例：Todo 应用（支持别名）

```java
package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class TodoApp implements FishuAppI {

    @Override
    public String getAppId() {
        return "todo";  // 命令前缀：/todo
    }

    @Override
    public String getAppName() {
        return "待办事项";
    }

    @Override
    public String getDescription() {
        return "管理待办事项";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("td", "task", "list");  // /td, /task, /list 也能触发
    }

    @Override
    public String getHelp() {
        return """
用法：/todo <命令> [参数]

命令：
  add <事项>        添加待办
  list             列出所有待办
  done <序号>       标记完成
  remove <序号>    删除待办

示例：
  /todo add 买牛奶
  /td list
  /task done 1
        """;
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 3);

        if (parts.length < 2) {
            return getHelp();
        }

        String command = parts[1].toLowerCase();
        String param = parts.length > 2 ? parts[2] : "";

        switch (command) {
            case "add":
            return addTodo(param);
            case "list":
            case "ls":
                return listTodos();
            case "done":
                return markDone(param);
            case "remove":
                return removeTodo(param);
            default:
                return "未知命令: " + command + "\n" + getHelp();
        }
    }

    private String addTodo(String text) {
        return "✅ 已添加待办：" + text;
    }

    private String listTodos() {
        return "📝 待办列表：\n1. 完成文档\n2. 修复bug";
    }

    private String markDone(String index) {
        return "✓ 待办 " + index + " 已标记完成";
    }

    private String removeTodo(String index) {
        return "🗑️ 待办 " + index + " 已删除";
    }
}
```

**触发方式**：
- `/todo add 买咖啡` ✅
- `/td list` ✅（别名）
- `/task done 1` ✅（别名）
- `/list` ✅（别名）

---

## ⚙️ 高级功能

### 1. 命令别名机制

**用途**：为应用定义多个命令触发方式

**实现方式**：

```java
@Override
public List<String> getAppAliases() {
    return Arrays.asList("alias1", "alias2", "alias3");
}
```

**示例**：
| 应用ID | 主命令 | 别名 | 所有触发方式 |
|--------|--------|------|-------------|
| `bash` | `/bash` | `/cmd`, `/shell`, `/exec` | 4 种 |
| `time` | `/time` | `/t`, `/now`, `/date` | 4 种 |
| `todo` | `/todo` | `/td`, `/task`, `/list` | 4 种 |

**特点**：
- 大小写不敏感：`/Bash`, `/bash`, `/BASH` 都可以
- 帮助信息自动显示所有别名
- 详见：[命令别名机制](./docs/COMMAND-ALIASES.md)

### 2. 话题映射和上下文

**用途**：在话题中自动路由到对应应用，支持无前缀命令

**机制**：
1. 首次使用命令创建话题时，系统自动保存 `topicId → appId` 映射
2. 在该话题中后续消息，自动路由到对应应用
3. 支持无前缀命令（如 `pwd` 而非 `/bash pwd`）

**示例流程**：

```
用户: /bash pwd
Bot: [创建话题] /root/workspace/feishu-backend
系统: 保存映射 topicId → bash

用户（在话题中）: pwd
Bot: /root/workspace/feishu-backend  （自动添加 /bash 前缀）
系统: 通过映射找到 bash 应用，执行命令
```

**如何启用**：
- 自动启用，无需配置
- 使用 SQLite 持久化话题映射
- 详见：[SQLite 持久化](./docs/SQLITE-PERSISTENCE.md)

### 3. ReplyMode 回复模式

**DIRECT 模式**：直接回复，不创建话题

```java
@Override
public ReplyMode getReplyMode() {
    return ReplyMode.DIRECT;
}
```

- 适用：简单查询、帮助信息
- 特点：每次回复都是独立消息

**TOPIC 模式**：创建话题，所有消息在话题中

```java
@Override
public ReplyMode getReplyMode() {
    return ReplyMode.TOPIC;
}
```

- 适用：需要上下文的交互
- 特点：首次回复创建话题，后续回复到话题

**DEFAULT 模式**：智能选择（推荐）

```java
@Override
public ReplyMode getReplyMode() {
    return ReplyMode.DEFAULT;  // 通常创建话题
}
```

- 适用：大多数应用
- 特点：智能选择，通常创建话题

---

## 🧪 测试和调试

### 本地构建测试

```bash
# 1. 清理并构建
cd /root/workspace/feishu-backend
mvn clean install -DskipTests

# 2. 检查编译是否成功
echo $?

# 3. 查看应用注册日志
grep "应用注册" /tmp/feishu-run.log | tail -20
```

### 飞书中测试

**测试主命令**：
```
/yourapp
```

**测试别名**：
```
/alias1
/alias2
```

**测试帮助**：
```
/yourapp
```

### 调试命令

```bash
# 查看应用注册日志
grep "应用注册" /tmp/feishu-run.log

# 查看消息处理日志
grep "BotMessageService" /tmp/feishu-run.log

# 查看应用执行日志
grep "YourApp" /tmp/feishu-run.log

# 实时监控
tail -f /tmp/feishu-run.log | grep -E "(注册|执行|错误)"
```

---

## 🔍 常见问题

### Q: 应用没有生效？

**检查清单**：
1. ✅ 确认类添加了 `@Component` 注解
2. ✅ 确认实现了 `FishuAppI` 接口
3. ✅ 确认 `getAppId()` 返回值不为空
4. ✅ 查看启动日志，确认应用已注册
5. ✅ 确认 appId 唯一（没有其他应用使用相同ID）

**调试步骤**：
```bash
# 1. 查看所有已注册的应用
grep "应用注册" /tmp/feishu-run.log

# 2. 验证 appId 正确性
grep "应用ID: yourapp" /tmp/feishu-run.log

# 3. 检查编译错误
mvn clean compile
```

### Q: 如何禁用某个应用？

**方法 1：注释掉注解（推荐）**

```java
// @Component  // 注释这行以禁用应用
public class DisabledApp implements FishuAppI {
    // ...
}
```

**方法 2：重命名 appId**

```java
@Override
public String getAppId() {
    return "disabled-app";  // 改成不冲突的ID
}
```

### Q: 如何添加应用配置？

**方法 1：通过构造函数注入（推荐）**

```java
@Component
public class ConfigurableApp implements FishuAppI {

    private final SomeConfig config;

    public ConfigurableApp(SomeConfig config) {
        this.config = config;
    }

    @Override
    public String execute(Message message) {
        // 使用 config
        return config.getValue();
    }
}
```

**方法 2：使用 @Value 注解**

```java
@Component
public class PropertyApp implements FishuAppI {

    @Value("${some.property:default-value}")
    private String someProperty;

    @Override
    public String execute(Message message) {
        // 使用 someProperty
    }
}
```

### Q: 如何注入其他服务？

**示例：注入 FeishuGateway**

```java
@Component
public class GatewayApp implements FishuAppI {

    private final FeishuGateway gateway;

    public GatewayApp(FeishuGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String execute(Message message) {
        // 使用 gateway
        gateway.sendMessage(message, "回复", null);
        return "已发送回复";
    }
}
```

**示例：注入 AppRegistry（使用 @Lazy 避免循环依赖）**

```java
@Component
public class HelpApp implements FishuAppI {

    @Autowired
    @Lazy
    private AppRegistry appRegistry;

    @Override
    public String execute(Message message) {
        // 使用 appRegistry
        appRegistry.getAllApps().forEach(app -> {
            // ...
        });
        return "帮助信息";
    }
}
```

### Q: 如何处理异常？

**推荐做法**：

```java
@Override
public String execute(Message message) {
    try {
        return doSomething();
    } catch (Exception e) {
        log.error("应用执行失败", e);
        return "错误：" + e.getMessage();
    }
}

private String doSomething() throws Exception {
    // 业务逻辑
}
```

---

## 📊 已实现应用列表

| 应用ID | 应用名称 | 别名 | 回复模式 | 特殊说明 |
|--------|---------|------|----------|----------|
| `bash` | 命令执行 | `/cmd`, `/shell`, `/exec` | DEFAULT | 异步执行，命令白名单，工作空间隔离 |
| `time` | 时间查询 | `/t`, `/now`, `/date` | TOPIC | - |
| `help` | 帮助信息 | `/h`, `/?`, `/man` | DIRECT | 使用 `@Lazy` 注入 AppRegistry |
| `history` | 历史查询 | - | DEFAULT | - |

---

## 🚀 最佳实践

### 命名规范

| 项目 | 规范 | 示例 | 反例 |
|------|------|------|------|
| **AppId** | 小写英文，连字符分隔 | `weather-forecast`, `my-app` | `WeatherApp`, `myApp` |
| **AppName** | 中文，简洁明了 | `天气查询` | `天气应用` |
| **类名** | 以 `App` 结尾，PascalCase | `WeatherApp.java` | `weather.java`, `Weather.java` |
| **包名** | `com.qdw.feishu.domain.app` | - | - |

### 开发流程

1. 创建类 → 添加 `@Component` → 实现 `FishuAppI`
2. 实现业务逻辑（处理参数、调用服务）
3. 添加日志（`@Slf4j`）
4. 构建并测试
5. 添加别名（可选）
6. 选择回复模式（可选）

**关键原则**：
- ✅ 遵循 COLA 架构
- ✅ Spring 自动发现和注册
- ✅ 无需手动配置
- ✅ 处理异常
- ✅ 提供帮助信息

### 消息返回格式

**移动端优化**：
- ✅ 减少表情符号（兼容性）
- ✅ 简洁明了的信息结构
- ✅ 适当的换行和分段

**好的示例**：
```
待办列表：

1. 完成文档
2. 修复bug
3. 编写测试
```

**避免的格式**：
```
📝 待办列表 ⏰

1. 完成文档...
```

---

## 📝 总结

| 任务 | 复杂度 | 时间 |
|------|---------|------|
| 创建基础应用类 | ⭐ 简单 | 5 分钟 |
| 添加业务逻辑 | ⭐⭐ 中等 | 10-30 分钟 |
| 添加别名和帮助 | ⭐ 简单 | 5 分钟 |
| 测试和调试 | ⭐ 简单 | 10 分钟 |
| **总计** | - | **30-50 分钟** |

---

**相关文档**：
- [命令别名机制](./docs/COMMAND-ALIASES.md) - 如何添加命令别名
- [SQLite 持久化](./docs/SQLITE-PERSISTENCE.md) - 话题映射如何工作
- [项目整体规范](./AGENTS.md) - COLA 架构和分层原则

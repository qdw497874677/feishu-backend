# 飞书机器人 - 开发规范

---

## 📋 快速开始

创建新应用只需要 **3 步骤**，全程无需修改配置：

### 1️⃣ 创建应用类

在 `feishu-bot-domain/.../domain/app/` 创建：

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
        return "your-app-id";
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
        return "回复内容";
    }
}
```

### 2️⃣ 构建项目

```bash
mvn clean install -Dmaven.test.skip=true
```

### 3️⃣ 启动应用

**Dev 环境（开发环境）**：
```bash
cd feishu-bot-start
LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**其他环境**：
```bash
cd feishu-bot-start
FEISHU_MODE=listener FEISHU_LISTENER_ENABLED=true mvn spring-boot:run
```

**完成！** 应用会自动注册，无需修改任何配置文件。

---

## 📐 必须遵循的规则

### ✅ DO（必须做）

| 规则 | 说明 |
|------|------|
| **位置** | 必须在 `feishu-bot-domain` 的 `app/` 目录 |
| **注解** | 必须添加 `@Component` |
| **接口** | 必须实现 `FishuAppI` |
| **AppId** | 必须唯一，小写英文标识符（如 `time`, `weather`） |
| **日志** | 建议使用 `@Slf4j` |
| **返回值** | `execute()` 必须返回 `String` |

### ❌ DON'T（禁止做）

| 禁止项 | 原因 |
|---------|------|
| 不要在其他模块创建应用 | 领域层应该在 `domain` 模块 |
| 不要手动注册应用 | Spring 自动扫描并注册 |
| 不要修改 `AppRegistry` 或 `AppRouter` | 无需手动修改 |
| 不要修改配置文件 | 应用会自动发现 |
| 不要使用 WebHook | 项目只允许长连接模式 |
| 不要直接在构造函数中注入 AppRegistry | 会造成循环依赖，使用 `@Lazy` |

---

## 🎯 FishuAppI 接口

```java
public interface FishuAppI {
    
    String getAppId();                    // 必须：应用唯一标识
    String getAppName();                  // 必须：应用显示名称
    String getDescription();               // 必须：功能描述
    default String getHelp() {            // 可选：帮助信息
        return "用法：" + getTriggerCommand();
    }
    String execute(Message message);          // 必须：执行逻辑
    default String getTriggerCommand() {      // 可选：触发命令
        return "/" + getAppId();
    }
}
```

---

## 📁 完整实例

### 示例：天气应用

```java
package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherApp implements FishuAppI {

    @Override
    public String getAppId() {
        return "weather";
    }

    @Override
    public String getAppName() {
        return "天气查询";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的天气";
    }

    @Override
    public String getHelp() {
        return "用法：/weather <城市>\n示例：/weather 北京";
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent();
        String[] parts = content.split("\\s+", 2);
        
        if (parts.length < 2) {
            return "请输入城市名称\n" + getHelp();
        }
        
        String city = parts[1];
        return "正在查询 " + city + " 的天气...";
    }
}
```

### 提示：参数处理

```java
@Override
public String execute(Message message) {
    String content = message.getContent().trim();
    String[] parts = content.split("\\s+", 2);
    
    if (parts.length > 1) {
        String param = parts[1];  // 获取参数
    }
}
```

### 提示：异常处理

```java
@Override
public String execute(Message message) {
    try {
        return doSomething();
    } catch (Exception e) {
        log.error("执行失败", e);
        return "应用执行失败: " + e.getMessage();
    }
}
```

---

## 🧪 测试应用

### 本地测试

```bash
# 构建项目
mvn clean install -Dmaven.test.skip=true

# 启动应用
cd feishu-bot-start
FEISHU_MODE=listener FEISHU_LISTENER_ENABLED=true mvn spring-boot:run

# 查看日志
tail -f /tmp/feishu-start.log | grep "应用注册"
```

### 飞书测试

发送消息到飞书机器人：
- `/time` - 测试时间应用
- `/weather 北京` - 测试天气应用
- `/help` - 查看所有应用列表（可通过 AppRegistry.getAppHelp() 实现）

---

## ⚙️ 消息处理流程

```
用户发送 "/weather 北京"
    ↓
FeishuEventListener (长连接接收)
    ↓
ReceiveMessageListenerExe (解析消息)
    ↓
BotMessageService (处理消息)
    ↓
AppRouter (路由到应用)
    ↓
AppRegistry (查找应用)
    ↓
WeatherApp.execute() (执行逻辑)
    ↓
FeishuGateway (发送回复)
    ↓
用户收到回复
```

---

## 🔍 常见问题

### Q: 应用没有生效？

**A: 检查以下几点**
1. 确认类添加了 `@Component` 注解
2. 确认实现了 `FishuAppI` 接口
3. 查看启动日志，确认应用已注册
4. 确认 `appId` 在 URL 中使用（如 `/weather`）

### Q: 如何禁用某个应用？

**A:** 注释掉 `@Component` 注解：

```java
// @Component  // 注释这行以禁用应用
public class DisabledApp implements FishuAppI { }
```

### Q: 如何添加应用配置？

**A:** 在构造函数注入配置：

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
    }
}
```

---

## 📊 已实现应用

| 应用ID | 应用名称 | 文件位置 | 状态 | 特殊说明 |
|---------|---------|-----------|------|----------|
| `time` | 时间查询 | `TimeApp.java` | ✅ 已实现 | - |
| `help` | 帮助信息 | `HelpApp.java` | ✅ 已实现 | 使用 `@Lazy` 注入 AppRegistry |

---

## 🚀 最佳实践

### 命名规范

- **AppId**: 小写英文，使用连字符分隔单词（如 `weather-forecast`）
- **AppName**: 中文，简洁明了（如 `天气查询`）
- **类名**: 以 `App` 结尾（如 `WeatherApp`）

### 开发流程

1. 创建类 → 添加 `@Component` → 实现 `FishuAppI`
2. 实现业务逻辑
3. 构建并测试
4. 无需修改任何配置文件

**关键原则**：
- ✅ 遵循 COLA 架构：所有应用在 `feishu-bot-domain` 模块
- ✅ Spring 自动发现和注册
- ✅ 无需手动配置

### 循环依赖处理

**场景**：如果应用需要注入 `AppRegistry` 以获取其他应用信息（如 HelpApp）

**问题**：形成循环依赖 `AppRegistry → HelpApp → AppRegistry`

**解决方案**：使用 `@Lazy` 注解

```java
@Component
public class HelpApp implements FishuAppI {

    @Autowired
    @Lazy
    private AppRegistry appRegistry;
}
```

### 消息返回格式

**移动端优化**：
- ✅ 移除表情符号（兼容性）
- ✅ 减少分隔线和空行（节省空间）
- ✅ 合并重复内容（避免冗余）
- ✅ 简洁明了的信息结构

**示例**：
```text
/weather - 天气查询
  查询指定城市的天气

/help - 帮助信息
  显示所有可用命令和使用说明
```

---

## 📝 总结

| 任务 | 复杂度 | 时间 |
|------|---------|------|
| 创建新应用类 | ⭐ 简单 | 5 分钟 |
| 实现业务逻辑 | ⭐⭐ 中等 | 10-30 分钟 |
| 测试应用 | ⭐ 简单 | 5 分钟 |
| **总计** | - | **20-40 分钟** |

---

**最后更新**: 2026-01-25

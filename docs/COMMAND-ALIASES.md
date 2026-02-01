# 命令别名机制使用指南

## 📋 概述

飞书机器人现在支持命令别名功能，允许为每个应用定义多个命令触发方式。用户可以使用主命令或任意别名来触发同一个应用。

---

## 🎯 优势

- ✅ **便捷性**：提供更简短的命令（如 `/t` 代替 `/time`）
- ✅ **灵活性**：支持多种命名习惯
- ✅ **兼容性**：大小写不敏感（`/Bash`, `/bash`, `/BASH` 都可以）
- ✅ **向后兼容**：不影响现有主命令的使用

---

## 📝 当前应用的别名列表

| 应用 | 主命令 | 别名 | 触发方式 |
|------|--------|------|----------|
| **命令执行** | `/bash` | `/cmd`, `/shell`, `/exec` | `/bash ls`, `/cmd ls`, `/shell pwd` |
| **时间查询** | `/time` | `/t`, `/now`, `/date` | `/time`, `/t`, `/now` |
| **帮助信息** | `/help` | `/h`, `/?`, `/man` | `/help`, `/h`, `/?` |
| **历史查询** | `/history` | 无 | `/history` |

---

## 🔧 如何添加别名

### 为新应用添加别名

```java
@Component
public class YourApp implements FishuAppI {

    @Override
    public String getAppId() {
        return "yourapp";  // 主命令：/yourapp
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("alias1", "alias2", "alias3");
        // 触发方式：/yourapp, /alias1, /alias2, /alias3
    }
}
```

### 为现有应用添加别名

找到应用类（如 `BashApp.java`），添加或修改 `getAppAliases()` 方法：

```java
@Override
public List<String> getAppAliases() {
    return Arrays.asList("cmd", "shell", "exec");
}
```

---

## 💡 使用示例

### 示例 1：使用别名执行 bash 命令

**传统方式**：
```
/bash pwd
/bash ls -la
```

**使用别名**：
```
/cmd pwd
/shell ls -la
/exec history
```

### 示例 2：快速查询时间

**传统方式**：
```
/time
```

**使用别名**：
```
/t
/now
/date
```

### 示例 3：查看帮助

**传统方式**：
```
/help
```

**使用别名**：
```
/h
/?
/man
```

---

## ⚙️ 技术实现

### 接口定义

**位置**：`FishuAppI.java`

```java
public interface FishuAppI {
    String getAppId();

    /**
     * 获取应用的命令别名列表
     *
     * @return 别名列表，默认为空列表
     */
    default List<String> getAppAliases() {
        return Collections.emptyList();
    }

    /**
     * 获取所有可以触发此应用的命令（包括主命令和别名）
     *
     * @return 命令列表，格式：["/bash", "/cmd", "/shell"]
     */
    default List<String> getAllTriggerCommands() {
        List<String> commands = new ArrayList<>();
        commands.add(getTriggerCommand());
        getAppAliases().forEach(alias -> commands.add("/" + alias));
        return commands;
    }
}
```

### 路由逻辑

**位置**：`BotMessageService.java`

```java
private FishuAppI findAppByCommandOrAlias(String command) {
    String commandLower = command.toLowerCase();

    for (FishuAppI app : appRegistry.getAllApps()) {
        // 检查主命令
        if (app.getAppId().equalsIgnoreCase(commandLower)) {
            return app;
        }

        // 检查别名
        for (String alias : app.getAppAliases()) {
            if (alias.equalsIgnoreCase(commandLower)) {
                log.info("通过别名找到应用: command={}, alias={}, appId={}",
                        command, alias, app.getAppId());
                return app;
            }
        }
    }

    return null;
}
```

---

## 📊 帮助信息更新

HelpApp 现在会显示所有可用的命令和别名：

```
飞书机器人命令帮助

📌 /bash - 命令执行
   执行安全的bash命令
   别名: /cmd, /shell, /exec

📌 /time - 时间查询
   查询当前系统时间
   别名: /t, /now, /date

📌 /help - 帮助信息
   显示所有可用命令和使用说明
   别名: /h, /?, /man

💡 提示：
   - 发送任意非命令消息也会显示此帮助信息
   - 命令和别名不区分大小写（如 /Bash、/BASH、/bash 都可以）
```

---

## 🎨 最佳实践

### 1. 选择有意义的别名

**推荐**：
```java
// 好的别名：简短、易记、语义相关
return Arrays.asList("t", "now");  // time 应用的别名
return Arrays.asList("h", "?");    // help 应用的别名
```

**避免**：
```java
// 不好的别名：过长、无意义、容易混淆
return Arrays.asList("this-is-a-very-long-alias");
return Arrays.asList("xyz");  // 无意义
```

### 2. 保持别名数量适中

```java
// 推荐：2-4个别名
return Arrays.asList("cmd", "shell", "exec");

// 避免：过多别名
return Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");
```

### 3. 避免别名冲突

不同应用的别名不应该相同：

```java
// BashApp
return Arrays.asList("cmd");

// ❌ 避免：其他应用也使用 "cmd" 作为别名
// SomeOtherApp
return Arrays.asList("cmd");  // 会造成冲突
```

---

## 🔍 故障排查

### 问题 1：别名不生效

**检查清单**：
1. ✅ 应用类已添加 `@Component` 注解
2. ✅ `getAppAliases()` 方法返回非空列表
3. ✅ 重新构建并重启应用
4. ✅ 别名不包含特殊字符（如 `/`, 空格）

### 问题 2：大小写问题

**说明**：所有命令和别名都会转换为小写进行匹配，因此：
- `/Bash`, `/bash`, `/BASH` 都会触发 BashApp
- `/TIME`, `/Time`, `/time` 都会触发 TimeApp

### 问题 3：查看日志

启用 DEBUG 日志查看别名匹配过程：

```yaml
# application.yml
logging:
  level:
    com.qdw.feishu.domain.service.BotMessageService: DEBUG
```

**日志示例**：
```
2026-02-01 08:00:00.000 [INFO] 通过别名找到应用: command=cmd, alias=cmd, appId=bash
```

---

## 🚀 未来扩展

### 可能的增强功能

1. **配置文件定义别名**
   ```yaml
   # application.yml
   feishu:
     commands:
       aliases:
         bash: [cmd, shell, exec]
         time: [t, now, date]
   ```

2. **用户自定义别名**
   - 允许用户在对话中设置自己的命令别名
   - 持久化用户偏好设置

3. **别名分组**
   - 按功能分组别名（如 `@sys`, `@dev`, `@tools`）

---

## 📚 相关文档

- [应用开发指南](./APP_GUIDE.md) - 如何创建新应用
- [项目规范](../AGENTS.md) - 架构和开发规范
- [SQLite 持久化](./SQLITE-PERSISTENCE.md) - 数据持久化方案

---

**最后更新**: 2026-02-01

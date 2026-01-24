# 时间命令功能设计

## 概述

为飞书机器人添加 `/time` 命令，用于查询当前日期和时间。

## 需求

- 用户输入 `/time` 命令触发
- 返回当前时间，中文格式：`2026年1月24日 星期六 12:30:45`
- 使用统一的命令创建方式，便于未来扩展（如 `/help`、`/agent` 等）
- 遵循 COLA 架构规范

## 架构设计

### 架构概览

**核心组件**：

1. **命令路由器 (CommandRouter)** - `feishu-bot-app`
   - 职责：解析消息内容，识别命令关键词，路由到对应命令执行器
   - 位置：`com.qdw.feishu.app.router.CommandRouter`
   - 关键方法：`route(Message message)`

2. **时间命令 (TimeCmd)** - `feishu-bot-client`
   - 职责：时间查询命令的 DTO
   - 位置：`com.qdw.feishu.client.cmd.TimeCmd`
   - 继承：`Command`（COLA 基类）
   - 包含：发送者信息

3. **时间命令执行器 (TimeCmdExe)** - `feishu-bot-app`
   - 职责：执行时间查询，返回格式化的时间字符串
   - 位置：`com.qdw.feishu.app.time.TimeCmdExe`
   - 实现：`CommandExecutorI` 接口

4. **领域服务 (BotMessageService)** - `feishu-bot-domain`
   - 修改：`handleMessage()` 方法添加命令路由逻辑
   - 优先级：命令处理 > 普通消息处理

**模块分布**（遵循 COLA 规范）：
- `feishu-bot-client`：TimeCmd（DTO）
- `feishu-bot-app`：CommandRouter、TimeCmdExe
- `feishu-bot-domain`：BotMessageService（修改）

### 数据流

```
用户发送: "/time"
    ↓
FeishuEventListener (Adapter层)
  - 接收 WebSocket 消息
  - 创建 Message 实体
    ↓
ReceiveMessageListenerExe (App层)
  - execute(Message message)
    ↓
BotMessageService.handleMessage() (Domain层)
  - 检查是否为命令（以 "/" 开头）
  - 如果是命令：调用 CommandRouter.route()
  - 如果是普通消息：使用扩展点生成回复
    ↓
CommandRouter.route(message) (App层)
  - 解析命令关键词："/time" → "time"
  - 创建 TimeCmd 实例
  - 执行 TimeCmdExe.execute(timeCmd)
    ↓
TimeCmdExe.execute() (App层)
  - 获取当前时间：LocalDateTime.now()
  - 格式化：中文格式 "2026年1月24日 星期六 12:30:45"
  - 返回时间字符串
    ↓
FeishuGateway.sendReply() (Infrastructure层)
  - 发送时间信息回用户
```

## 类定义

### 1. CommandExecutorI（命令执行器接口）

```java
package com.qdw.feishu.app.executor;

import com.alibaba.cola.dto.Command;

/**
 * 命令执行器接口
 * 所有命令执行器都需要实现此接口
 */
public interface CommandExecutorI {

    /**
     * 获取命令关键词（如 "time" 对应 "/time"）
     */
    String getCommand();

    /**
     * 执行命令
     * @param cmd 命令对象
     * @return 命令执行结果（回复内容）
     */
    String execute(Command cmd);
}
```

### 2. CommandRouter（命令路由器）

```java
package com.qdw.feishu.app.router;

import com.qdw.feishu.app.executor.CommandExecutorI;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 命令路由器
 * 根据消息内容路由到对应的命令执行器
 */
@Slf4j
@Component
public class CommandRouter {

    private final Map<String, CommandExecutorI> executors;

    public CommandRouter(List<CommandExecutorI> allExecutors) {
        this.executors = allExecutors.stream()
            .collect(Collectors.toMap(
                CommandExecutorI::getCommand,
                Function.identity()
            ));
        log.info("CommandRouter initialized with {} commands: {}",
            executors.size(), executors.keySet());
    }

    /**
     * 路由命令
     * @param message 消息对象
     * @return 命令执行结果，如果不是命令则返回 null
     */
    public String route(Message message) {
        String content = message.getContent().trim();

        if (!content.startsWith("/")) {
            return null; // 不是命令
        }

        String[] parts = content.split("\\s+", 2);
        String commandKey = parts[0].substring(1).toLowerCase(); // 去掉 "/"

        CommandExecutorI executor = executors.get(commandKey);
        if (executor == null) {
            return "未知命令: " + commandKey + "\n输入 /help 查看帮助";
        }

        try {
            return executor.execute(message);
        } catch (Exception e) {
            log.error("Command execution failed: {}", commandKey, e);
            return "命令执行失败: " + e.getMessage();
        }
    }
}
```

### 3. TimeCmd（命令 DTO）

```java
package com.qdw.feishu.client.cmd;

import com.alibaba.cola.dto.Command;
import lombok.Data;

/**
 * 时间命令对象
 */
@Data
public class TimeCmd extends Command {

    private String senderOpenId;
}
```

### 4. TimeCmdExe（时间命令执行器）

```java
package com.qdw.feishu.app.time;

import com.qdw.feishu.app.executor.CommandExecutorI;
import com.qdw.feishu.client.cmd.TimeCmd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 时间命令执行器
 */
@Slf4j
@Component
public class TimeCmdExe implements CommandExecutorI {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINA);

    @Override
    public String getCommand() {
        return "time";
    }

    @Override
    public String execute(com.alibaba.cola.dto.Command cmd) {
        log.debug("Executing /time command");

        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(FORMATTER);

        log.info("Current time: {}", formattedTime);
        return "当前时间：" + formattedTime;
    }
}
```

### 5. BotMessageService（修改）

```java
package com.qdw.feishu.domain.service;

import com.qdw.feishu.app.router.CommandRouter;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.lark.oapi.service.im.v1.model.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BotMessageService {

    private final FeishuGateway feishuGateway;
    private final CommandRouter commandRouter;

    public BotMessageService(FeishuGateway feishuGateway, CommandRouter commandRouter) {
        this.feishuGateway = feishuGateway;
        this.commandRouter = commandRouter;
    }

    private String getOpenIdFromSender(Object sender) {
        if (sender instanceof UserId) {
            UserId userId = (UserId) sender;
            return userId.getOpenId() != null ? userId.getOpenId() : "";
        }
        if (sender instanceof String) {
            return (String) sender;
        }
        return "";
    }

    public SendResult handleMessage(Message message) {
        try {
            message.validate();

            // 优先检查是否为命令
            String reply = null;
            if (message.getContent().trim().startsWith("/")) {
                reply = commandRouter.route(message);
                log.info("Command routed, reply: {}", reply);
            }

            // 如果不是命令或命令路由失败，使用原有逻辑
            if (reply == null) {
                reply = message.generateReply();
            }

            Object sender = message.getSender().getOpenId();
            String openId = getOpenIdFromSender(sender);

            SendResult result = feishuGateway.sendReply(
                openId,
                reply
            );

            message.markProcessed();

            return result;

        } catch (MessageBizException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageSysException("MESSAGE_HANDLE_FAILED", "消息处理失败", e);
        }
    }
}
```

## 错误处理

### 错误处理策略

1. **命令格式错误**
   - 场景：`/time xxx`（带额外参数）
   - 处理：忽略额外参数，仍返回时间
   - 原因：简单命令，容错性强

2. **未知命令**
   - 场景：`/unknown`
   - 处理：返回友好提示 + help 命令建议
   - 示例：`未知命令: unknown\n输入 /help 查看帮助`

3. **执行器异常**
   - 场景：时间获取失败、格式化异常
   - 处理：捕获异常，返回错误提示
   - 日志：记录完整堆栈用于排查

4. **非命令消息**
   - 场景：普通消息 "你好"
   - 处理：回退到现有扩展点处理
   - 保持向后兼容

### 异常层次

```
CommandNotFoundException (应用层)
  - 命令不存在

CommandExecutionException (应用层)
  - 命令执行失败
    └─ TimeFormatException
```

## 测试策略

### 单元测试覆盖

1. **CommandRouter 测试**
   - 测试命令识别：`/time` → 正确路由
   - 测试未知命令：`/unknown` → 返回错误提示
   - 测试非命令：`hello` → 返回 null
   - 测试大小写不敏感：`/Time`、`/TIME` 都能识别

2. **TimeCmdExe 测试**
   - 测试时间格式：验证中文格式正确
   - 测试时间准确性：返回的是当前时间
   - Mock 时间：验证不同场景下的输出

3. **集成测试**
   - 端到端测试：从消息接收到回复的完整流程
   - 使用 `FeishuListenerIntegrationTest` 模式

### 测试工具

- JUnit 5
- Mockito（Mock 依赖）
- Awaitility（异步测试）

### 测试优先级

1. 先写 TimeCmdExe 单元测试（最简单）
2. 再写 CommandRouter 单元测试
3. 最后写集成测试

## 扩展性

### 未来可添加的命令

通过此架构，可以轻松添加新命令：

1. `/help` - 显示帮助信息
2. `/agent` - 启动 AI Agent 模式
3. `/weather` - 查询天气
4. `/todo` - 任务管理

### 添加新命令的步骤

1. 在 `feishu-bot-client` 创建 `XxxCmd extends Command`
2. 在 `feishu-bot-app` 创建 `XxxCmdExe implements CommandExecutorI`
3. `XxxCmdExe` 添加 `@Component` 注解（Spring 自动注册）
4. 无需修改 `CommandRouter`，自动发现并注册

## 实现计划

1. 创建接口 `CommandExecutorI`
2. 创建路由器 `CommandRouter`
3. 创建命令 `TimeCmd`
4. 创建执行器 `TimeCmdExe`
5. 修改 `BotMessageService.handleMessage()`
6. 编写单元测试
7. 集成测试
8. 验证功能

## 技术栈

- JDK 17+
- Spring Boot 3.2.1
- COLA 5.0.0
- JUnit 5
- Mockito

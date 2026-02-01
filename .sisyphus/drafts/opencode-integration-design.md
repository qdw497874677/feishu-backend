# 飞书 OpenCode 集成应用设计方案 v2.0

## 📋 项目概述

**目标**：创建一个飞书机器人应用，通过话题对话远程控制 OpenCode 会话

**核心价值**：
- ✅ 在飞书中无缝使用 OpenCode 的强大功能
- ✅ **支持话题内多轮对话（关键特性）**
- ✅ 异步执行长时间任务
- ✅ 会话管理和持久化
- ✅ 上下文自动保持

---

## 🎯 功能需求（更新）

### 1. 核心命令

| 命令 | 格式 | 说明 |
|------|------|------|
| 执行任务 | `/opencode <prompt>` | 执行 opencode 命令（自动保持会话） |
| 新会话 | `/opencode new <prompt>` | 创建新会话并执行 |
| 当前会话 | `/opencode session status` | 查看当前会话信息 |
| 会话列表 | `/opencode session list` | 查看所有 OpenCode 会话 |
| 继续会话 | `/opencode session continue <id>` | 继续指定会话 |
| 帮助 | `/opencode help` | 显示帮助信息 |

### 2. 多轮对话机制（✨ 新增）

**核心特性**：
- 🔄 **自动上下文保持**：在话题中自动复用同一 sessionID
- 💾 **会话持久化**：话题映射中保存 OpenCode sessionID
- 🆕 **灵活创建新会话**：支持显式创建新会话或继续旧会话

**使用场景**：
```
# 第1轮：创建会话
用户: /opencode 重构 TimeApp，添加日期验证
机器人: ✅ 已完成重构... [Session: ses_abc123]

# 第2轮：自动继续同一会话
用户: /opencode 添加单元测试
机器人: ✅ 已添加单元测试... [继续 Session: ses_abc123]

# 第3轮：还是同一会话
用户: /opencode 运行测试验证
机器人: ✅ 测试通过... [继续 Session: ses_abc123]

# 创建新会话
用户: /opencode new 优化 BashApp 性能
机器人: ✅ 开始优化... [新 Session: ses_def456]
```

---

## 🎯 功能需求

### 1. 核心命令

| 命令 | 格式 | 说明 |
|------|------|------|
| 执行任务 | `/opencode <prompt>` | 执行 opencode run 命令 |
| 会话列表 | `/opencode session list` | 查看所有 OpenCode 会话 |
| 继续会话 | `/opencode session continue <id>` | 继续指定会话 |
| 帮助 | `/opencode help` | 显示帮助信息 |

### 2. 高级功能（Phase 2）

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 服务器模式 | 启动 opencode serve 并复用连接 | P2 |
| 会话绑定 | 每个话题绑定独立会话 | P1 |
| 上下文管理 | 在话题中自动传递会话ID | P1 |
| 异步执行 | 长时间任务异步执行并回调 | P1 |

---

## 🏗️ 架构设计

### COLA 分层架构

```
┌─────────────────────────────────────────┐
│     feishu-bot-adapter (适配层)         │
│  FeishuEventListener (已有，无需修改)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     feishu-bot-app (应用层)             │
│  (已有，无需修改)                        │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│     feishu-bot-domain (领域层)         │
│  ┌─────────────────────────────────┐   │
│  │ OpenCodeApp (NEW)               │   │
│  │  - 命令解析                     │   │
│  │  - 会话管理                     │   │
│  │  - 异步执行协调                 │   │
│  └────────────┬────────────────────┘   │
│               │                          │
│  ┌────────────▼────────────────────┐   │
│  │ OpenCodeGateway (接口 - NEW)    │   │
│  │  - executeCommand()             │   │
│  │  - listSessions()               │   │
│  │  - continueSession()            │   │
│  └────────────┬────────────────────┘   │
└───────────────┼────────────────────────┘
                │
┌───────────────▼──────────────────────────────┐
│  feishu-bot-infrastructure (基础设施层)       │
│  ┌──────────────────────────────────────┐   │
│  │ OpenCodeGatewayImpl (实现 - NEW)     │   │
│  │  - 调用 opencode CLI                 │   │
│  │  - 解析 JSON 输出                    │   │
│  │  - 管理子进程                        │   │
│  └────────────┬─────────────────────────┘   │
│               │                              │
│  ┌────────────▼─────────────────────────┐  │
│  │ OpenCodeProperties (配置 - NEW)      │  │
│  │  - opencode executable path          │  │
│  │  - default timeout                   │  │
│  │  - max output length                 │  │
│  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

---

## 📂 文件清单

### 领域层（feishu-bot-domain）

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── app/
│   └── OpenCodeApp.java                    # 主应用类
├── gateway/
│   └── OpenCodeGateway.java                # 网关接口
├── model/
│   ├── OpenCodeSession.java                # 会话实体（可选）
│   └── OpenCodeCommand.java                # 命令枚举
└── exception/
    └── OpenCodeException.java              # 自定义异常
```

### 基础设施层（feishu-bot-infrastructure）

```
feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── gateway/
│   └── OpenCodeGatewayImpl.java            # 网关实现
└── config/
    └── OpenCodeProperties.java             # 配置属性
```

---

## 💻 核心类设计

### 1. OpenCodeApp.java

```java
package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class OpenCodeApp implements FishuAppI {

    private final OpenCodeGateway openCodeGateway;
    private final FeishuGateway feishuGateway;

    // 同步执行超时阈值（5秒）
    private static final long SYNC_TIMEOUT_MS = 5000;

    public OpenCodeApp(OpenCodeGateway openCodeGateway,
                       FeishuGateway feishuGateway) {
        this.openCodeGateway = openCodeGateway;
        this.feishuGateway = feishuGateway;
    }

    @Override
    public String getAppId() {
        return "opencode";
    }

    @Override
    public String getAppName() {
        return "OpenCode 助手";
    }

    @Override
    public String getDescription() {
        return "通过飞书对话控制 OpenCode 会话";
    }

    @Override
    public String getHelp() {
        return "用法：\n" +
               "/opencode <提示词>              - 执行 OpenCode 任务\n" +
               "/opencode session list         - 查看所有会话\n" +
               "/opencode session continue <id> - 继续指定会话\n" +
               "/opencode help                 - 显示此帮助\n\n" +
               "示例：\n" +
               "/opencode 解释这个函数的作用\n" +
               "/opencode 添加错误处理";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("oc", "code");
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;  // 使用话题模式，支持上下文
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 3);

        if (parts.length < 2) {
            return getHelp();
        }

        String subCommand = parts[1].toLowerCase();

        // 处理不同的子命令
        switch (subCommand) {
            case "help":
                return getHelp();

            case "session":
                return handleSessionCommand(parts, message);

            default:
                // 默认：执行 opencode run
                String prompt = content.substring(content.indexOf(' ') + 1).trim();
                return executeOpenCodeTask(message, prompt);
        }
    }

    /**
     * 处理会话相关命令
     */
    private String handleSessionCommand(String[] parts, Message message) {
        if (parts.length < 3) {
            return "用法：/opencode session <list|continue> [args]";
        }

        String action = parts[2].toLowerCase();

        if ("list".equals(action)) {
            return openCodeGateway.listSessions();
        } else if ("continue".equals(action)) {
            if (parts.length < 4) {
                return "用法：/opencode session continue <session_id>";
            }
            String sessionId = parts[3];
            return executeOpenCodeTask(message, null, sessionId);
        } else {
            return "未知的 session 命令: " + action;
        }
    }

    /**
     * 执行 OpenCode 任务（同步或异步）
     */
    private String executeOpenCodeTask(Message message, String prompt) {
        return executeOpenCodeTask(message, prompt, null);
    }

    /**
     * 执行 OpenCode 任务（支持会话继续）
     */
    private String executeOpenCodeTask(Message message, String prompt, String sessionId) {
        long startTime = System.nanoTime();

        try {
            // 尝试同步执行（5秒超时）
            String result = openCodeGateway.executeCommand(prompt, sessionId, 5);

            if (result == null) {
                // 执行时间超过5秒，转为异步执行
                log.info("任务执行超过5秒，转为异步执行");
                feishuGateway.sendMessage(message, "⏳ 任务正在执行中，结果将稍后返回...",
                                          message.getTopicId());
                executeOpenCodeAsync(message, prompt, sessionId);
                return null;
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // 如果执行时间超过2秒，先发送"执行中"消息
            if (durationMs > 2000) {
                feishuGateway.sendMessage(message, "⏳ 任务执行中...",
                                          message.getTopicId());
            }

            return formatOutput(result);

        } catch (Exception e) {
            log.error("OpenCode 执行失败", e);
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
     * 异步执行 OpenCode 任务
     */
    @Async("opencodeExecutor")
    public void executeOpenCodeAsync(Message message, String prompt, String sessionId) {
        try {
            String result = openCodeGateway.executeCommand(prompt, sessionId, 0);  // 0表示无超时限制
            String formatted = formatOutput(result);
            feishuGateway.sendMessage(message, formatted, message.getTopicId());
        } catch (Exception e) {
            log.error("异步执行失败", e);
            feishuGateway.sendMessage(message, "❌ 执行失败: " + e.getMessage(),
                                      message.getTopicId());
        }
    }

    /**
     * 格式化输出结果
     */
    private String formatOutput(String rawOutput) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "✅ 执行完成，无输出";
        }

        // 截断过长的输出（飞书消息限制）
        int maxLength = 2000;
        if (rawOutput.length() > maxLength) {
            return rawOutput.substring(0, maxLength - 20) + "\n...(输出过长，已截断)";
        }

        return rawOutput;
    }
}
```

---

### 2. OpenCodeGateway.java（接口）

```java
package com.qdw.feishu.domain.gateway;

/**
 * OpenCode Gateway 接口
 *
 * 定义与 OpenCode CLI 交互的抽象
 */
public interface OpenCodeGateway {

    /**
     * 执行 OpenCode 命令
     *
     * @param prompt 提示词（可为null，如果继续会话）
     * @param sessionId 会话ID（可为null，如果是新会话）
     * @param timeoutSeconds 超时时间（秒），0表示无限制
     * @return 执行结果，如果超时返回null
     * @throws Exception 执行异常
     */
    String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception;

    /**
     * 列出所有会话
     *
     * @return 格式化的会话列表
     */
    String listSessions();

    /**
     * 获取服务器状态
     *
     * @return 状态信息
     */
    String getServerStatus();
}
```

---

### 3. OpenCodeGatewayImpl.java（实现）

```java
package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.config.OpenCodeProperties;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class OpenCodeGatewayImpl implements OpenCodeGateway {

    private final OpenCodeProperties properties;
    private final String opencodeExecutable;

    public OpenCodeGatewayImpl(OpenCodeProperties properties) {
        this.properties = properties;
        this.opencodeExecutable = findExecutable();
        log.info("OpenCode Gateway 初始化完成，可执行文件: {}", opencodeExecutable);
    }

    /**
     * 查找 opencode 可执行文件
     */
    private String findExecutable() {
        String path = properties.getExecutablePath();
        if (path != null && !path.isEmpty()) {
            return path;
        }

        // 尝试从 PATH 中查找
        String[] searchPaths = {"/usr/bin/opencode", "/usr/local/bin/opencode"};
        for (String testPath : searchPaths) {
            if (new java.io.File(testPath).exists()) {
                return testPath;
            }
        }

        // 默认使用 "opencode"，依赖 PATH
        return "opencode";
    }

    @Override
    public String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(opencodeExecutable);
        command.add("run");
        command.add("--format");  // 使用 JSON 格式输出
        command.add("json");

        // 添加会话继续参数
        if (sessionId != null && !sessionId.isEmpty()) {
            command.add("--session");
            command.add(sessionId);
        }

        // 构建进程
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        log.info("执行 OpenCode 命令: {}", String.join(" ", command));

        Process process = pb.start();

        // 如果有超时限制
        if (timeoutSeconds > 0) {
            ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(() -> readProcessOutput(process));

            try {
                String output = future.get(timeoutSeconds, TimeUnit.SECONDS);
                executor.shutdown();
                return parseOpenCodeOutput(output);
            } catch (TimeoutException e) {
                process.destroyForcibly();
                executor.shutdownNow();
                log.warn("OpenCode 执行超时（{}秒）", timeoutSeconds);
                return null;  // 超时返回null
            }
        } else {
            // 无超时限制
            String output = readProcessOutput(process);
            return parseOpenCodeOutput(output);
        }
    }

    @Override
    public String listSessions() {
        try {
            List<String> command = List.of(opencodeExecutable, "session", "list");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            // 解析输出并格式化
            if (output.isEmpty() || output.contains("No sessions found")) {
                return "📋 暂无会话记录";
            }

            return "📋 OpenCode 会话列表:\n\n" + output;

        } catch (Exception e) {
            log.error("列出会话失败", e);
            return "❌ 获取会话列表失败: " + e.getMessage();
        }
    }

    @Override
    public String getServerStatus() {
        // TODO: 实现服务器状态检查
        return "✅ OpenCode 可用";
    }

    /**
     * 读取进程输出
     */
    private String readProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();
            return output.toString();

        } catch (Exception e) {
            log.error("读取进程输出失败", e);
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 解析 OpenCode JSON 输出，提取文本内容
     */
    private String parseOpenCodeOutput(String jsonOutput) {
        if (jsonOutput == null || jsonOutput.isEmpty()) {
            return "";
        }

        StringBuilder textContent = new StringBuilder();

        // 解析 JSON Lines 格式
        String[] lines = jsonOutput.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            try {
                com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(line);

                // 提取 text 类型消息
                if (node.has("type") && "text".equals(node.get("type").asText())) {
                    if (node.has("part") && node.get("part").has("text")) {
                        String text = node.get("part").get("text").asText();
                        textContent.append(text).append("\n");
                    }
                }

                // 提取 tool_use 输出
                if (node.has("type") && "tool_use".equals(node.get("type").asText())) {
                    if (node.has("part") && node.get("part").has("state")) {
                        var state = node.get("part").get("state");
                        if (state.has("output")) {
                            String output = state.get("output").asText();
                            textContent.append("```\n").append(output).append("\n```\n");
                        }
                    }
                }

            } catch (Exception e) {
                // JSON 解析失败，保留原始行
                textContent.append(line).append("\n");
            }
        }

        return textContent.toString().trim();
    }
}
```

---

### 4. OpenCodeProperties.java（配置）

```java
package com.qdw.feishu.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenCode 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "opencode")
public class OpenCodeProperties {

    /**
     * OpenCode 可执行文件路径
     * 如果为null，则从PATH中查找
     */
    private String executablePath;

    /**
     * 默认超时时间（秒）
     */
    private int defaultTimeout = 30;

    /**
     * 最大输出长度（字符）
     */
    private int maxOutputLength = 2000;

    /**
     * 是否启用异步执行
     */
    private boolean asyncEnabled = true;
}
```

---

### 5. AsyncConfig.java（异步配置）

```java
package com.qdw.feishu.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步执行器配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * OpenCode 异步执行线程池
     */
    @Bean(name = "opencodeExecutor")
    public Executor opencodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("opencode-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
```

---

## ⚙️ 配置文件

### application.yml 添加配置

```yaml
# OpenCode 配置
opencode:
  executable-path: /usr/bin/opencode  # 可选，默认从PATH查找
  default-timeout: 30                 # 默认超时时间（秒）
  max-output-length: 2000            # 最大输出长度
  async-enabled: true                 # 启用异步执行
```

---

## 🔄 执行流程

### 同步执行（< 5秒）

```
用户: /opencode 解释这个函数
    ↓
OpenCodeApp.execute()
    ↓
openCodeGateway.executeCommand(prompt, null, 5)
    ↓
ProcessBuilder 执行 opencode run --format json
    ↓
等待5秒内完成
    ↓
解析 JSON 输出
    ↓
返回结果给用户
```

### 异步执行（> 5秒）

```
用户: /opencode 重构整个模块
    ↓
OpenCodeApp.execute()
    ↓
openCodeGateway.executeCommand(prompt, null, 5)
    ↓
5秒超时，返回 null
    ↓
发送 "⏳ 任务正在执行中..."
    ↓
executeOpenCodeAsync()
    ↓
opencodeExecutor 线程池异步执行
    ↓
完成后发送结果到飞书话题
```

---

## 📝 使用示例

### 示例1：简单任务

```
用户: /opencode 解释闭包的概念

机器人:
在编程中，闭包（Closure）是指有权访问另一个函数作用域中
变量的函数...

[详细的解释和示例]
```

### 示例2：代码重构

```
用户: /opencode 重构 TimeApp，添加日期格式验证

机器人:
⏳ 任务正在执行中...

[1分钟后]

✅ 重构完成

已添加以下内容：
1. validateDateFormat() 方法
2. 异常处理逻辑
3. 单元测试建议

修改的文件：
- feishu-bot-domain/.../TimeApp.java
- feishu-bot-domain/.../DateValidator.java (新增)
```

### 示例3：会话管理

```
用户: /opencode session list

机器人:
📋 OpenCode 会话列表:

1. [2026-02-01 14:30] "重构登录模块"
   Session ID: ses_abc123
   状态: 已完成

2. [2026-02-01 15:45] "添加单元测试"
   Session ID: ses_def456
   状态: 执行中

用户: /opencode session continue ses_def456

机器人:
📋 继续会话 ses_def456

上次的任务："添加单元测试"

当前进度：已完成3个测试用例，还剩2个...
```

---

## 🚀 实施计划

### Phase 1: MVP（最小可行产品）

**目标**：基本功能可用

- [ ] 创建 OpenCodeApp
- [ ] 创建 OpenCodeGateway 接口
- [ ] 创建 OpenCodeGatewayImpl
- [ ] 创建 OpenCodeProperties
- [ ] 添加异步配置
- [ ] 测试基本命令执行

**估计时间**: 2-3小时

### Phase 2: 会话管理

**目标**：支持会话列表和继续

- [ ] 实现 listSessions()
- [ ] 实现 session continue
- [ ] 会话状态持久化（可选）

**估计时间**: 1-2小时

### Phase 3: 高级功能

**目标**：服务器模式和优化

- [ ] opencode serve 支持
- [ ] 话题与会话绑定
- [ ] 输出格式优化（Markdown支持）
- [ ] 错误处理增强

**估计时间**: 2-3小时

---

## ⚠️ 注意事项

### 安全性

1. **命令白名单**：虽然 OpenCode 本身有安全机制，但建议限制可执行的操作
2. **资源限制**：限制并发任务数量，防止资源耗尽
3. **超时控制**：防止无限期执行

### 性能优化

1. **连接复用**：考虑使用 `opencode serve` + HTTP API（Phase 3）
2. **输出缓存**：缓存常用命令的结果
3. **异步优化**：合理配置线程池大小

### 用户体验

1. **进度反馈**：长时间任务定期发送进度更新
2. **错误友好**：清晰的错误提示和建议
3. **结果格式化**：使用 Markdown 改善可读性

---

## 📚 参考资料

- [OpenCode CLI 文档](https://opencode.ai/docs/cli/)
- [COLA 架构规范](../AGENTS.md)
- [应用开发指南](../APP_GUIDE.md)
- [BashApp 实现参考](../feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/BashApp.java)

---

**创建时间**: 2026-02-01
**最后更新**: 2026-02-01
**状态**: 设计阶段，待实施

# Tool Visibility Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 OpenCode 工具执行可见性，让用户看到 AI 执行了哪些操作（读取文件、编辑代码、执行命令等）

**Architecture:** Phase 1 通过解析 HTTP 响应中的 `tool_use` 信息，提取工具名称和执行结果，格式化为用户友好的摘要显示。采用表情反应 + 消息内容组合方案。

**Tech Stack:** Java 17, Spring Boot, Jackson (JSON), Lombok

---

## 前置检查

```bash
# 确认当前分支状态
git status

# 确认项目可编译
mvn clean compile -q
```

---

## Task 1: 创建 ToolExecution 数据模型

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/ToolExecution.java`

**Step 1: 创建 ToolExecution 类**

```java
package com.qdw.feishu.domain.opencode;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行记录
 *
 * 记录 OpenCode 执行的单个工具调用信息
 */
@Data
@Builder
public class ToolExecution {

    private String toolName;

    private String action;

    private String status;

    private String summary;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/ToolExecution.java
git commit -m "feat(opencode): add ToolExecution data model"
```

---

## Task 2: 创建 CommandResult 数据模型

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/CommandResult.java`

**Step 1: 创建 CommandResult 类**

```java
package com.qdw.feishu.domain.opencode;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 命令执行结果
 *
 * 包含 AI 响应内容和工具执行列表
 */
@Data
@Builder
public class CommandResult {

    private String content;

    @Builder.Default
    private List<ToolExecution> tools = new ArrayList<>();

    private boolean success;

    private String errorMessage;

    private String sessionId;

    public int getToolCount() {
        return tools != null ? tools.size() : 0;
    }

    public boolean hasTools() {
        return getToolCount() > 0;
    }

    public static CommandResult error(String errorMessage) {
        return CommandResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static CommandResult success(String content) {
        return CommandResult.builder()
                .content(content)
                .success(true)
                .build();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/CommandResult.java
git commit -m "feat(opencode): add CommandResult data model"
```

---

## Task 3: 创建工具图标映射枚举

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/ToolIcon.java`

**Step 1: 创建 ToolIcon 枚举**

```java
package com.qdw.feishu.domain.opencode;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具图标映射
 *
 * 为不同工具类型提供友好的表情图标
 */
public enum ToolIcon {

    READ("read", "📖", "读取文件"),
    EDIT("edit", "✏️", "编辑文件"),
    BASH("bash", "⚡", "执行命令"),
    GREP("grep", "🔍", "搜索内容"),
    GLOB("glob", "📁", "查找文件"),
    WRITE("write", "📝", "写入文件"),
    LIST("list_directory", "📂", "列出目录"),
    WEB("web", "🌐", "网络请求"),
    UNKNOWN("unknown", "🔧", "执行操作");

    private final String toolName;
    private final String icon;
    private final String description;

    private static final Map<String, ToolIcon> TOOL_MAP = new HashMap<>();

    static {
        for (ToolIcon tool : values()) {
            TOOL_MAP.put(tool.toolName, tool);
        }
    }

    ToolIcon(String toolName, String icon, String description) {
        this.toolName = toolName;
        this.icon = icon;
        this.description = description;
    }

    public static ToolIcon fromToolName(String toolName) {
        if (toolName == null) {
            return UNKNOWN;
        }
        String lowerName = toolName.toLowerCase();
        for (Map.Entry<String, ToolIcon> entry : TOOL_MAP.entrySet()) {
            if (lowerName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return UNKNOWN;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/ToolIcon.java
git commit -m "feat(opencode): add ToolIcon enum for tool visualization"
```

---

## Task 4: 更新 OpenCodeResponseFormatter 支持工具解析

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeResponseFormatter.java`

**Step 1: 添加工具解析方法**

在 `OpenCodeResponseFormatter` 类中添加以下方法：

```java
/**
 * 解析 OpenCode 响应为 CommandResult
 *
 * @param rawOutput 原始 JSON 响应
 * @return CommandResult 包含内容和工具列表
 */
public CommandResult parseResponse(String rawOutput) {
    if (rawOutput == null || rawOutput.isEmpty()) {
        return CommandResult.success("✅ 执行完成，无输出");
    }

    try {
        JsonNode root = objectMapper.readTree(rawOutput);
        
        CommandResult.CommandResultBuilder builder = CommandResult.builder()
                .success(true);

        StringBuilder contentBuilder = new StringBuilder();
        List<ToolExecution> tools = new ArrayList<>();

        if (root.has("parts") && root.get("parts").isArray()) {
            JsonNode parts = root.get("parts");
            for (JsonNode part : parts) {
                String type = part.has("type") ? part.get("type").asText() : "";

                if ("text".equals(type)) {
                    extractTextContent(part, contentBuilder);
                } else if ("tool_use".equals(type)) {
                    ToolExecution tool = extractToolExecution(part);
                    if (tool != null) {
                        tools.add(tool);
                    }
                }
            }
        }

        String sessionId = extractSessionIdFromJson(root);
        
        return builder
                .content(contentBuilder.toString().trim())
                .tools(tools)
                .sessionId(sessionId)
                .build();

    } catch (Exception e) {
        log.warn("JSON 解析失败，返回原始内容: {}", e.getMessage());
        return CommandResult.success(rawOutput);
    }
}

private void extractTextContent(JsonNode part, StringBuilder contentBuilder) {
    if (part.has("text")) {
        JsonNode textNode = part.get("text");
        if (textNode.isTextual()) {
            contentBuilder.append(textNode.asText()).append("\n");
        } else if (textNode.has("content")) {
            contentBuilder.append(textNode.get("content").asText()).append("\n");
        }
    }
}

private ToolExecution extractToolExecution(JsonNode part) {
    if (!part.has("toolUse")) {
        return null;
    }

    JsonNode toolUse = part.get("toolUse");
    String toolName = toolUse.has("name") ? toolUse.get("name").asText() : "unknown";
    
    String action = extractToolAction(toolUse);
    String summary = extractToolSummary(toolUse, toolName);
    String status = toolUse.has("output") ? "success" : "error";

    return ToolExecution.builder()
            .toolName(toolName)
            .action(action)
            .status(status)
            .summary(summary)
            .build();
}

private String extractToolAction(JsonNode toolUse) {
    if (!toolUse.has("input")) {
        return "";
    }

    JsonNode input = toolUse.get("input");
    
    if (input.has("file_path")) {
        return input.get("file_path").asText();
    }
    if (input.has("command")) {
        return input.get("command").asText();
    }
    if (input.has("pattern")) {
        return input.get("pattern").asText();
    }

    return "";
}

private String extractToolSummary(JsonNode toolUse, String toolName) {
    ToolIcon icon = ToolIcon.fromToolName(toolName);
    String action = extractToolAction(toolUse);
    
    if (action.isEmpty()) {
        return String.format("%s %s", icon.getIcon(), icon.getDescription());
    }

    if (action.length() > 50) {
        action = action.substring(0, 47) + "...";
    }

    return String.format("%s %s: %s", icon.getIcon(), icon.getDescription(), action);
}

private String extractSessionIdFromJson(JsonNode root) {
    if (root.has("session_id")) {
        return root.get("session_id").asText();
    }

    if (root.has("parts") && root.get("parts").isArray()) {
        for (JsonNode part : root.get("parts")) {
            if (part.has("session_id")) {
                return part.get("session_id").asText();
            }
        }
    }

    return null;
}
```

**Step 2: 添加格式化方法**

```java
/**
 * 格式化 CommandResult 为用户友好的消息
 *
 * @param result 命令结果
 * @return 格式化后的消息
 */
public String formatResult(CommandResult result) {
    if (!result.isSuccess()) {
        return formatErrorResult(result);
    }

    StringBuilder sb = new StringBuilder();

    if (result.hasTools()) {
        sb.append("✅ 完成（执行了 ").append(result.getToolCount()).append(" 个操作）\n\n");
    } else {
        sb.append("✅ 完成\n\n");
    }

    if (result.getContent() != null && !result.getContent().isEmpty()) {
        sb.append("📝 **AI 响应**：\n");
        sb.append(result.getContent()).append("\n");
    }

    if (result.hasTools()) {
        sb.append("\n---\n\n");
        sb.append("🔧 **执行的操作**：\n");
        for (ToolExecution tool : result.getTools()) {
            sb.append("• ").append(tool.getSummary());
            if (!tool.isSuccess()) {
                sb.append(" ❌");
            }
            sb.append("\n");
        }
    }

    if (result.getSessionId() != null && !result.getSessionId().isEmpty()) {
        sb.append("\n💾 _会话ID: `").append(result.getSessionId()).append("`_");
    }

    String output = sb.toString();
    
    if (output.length() > 3500) {
        output = output.substring(0, 3450) + "\n\n...(输出过长，已截断)";
    }

    return output;
}

private String formatErrorResult(CommandResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("❌ 执行失败\n\n");

    if (result.getErrorMessage() != null) {
        sb.append("**错误信息**：").append(result.getErrorMessage()).append("\n\n");
    }

    sb.append("💡 **建议**：\n");
    sb.append("• 检查 OpenCode 服务是否启动\n");
    sb.append("• 使用 /opencode status 查看服务状态\n");

    return sb.toString();
}
```

**Step 3: 添加必要的 import**

在文件顶部添加：

```java
import com.qdw.feishu.domain.opencode.CommandResult;
import com.qdw.feishu.domain.opencode.ToolExecution;
import com.qdw.feishu.domain.opencode.ToolIcon;
import java.util.List;
import java.util.ArrayList;
```

**Step 4: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeResponseFormatter.java
git commit -m "feat(opencode): add tool parsing and formatting to ResponseFormatter"
```

---

## Task 5: 更新 OpenCodeGateway 接口

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeGateway.java`

**Step 1: 添加新方法**

在接口中添加：

```java
import com.qdw.feishu.domain.opencode.CommandResult;

/**
 * 执行命令并返回结构化结果
 *
 * @param prompt 提示词
 * @param sessionId 会话 ID（可为 null）
 * @param timeoutSeconds 超时时间（秒）
 * @return CommandResult 结构化结果
 * @throws Exception 执行异常
 */
CommandResult executeCommandWithResult(String prompt, String sessionId, int timeoutSeconds) throws Exception;
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeGateway.java
git commit -m "feat(opencode): add executeCommandWithResult method to Gateway interface"
```

---

## Task 6: 实现 OpenCodeGatewayImpl 新方法

**Files:**
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java`

**Step 1: 添加 import**

```java
import com.qdw.feishu.domain.opencode.CommandResult;
import com.qdw.feishu.domain.opencode.OpenCodeResponseFormatter;
import org.springframework.beans.factory.annotation.Autowired;
```

**Step 2: 注入 ResponseFormatter**

在类中添加：

```java
private final OpenCodeResponseFormatter responseFormatter;

public OpenCodeGatewayImpl(OpenCodeProperties properties, 
                           @Autowired(required = false) OpenCodeResponseFormatter responseFormatter) {
    this.properties = properties;
    this.responseFormatter = responseFormatter;
    this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
            .build();
    log.info("OpenCode Gateway 初始化完成，服务端: {}", properties.getServerUrl());
}
```

**Step 3: 实现 executeCommandWithResult 方法**

```java
@Override
public CommandResult executeCommandWithResult(String prompt, String sessionId, int timeoutSeconds) throws Exception {
    String rawResponse = executeCommand(prompt, sessionId, timeoutSeconds);
    
    if (rawResponse == null) {
        return CommandResult.error("执行超时");
    }

    if (rawResponse.startsWith("❌")) {
        return CommandResult.error(rawResponse);
    }

    if (responseFormatter != null) {
        return responseFormatter.parseResponse(rawResponse);
    }

    return CommandResult.success(rawResponse);
}
```

**Step 4: 编译验证**

Run: `mvn compile -pl feishu-bot-infrastructure -q`
Expected: BUILD SUCCESS

**Step 5: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java
git commit -m "feat(opencode): implement executeCommandWithResult in GatewayImpl"
```

---

## Task 7: 更新 OpenCodeTaskExecutor 使用新格式

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java`

**Step 1: 修改 executeAsync 方法**

将原来的 `executeAsync` 方法修改为：

```java
@Async("opencodeExecutor")
public void executeAsync(Message message, String prompt, String sessionId) {
    String messageId = message.getMessageId();
    log.info("异步执行开始: messageId={}, sessionId={}", messageId, sessionId);

    try {
        CommandResult result = openCodeGateway.executeCommandWithResult(prompt, sessionId, EXECUTE_TIMEOUT);

        if (result == null || (!result.isSuccess() && result.getErrorMessage() != null && result.getErrorMessage().contains("超时"))) {
            log.warn("异步执行超时（{}秒）", EXECUTE_TIMEOUT);
            feishuGateway.sendMessage(message, 
                "⚠️ 任务执行超时，请稍后重试或尝试简化问题。", 
                message.getTopicId());
            return;
        }

        boolean reactionAdded = feishuGateway.addReaction(messageId, ReactionEmoji.CLAP);
        if (!reactionAdded) {
            log.debug("完成表情添加失败，但不影响主流程");
        }
        log.info("异步完成，添加表情: CLAP");

        String extractedSessionId = result.getSessionId();
        if (extractedSessionId != null && message.getTopicId() != null) {
            sessionManager.saveSession(message.getTopicId(), extractedSessionId);
        }

        String formatted = responseFormatter.formatResult(result);
        feishuGateway.sendMessage(message, formatted, message.getTopicId());

    } catch (Exception e) {
        log.error("异步执行失败", e);
        feishuGateway.sendMessage(message, "❌ 执行失败: " + e.getMessage(), message.getTopicId());
    }
}
```

**Step 2: 添加 import**

```java
import com.qdw.feishu.domain.opencode.CommandResult;
```

**Step 3: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java
git commit -m "feat(opencode): integrate tool visibility in TaskExecutor"
```

---

## Task 8: 编写单元测试

**Files:**
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeResponseFormatterTest.java`

**Step 1: 创建测试类**

```java
package com.qdw.feishu.domain.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenCodeResponseFormatterTest {

    private OpenCodeResponseFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new OpenCodeResponseFormatter(new ObjectMapper());
    }

    @Test
    void should_parseTextContent_when_givenTextPart() {
        String json = """
            {
              "parts": [
                {
                  "type": "text",
                  "text": {"content": "Hello World"}
                }
              ]
            }
            """;

        CommandResult result = formatter.parseResponse(json);

        assertTrue(result.isSuccess());
        assertEquals("Hello World", result.getContent());
        assertFalse(result.hasTools());
    }

    @Test
    void should_parseToolExecution_when_givenToolUsePart() {
        String json = """
            {
              "parts": [
                {
                  "type": "tool_use",
                  "toolUse": {
                    "name": "read",
                    "input": {"file_path": "/src/main.java"},
                    "output": "file content"
                  }
                }
              ]
            }
            """;

        CommandResult result = formatter.parseResponse(json);

        assertTrue(result.isSuccess());
        assertTrue(result.hasTools());
        assertEquals(1, result.getToolCount());
        
        ToolExecution tool = result.getTools().get(0);
        assertEquals("read", tool.getToolName());
        assertTrue(tool.getSummary().contains("📖"));
    }

    @Test
    void should_parseMultipleTools_when_givenMultipleToolUseParts() {
        String json = """
            {
              "parts": [
                {
                  "type": "tool_use",
                  "toolUse": {
                    "name": "read",
                    "input": {"file_path": "/src/main.java"},
                    "output": "content"
                  }
                },
                {
                  "type": "tool_use",
                  "toolUse": {
                    "name": "edit",
                    "input": {"file_path": "/src/main.java"},
                    "output": "edited"
                  }
                }
              ]
            }
            """;

        CommandResult result = formatter.parseResponse(json);

        assertEquals(2, result.getToolCount());
    }

    @Test
    void should_returnSuccessWithRawContent_when_jsonParseFails() {
        String invalidJson = "This is not JSON";

        CommandResult result = formatter.parseResponse(invalidJson);

        assertTrue(result.isSuccess());
        assertEquals("This is not JSON", result.getContent());
    }

    @Test
    void should_formatResultCorrectly_when_hasTools() {
        CommandResult result = CommandResult.builder()
                .success(true)
                .content("AI response text")
                .tools(java.util.List.of(
                    ToolExecution.builder()
                        .toolName("read")
                        .summary("📖 读取文件: /src/main.java")
                        .status("success")
                        .build()
                ))
                .build();

        String formatted = formatter.formatResult(result);

        assertTrue(formatted.contains("✅ 完成"));
        assertTrue(formatted.contains("执行了 1 个操作"));
        assertTrue(formatted.contains("🔧 **执行的操作**"));
        assertTrue(formatted.contains("📖 读取文件"));
    }

    @Test
    void should_formatErrorResult_when_resultIsNotSuccess() {
        CommandResult result = CommandResult.error("Connection failed");

        String formatted = formatter.formatResult(result);

        assertTrue(formatted.contains("❌ 执行失败"));
        assertTrue(formatted.contains("Connection failed"));
    }

    @Test
    void should_returnErrorResult_when_inputIsNull() {
        CommandResult result = formatter.parseResponse(null);

        assertTrue(result.isSuccess());
        assertTrue(result.getContent().contains("无输出"));
    }
}
```

**Step 2: 运行测试**

Run: `mvn test -pl feishu-bot-domain -Dtest=OpenCodeResponseFormatterTest -q`
Expected: Tests run: 7, Failures: 0, Errors: 0

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeResponseFormatterTest.java
git commit -m "test(opencode): add unit tests for ResponseFormatter tool parsing"
```

---

## Task 9: 编写 ToolIcon 测试

**Files:**
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/ToolIconTest.java`

**Step 1: 创建测试类**

```java
package com.qdw.feishu.domain.opencode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolIconTest {

    @Test
    void should_returnReadIcon_when_toolNameIsRead() {
        ToolIcon icon = ToolIcon.fromToolName("read");
        assertEquals("📖", icon.getIcon());
        assertEquals("读取文件", icon.getDescription());
    }

    @Test
    void should_returnEditIcon_when_toolNameIsEdit() {
        ToolIcon icon = ToolIcon.fromToolName("edit");
        assertEquals("✏️", icon.getIcon());
    }

    @Test
    void should_returnBashIcon_when_toolNameIsBash() {
        ToolIcon icon = ToolIcon.fromToolName("bash");
        assertEquals("⚡", icon.getIcon());
    }

    @Test
    void should_returnUnknownIcon_when_toolNameIsUnknown() {
        ToolIcon icon = ToolIcon.fromToolName("unknown_tool");
        assertEquals("🔧", icon.getIcon());
    }

    @Test
    void should_returnUnknownIcon_when_toolNameIsNull() {
        ToolIcon icon = ToolIcon.fromToolName(null);
        assertEquals("🔧", icon.getIcon());
    }

    @Test
    void should_matchByPartialName_when_toolNameContains() {
        ToolIcon icon = ToolIcon.fromToolName("read_file");
        assertEquals("📖", icon.getIcon());
    }
}
```

**Step 2: 运行测试**

Run: `mvn test -pl feishu-bot-domain -Dtest=ToolIconTest -q`
Expected: Tests run: 6, Failures: 0, Errors: 0

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/ToolIconTest.java
git commit -m "test(opencode): add unit tests for ToolIcon enum"
```

---

## Task 10: 全量测试和构建验证

**Step 1: 运行所有测试**

Run: `mvn test -q`
Expected: All tests pass

**Step 2: 完整构建**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS

**Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat(opencode): complete tool visibility implementation (Phase 1)

- Add CommandResult and ToolExecution data models
- Add ToolIcon enum for tool visualization
- Enhance OpenCodeResponseFormatter with tool parsing
- Update OpenCodeGateway interface with executeCommandWithResult
- Integrate tool visibility in OpenCodeTaskExecutor
- Add comprehensive unit tests"
```

---

## 验证清单

- [ ] `mvn compile` 无错误
- [ ] `mvn test` 全部通过
- [ ] 新代码有单元测试覆盖
- [ ] 所有新类有 Javadoc 注释
- [ ] 代码符合项目规范（COLA 架构）

---

## 测试指南

部署后测试：

1. **纯文本对话测试**
   ```
   /opencode chat 你好
   ```
   预期：显示 "✅ 完成" 无工具摘要

2. **工具执行测试**
   ```
   /opencode chat 读取 pom.xml 文件
   ```
   预期：显示 "✅ 完成（执行了 1 个操作）" + 工具摘要

3. **多工具测试**
   ```
   /opencode chat 查找所有 Java 文件并列出它们
   ```
   预期：显示多个工具执行摘要

---

**最后更新**: 2026-02-24

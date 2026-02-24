# 工具执行可见性设计

> **状态**: 设计完成，待实现  
> **日期**: 2026-02-24  
> **作者**: AI Assistant

---

## 1. 背景与目标

### 1.1 问题

当前 OpenCode 应用在处理用户请求时，用户无法看到 AI 正在做什么：
- 长时间等待时用户体验差
- 不知道 AI 是否在工作
- 无法了解 AI 执行了哪些操作

### 1.2 目标

1. **状态可见性**：用户能看到 AI 正在处理请求
2. **工具可见性**：用户能看到 AI 执行了哪些工具（读取文件、编辑代码等）
3. **结果摘要**：显示工具执行的简短摘要

### 1.3 参考

借鉴 [opencode-chat-bridge](https://github.com/ominiverdi/opencode-chat-bridge) 的设计：
- 工具执行实时显示
- 状态表情指示
- 结果格式化

---

## 2. 设计方案

### 2.1 分阶段实现

| 阶段 | 方案 | 描述 | 效果 |
|------|------|------|------|
| **Phase 1** | HTTP 响应解析 | 解析响应中的工具信息，格式化显示 | 完成后显示工具摘要 |
| **Phase 2** | ACP 协议 | 通过 `opencode acp` 子进程获取实时事件 | 真正的流式响应 |

本文档专注于 **Phase 1**。

### 2.2 组合状态显示

采用表情反应 + 消息内容的组合方案：

| 状态 | 表情 | 消息内容 |
|------|------|---------|
| 处理中 | ⏳ | （无，仅表情） |
| 完成 | ✅ | 工具摘要 + AI 响应 |
| 错误 | ❌ | 错误信息 |

---

## 3. 架构设计

### 3.1 组件关系

```
┌─────────────────────────────────────────────────────────────┐
│                     FeishuEventListener                       │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   OpenCodeCommandHandler                     │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 1. addReaction(messageId, ⏳)                           │ │
│  │ 2. executeCommand() → CommandResult                     │ │
│  │ 3. formatResponse(result)                               │ │
│  │ 4. reply(formattedResponse)                             │ │
│  │ 5. removeReaction(messageId, ⏳)                        │ │
│  │ 6. addReaction(messageId, ✅/❌)                        │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    OpenCodeGatewayImpl                       │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ parseMessageResponse() → CommandResult                  │ │
│  │   - 提取 TextContent                                    │ │
│  │   - 提取 ToolCall 信息                                  │ │
│  │   - 提取 ToolResult 信息                                │ │
│  │   - 构建 ToolExecution 列表                             │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 数据模型

```java
// CommandResult - 命令执行结果
public class CommandResult {
    private String content;              // AI 文本内容
    private List<ToolExecution> tools;   // 工具执行列表
    private boolean success;             // 是否成功
    private String errorMessage;         // 错误信息
}

// ToolExecution - 工具执行记录
public class ToolExecution {
    private String toolName;     // 工具名称：read, bash, edit, grep...
    private String action;       // 操作描述
    private String status;       // success, error
    private String summary;      // 简短摘要
}
```

### 3.3 响应解析

OpenCode HTTP API 返回的消息结构：

```json
{
  "parts": [
    {
      "type": "text",
      "text": { "content": "AI 响应文本..." }
    },
    {
      "type": "tool_use",
      "toolUse": {
        "id": "tool_123",
        "name": "read",
        "input": { "file_path": "/src/main.java" },
        "output": "文件内容..."
      }
    }
  ]
}
```

解析逻辑：
1. 遍历 `parts[]` 数组
2. `type == "text"` → 提取文本内容
3. `type == "tool_use"` → 提取工具名称、输入、输出

---

## 4. 消息格式

### 4.1 正常响应

```
✅ 完成（执行了 3 个操作）

📝 **AI 响应**：
我已经完成了代码修改...

---

🔧 **执行的操作**：
• 📖 读取文件：src/main/java/App.java
• ✏️ 编辑文件：添加了错误处理逻辑
• ⚡️ 执行命令：npm test ✓
```

### 4.2 无工具执行

```
✅ 完成

📝 **AI 响应**：
这是纯文本回复，没有执行任何工具...
```

### 4.3 错误响应

```
❌ 执行失败

**错误信息**：无法连接到 OpenCode 服务

💡 **建议**：
• 检查 OpenCode 服务是否启动
• 使用 /opencode status 查看服务状态
```

---

## 5. 实现步骤

### 5.1 Phase 1: HTTP 响应解析

| 步骤 | 任务 | 文件 |
|------|------|------|
| 1 | 创建 `CommandResult` 和 `ToolExecution` 类 | `domain/opencode/` |
| 2 | 修改 `OpenCodeGateway` 接口 | `domain/gateway/` |
| 3 | 增强 `parseMessageResponse()` 方法 | `infrastructure/gateway/` |
| 4 | 创建 `ToolResultFormatter` 工具类 | `domain/opencode/` |
| 5 | 修改 `OpenCodeCommandHandler` 集成状态显示 | `domain/opencode/` |
| 6 | 添加表情反应管理 | `domain/gateway/` + `infrastructure/` |

### 5.2 代码变更清单

**新增文件**：
```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/
├── CommandResult.java          # 命令执行结果
├── ToolExecution.java          # 工具执行记录
└── ToolResultFormatter.java    # 结果格式化器
```

**修改文件**：
```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/
└── OpenCodeGateway.java        # 接口变更

feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/
└── OpenCodeGatewayImpl.java    # 解析逻辑增强

feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/
└── OpenCodeCommandHandler.java # 集成状态显示
```

---

## 6. 测试计划

### 6.1 单元测试

| 测试用例 | 描述 |
|---------|------|
| `testParseTextContent` | 解析纯文本响应 |
| `testParseToolCall` | 解析工具调用信息 |
| `testParseMultipleTools` | 解析多个工具执行 |
| `testFormatToolSummary` | 格式化工具摘要 |
| `testFormatEmptyTools` | 无工具时的格式化 |

### 6.2 集成测试

| 测试场景 | 描述 |
|---------|------|
| 纯文本对话 | 验证无工具时的显示 |
| 代码修改 | 验证 read + edit 工具显示 |
| 命令执行 | 验证 bash 工具显示 |
| 错误处理 | 验证错误状态显示 |

---

## 7. Phase 2 预览：ACP 协议

### 7.1 概述

ACP (Agent Client Protocol) 是 OpenCode 提供的 JSON-RPC 协议，通过子进程 stdio 通信。

### 7.2 架构

```
┌─────────────────┐     stdio      ┌─────────────────┐
│   ACPClient     │ ◄────────────► │  opencode acp   │
│   (Java)        │   JSON-RPC     │   (子进程)       │
└────────┬────────┘                └─────────────────┘
         │
         │ events
         ▼
┌─────────────────┐
│  Event Handlers │
│  - onChunk()    │  → 实时文本流
│  - onToolStart()│  → "正在读取文件..."
│  - onToolEnd()  │  → 工具完成
└─────────────────┘
```

### 7.3 实现计划

1. 创建 `ACPClient` 类，启动子进程
2. 实现 JSON-RPC 通信
3. 处理事件流
4. 集成到飞书消息流程

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| HTTP 响应解析失败 | 显示不完整 | 降级为显示原始内容 |
| 表情反应 API 失败 | 状态不可见 | 不影响主要功能，仅日志记录 |
| 工具信息过多 | 消息过长 | 限制显示数量，超过折叠 |

---

## 9. 参考资料

- [opencode-chat-bridge](https://github.com/ominiverdi/opencode-chat-bridge) - 多平台桥接实现
- [OpenCode Event System](https://zread.ai/opencode-ai/opencode/26-message-and-event-system) - 事件系统文档
- [飞书消息 API](https://open.feishu.cn/document/serverSdk/im sdk) - 消息发送接口

---

**最后更新**: 2026-02-24

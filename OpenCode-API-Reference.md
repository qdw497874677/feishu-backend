# OpenCode Server API 完整指南

## 📋 概述

OpenCode Server 是一个基于 HTTP 的无头服务器，通过 OpenAPI 3.1 规范暴露接口，支持程序化交互。

### 启动方式

```bash
opencode serve [--port <number>] [--hostname <string>] [--cors <origin>]
```

**默认配置：**
- 端口：`4096`
- 主机：`127.0.0.1`
- OpenAPI 规范：`http://localhost:4096/doc`

**认证：**
```bash
OPENCODE_SERVER_PASSWORD=your-password opencode serve
# 用户名默认为 'opencode'，可通过 OPENCODE_SERVER_USERNAME 覆盖
```

---

## 🗂️ API 分类

### 1. **Global** - 全局接口

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/global/health` | 服务器健康检查和版本 | `{ healthy: true, version: string }` |
| `GET` | `/global/event` | 全局事件流 (SSE) | Server-Sent Events 流 |

### 2. **Project** - 项目管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/project` | 列出所有项目 | `Project[]` |
| `GET` | `/project/current` | 获取当前项目 | `Project` |

**Project 类型：**
```typescript
type Project = {
  id: string
  worktree: string
  vcsDir?: string
  vcs?: "git"
  time: {
    created: number
    initialized?: number
  }
}
```

### 3. **Path & VCS** - 路径和版本控制

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/path` | 获取当前路径 | `Path` |
| `GET` | `/vcs` | 获取 VCS 信息 | `VcsInfo` |

### 4. **Instance** - 实例管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `POST` | `/instance/dispose` | 销毁当前实例 | `boolean` |

### 5. **Config** - 配置管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/config` | 获取配置信息 | `Config` |
| `PATCH` | `/config` | 更新配置 | `Config` |
| `GET` | `/config/providers` | 列出提供商和默认模型 | `{ providers: Provider[], default: {...} }` |

### 6. **Provider** - 提供商管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/provider` | 列出所有提供商 | `{ all: Provider[], default: {...}, connected: string[] }` |
| `GET` | `/provider/auth` | 获取提供商认证方法 | `{ [providerID: string]: ProviderAuthMethod[] }` |
| `POST` | `/provider/{id}/oauth/authorize` | OAuth 授权 | `ProviderAuthAuthorization` |
| `POST` | `/provider/{id}/oauth/callback` | OAuth 回调处理 | `boolean` |

### 7. **Sessions** - 会话管理 ⭐

#### 核心会话操作

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/session` | 列出所有会话 | `Session[]` |
| `POST` | `/session` | 创建新会话 | `Session` |
| `GET` | `/session/status` | 获取所有会话状态 | `{ [sessionID: string]: SessionStatus }` |
| `GET` | `/session/:id` | 获取会话详情 | `Session` |
| `DELETE` | `/session/:id` | 删除会话及其所有数据 | `boolean` |
| `PATCH` | `/session/:id` | 更新会话属性 | `Session` |

#### 会话高级操作

| 方法 | 路径 | 描述 |
|------|------|------|
| `GET` | `/session/:id/children` | 获取子会话 |
| `GET` | `/session/:id/todo` | 获取会话的待办列表 |
| `POST` | `/session/:id/init` | 分析应用并创建 AGENTS.md |
| `POST` | `/session/:id/fork` | 在某个消息处分叉会话 |
| `POST` | `/session/:id/abort` | 中止正在运行的会话 |
| `POST` | `/session/:id/share` | 分享会话 |
| `DELETE` | `/session/:id/share` | 取消分享 |
| `GET` | `/session/:id/diff` | 获取会话的 diff |
| `POST` | `/session/:id/summarize` | 总结会话 |
| `POST` | `/session/:id/revert` | 回退消息 |
| `POST` | `/session/:id/unrevert` | 恢复所有回退的消息 |
| `POST` | `/session/:id/permissions/:permissionID` | 响应权限请求 |

**Session 类型：**
```typescript
type Session = {
  id: string
  projectID: string
  directory: string
  parentID?: string
  summary?: {
    additions: number
    deletions: number
    files: number
    diffs?: Array<FileDiff>
  }
  share?: { url: string }
  title: string
  version: string
  time: {
    created: number
    updated: number
    compacting?: number
  }
  revert?: {
    messageID: string
    partID?: string
    snapshot?: string
    diff?: string
  }
}
```

**Todo 类型：**
```typescript
type Todo = {
  content: string           // 任务描述
  status: string            // pending, in_progress, completed, cancelled
  priority: string          // high, medium, low
  id: string               // 唯一标识符
}
```

### 8. **Messages** - 消息管理 ⭐

| 方法 | 路径 | 描述 |
|------|------|------|
| `GET` | `/session/:id/message` | 列出会话中的消息 (query: `limit?`) |
| `POST` | `/session/:id/message` | 发送消息并等待响应 |
| `GET` | `/session/:id/message/:messageID` | 获取消息详情 |
| `POST` | `/session/:id/prompt_async` | 异步发送消息 (不等待) |
| `POST` | `/session/:id/command` | 执行斜杠命令 |
| `POST` | `/session/:id/shell` | 运行 shell 命令 |

**Message 类型：**
```typescript
// 用户消息
type UserMessage = {
  id: string
  sessionID: string
  role: "user"
  time: { created: number }
  summary?: {
    title?: string
    body?: string
    diffs: Array<FileDiff>
  }
  agent: string
  model: {
    providerID: string
    modelID: string
  }
  system?: string
  tools?: { [key: string]: boolean }
}

// 助手消息
type AssistantMessage = {
  id: string
  sessionID: string
  role: "assistant"
  time: { created: number; completed?: number }
  error?: ProviderAuthError | UnknownError | ApiError
  parentID: string
  modelID: string
  providerID: string
  mode: string
  path: { cwd: string; root: string }
  summary?: boolean
  cost: number
  tokens: {
    input: number
    output: number
    reasoning: number
    cache: { read: number; write: number }
  }
  finish?: string
}

type Message = UserMessage | AssistantMessage
```

**Part 类型 (消息组成部分)：**
```typescript
type Part =
  | TextPart        // 文本内容
  | ReasoningPart   // 推理内容
  | FilePart        // 文件内容
  | ToolPart        // 工具调用
  | StepStartPart   // 步骤开始
  | StepFinishPart  // 步骤完成
  | SnapshotPart    // 快照
  | PatchPart       // 补丁
  | AgentPart       // 子代理
  | RetryPart       // 重试
  | CompactionPart  // 压缩
```

### 9. **Commands** - 命令管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/command` | 列出所有命令 | `Command[]` |

**Command 类型：**
```typescript
type Command = {
  name: string
  description?: string
  agent?: string
  model?: string
  template: string
  subtask?: boolean
}
```

### 10. **Files** - 文件操作

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/find?pattern=<pat>` | 在文件中搜索文本 | 匹配对象数组 |
| `GET` | `/find/file?query=<q>` | 按名称查找文件和目录 | `string[]` |
| `GET` | `/find/symbol?query=<q>` | 查找工作区符号 | `Symbol[]` |
| `GET` | `/file?path=<path>` | 列出文件和目录 | `FileNode[]` |
| `GET` | `/file/content?path=<p>` | 读取文件 | `FileContent` |
| `GET` | `/file/status` | 获取已跟踪文件的状态 | `File[]` |

**FileContent 类型：**
```typescript
type FileContent = {
  type: "text" | "binary"
  content: string
  diff?: string
  patch?: { ... }
  encoding?: "base64"
  mimeType?: string
}
```

### 11. **Tools (Experimental)** - 工具管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/experimental/tool/ids` | 列出所有工具 ID | `ToolIDs` |
| `GET` | `/experimental/tool?provider=<p>&model=<m>` | 列出模型的工具及其 JSON schemas | `ToolList` |

### 12. **LSP, Formatters & MCP** - 外部集成

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/lsp` | 获取 LSP 服务器状态 | `LSPStatus[]` |
| `GET` | `/formatter` | 获取格式化器状态 | `FormatterStatus[]` |
| `GET` | `/mcp` | 获取 MCP 服务器状态 | `{ [name: string]: MCPStatus }` |
| `POST` | `/mcp` | 动态添加 MCP 服务器 | MCP 状态对象 |

### 13. **Agents** - 代理管理

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/agent` | 列出所有可用代理 | `Agent[]` |

**Agent 类型：**
```typescript
type Agent = {
  name: string
  description?: string
  mode: "subagent" | "primary" | "all"
  builtIn: boolean
  topP?: number
  temperature?: number
  color?: string
  permission: {
    edit: "ask" | "allow" | "deny"
    bash: { [key: string]: "ask" | "allow" | "deny" }
    webfetch?: "ask" | "allow" | "deny"
    doom_loop?: "ask" | "allow" | "deny"
    external_directory?: "ask" | "allow" | "deny"
  }
  model?: { modelID: string; providerID: string }
  prompt?: string
  tools: { [key: string]: boolean }
  options: { [key: string]: unknown }
  maxSteps?: number
}
```

### 14. **Logging** - 日志

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `POST` | `/log` | 写入日志条目 | `boolean` |

**请求体：**
```typescript
{
  service: string
  level: string
  message: string
  extra?: { [key: string]: unknown }
}
```

### 15. **TUI** - 终端用户界面控制

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `POST` | `/tui/append-prompt` | 向提示追加文本 | `boolean` |
| `POST` | `/tui/open-help` | 打开帮助对话框 | `boolean` |
| `POST` | `/tui/open-sessions` | 打开会话选择器 | `boolean` |
| `POST` | `/tui/open-themes` | 打开主题选择器 | `boolean` |
| `POST` | `/tui/open-models` | 打开模型选择器 | `boolean` |
| `POST` | `/tui/submit-prompt` | 提交当前提示 | `boolean` |
| `POST` | `/tui/clear-prompt` | 清除提示 | `boolean` |
| `POST` | `/tui/execute-command` | 执行命令 | `boolean` |
| `POST` | `/tui/show-toast` | 显示提示消息 | `boolean` |
| `GET` | `/tui/control/next` | 等待下一个控制请求 | Control request object |
| `POST` | `/tui/control/response` | 响应控制请求 | `boolean` |

### 16. **Auth** - 认证

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `PUT` | `/auth/:id` | 设置认证凭据 | `boolean` |

### 17. **Events** - 事件流

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/event` | Server-Sent 事件流 | SSE 流 |

**事件类型：**
```typescript
type Event =
  | EventServerConnected          // 服务器已连接
  | EventMessageUpdated           // 消息已更新
  | EventMessageRemoved           // 消息已移除
  | EventMessagePartUpdated       // 消息部分已更新
  | EventSessionCreated           // 会话已创建
  | EventSessionUpdated           // 会话已更新
  | EventSessionDeleted           // 会话已删除
  | EventSessionStatus            // 会话状态
  | EventPermissionUpdated        // 权限已更新
  | EventTodoUpdated              // 待办已更新
  | EventFileEdited               // 文件已编辑
  | EventCommandExecuted          // 命令已执行
  // ... 更多事件类型
```

### 18. **Docs** - 文档

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/doc` | OpenAPI 3.1 规范 | HTML 页面 |

---

## 🔌 Pty (伪终端) API

| 方法 | 路径 | 描述 | 响应 |
|------|------|------|------|
| `GET` | `/pty` | 列出所有 Pty 会话 | `Pty[]` |
| `POST` | `/pty` | 创建新的 Pty 会话 | `Pty` |
| `DELETE` | `/pty/{id}` | 删除 Pty 会话 | `boolean` |
| `GET` | `/pty/{id}` | 获取 Pty 会话详情 | `Pty` |
| `PATCH` | `/pty/{id}` | 更新 Pty 会话 | `Pty` |
| `POST` | `/pty/{id}/connect` | 连接到 Pty 会话 | `boolean` |

**Pty 类型：**
```typescript
type Pty = {
  id: string
  title: string
  command: string
  args: Array<string>
  cwd: string
  status: "running" | "exited"
  pid: number
}
```

---

## 📊 核心数据结构

### Config - 配置结构

```typescript
type Config = {
  $schema?: string
  theme?: string
  keybinds?: KeybindsConfig
  logLevel?: "DEBUG" | "INFO" | "WARN" | "ERROR"
  tui?: {
    scroll_speed?: number
    scroll_acceleration?: { enabled: boolean }
    diff_style?: "auto" | "stacked"
  }
  command?: { [key: string]: Command }
  watcher?: { ignore?: Array<string> }
  plugin?: Array<string>
  snapshot?: boolean
  share?: "manual" | "auto" | "disabled"
  autoupdate?: boolean | "notify"
  disabled_providers?: Array<string>
  enabled_providers?: Array<string>
  model?: string
  small_model?: string
  username?: string
  agent?: { [key: string]: AgentConfig }
  provider?: { [key: string]: ProviderConfig }
  mcp?: { [key: string]: McpLocalConfig | McpRemoteConfig }
  formatter?: false | { [key: string]: FormatterConfig }
  lsp?: false | { [key: string]: LSPConfig }
  instructions?: Array<string>
  layout?: LayoutConfig
  permission?: PermissionConfig
  tools?: { [key: string]: boolean }
  enterprise?: { url?: string }
  experimental?: ExperimentalConfig
}
```

### Model - 模型结构

```typescript
type Model = {
  id: string
  providerID: string
  api: { id: string; url: string; npm: string }
  name: string
  capabilities: {
    temperature: boolean
    reasoning: boolean
    attachment: boolean
    toolcall: boolean
    input: { text: boolean; audio: boolean; image: boolean; video: boolean; pdf: boolean }
    output: { text: boolean; audio: boolean; image: boolean; video: boolean; pdf: boolean }
  }
  cost: {
    input: number
    output: number
    cache: { read: number; write: number }
  }
  limit: { context: number; output: number }
  status: "alpha" | "beta" | "deprecated" | "active"
  options: { [key: string]: unknown }
  headers: { [key: string]: string }
}
```

---

## 🚀 使用示例

### 1. 创建新会话并发送消息

```bash
# 创建会话
curl -X POST http://localhost:4096/session \
  -H "Content-Type: application/json" \
  -d '{"title": "My Session"}'

# 发送消息
curl -X POST http://localhost:4096/session/{sessionId}/message \
  -H "Content-Type: application/json" \
  -d '{
    "parts": [
      { "type": "text", "text": "Hello, OpenCode!" }
    ]
  }'
```

### 2. 获取会话的待办列表

```bash
curl http://localhost:4096/session/{sessionId}/todo
```

### 3. 列出所有可用代理

```bash
curl http://localhost:4096/agent
```

### 4. 执行斜杠命令

```bash
curl -X POST http://localhost:4096/session/{sessionId}/command \
  -H "Content-Type: application/json" \
  -d '{
    "command": "/help",
    "arguments": ""
  }'
```

### 5. 运行 shell 命令

```bash
curl -X POST http://localhost:4096/session/{sessionId}/shell \
  -H "Content-Type: application/json" \
  -d '{
    "agent": "build",
    "command": "npm test"
  }'
```

### 6. 监听事件流

```bash
curl -N http://localhost:4096/event
```

---

## 🔐 认证

如果设置了密码，需要使用 HTTP Basic Auth:

```bash
curl -u opencode:your-password http://localhost:4096/project/current
```

---

## 📚 相关资源

- **SDK 文档**：https://opencode.ai/docs/sdk/
- **完整 API 规范**：http://localhost:4096/doc (运行服务后访问)
- **GitHub 仓库**：https://github.com/anomalyco/opencode
- **OpenCode 官网**：https://opencode.ai

---

**生成时间**：2026-02-01
**文档版本**：基于 OpenCode Server API 最新规范

# OpenCode 服务端 API 参考文档

> **项目**: 飞书机器人后端 - OpenCode 集成  
> **架构**: HTTP API 模式  
> **服务端端口**: 4098  
> **基础 URL**: `http://localhost:4098`

---

## 📋 目录

1. [认证方式](#认证方式)
2. [全局端点](#全局端点)
3. [项目管理](#项目管理)
4. [会话管理](#会话管理)
5. [消息操作](#消息操作)
6. [命令列表](#命令列表)
7. [错误处理](#错误处理)
8. [实际应用示例](#实际应用示例)

---

## 🔐 认证方式

### HTTP 基本认证（可选）

如果设置了 `OPENCODE_SERVER_PASSWORD` 环境变量，所有请求需要包含认证头：

```http
Authorization: Basic <base64(username:password)>
```

**默认配置**：
- 用户名: `opencode`
- 密码: 通过环境变量 `OPENCODE_SERVER_PASSWORD` 设置

**Java 示例**：
```java
String auth = username + ":" + password;
String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
String header = "Basic " + encodedAuth;
```

---

## 🌐 全局端点

### 1. 健康检查

**端点**: `GET /global/health`

**描述**: 获取服务器健康状态和版本信息

**请求**:
```http
GET /global/health
Authorization: Basic <credentials>
```

**响应**:
```json
{
  "healthy": true,
  "version": "1.1.48"
}
```

**字段说明**:
- `healthy` (boolean): 服务器是否健康
- `version` (string): OpenCode 版本号

**Java 实现**:
```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(serverUrl + "/global/health"))
    .header("Authorization", authHeader)
    .GET()
    .build();
```

---

## 📁 项目管理

### 1. 列出所有项目

**端点**: `GET /project`

**描述**: 获取所有 OpenCode 项目列表

**请求**:
```http
GET /project
Authorization: Basic <credentials>
```

**响应**:
```json
[
  {
    "id": "1203042a781d466a828694e53102ef819b9c7ed3",
    "worktree": "/root/workspace/feishu-backend",
    "vcs": "git",
    "sandboxes": [],
    "time": {
      "created": 1769345676520,
      "updated": 1769960756457
    },
    "icon": {
      "color": "lime"
    }
  },
  {
    "id": "global",
    "worktree": "/",
    "sandboxes": [],
    "time": {
      "created": 1768139917551,
      "updated": 1769959415898,
      "initialized": 1769939970088
    },
    "icon": {
      "color": "purple"
    }
  }
]
```

**字段说明**:
- `id` (string): 项目唯一标识
- `worktree` (string): 项目工作目录路径
- `vcs` (string): 版本控制系统类型（如 "git"）
- `sandboxes` (array): 沙箱配置
- `time.created` (number): 创建时间戳（毫秒）
- `time.updated` (number): 更新时间戳（毫秒）
- `time.initialized` (number, 可选): 初始化时间戳
- `icon.color` (string): 项目图标颜色

**Java 实现**:
```java
public String listProjects() {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl + "/project"))
        .header("Authorization", authHeader)
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        return formatProjectList(response.body());
    }
    return "❌ 获取项目列表失败";
}
```

**格式化输出示例**:
```
📁 OpenCode 项目列表:

1. **feishu-backend**
   路径: /root/workspace/feishu-backend
   VCS: GIT

2. **happy**
   路径: /workspace/projects/happy
   VCS: GIT
```

---

## 💬 会话管理

### 1. 创建新会话

**端点**: `POST /session`

**描述**: 创建一个新的 OpenCode 会话

**请求**:
```http
POST /session
Content-Type: application/json
Authorization: Basic <credentials>

{
  "parentID": "ses_parent_session_id",
  "title": "会话标题"
}
```

**参数说明**:
- `parentID` (string, 可选): 父会话 ID，用于创建子会话
- `title` (string, 可选): 会话标题

**响应**:
```json
{
  "id": "ses_3e6122956ffetcuG3KKYRh3QcW",
  "parentID": "ses_parent_id",
  "title": "会话标题",
  "messageCount": 0
}
```

**字段说明**:
- `id` (string): 新创建的会话 ID
- `parentID` (string, 可选): 父会话 ID
- `title` (string): 会话标题
- `messageCount` (number): 当前消息数量

**Java 实现**:
```java
private String createSession(String parentID) {
    String body = parentID != null
        ? String.format("{\"parentID\":\"%s\"}", parentID)
        : "{}";

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl + "/session"))
        .header("Content-Type", "application/json")
        .header("Authorization", authHeader)
        .POST(BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200 || response.statusCode() == 201) {
        JsonNode json = objectMapper.readTree(response.body());
        return json.get("id").asText();
    }
    return null;
}
```

---

### 2. 列出所有会话

**端点**: `GET /session`

**描述**: 获取所有 OpenCode 会话列表

**请求**:
```http
GET /session
Authorization: Basic <credentials>
```

**响应**:
```json
[
  {
    "id": "ses_3e6122956ffetcuG3KKYRh3QcW",
    "title": "重构 TimeApp",
    "messageCount": 15,
    "created": 1769345676520,
    "updated": 1769960756457
  }
]
```

**字段说明**:
- `id` (string): 会话 ID
- `title` (string): 会话标题
- `messageCount` (number): 消息数量
- `created` (number): 创建时间戳
- `updated` (number): 更新时间戳

**Java 实现**:
```java
public String listSessions() {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl + "/session"))
        .header("Authorization", authHeader)
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        return formatSessionList(response.body());
    }
    return "❌ 获取会话列表失败";
}
```

**格式化输出示例**:
```
📋 OpenCode 会话列表:

1. 重构 TimeApp
   ID: ses_3e6122956ffetcuG3KKYRh3QcW

2. 无标题
   ID: ses_3e5245d30ffe3blD6te6SH2Fsc
```

---

### 3. 获取会话详情

**端点**: `GET /session/:id`

**描述**: 获取指定会话的详细信息

**请求**:
```http
GET /session/ses_3e6122956ffetcuG3KKYRh3QcW
Authorization: Basic <credentials>
```

**URL 参数**:
- `id` (string): 会话 ID

**响应**:
```json
{
  "id": "ses_3e6122956ffetcuG3KKYRh3QcW",
  "title": "重构 TimeApp",
  "messageCount": 15,
  "created": 1769345676520,
  "updated": 1769960756457
}
```

---

### 4. 更新会话属性

**端点**: `PATCH /session/:id`

**描述**: 更新会话的属性（如标题）

**请求**:
```http
PATCH /session/ses_3e6122956ffetcuG3KKYRh3QcW
Content-Type: application/json
Authorization: Basic <credentials>

{
  "title": "新标题"
}
```

**参数说明**:
- `title` (string, 可选): 新的会话标题

**响应**: 返回更新后的会话对象

---

### 5. 删除会话

**端点**: `DELETE /session/:id`

**描述**: 删除指定会话及其所有数据

**请求**:
```http
DELETE /session/ses_3e6122956ffetcuG3KKYRh3QcW
Authorization: Basic <credentials>
```

**响应**:
```json
true
```

---

### 6. Fork 会话

**端点**: `POST /session/:id/fork`

**描述**: 从指定消息处创建会话分支

**请求**:
```http
POST /session/ses_3e6122956ffetcuG3KKYRh3QcW/fork
Content-Type: application/json
Authorization: Basic <credentials>

{
  "messageID": "msg_123"
}
```

**参数说明**:
- `messageID` (string, 可选): 分支点的消息 ID

**响应**: 返回新创建的 fork 会话对象

---

## ✉️ 消息操作

### 1. 发送消息（同步）

**端点**: `POST /session/:id/message`

**描述**: 向会话发送消息并等待响应（同步模式）

**请求**:
```http
POST /session/ses_3e6122956ffetcuG3KKYRh3QcW/message
Content-Type: application/json
Authorization: Basic <credentials>

{
  "parts": [
    {
      "type": "text",
      "text": "你好，请帮我重构这个函数"
    }
  ]
}
```

**请求体参数**:
- `parts` (array): 消息部分数组
  - `type` (string): 类型，支持 "text", "tool_use" 等
  - `text` (string): **注意：直接是字符串，不是对象**

**响应**:
```json
{
  "info": {
    "id": "msg_abc123",
    "role": "user",
    "timestamp": 1769345676520
  },
  "parts": [
    {
      "type": "text",
      "text": "你好！我可以帮你重构函数。请提供函数代码和需求。"
    }
  ]
}
```

**响应体参数**:
- `info.id` (string): 消息 ID
- `info.role` (string): 角色（user/assistant）
- `info.timestamp` (number): 时间戳
- `parts` (array): 响应部分数组
  - `type` (string): 类型
  - `text` (string): 文本内容（如果类型是 text）
  - `toolUse` (object): 工具使用信息（如果类型是 tool_use）

**Java 实现**:
```java
private String sendMessageSync(String sessionId, String prompt, int timeoutSeconds) {
    String body = String.format(
        "{\"parts\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
        escapeJson(prompt)
    );

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl + "/session/" + sessionId + "/message"))
        .header("Content-Type", "application/json")
        .header("Authorization", authHeader)
        .timeout(Duration.ofSeconds(timeoutSeconds))
        .POST(BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        return parseMessageResponse(response.body());
    }
    return "❌ 发送消息失败";
}
```

**关键点**:
- ✅ `"text":"内容"` （正确）
- ❌ `"text":{"content":"内容"}` （错误，会返回 400）

---

### 2. 发送消息（异步）

**端点**: `POST /session/:id/prompt_async`

**描述**: 向会话发送消息，不等待响应（异步模式）

**请求**:
```http
POST /session/ses_3e6122956ffetcuG3KKYRh3QcW/prompt_async
Content-Type: application/json
Authorization: Basic <credentials>

{
  "parts": [
    {
      "type": "text",
      "text": "执行耗时任务"
    }
  ]
}
```

**响应**:
- 状态码: `204 No Content`
- 无响应体

**Java 实现**:
```java
public void sendMessageAsync(String sessionId, String prompt) {
    String body = String.format(
        "{\"parts\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
        escapeJson(prompt)
    );

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl + "/session/" + sessionId + "/prompt_async"))
        .header("Content-Type", "application/json")
        .header("Authorization", authHeader)
        .POST(BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 204) {
        log.info("异步消息发送成功: sessionId={}", sessionId);
    }
}
```

---

### 3. 获取会话消息列表

**端点**: `GET /session/:id/message`

**描述**: 获取会话中的所有消息

**请求**:
```http
GET /session/ses_3e6122956ffetcuG3KKYRh3QcW/message?limit=20
Authorization: Basic <credentials>
```

**查询参数**:
- `limit` (number, 可选): 限制返回的消息数量

**响应**:
```json
[
  {
    "info": {
      "id": "msg_001",
      "role": "user",
      "timestamp": 1769345676520
    },
    "parts": [
      {
        "type": "text",
        "text": "你好"
      }
    ]
  },
  {
    "info": {
      "id": "msg_002",
      "role": "assistant",
      "timestamp": 1769345678000
    },
    "parts": [
      {
        "type": "text",
        "text": "你好！有什么可以帮助你的？"
      }
    ]
  }
]
```

---

### 4. 获取单条消息详情

**端点**: `GET /session/:id/message/:messageID`

**描述**: 获取指定消息的详细信息

**请求**:
```http
GET /session/ses_3e6122956ffetcuG3KKYRh3QcW/message/msg_001
Authorization: Basic <credentials>
```

**URL 参数**:
- `id` (string): 会话 ID
- `messageID` (string): 消息 ID

**响应**: 返回消息对象（与获取消息列表中的单个消息格式相同）

---

## ⚡️ 命令列表

### 1. 获取所有斜杠命令

**端点**: `GET /command`

**描述**: 获取 OpenCode 中所有可用的斜杠命令

**请求**:
```http
GET /command
Authorization: Basic <credentials>
```

**响应**:
```json
[
  {
    "id": "cmd_help",
    "name": "/help",
    "description": "显示帮助信息",
    "enabled": true
  },
  {
    "id": "cmd_new",
    "name": "/new",
    "description": "创建新会话",
    "enabled": true
  },
  {
    "id": "cmd_clear",
    "name": "/clear",
    "description": "清空上下文",
    "enabled": true
  }
]
```

**字段说明**:
- `id` (string): 命令唯一标识
- `name` (string): 命令名称（如 /help）
- `description` (string): 命令描述
- `enabled` (boolean): 命令是否启用

**Java 实现**:
```java
public String listCommands() {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl + "/command"))
        .header("Authorization", authHeader)
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        return formatCommandList(response.body());
    }
    return "❌ 获取命令列表失败";
}
```

**格式化输出示例**:
```
⚡️ OpenCode 斜杠命令:

**✅** `/help` - 显示帮助信息

**✅** `/new` - 创建新会话

**✅** `/clear` - 清空上下文
```

---

## ❌ 错误处理

### HTTP 状态码

| 状态码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 成功 | 正常处理响应 |
| 201 | 创建成功 | 资源创建成功 |
| 204 | 无内容 | 异步操作已接受 |
| 400 | 请求错误 | 检查请求体格式（特别是 `text` 字段） |
| 401 | 未授权 | 检查认证凭据 |
| 404 | 未找到 | 资源不存在 |
| 500 | 服务器错误 | 联系 OpenCode 支持 |

### 常见错误示例

**1. JSON 格式错误**
```json
{
  "success": false,
  "error": [
    {
      "code": "invalid_type",
      "message": "Invalid input: expected string, received object",
      "path": ["parts", 0, "text"]
    }
  ]
}
```

**原因**: 使用了错误的 `text` 字段格式
```json
// ❌ 错误
{"parts":[{"type":"text","text":{"content":"你好"}}]}

// ✅ 正确
{"parts":[{"type":"text","text":"你好"}]}
```

**2. 超时错误**
```java
java.net.http.HttpTimeoutException: request timed out
```

**处理方案**:
- 同步请求超过超时时间时，捕获异常并返回 `null`
- 转为异步执行，使用 `/prompt_async` 端点
- 向用户发送"任务正在执行中，结果将稍后返回..."提示

**3. 连接错误**
```java
java.net.ConnectException: Connection refused
```

**处理方案**:
- 检查 OpenCode 服务端是否运行
- 验证服务端 URL 和端口配置
- 使用重试机制（指数退避）

---

## 🚀 实际应用示例

### 场景 1: 创建新会话并发送消息

```java
// 1. 创建会话
String sessionId = createSession(null);
if (sessionId == null) {
    return "❌ 创建会话失败";
}

// 2. 发送消息（同步，5秒超时）
String response = sendMessageSync(sessionId, "你好", 5);

// 3. 处理响应
if (response == null) {
    // 超时，转为异步执行
    feishuGateway.sendMessage(message, "⏳ 任务正在执行中...", topicId);
    sendMessageAsync(sessionId, "你好");
} else {
    // 直接返回结果
    return response;
}
```

### 场景 2: 查看项目列表

```java
public String listProjects() {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:4098/project"))
        .header("Authorization", authHeader)
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    JsonNode projects = objectMapper.readTree(response.body());
    StringBuilder sb = new StringBuilder("📁 项目列表:\n\n");

    for (JsonNode project : projects) {
        String name = extractProjectName(project.get("worktree").asText());
        String path = project.get("worktree").asText();
        String vcs = project.get("vcs").asText();

        sb.append(String.format("**%s**\n路径: %s\nVCS: %s\n\n",
            name, path, vcs.toUpperCase()));
    }

    return sb.toString();
}
```

### 场景 3: 多轮对话

```java
// 第一轮：发送消息
String sessionId = createSession(null);
String response1 = sendMessageSync(sessionId, "帮我写一个排序函数", 30);

// 第二轮：继续对话（使用同一个 sessionId）
String response2 = sendMessageSync(sessionId, "添加注释", 30);

// 第三轮：继续对话
String response3 = sendMessageSync(sessionId, "优化性能", 30);
```

---

## 📚 相关文档

- [OpenCode 官方文档](https://opencode.ai/docs/server)
- [OpenAPI 规范](http://localhost:4098/doc)
- [飞书机器人后端项目](../README.md)
- [应用开发规范](../APP_GUIDE.md)

---

## 🔧 配置参考

### application.yml

```yaml
opencode:
  # 服务端 URL
  server-url: http://localhost:4098
  
  # HTTP 基本认证
  username: opencode
  password: ${OPencode_SERVER_PASSWORD:}
  
  # 超时配置
  connect-timeout: 10  # 连接超时（秒）
  request-timeout: 120  # 请求超时（秒）
  
  # 输出限制
  max-output-length: 5000  # 最大输出长度（字符）
  
  # 异步执行
  async-enabled: true
```

### 环境变量

```bash
# OpenCode 服务端密码（可选）
export OPencode_SERVER_PASSWORD="your-password"

# 服务端端口（默认 4098）
export OPencode_SERVER_PORT="4098"
```

---

**最后更新**: 2026-02-01  
**文档版本**: 1.0  
**OpenCode 版本**: 1.1.48

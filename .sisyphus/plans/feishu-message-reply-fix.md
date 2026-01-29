# 诊断和修复飞书机器人消息回复失败问题

## Context

### Original Request
用户反馈：在飞书话题中发送消息后，机器人没有回复，需要排查问题。

### Log Analysis Summary

**关键日志时间线：**

1. **14:35:31.749** - 接收到消息 "你好"
   - `ChatType: p2p`（私聊）
   - 事件ID: `9a10c582edb6706c4f78b37561856e7b`

2. **14:35:31.756** - HelpApp 执行完成，准备发送回复
   - 使用 `sendDirectReply` 模式
   - 日志：`Sending direct reply to chatId: oc_aebdfe4fb0fe1b6efb44ffc1f9f8867e`

3. **14:35:41.787** - 发送失败（耗时约 10 秒）
   - `Exception sending direct reply`
   - `java.net.UnknownHostException: open.feishu.cn` - **DNS 解析失败**

4. **14:35:50.268** - 同一条消息被重复接收（相同 event_id）
   - 再次尝试发送，同样失败

5. **14:37:31.447** - 第三次收到 "你好"
   - **14:37:32.159** - 发送成功！`发送回复成功: topicId=null`
   - 说明 DNS 解析偶尔成功，属于网络不稳定问题

6. **14:37:35.556** - 收到 `/time` 命令
   - 使用话题模式回复：`创建新话题`
   - 日志：`Thread created success: messageId=om_x100b58834432f0a8c2e3fe2601838b1, threadId=omt_1a1ef867cd8f5be1`

7. **14:37:41.517** - 收到话题内的消息 "你好"（有 thread_id）
   - 尝试回复到现有话题
   - **14:37:41.612** - 发送失败：`Exception sending message: Failed to send thread reply: HTTP 400`
   - **HTTP 400 错误表示 API 调用参数或端点错误**

### Identified Issues

**问题 1：间歇性 DNS 解析失败**（影响所有消息发送）
- 症状：`UnknownHostException: open.feishu.cn`
- 频率：约 2/3 的请求失败
- 根本原因：网络环境 DNS 不稳定或 DNS 服务器配置问题

**问题 2：话题回复 API 调用错误**（影响话题内消息）
- 症状：`Failed to send thread reply: HTTP 400`
- 触发条件：消息包含 `thread_id` 时
- 根本原因：API 端点或请求参数不正确

**问题 3：重复消息处理**
- 症状：同一条消息（相同 event_id）被重复处理 3 次
- 影响：浪费资源，增加网络压力

### Research Findings

**代码路径分析：**
- `FeishuGatewayImpl.sendDirectReply()` (L97-127)
  - 使用 `openId` 作为接收者
  - 调用 `httpClient.im().message().create(req)`
  - **失败位置**：DNS 解析 `open.feishu.cn`

- `FeishuGatewayImpl.createThreadByReply()` (L129-162)
  - 调用 `httpClient.post("/open-apis/im/v1/messages/" + messageId + "/reply", ...)`
  - **失败位置**：HTTP 400

- `FeishuGatewayImpl.sendReplyToTopic()` (L164-180)
  - 先调用 `listMessages()` 查找话题根消息
  - 然后调用 `sendReplyToMessage()` 回复

- `FeishuGatewayImpl.sendReplyToMessage()` (L182-204)
  - 使用 `ReplyMessageReq` SDK 方法
  - 调用 `httpClient.im().message().reply(req)`
  - **可能失败位置**：话题回复场景

**飞书 API 端点分析：**
根据飞书 IM SDK 文档：
- **创建消息**：`POST /open-apis/im/v1/messages` - ✅ 正确
- **回复消息**：`POST /open-apis/im/v1/messages/{message_id}/reply` - ⚠️ **需要验证**
- **在话题内回复**：应该使用 `reply_type` 参数指定回复模式

---

## Work Objectives

### Core Objective
诊断并修复飞书机器人消息回复失败问题，确保消息能够稳定发送。

### Concrete Deliverables

1. **修复 DNS 解析问题**
   - 添加 DNS 解析重试机制
   - 配置备用 DNS 服务器
   - 添加网络连通性检测

2. **修复话题回复 API 错误**
   - 修正 API 端点或请求参数
   - 验证飞书 API 文档的正确使用方式
   - 添加详细的错误日志

3. **优化消息去重**
   - 基于 event_id 的消息去重机制
   - 防止重复处理相同消息

4. **增强错误处理**
   - 添加指数退避重试策略
   - 记录详细的错误上下文
   - 提供友好的错误提示

### Definition of Done

- [ ] DNS 解析成功率 > 95%（通过 20 次测试验证）
- [ ] 话题内消息能够正常回复（测试 5 条消息）
- [ ] 重复消息被正确过滤（测试相同 event_id 的重复消息）
- [ ] 所有错误都有详细的日志记录
- [ ] 用户能够看到清晰的错误提示（当发送失败时）

### Must Have

- ✅ 不破坏现有的长连接接收机制
- ✅ 保持 COLA 架构规范（domain 定义接口，infrastructure 实现）
- ✅ 不引入新的外部依赖（使用现有的飞书 SDK）

### Must NOT Have (Guardrails)

- ❌ 不得使用 WebHook 模式
- ❌ 不得修改 `MessageListenerGateway` 接口定义
- ❌ 不得硬编码 DNS 服务器地址
- ❌ 不得绕过飞书 SDK 直接调用 API（除非 SDK 不支持）

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: NO（未找到测试框架配置）
- **User wants tests**: **YES (Manual QA)** - 用户需要手动验证功能
- **Framework**: 无（使用手动验证）

### Manual QA Strategy

由于项目没有测试基础设施，采用手动验证方式：

**场景 1：私聊消息发送（不创建话题）**
- [ ] 向机器人发送任意文本（如"你好"）
- [ ] 验证：机器人回复帮助信息
- [ ] 验证：日志显示 `发送回复成功: topicId=null`
- [ ] 验证：没有 DNS 解析错误或 UnknownHostException

**场景 2：命令触发创建话题**
- [ ] 向机器人发送 `/time` 命令
- [ ] 验证：机器人回复当前时间并创建话题
- [ ] 验证：日志显示 `Thread created success: threadId=omt_xxxx`
- [ ] 在飞书客户端中验证：话题已创建（消息下方的"回复"按钮可用）

**场景 3：话题内消息回复**
- [ ] 在已创建的话题中发送消息（如"你好"）
- [ ] 验证：机器人在话题内回复
- [ ] 验证：日志显示 `Reply success: messageId=xxxx, threadId=xxxx`
- [ ] 验证：没有 HTTP 400 错误

**场景 4：网络不稳定场景测试**
- [ ] 重启应用（模拟网络初始化）
- [ ] 连续发送 20 条测试消息
- [ ] 验证：至少 19 条成功发送（成功率 ≥ 95%）
- [ ] 验证：失败的 1 条有详细错误日志

**场景 5：重复消息去重测试**
- [ ] 发送一条消息
- [ ] 等待 5 秒
- [ ] 检查日志
- [ ] 验证：该消息只被处理一次（只有一次 `BotMessageService.handleMessage 开始`）

**证据收集要求：**
- 每个验证步骤都需要截图或复制相关日志
- 网络测试需要记录成功/失败次数
- 最终需要提供一份测试报告

---

## Task Flow

```
Task 1 (Research) → Task 2 (Fix DNS) → Task 3 (Fix Thread Reply)
     ↓                      ↓                      ↓
Task 4 (Dedup) → Task 5 (Error Handling) → Task 6 (Manual QA)
```

## Parallelization

| Group | Tasks | Reason |
|-------|-------|--------|
| A | 1 | 独立研究任务 |

| Task | Depends On | Reason |
|------|------------|--------|
| 2 | 1 | 需要研究结果 |
| 3 | 1 | 需要研究结果 |
| 4 | 1 | 需要研究结果 |
| 5 | 2, 3, 4 | 需要前面的修复 |
| 6 | 5 | 所有修复完成后测试 |

---

## TODOs

### Task 1: 研究飞书 API 和 SDK 文档

**What to do**:
- 查找飞书 IM SDK 官方文档
- 确认消息回复 API 的正确端点和参数
- 确认话题回复的最佳实践
- 研究 DNS 解析和重试策略

**Must NOT do**:
- 不得修改任何代码
- 不得编写测试用例

**Parallelizable**: NO

**References**:

**External References** (libraries and frameworks):
- 飞书 IM SDK 文档: `https://open.feishu.cn/document/serverSdk/im-sdk/message-v1/create-message`
- 飞书消息回复 API: `https://open.feishu.cn/document/serverSdk/im-sdk/message-v1/reply-message`
- 飞书话题 API: `https://open.feishu.cn/document/serverSdk/im-sdk/message-v1/thread`
- 飞书 Java SDK GitHub: `https://github.com/larksuite/oapi-sdk-java`

**WHY Each Reference Matters**:
- 这些官方文档将帮助我们确定正确的 API 调用方式
- 特别需要确认话题回复时是否需要特殊参数（如 `reply_type`）
- DNS 重试策略可以参考 Java 网络编程最佳实践

**Acceptance Criteria**:

**Manual Execution Verification**:
- [ ] 创建研究报告文件：`.sisyphus/research/feishu-api-research.md`
- [ ] 文档包含：
  - 消息创建 API 的端点和参数说明
  - 消息回复 API 的端点和参数说明
  - 话题回复的正确调用方式
  - DNS 解析重试的最佳实践
- [ ] 记录当前代码中可能错误的 API 调用

**Evidence Required**:
- [ ] 研究文档内容截图
- [ ] 当前代码错误点的标注

**Commit**: NO

---

### Task 2: 修复 DNS 解析问题（添加重试机制）

**What to do**:
- 在 `FeishuGatewayImpl` 中添加重试机制
- 使用指数退避策略（1s, 2s, 4s, 8s）
- 最大重试次数：3 次
- 捕获 `UnknownHostException` 并触发重试

**Must NOT do**:
- 不得硬编码 DNS 服务器地址
- 不得修改 `MessageListenerGateway` 接口

**Parallelizable**: NO (depends on 1)

**References** (CRITICAL - Be Exhaustive):

**Pattern References** (existing code to follow):
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java:35-42` - Client 初始化模式
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java:71-74` - 异常处理模式

**WHY Each Reference Matters**:
- 需要在 Client 初始化时配置重试参数
- 需要参考现有的异常处理模式，确保一致性

**Acceptance Criteria**:

**If TDD (tests enabled):**
(不适用，无测试基础设施)

**Manual Execution Verification**:

*For DNS Retry Verification:*
- [ ] 修改文件：`FeishuGatewayImpl.java`
  - 在构造函数中添加重试配置
  - 在 `sendDirectReply()` 方法中添加重试逻辑
  - 在 `sendMessage()` 方法中添加重试逻辑
- [ ] 重启应用：`cd feishu-bot-start && LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- [ ] 等待应用启动完成（查看日志：`Feishu Bot Backend Started!`）
- [ ] 连续发送 20 条测试消息
- [ ] 统计成功次数：`grep "发送回复成功" /tmp/feishu-run.log | wc -l`
- [ ] 验证：成功次数 ≥ 19（成功率 ≥ 95%）
- [ ] 验证：日志中有重试记录（如 `Retry 1/3 for DNS resolution`）

**Evidence Required**:
- [ ] 修改前后的代码 diff
- [ ] 测试消息发送的终端输出（复制到文档）
- [ ] 成功率统计结果截图

**Commit**: YES
- Message: `fix(infrastructure): add DNS resolution retry with exponential backoff`
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java`
- Pre-commit: `mvn clean compile -pl :feishu-bot-infrastructure`（验证编译）

---

### Task 3: 修复话题回复 API 调用错误

**What to do**:
- 根据研究结果，修正 `sendReplyToTopic()` 和 `sendReplyToMessage()` 的 API 调用
- 如果 SDK 方法不正确，改为使用正确的 API 端点
- 添加详细的错误日志（记录请求和响应）
- 验证话题回复的正确参数（如 `reply_type`）

**Must NOT do**:
- 不得绕过飞书 SDK 直接调用 API（除非 SDK 不支持）
- 不得使用 WebHook 模式

**Parallelizable**: NO (depends on 1)

**References** (CRITICAL - Be Exhaustive):

**Pattern References** (existing code to follow):
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java:182-204` - 当前 `sendReplyToMessage()` 实现
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java:164-180` - 当前 `sendReplyToTopic()` 实现

**WHY Each Reference Matters**:
- 需要分析当前实现的问题所在
- 需要修正 API 调用参数或端点

**Acceptance Criteria**:

**Manual Execution Verification**:

*For Thread Reply Verification:*
- [ ] 修改文件：`FeishuGatewayImpl.java`
  - 修正 `sendReplyToMessage()` 方法
  - 修正 `sendReplyToTopic()` 方法
  - 添加详细的日志记录（记录请求和响应）
- [ ] 重启应用
- [ ] 发送 `/time` 命令（触发话题创建）
- [ ] 验证：机器人成功回复并创建话题
- [ ] 在话题中发送消息（如"你好"）
- [ ] 验证：机器人在话题内成功回复
- [ ] 检查日志：`grep -A 5 "Replying to thread" /tmp/feishu-run.log`
- [ ] 验证：日志显示 `Reply success: messageId=xxxx, threadId=xxxx`
- [ ] 验证：没有 HTTP 400 错误

**Evidence Required**:
- [ ] 修改前后的代码 diff
- [ ] 飞书客户端中话题回复的截图
- [ ] 日志输出（包含成功回复的记录）

**Commit**: YES
- Message: `fix(infrastructure): fix thread reply API call parameters`
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java`
- Pre-commit: `mvn clean compile -pl :feishu-bot-infrastructure`（验证编译）

---

### Task 4: 实现消息去重机制

**What to do**:
- 基于 event_id 实现消息去重
- 使用内存缓存（如 `ConcurrentHashMap<String, Instant>`）
- 缓存过期时间：5 分钟
- 在 `MessageListenerGatewayImpl` 或 `ReceiveMessageListenerExe` 中实现去重逻辑

**Must NOT do**:
- 不得使用数据库（轻量级缓存即可）
- 不得影响消息处理性能

**Parallelizable**: NO (depends on 1)

**References** (CRITICAL - Be Exhaustive):

**Pattern References** (existing code to follow):
- `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/ReceiveMessageListenerExe.java` - 消息处理入口
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java:42-55` - 事件处理模式

**WHY Each Reference Matters**:
- 需要在消息处理入口处添加去重逻辑
- 需要了解 event_id 的获取方式

**Acceptance Criteria**:

**Manual Execution Verification**:

*For Message Deduplication Verification:*
- [ ] 创建去重工具类或修改现有类：
  - 在 `ReceiveMessageListenerExe` 中添加去重逻辑
  - 使用 `ConcurrentHashMap<String, Instant>` 缓存已处理的 event_id
  - 设置缓存过期时间：5 分钟
- [ ] 重启应用
- [ ] 发送一条消息（如"测试"）
- [ ] 等待 5 秒
- [ ] 检查日志：`grep "BotMessageService.handleMessage 开始" /tmp/feishu-run.log | tail -10`
- [ ] 验证：该消息只被处理一次
- [ ] 5 分钟后再发送相同的消息
- [ ] 验证：消息被正常处理（缓存已过期）

**Evidence Required**:
- [ ] 新增代码的 diff
- [ ] 去重测试的日志输出（显示只处理一次）
- [ ] 缓存过期测试的日志输出（显示 5 分钟后重新处理）

**Commit**: YES
- Message: `feat(adapter): add message deduplication based on event_id`
- Files: `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/ReceiveMessageListenerExe.java`
- Pre-commit: `mvn clean compile -pl :feishu-bot-adapter`（验证编译）

---

### Task 5: 增强错误处理和日志

**What to do**:
- 在所有发送消息的方法中添加详细的错误日志
- 记录请求参数（敏感信息脱敏）
- 记录响应状态和错误信息
- 添加用户友好的错误提示（当发送失败时）

**Must NOT do**:
- 不得记录敏感信息（如 access_token, app_secret）
- 不得修改日志框架配置

**Parallelizable**: NO (depends on 2, 3, 4)

**References** (CRITICAL - Be Exhaustive):

**Pattern References** (existing code to follow):
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java:116-118` - 错误日志模式
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java:173-179` - 异常处理模式

**WHY Each Reference Matters**:
- 需要保持日志格式和错误处理的一致性
- 需要参考现有的敏感信息处理方式

**Acceptance Criteria**:

**Manual Execution Verification**:

*For Error Handling Verification:*
- [ ] 修改文件：`FeishuGatewayImpl.java`
  - 在 `sendDirectReply()` 中添加详细日志
  - 在 `sendMessage()` 中添加详细日志
  - 在 `createThreadByReply()` 中添加详细日志
  - 在 `sendReplyToMessage()` 中添加详细日志
- [ ] 修改文件：`BotMessageService.java`
  - 在 `handleMessage()` 中捕获更详细的异常信息
  - 添加用户友好的错误提示（如"网络连接失败，请稍后重试"）
- [ ] 重启应用
- [ ] 发送测试消息
- [ ] 检查日志：`grep -A 10 "Exception" /tmp/feishu-run.log | tail -20`
- [ ] 验证：日志包含详细的错误上下文
- [ ] 验证：敏感信息已脱敏
- [ ] 验证：用户看到友好的错误提示

**Evidence Required**:
- [ ] 代码 diff（显示新增的日志语句）
- [ ] 错误日志输出示例（脱敏后）
- [ ] 飞书客户端中错误提示的截图

**Commit**: YES
- Message: `feat(infrastructure): enhance error logging and user-friendly error messages`
- Files:
  - `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java`
  - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`
- Pre-commit: `mvn clean compile`（验证编译）

---

### Task 6: 完整的手动 QA 测试

**What to do**:
- 按照验证策略中的 5 个场景逐一测试
- 记录测试结果
- 生成测试报告

**Must NOT do**:
- 不得跳过任何测试场景
- 不得伪造测试结果

**Parallelizable**: NO (depends on 5)

**References** (CRITICAL - Be Exhaustive):

**Verification References** (manual test procedures):
- 本计划中的"Verification Strategy"章节（场景 1-5）

**Acceptance Criteria**:

**Manual Execution Verification**:

*For Each Scenario:*

**场景 1：私聊消息发送**
- [ ] 向机器人发送"你好"
- [ ] 验证：机器人回复帮助信息
- [ ] 验证：日志显示成功
- [ ] 记录：✅/❌ + 截图/日志

**场景 2：命令触发创建话题**
- [ ] 向机器人发送 `/time`
- [ ] 验证：机器人回复时间
- [ ] 验证：话题已创建
- [ ] 记录：✅/❌ + 截图/日志

**场景 3：话题内消息回复**
- [ ] 在话题中发送"你好"
- [ ] 验证：机器人在话题内回复
- [ ] 验证：没有 HTTP 400 错误
- [ ] 记录：✅/❌ + 截图/日志

**场景 4：网络不稳定测试**
- [ ] 重启应用
- [ ] 发送 20 条测试消息
- [ ] 统计成功率
- [ ] 验证：成功率 ≥ 95%
- [ ] 记录：成功次数/失败次数 + 日志

**场景 5：重复消息去重**
- [ ] 发送一条消息
- [ ] 等待 5 秒
- [ ] 检查日志
- [ ] 验证：只处理一次
- [ ] 记录：✅/❌ + 日志

**Evidence Required**:
- [ ] 测试报告文件：`.sisyphus/test-report/feishu-bot-fix-test-report.md`
- [ ] 每个场景的截图或日志
- [ ] 成功率统计结果
- [ ] 问题汇总和建议

**Commit**: NO（测试结果不提交）

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 2 | `fix(infrastructure): add DNS resolution retry with exponential backoff` | FeishuGatewayImpl.java | `mvn clean compile -pl :feishu-bot-infrastructure` |
| 3 | `fix(infrastructure): fix thread reply API call parameters` | FeishuGatewayImpl.java | `mvn clean compile -pl :feishu-bot-infrastructure` |
| 4 | `feat(adapter): add message deduplication based on event_id` | ReceiveMessageListenerExe.java | `mvn clean compile -pl :feishu-bot-adapter` |
| 5 | `feat(infrastructure): enhance error logging and user-friendly error messages` | FeishuGatewayImpl.java, BotMessageService.java | `mvn clean compile` |
| 6 | (不提交测试结果) | - | - |

---

## Success Criteria

### Verification Commands

```bash
# 场景 1-3：基本功能测试
# 向机器人发送消息，检查日志：
tail -f /tmp/feishu-run.log | grep -E "(发送回复成功|Reply success)"

# 场景 4：网络稳定性测试
# 发送 20 条消息后，统计成功率：
grep "发送回复成功\|Reply success" /tmp/feishu-run.log | wc -l

# 场景 5：消息去重测试
# 发送消息后，检查处理次数：
grep "BotMessageService.handleMessage 开始" /tmp/feishu-run.log | tail -10

# 检查错误日志：
grep -A 10 "Exception" /tmp/feishu-run.log | tail -20
```

### Final Checklist

- [ ] 所有场景的测试结果都记录在测试报告中
- [ ] DNS 解析成功率 ≥ 95%（20 次测试中至少 19 次成功）
- [ ] 话题回复功能正常（没有 HTTP 400 错误）
- [ ] 重复消息被正确过滤（相同 event_id 只处理一次）
- [ ] 所有错误都有详细的日志记录
- [ ] 用户能够看到友好的错误提示
- [ ] 代码编译通过
- [ ] 没有违反 COLA 架构规范
- [ ] 没有引入 WebHook 模式
- [ ] 飞书客户端中的消息交互正常

---

## Notes

**当前环境信息：**
- JDK 版本：17
- Spring Boot 版本：3.2.1
- 飞书 SDK 版本：2.5.2
- 应用运行模式：dev（长连接模式）
- 运行端口：8081

**已知限制：**
- 项目没有测试基础设施，所有验证都依赖手动 QA
- 网络环境可能存在 DNS 解析不稳定的问题
- 飞书 API 可能有变更，需要及时更新

**后续优化建议：**
1. 添加集成测试框架（如 JUnit + MockMvc）
2. 配置稳定的 DNS 服务器（如 8.8.8.8, 114.114.114.114）
3. 监控消息发送成功率和失败率
4. 实现消息发送的异步队列（提高性能）
5. 添加消息发送失败的重试队列（离线缓存）

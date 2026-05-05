# CONTEXT.md — Phase 2: Command Router & Conversation UX

**Created:** 2026-04-10
**Phase:** 02-command-router-conversation-ux
**Requirements:** CMD-01, CMD-02, CMD-03, CMD-04, UX-01, UX-02, UX-03, COMPAT-02

---

## Phase Overview

**Goal:** Redesign command routing + enable direct typing in bound topics + suppress ghost reply bubbles + add status indicators and next-step suggestions. After this phase, the core conversation experience works end-to-end.

**Dependency:** Phase 1 (Context Foundation) — correct context binding required for all routing decisions.

**Starting State (from Phase 1):**
- 280 tests passing (174 domain + 44 app + 59 infra + 3 start)
- `AppExecutionResult` DTO with factory methods `text()`, `noReply()`, `withSession()`
- `MessageContext` resolve-once pipeline threaded through full chain
- `chatId→threadId` binding propagation working
- Graceful degradation for unbound topics
- Structured session ID passing (no text parsing)
- Dual state model still exists: `TopicState` (domain) + `ContextSessionState` (app)

---

## Design Decisions

### Decision 1: 直接输入路由策略 (UX-01)

**Decision:** 在 `OpenCodeMessageAppService` 中预处理合成命令

**Rationale:** 将纯文本合成命令后再路由，使命令路由逻辑（`OpenCodeApp` / `OpenCodeCommandHandler`）不被"隐式聊天"逻辑污染。所有命令统一走 `switch-case` 路由，没有特殊分支。预处理器在应用层做"是否需要合成"的判断，领域层的命令处理保持纯粹。

**Implementation Notes:**

1. **改动位置：** `OpenCodeMessageAppService.tryHandle()` 或 `handleMessageInternal()`
2. **合成条件：**
   - 消息内容不以 `/` 开头（非命令）
   - 消息来自已初始化话题（`TopicState.INITIALIZED`）
   - 消息来源话题已绑定 OpenCode 且有活跃 session
3. **合成行为：** 将 `message.content` 修改为 `/opencode chat <原始内容>` 或构造新的 Message 副本
4. **边界情况：**
   - 非 `/` 开头但在未初始化话题 → 不合成，显示引导
   - 非 `/` 开头但在群聊主对话（无 topicId） → 不合成，走正常路由（可能匹配其他 app）
   - `/opencode` 或 `/oc` 开头的显式命令 → 不影响，正常路由
5. **注意点：** 合成命令需要保持原 Message 的 `topicId`、`chatId`、`eventId` 不变，只修改 `content`

**Affected Files:**
- `feishu-bot-app/.../opencode/OpenCodeMessageAppService.java` — 添加合成逻辑
- `feishu-bot-domain/.../opencode/OpenCodeApp.java` — `parts.length < 2` 时话题内不再显示状态（已由合成逻辑覆盖）

**Success Criteria Mapping:** 用户在已绑定话题中直接输入文字，收到 OpenCode 的 AI 回复（无需 `/oc chat` 前缀）

---

### Decision 2: 命令集重设计 (CMD-01)

**Decision:** 小范围调整 — 保持现有命令集 + 新增 `status` 快捷命令 + UX 增强

**Rationale:** 现有命令集已经覆盖了手动控制流的全部路径（projects → sessions → sc → chat），不需要大范围重构。Phase 2 重点是让已有命令的行为正确、错误信息可操作、操作后有引导。新增 `status` 快捷命令提升可发现性。

**Command Set (Phase 2 final):**

| 命令 | 别名 | 状态要求 | 行为 |
|------|------|----------|------|
| `help` | — | 所有 | 显示帮助信息 |
| `connect` | — | 所有 | 检查 OpenCode 连接 |
| `projects` | `p` | 所有 | 列出项目 |
| `sessions <项目>` | `s` | 所有 | 列出项目会话 |
| `session status` | — | 话题内 | 查看当前会话信息 |
| `session list` | — | 话题内 | 列出所有会话 |
| **`status`** | — | 话题内 | **新增：快捷查看绑定状态** |
| `sc <会话ID>` | — | 话题内 | 绑定会话到话题 |
| `chat <内容>` | — | INITIALIZED | 继续对话 |
| `chatnow <内容>` | `cn` | 所有 | 快速创建会话并对话 |
| `new [项目] <内容>` | — | 所有/话题内 | 创建新会话 |
| `reset` | — | 话题内 | 解绑话题 |
| `commands` | — | 所有 | 列出可用命令 |

**Whitelist Per State (CMD-02):**

- **NON_TOPIC:** help, connect, projects/p, sessions/s, sc, chatnow/cn, new
- **UNINITIALIZED:** help, connect, projects/p, sessions/s, session, sc, status, reset, commands, chatnow/cn, new
- **INITIALIZED:** 全部命令

**Implementation Notes:**

1. `status` 命令作为 `session status` 的快捷方式，在 `OpenCodeCommandHandler` 的 switch-case 中添加 `case "status"` 路由
2. 更新 `OpenCodeApp.getCommandWhitelist()` 将 `status` 加入 UNINITIALIZED 和 INITIALIZED 白名单
3. 更新 `OpenCodeApp.getHelp()` 添加 `status` 命令说明
4. 别名检查：确保所有白名单包含完整别名（如 `p` 对应 `projects`，`s` 对应 `sessions`，`cn` 对应 `chatnow`）

**Affected Files:**
- `feishu-bot-domain/.../opencode/OpenCodeCommandHandler.java` — 添加 `status` case
- `feishu-bot-domain/.../opencode/OpenCodeApp.java` — 更新白名单 + 帮助文本
- `feishu-bot-domain/.../opencode/OpenCodeSessionManager.java` — 可能需要 `getCurrentSessionStatus()` 的快捷入口

**Success Criteria Mapping:** 命令集匹配手动控制流，每种状态只显示/允许有效命令

---

### Decision 3: 空气泡消除策略 (UX-02)

**Decision:** 简单返回 `AppExecutionResult.noReply()`，不发送任何即时消息，等待流式卡片出现

**Rationale:** 最简单的方案。`sendReply()` 已经正确处理 null/noReply（跳过回复）。流式卡片会在异步任务开始后 1-2 秒内出现（通过 SSE + CardKit），用户体验是输入 → 流式卡片开始输出，没有中间气泡。发送"处理中"提示反而增加了多余的视觉噪音和 API 调用。

**Implementation Notes:**

1. **核心改动：** 在 `OpenCodeTaskExecutor.executeTask()` 中，将返回值从 `AppExecutionResult.text("")` 改为 `AppExecutionResult.noReply()`
2. **全面排查：** 检查所有异步任务路径（`executeWithAutoSession`、`executeTask`、`createSessionOnly` 等）确保无空字符串返回
3. **防护措施：** 在 `BotMessageAppService.sendReply()` 中增加空字符串检查，即使某处遗漏返回了 `""` 也不会发送空气泡：
   ```java
   if (replyContent == null || replyContent.isEmpty()) {
       return null; // 不发送回复
   }
   ```
4. **注意点：** `chatnow/cn` 路径可能有特殊行为 — 创建会话后需要返回确认信息（非空），所以只在纯聊天执行路径返回 noReply

**Affected Files:**
- `feishu-bot-domain/.../opencode/OpenCodeTaskExecutor.java` — `executeTask()` 返回 `noReply()`
- `feishu-bot-app/.../message/BotMessageAppService.java` — `sendReply()` 增加空字符串防护

**Success Criteria Mapping:** 异步任务执行后不出现空消息气泡，只有流式卡片响应

---

### Decision 4: 状态指示器格式 (UX-03)

**Decision:** 每条回复头部添加状态行，始终可见

**Rationale:** 用户在任何时候都需要知道"我在哪个项目的哪个会话中"。将状态行放在回复头部最显眼，且不会和流式卡片内容混淆（流式卡片是动态更新的，状态行是静态头部）。格式简洁不干扰阅读。

**Implementation Notes:**

1. **格式：** `📎 feishu-backend | ses_abc123` 或 `📎 feishu-backend | 未绑定会话`
2. **放置位置：** 在 `BotMessageAppService.sendReply()` 或 `NextStepSuggester` 中统一添加，不分散到各命令处理器
3. **状态行生成逻辑：**
   - 从 `MessageContext` 中获取绑定的 `appId` + `sessionId`
   - 如果有 session，显示项目名 + session ID（取前 8 位）
   - 如果无 session 但在 app context 中，显示项目名 + "未绑定会话"
   - 如果不在 app context 中，不显示状态行
4. **排除场景：**
   - 非 OpenCode 应用的回复不添加状态行
   - 流式卡片回复的状态行通过卡片 header 展示（非文本拼接，避免和流式内容冲突）
   - help 命令的回复不添加状态行（帮助文本已经足够长）
5. **项目名获取：** 需要从 session 数据或 binding 中获取项目名，可能需要扩展 `AppSession` 数据结构或在 `OpenCodeSessionData` 中存储项目名

**Affected Files:**
- `feishu-bot-app/.../message/BotMessageAppService.java` — 或新建 `StatusIndicator` 组件
- `feishu-bot-domain/.../model/MessageContext.java` — 可能需要携带更多绑定信息
- `feishu-bot-domain/.../model/opencode/OpenCodeSessionData.java` — 确保包含项目名

**Success Criteria Mapping:** 每条机器人回复包含当前绑定状态（项目名/会话ID）

---

### Decision 5: 下一步提示实现方式 (CMD-04)

**Decision:** 集中式 `NextStepSuggester` 服务

**Rationale:** 集中式方案避免各命令处理器重复编写"下一步"逻辑，保证格式和内容的一致性。当命令集或流程变化时，只需更新一处。`NextStepSuggester` 接收当前状态 + 刚执行的命令，返回建议文本。各处理器只需调用一行代码。

**Implementation Notes:**

1. **类位置：** `feishu-bot-domain/.../opencode/NextStepSuggester.java`（OpenCode 领域内的组件）
2. **接口设计：**
   ```java
   public class NextStepSuggester {
       public String suggest(TopicState state, String executedCommand, MessageContext context) {
           // 根据状态 + 命令 + 上下文返回下一步建议
       }
   }
   ```
3. **建议规则示例：**
   - `projects` 执行后 → `💡 下一步：sessions <项目名> 查看会话列表`
   - `sessions` 执行后 → `💡 下一步：sc <会话ID> 绑定会话到话题`
   - `sc` 绑定成功后 → `💡 下一步：直接输入问题开始对话，或 /oc chat <内容>`
   - `chat` 执行后 → 无建议（已在对话中，不需要提示）
   - `reset` 执行后 → `💡 下一步：sc <会话ID> 重新绑定，或 sessions 查看会话`
   - `chatnow` 执行后 → 无建议（已在对话中）
   - `new` 执行后 → `💡 下一步：直接输入问题开始对话`
   - 错误场景 → 错误信息本身已包含建议（CMD-03）
4. **附加位置：** 在回复内容末尾追加 `\n\n---\n` + 建议文本
5. **与状态指示器 (UX-03) 的结合：** 状态行在头部，下一步建议在尾部，两者不在同一区域
6. **注册为 `@Component`**，通过构造器注入到 `OpenCodeCommandHandler` 或通过 `BotMessageAppService` 统一附加

**Affected Files:**
- `feishu-bot-domain/.../opencode/NextStepSuggester.java` — 新建
- `feishu-bot-domain/.../opencode/OpenCodeCommandHandler.java` — 注入并使用
- 或 `feishu-bot-app/.../message/BotMessageAppService.java` — 统一附加逻辑

**Success Criteria Mapping:** 每个操作完成后的回复中包含"下一步"建议

---

### Decision 6: group→topic 模型 (COMPAT-02)

**Decision:** 按已有理解执行

**Rationale:** Phase 1 已建立 `chatId→threadId` 绑定传播机制（`BotMessageAppService.persistBindingIfNeeded()` 在 `SendResult` 返回新 threadId 后将绑定复制到线程上下文）。Phase 2 需要确保所有命令路径正确利用此机制，并在不适合的场景中给出清晰引导。

**Implementation Notes:**

1. **群聊主对话中的行为：**
   - `chatnow/cn` → 创建会话 → 回复创建话题 → 绑定传播到新 topicId ✓（Phase 1 已实现）
   - `new` → 同上 ✓
   - `projects`、`sessions`、`help` → 正常执行，不涉及话题 ✓
   - `chat`、`sc`、`session` → 错误提示"请在话题中操作"，附带示例：`"先发送 /oc cn <问题> 创建话题，或进入已有话题操作"`
2. **话题中的行为：**
   - 所有话题内命令正常工作
   - 纯文本 → 合成 chat 命令 → 路由到 OpenCode ✓（Decision 1）
3. **绑定传播验证：** 确保 `chatnow` 和 `new` 路径的 `AppExecutionResult.withSession()` 正确携带 `openCodeSessionId`，以便 `BotMessageAppService` 在 `persistBindingIfNeeded()` 中使用
4. **已在 Phase 1 实现的关键机制：**
   - `SendResult` 携带 `persistedThreadId`（新创建的话题 ID）
   - `BotMessageAppService.handleMessage()` 在回复后将绑定从 chatId 复制到新 threadId
   - `AppExecutionResult.openCodeSessionId` 结构化传递 session ID

**Affected Files:**
- `feishu-bot-domain/.../opencode/OpenCodeCommandHandler.java` — `chat`/`sc`/`session` 在 NON_TOPIC 状态的白名单拦截信息中包含群聊引导
- `feishu-bot-domain/.../topic/TopicCommandValidator.java` — 确保拦截消息包含"请在话题中操作"
- 已有机制无需修改（绑定传播已在 Phase 1 验证）

**Success Criteria Mapping:** 在非话题环境中执行话题命令，错误消息明确告知"请在话题中操作"并给出示例

---

## Implementation Priority Order

建议的任务执行顺序（基于依赖关系）：

| Priority | Task | Requirements | Rationale |
|----------|------|-------------|-----------|
| 1 | 空气泡消除 (UX-02) | UX-02 | 最简单、最独立、风险最低。改动小（1-2个文件），可立即验证 |
| 2 | 直接输入路由 (UX-01) | UX-01 | 核心体验修复。依赖正确的状态检测（Phase 1 已保证），是后续 UX 增强的基础 |
| 3 | 新增 status 命令 + 白名单完善 (CMD-01, CMD-02) | CMD-01, CMD-02 | 命令集小调整 + 白名单别名完整性检查。为下一步提示提供命令上下文 |
| 4 | 集中式 NextStepSuggester (CMD-04) | CMD-04 | 依赖命令集稳定后实现。新建组件，不侵入现有逻辑 |
| 5 | 可操作错误信息 (CMD-03) | CMD-03 | 增强现有错误路径，加入下一步建议。可与 NextStepSuggester 协同 |
| 6 | 状态指示器 (UX-03) | UX-03 | 需要确定项目名获取方式，可能涉及数据结构扩展 |
| 7 | group→topic 引导完善 (COMPAT-02) | COMPAT-02 | 确保所有非话题场景的拦截信息包含清晰引导。大部分已在 Phase 1 + 上述任务中覆盖 |

---

## Cross-Cutting Concerns

### 测试要求

- 所有 280 个现有测试必须继续通过
- 新增测试覆盖：
  - 纯文本在已初始化话题中被正确合成为 chat 命令
  - `status` 命令返回正确的绑定信息
  - 空字符串返回不触发回复发送
  - NextStepSuggester 各状态/命令组合返回正确建议
  - 状态指示器包含正确的项目名和 session ID
  - 群聊中话题命令的错误引导信息

### 不变的约束

- COLA 架构分层规则
- WebSocket 长连接（禁止 WebHook）
- 无状态应用（Help/Time/Bash/History）行为不变
- `FishuAppI` 接口的 `@Deprecated` 方法保持兼容
- `MessageContext` resolve-once 模式不变

### Phase 1 遗留（不在 Phase 2 scope 内）

- 双状态模型合并（TopicState vs ContextSessionState）→ V2-03 延期
- `chatnow` 不执行 prompt → 本 phase 是否修复取决于实现复杂度（V2-01 延期项，但如果改动小可顺手修复）
- Domain 层 Spring 注解 → 已知 COLA 违规，不在 scope

---

## Key Files Reference

**Phase 2 主要改动文件：**

| File | Module | Changes |
|------|--------|---------|
| `OpenCodeMessageAppService.java` | app | 纯文本合成 chat 命令 |
| `OpenCodeApp.java` | domain | 白名单更新、帮助文本更新、移除纯文本显示状态逻辑 |
| `OpenCodeCommandHandler.java` | domain | 新增 `status` case、注入 NextStepSuggester、错误信息增强 |
| `OpenCodeTaskExecutor.java` | domain | 返回 noReply() 替代空字符串 |
| `BotMessageAppService.java` | app | 空字符串防护、状态指示器附加 |
| `NextStepSuggester.java` | domain | **新建** — 集中式下一步建议 |
| `CommandWhitelist` 构建逻辑 | domain | 白名单别名完整性检查 |

**Phase 1 已建基础设施（只读参考）：**

| File | Module | Phase 1 Contribution |
|------|--------|---------------------|
| `AppExecutionResult.java` | domain | 结构化执行结果 DTO |
| `MessageContext.java` | domain | 请求级上下文传递 |
| `MessageContextResolver.java` | app | 绑定一次性解析 |
| `ImContextBindingGatewayImpl.java` | infra | 原子 upsert + chatId→threadId 传播 |
| `ContextSessionOrchestratorImpl.java` | app | MessageContext overloads |

---

*CONTEXT.md created: 2026-04-10*
*Decisions locked for Phase 2 planning*

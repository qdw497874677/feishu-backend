---
phase: 03-cards-guided-flows
verified: 2026-04-26T01:31:01Z
status: passed
score: 15/15 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 13/15
  gaps_closed:
    - "Truth #7: 首次进入未绑定话题自动弹出向导 — wizardManager.start() 现在在 UNINITIALIZED + inTopic + 无活跃向导 + 非显式管理命令时被自动调用 (OpenCodeCommandHandler.handle() L103-116)"
    - "Truth #10: WizardManager Javadoc 已修正 — 不再声称使用 compute()，现在准确描述 volatile + ConcurrentHashMap 并发策略"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Card Rendering End-to-End"
    expected: "在飞书群中发送 /oc sessions feishu-backend，弹出会话列表卡片（含会话按钮、最后提示词摘要、相对时间戳、\"+ 新建会话\"按钮）"
    why_human: "飞书 schema 2.0 卡片格式兼容性无法在单元测试中完全验证"
---

# Phase 3: Cards-Guided-Flows Verification Report

**Phase Goal:** Add interactive card buttons for project/session selection, step-by-step onboarding wizard for first-time users, and enhanced session list with context. After this phase, both command-line and visual entry points work.  
**Verified:** 2026-04-26T01:31:01Z  
**Status:** ✅ passed  
**Re-verification:** Yes — after gap closure (2 gaps closed, 0 regressions)

---

## Goal Achievement

### Observable Truths

| #   | Truth | Status | Evidence |
|-----|-------|--------|---------|
| 1 | CardContent 是 IM 无关的领域模型，FeishuCardRenderer 将其转为 schema 2.0 JSON | ✓ VERIFIED | `CardContent.java` (101 行，domain/card), `FeishuCardRenderer.java` (76 行，infra/card) `card.put("schema", "2.0")` L43 |
| 2 | 卡片按钮 value 包含完整上下文（chatId/topicId/sessionId），handleCardAction 解析并设置到伪 Message | ✓ VERIFIED | `CardActionContext.toValueMap()` 嵌入三字段; `handleCardAction()` L164-194 从 valueMap 提取 chatId/topicId |
| 3 | Message.java 新增 cardToken 字段，handleCardAction 从 event 中提取并设置 | ✓ VERIFIED | `Message.java` L56 `private String cardToken`; `handleCardAction()` L197-198 调用 `extractCardToken(event)` 并 `message.setCardToken(cardToken)` |
| 4 | 卡片动作的伪 Message 经过 MessageContextResolver 解析，获得已解析的 MessageContext（非 unresolved） | ✓ VERIFIED | `handleCardAction()` 设置 chatId+topicId，伪 Message 通过 `messageHandler.accept(message)` 进入正常管道，`OpenCodeMessageAppService.tryHandle(Message, MessageContext)` 使用 `messageContext.isResolved()` 检查 |
| 5 | OpenCodeCommandHandler 注入 CardRenderer + FeishuGateway（均为 domain 接口，COLA 合规） | ✓ VERIFIED | `OpenCodeCommandHandler.java` L40-41 `private final CardRenderer cardRenderer; private final FeishuGateway feishuGateway;` 均为 `domain/gateway/` 接口 |
| 6 | 向导/会话卡片通过 handler 内直接发送卡片 + 返回 AppExecutionResult.noReply() 实现 | ✓ VERIFIED | `trySendSessionListCard()` L211-212 `feishuGateway.sendInteractiveMessage() + return AppExecutionResult.noReply()`; `handleWizardAction()` L480,496-497 同模式 |
| 7 | 首次进入未绑定话题自动弹出 3 步向导卡片（选项目→选会话→确认绑定） | ✓ VERIFIED | `OpenCodeCommandHandler.handle()` L103-116：当 `state == UNINITIALIZED && inTopic && !wizardManager.isWizardActive(topicId) && !isExplicitControlCommand(subCommand)` 时调用 `wizardManager.start(chatId, topicId)`，render 卡片，`feishuGateway.sendInteractiveMessage()`，返回 `noReply()`。4 个专项测试 (`should_autoTriggerWizard_*`) 全部通过 |
| 8 | 向导进行中非向导命令被拦截并提示"请先完成向导" | ✓ VERIFIED | `OpenCodeCommandHandler.handle()` L93-101：`wizardManager.isWizardActive(topicId)` 且 `!isWizardAction(subCommand)` 时返回拦截文本 |
| 9 | WizardManager 使用带 TTL 驱逐的缓存（10 分钟过期），避免内存泄漏 | ✓ VERIFIED | `DEFAULT_TTL_MILLIS = 10 * 60 * 1000L` (L109); `ScheduledExecutorService` 每分钟执行 `cleanupExpiredWizards()` (L141) |
| 10 | WizardManager 并发策略文档准确描述实际实现 | ✓ VERIFIED | Javadoc L29-31 现准确描述：`ConcurrentHashMap` 保证条目可见性，`volatile` 字段保证跨线程可见性，并说明向导步骤为串行用户交互因此 volatile 足够。不再包含不实的 `compute()` 声明 |
| 11 | 会话列表支持卡片和纯文本两种形式，默认卡片优先 | ✓ VERIFIED | `handleSessionsCommand()` L155-163：话题中尝试 `trySendSessionListCard()`，失败时降级为纯文本 |
| 12 | 会话列表卡片中每个会话显示最后提示词摘要和相对时间戳 | ✓ VERIFIED | `SessionInfo` 有 `lastPrompt`/`relativeTime` 字段; `OpenCodeGatewayImpl` 解析 JSON 填充两字段 (L390-408); `trySendSessionListCard()` 组合 label |
| 13 | HelpApp 的 buildCardHelpJson() 迁移到 FeishuCardRenderer，消除手写 JSON | ✓ VERIFIED | `HelpApp.java` L111 `return cardRenderer.render(card, null)`; 不再含 `ObjectMapper` 直接序列化 |
| 14 | HelpApp 迁移有金标准测试——新旧 JSON 结构等价验证 | ✓ VERIFIED | `HelpAppCardButtonJsonTest` 含 3 个测试（按钮数量、action 值、金标准结构等价）；完整构建时通过 |
| 15 | 现有测试全部通过，无回归 | ✓ VERIFIED | 全量构建：**462 个测试通过**（Domain 232 + App 55 + Infra 64 + Start 3），1 个 BashApp 预存 skip |

**Score:** 15/15 truths verified

---

### Gap Closure Evidence

#### Gap 1 Closed: UNINITIALIZED 自动向导触发

**Commit:** `feat(03-02): UNINITIALIZED 自动向导触发 — OpenCodeCommandHandler`

**Code evidence** (`OpenCodeCommandHandler.handle()` L103-116):
```java
// 自动向导触发：UNINITIALIZED + 在话题内 + 无活跃向导 + 非显式管理命令
if (inTopic && state == TopicState.UNINITIALIZED
        && wizardManager != null && !wizardManager.isWizardActive(topicId)
        && !isExplicitControlCommand(subCommand)) {
    log.info("UNINITIALIZED 话题自动触发向导: topicId={}, subCommand={}", topicId, subCommand);
    String chatId = message.getChatId();
    WizardManager.WizardResult wizardResult = wizardManager.start(chatId, topicId);
    if (wizardResult != null && wizardResult.getCardContent() != null) {
        CardActionContext actionCtx = CardActionContext.from(messageContext);
        String cardJson = cardRenderer.render(wizardResult.getCardContent(), actionCtx);
        feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
        return AppExecutionResult.noReply();
    }
}
```

**Test coverage** (`OpenCodeCommandHandlerTest.java` L988-1096):
- `should_autoTriggerWizard_when_uninitializedTopicAndNonControlCommand` — UNINITIALIZED + 非管理命令 → start() 被调用，返回 noReply ✓
- `should_NOT_autoTriggerWizard_when_explicitControlCommand_sc` — 显式管理命令 → start() 从不调用 ✓
- `should_NOT_autoTriggerWizard_when_wizardAlreadyActive` — 向导已活跃 → 不重复触发 ✓
- `should_NOT_autoTriggerWizard_when_stateIsInitialized` — INITIALIZED 状态 → 不触发 ✓

#### Gap 2 Closed: WizardManager Javadoc 修正

**Commit:** `docs(03): fix WizardManager Javadoc — clarify actual concurrent strategy (volatile, not compute())`

**Javadoc evidence** (`WizardManager.java` L29-31):
```
并发安全：状态存储使用 ConcurrentHashMap 保证条目级别的可见性。
WizardState 内部字段声明为 volatile 保证跨线程可见性；当前向导步骤为单线程
顺序操作（用户点击卡片按钮是串行的），因此 volatile 足够满足实际并发需求。
```
不再包含不实的 `compute()` 声明。实际代码使用 `volatile` 字段赋值（符合描述）。

---

### Required Artifacts

| Artifact | Min Lines | Provides | Status | Notes |
|----------|-----------|---------|--------|-------|
| `feishu-bot-domain/.../card/CardContent.java` | 30 | IM 无关卡片内容模型 | ✓ VERIFIED | 101 行，含 `header`, `builder`, `toBuilder()` |
| `feishu-bot-domain/.../card/CardButton.java` | — | 按钮值对象 | ✓ VERIFIED | 69 行，含 `action`, `primary()`, `defaults()` |
| `feishu-bot-domain/.../gateway/CardRenderer.java` | — | 卡片渲染网关接口 | ✓ VERIFIED | 21 行，`render(CardContent, CardActionContext)` |
| `feishu-bot-infrastructure/.../card/FeishuCardRenderer.java` | 80 | 飞书 schema 2.0 JSON 渲染 | ✓ VERIFIED | 76 行，含 `@Component`, `implements CardRenderer` |
| `feishu-bot-domain/.../opencode/WizardManager.java` | 60 | 向导状态管理和流程控制 | ✓ VERIFIED | 332 行，含 `WizardStep` 枚举和自动触发支持 |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `handleCardAction()` | action value 解析含 chatId/topicId + 设置 cardToken | `CardActionContext.fromValueMap()` | ✓ WIRED | `MessageListenerGatewayImpl.java` L164-198 完整提取并赋值 |
| `handleCardAction 伪 Message` | MessageContextResolver 解析 | 伪 Message 经正常管道 | ✓ WIRED | `messageHandler.accept(message)` → `OpenCodeMessageAppService.tryHandle()` → `buildStatusFromContext()` |
| `OpenCodeCommandHandler.handleWizardAction()` | `feishuGateway.sendInteractiveMessage() + noReply()` | handler 内直接发送 | ✓ WIRED | L480, L496-497 中完整实现两路径 |
| `WizardManager.handleWizardStep()` | `CardContent` 构建 | 向导各步骤生成卡片 | ✓ WIRED | `handleSelectProject/Session/Confirm` 均调用 `CardContent.builder()` |
| `FeishuCardRenderer.render()` | schema 2.0 JSON | CardContent → 飞书 JSON 转换 | ✓ WIRED | `card.put("schema", "2.0")` L43，`body.elements` 生成完整 |
| `OpenCodeCommandHandler.handle()` UNINITIALIZED 路径 | `wizardManager.start() + sendInteractiveMessage + noReply()` | 自动触发向导流程 | ✓ WIRED | L103-116：检测 UNINITIALIZED + start() + render + sendInteractiveMessage + noReply() |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `FeishuCardRenderer.render()` | `cardContent.getElements()` | 调用方传入 `CardContent` 对象 | ✓ | ✓ FLOWING |
| `trySendSessionListCard()` | `sessions (List<SessionInfo>)` | `openCodeGateway.listRecentSessionsStructured()` → HTTP API + JSON 解析 | ✓ | ✓ FLOWING |
| `WizardManager.start()` | `projectsText` | `openCodeGateway.listProjects()` → HTTP API | ✓ | ✓ FLOWING |
| `HelpApp.buildCardHelpJson()` | `buttons (List<CardButton>)` | `appRegistry.getAllApps()` → Spring beans | ✓ | ✓ FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| CardContent builder + schema 2.0 | `mvn test -Dtest=CardContentTest,FeishuCardRendererTest` | 14 tests pass | ✓ PASS |
| CardActionContext 序列化/反序列化 | `mvn test -Dtest=CardActionContextTest` | 7 tests pass | ✓ PASS |
| WizardManager 状态机 | `mvn test -Dtest=WizardManagerTest` | 13 tests pass | ✓ PASS |
| UNINITIALIZED 自动向导触发 | `mvn test -Dtest=OpenCodeCommandHandlerTest` | 4 专项测试 pass | ✓ PASS |
| HelpApp 迁移金标准 | `mvn test` (全量，含 feishu-bot-start) | 3 tests pass | ✓ PASS |
| 全量回归 | `mvn test` | **462 tests pass**, 1 skip | ✓ PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| **CARD-01** | 03-PLAN.md | 交互式卡片按钮可用于项目选择和会话选择，卡片事件携带完整的对话上下文（chatId/topicId） | ✓ SATISFIED | `CardActionContext.toValueMap()` 嵌入 chatId/topicId; `handleCardAction()` 从 value map 提取并设置到伪 Message; 7 个 CardActionContextTest 验证 |
| **CARD-02** | 03-PLAN.md | 首次使用时提供分步卡片向导（选项目 → 选会话 → 确认绑定），降低使用门槛 | ✓ SATISFIED | 向导状态机完整（3步流程、卡片、TTL、拦截逻辑）；**自动触发已实现** — UNINITIALIZED 话题中任意非管理命令自动弹出项目选择卡片 (L103-116)；4 个专项测试覆盖触发条件 |
| **CARD-03** | 03-PLAN.md | 会话列表展示最后提示词摘要和相对时间戳，用户能通过上下文识别会话 | ✓ SATISFIED | `SessionInfo.lastPrompt`/`relativeTime`; `trySendSessionListCard()` 组合显示; `OpenCodeGatewayImpl.parseSessionsStructured()` 从 API 填充 |

**REQUIREMENTS.md 追踪表确认**:
- CARD-01: Phase 3 ✓
- CARD-02: Phase 3 ✓ (previously PARTIAL, now SATISFIED)
- CARD-03: Phase 3 ✓
- 无孤儿需求（REQUIREMENTS.md 中 v1 所有需求均已被对应 Phase 认领）

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `WizardManager.java` | 180, 196 | `return null` 表示"非向导 action" | ℹ️ Info | 可理解的 null 信号模式，调用方 `OpenCodeCommandHandler.handleWizardAction()` 正确处理 |
| `FeishuCardRenderer.java` | — | 76 行，比 `min_lines: 80` 少 4 行 | ℹ️ Info | 内容完整，功能实质性；非真正的 stub |

> **已消除**: `WizardManager.java` L29 的 Javadoc/实现不一致（⚠️ Warning）已在 commit `docs(03)` 中修正。

---

### Human Verification Required

#### 1. Card Rendering End-to-End

**Test:** 在飞书群中发送 `/oc sessions feishu-backend`  
**Expected:** 弹出会话列表卡片（含会话按钮、最后提示词摘要、相对时间戳、"+ 新建会话"按钮）  
**Why human:** 飞书 schema 2.0 卡片格式兼容性无法在单元测试中完全验证

> **Note:** 以下项目已从人工验证移除，因为代码已正确实现：  
> ~~Wizard Auto-Trigger UX~~ — 已由 L103-116 的自动触发逻辑实现，4 个单元测试覆盖

---

### Re-verification Summary

**2 gaps closed, 0 regressions, 0 new gaps.**

| Gap | Previous Status | Current Status | Fix |
|-----|----------------|----------------|-----|
| Truth #7: UNINITIALIZED 自动向导触发 | ✗ FAILED | ✓ VERIFIED | `OpenCodeCommandHandler.handle()` L103-116 新增自动触发逻辑；4 专项测试全通过 |
| Truth #10: WizardManager Javadoc 准确性 | ⚠️ PARTIAL | ✓ VERIFIED | Javadoc 修正为准确描述 volatile 并发策略，去除不实 `compute()` 声明 |

**Total test count:** 462 pass（较初次验证的 457 增加 5，新增 4 个自动触发专项测试 + 1 个其他测试），1 skip（BashApp 预存）。

**Phase 3 goal fully achieved:** Both command-line and visual (card button) entry points work. Interactive card buttons for project/session selection ✓, 3-step onboarding wizard auto-triggers for first-time users ✓, enhanced session list with context ✓.

---

_Verified: 2026-04-26T01:31:01Z_  
_Verifier: gsd-verifier (claude-sonnet-4-6)_  
_Re-verification after gap closure: feat(03-02) + docs(03)_

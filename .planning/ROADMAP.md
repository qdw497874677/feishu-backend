# Roadmap: OpenCode Interactive Flow Redesign

**Created:** 2026-04-07
**Phases:** 3
**Requirements:** 17 (100% mapped)
**Granularity:** Coarse

## Overview

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Context Foundation | Fix data flow — context propagation, structured IDs, request caching | CTX-01, CTX-02, CTX-03, CTX-04, CTX-05, COMPAT-01 | 4 | **COMPLETE** |
| 2 | Command Router & Conversation UX | Redesign command routing + direct typing + reply suppression | CMD-01, CMD-02, CMD-03, CMD-04, UX-01, UX-02, UX-03, COMPAT-02 | 5 |
| 3 | Cards & Guided Flows | Interactive card entry + onboarding wizard + enhanced session list | CARD-01, CARD-02, CARD-03 | 3 |

## Phase Details

### Phase 1: Context Foundation

**Goal:** Fix the data flow layer — context propagation from chatId to threadId, structured session ID passing, request-scoped caching, and graceful degradation. After this phase, context binding is reliable and performant.

**Requirements:**
- **CTX-01**: chatId→threadId 自动传播绑定
- **CTX-02**: 结构化 sessionId 传递
- **CTX-03**: 请求级缓存（MessageContext 参数传递）
- **CTX-04**: IM 绑定与 App 会话分层独立
- **CTX-05**: 旧话题静默降级
- **COMPAT-01**: 261 测试全过（跨阶段，锚定在此）

**Success Criteria:**
1. 在群聊中创建会话后，回复自动创建话题，后续在话题内的消息能正确找到绑定关系
2. sessionId 通过 `OpenCodeExecutionResult` 等结构体字段传递，代码中不存在从 markdown 文本解析 sessionId 的逻辑
3. 每条消息处理路径中，`ImContextBindingGateway.findBinding()` 只被调用一次（可通过日志/测试验证）
4. 访问无绑定的旧话题时，显示帮助引导而非报错

**Dependency:** None — foundational phase.

**Status:** **COMPLETE** (2026-04-10) — 9 tasks (7+2), 280 tests, 9 commits.
Summary: `.planning/phases/01-context-foundation/01-plan-SUMMARY.md`
Gap closure: `.planning/phases/01-context-foundation/01-02-SUMMARY.md`

**Plans:** 2 plans (2 complete)
Plans:
- [x] PLAN.md — Context foundation (AppExecutionResult, MessageContext pipeline, binding propagation, graceful degradation)
- [x] 01-02-PLAN.md — Gap closure: Thread MessageContext through OpenCode domain chain (CTX-03 complete)

**UI hint:** no

---

### Phase 2: Command Router & Conversation UX

**Goal:** Redesign the command set for manual-control flow, enable direct typing in bound topics, suppress ghost reply bubbles, add status indicators and next-step suggestions. After this phase, the core conversation experience works end-to-end.

**Requirements:**
- **CMD-01**: 新命令集（手动控制流）
- **CMD-02**: 按状态白名单（含别名）
- **CMD-03**: 可操作的错误信息
- **CMD-04**: 下一步提示
- **UX-01**: 直接输入 = 聊天
- **UX-02**: 消除空气泡
- **UX-03**: 状态指示器
- **COMPAT-02**: group→topic 模型

**Success Criteria:**
1. 用户在已绑定话题中直接输入文字，收到 OpenCode 的 AI 回复（无需 `/oc chat` 前缀）
2. 异步任务执行后不出现空消息气泡，只有流式卡片响应
3. 在非话题环境中执行话题命令，错误消息明确告知"请在话题中操作"并给出示例
4. 每条机器人回复包含当前绑定状态（项目名/会话ID）
5. 每个操作完成后的回复中包含"下一步"建议

**Dependency:** Phase 1 (correct context binding required for routing decisions).

**Plans:** 2 plans
Plans:
- [ ] 02-01-PLAN.md — 命令路由基础：空气泡消除 + status 命令 + 白名单完善 + 群聊引导 (UX-02, CMD-01, CMD-02, COMPAT-02)
- [ ] 02-02-PLAN.md — 对话 UX：直接输入路由 + NextStepSuggester + 状态指示器 (UX-01, CMD-04, CMD-03, UX-03)

**UI hint:** no

---

### Phase 3: Cards & Guided Flows

**Goal:** Add interactive card buttons for project/session selection, step-by-step onboarding wizard for first-time users, and enhanced session list with context. After this phase, both command-line and visual entry points work.

**Requirements:**
- **CARD-01**: 卡片按钮选择（修复上下文缺失）
- **CARD-02**: 引导式入门向导
- **CARD-03**: 增强会话列表

**Success Criteria:**
1. 用户点击卡片上的"选择项目"按钮，系统正确识别对话上下文并执行绑定操作
2. 首次使用时显示分步卡片引导，用户通过 2-3 次点击完成绑定
3. 会话列表卡片中每个会话显示最后提示词摘要和"X分钟前"时间戳

**Dependency:** Phase 1 + Phase 2 (stable context + stable commands required).

**UI hint:** no

---

## Phase Ordering Rationale

```
Phase 1: Context Foundation
    │
    │  Correct context binding is prerequisite for all routing
    │
    ▼
Phase 2: Command Router & Conversation UX
    │
    │  Stable commands + routing required for card actions
    │
    ▼
Phase 3: Cards & Guided Flows
```

- **Phase 1 first**: Everything depends on correct context — binding migration, structured IDs, and request caching must work before any routing changes.
- **Phase 2 second**: Commands and direct typing share the routing layer. Once context is correct, routing can be safely redesigned. UX improvements (ghost bubble, status indicator) are natural companions.
- **Phase 3 last**: Cards require stable context (Phase 1) and stable commands (Phase 2) to route card button actions correctly.

## Coverage Verification

| Category | Requirements | Mapped | Coverage |
|----------|-------------|--------|----------|
| 上下文与绑定 | 5 | 5 (Phase 1) | 100% |
| 对话体验 | 3 | 3 (Phase 2) | 100% |
| 命令与路由 | 4 | 4 (Phase 2) | 100% |
| 卡片与引导 | 3 | 3 (Phase 3) | 100% |
| 兼容性 | 2 | 2 (Phase 1+2) | 100% |
| **Total** | **17** | **17** | **100%** |

---
*Roadmap created: 2026-04-07*
*Last updated: 2026-04-10 — Phase 1 Plan 02 complete (CTX-03 gap closure)*

# OpenCode Interactive Flow Redesign

## What This Is

A full redesign of the OpenCode assistant's interactive flow within the Feishu chatbot. The current flow has 6 critical broken points — context mismatch between chatId and threadId, plain text not treated as chat, ghost empty reply bubbles, chatnow not executing prompts, dual state detection confusion, and fragile session ID extraction via text parsing. This redesign replaces the broken state machine, command set, and interaction model with a clean manual-control flow: user selects project → selects/creates session → binds to topic → converses directly.

## Core Value

A user in a bound topic can type plain text and get an AI response — no command prefix, no broken context, no ghost bubbles.

## Requirements

### Validated

- ✓ WebSocket long-connection communication with Feishu — existing
- ✓ COLA 4-layer architecture (adapter → app → domain ← infrastructure) — existing
- ✓ Application plugin system (FishuAppI + AppRegistry + auto-discovery) — existing
- ✓ Strategy pattern for reply handling (Direct/Topic/Default) — existing
- ✓ Anti-corruption layer isolating Feishu SDK from domain — existing
- ✓ Message deduplication (eventId-based) — existing
- ✓ Card button interaction pipeline (P2CardActionTrigger → pseudo-Message) — existing
- ✓ Streaming card responses via SSE + CardKit API — existing
- ✓ SQLite-backed IM context binding and app session persistence — existing
- ✓ Business exception reply (MessageBizException → user-facing error) — existing
- ✓ Stateless apps: HelpApp, TimeApp, BashApp, HistoryApp — existing
- ✓ Async OpenCode task execution with dedicated thread pool — existing

### Active

- [ ] **R1: Unified state model** — Consolidate TopicState + ContextSessionState into a single state detection mechanism; eliminate redundant DB queries
- [x] **R2: Context-aware binding** — When session is bound on chatId and reply creates a new topic, automatically migrate/propagate binding to the new threadId context — *Validated in Phase 1: Context Foundation (CTX-01)*
- [x] **R3: Direct typing in bound topics** — Plain text (no `/oc` prefix) in an initialized topic is treated as a chat prompt and forwarded to OpenCode — *Validated in Phase 2: Command Router & Conversation UX (UX-01)*
- [x] **R4: Suppress empty replies** — Async task paths return null (not "") so no ghost bubble appears before streaming card — *Validated in Phase 2: Command Router & Conversation UX (UX-02)*
- [ ] **R5: chatnow executes prompt** — `/oc cn <prompt>` creates session AND forwards the prompt in one step
- [x] **R6: Manual control flow** — User manually: picks project → picks/creates session → binds to topic → converses. No auto-magic. — *Validated in Phase 2: status command, next-step suggestions, and actionable error messages complete the manual flow (CMD-01, CMD-04, CMD-03)*
- [x] **R7: Card + command dual entry** — Both interactive card buttons and typed commands work as entry points for project/session selection — *Validated in Phase 3: Cards & Guided Flows (CARD-01, CARD-02, CARD-03)*
- [x] **R8: Group→Topic conversation model** — Project/session selection happens in group main chat; conversation happens in topic threads — *Validated in Phase 2: COMPAT-02 group chat guidance directs users to topics (CMD-02)*
- [x] **R9: Clean command set** — Redesigned commands reflecting the new flow (connect/bind/unbind/status/chat etc.) — *Validated in Phase 2: status shortcut, complete alias whitelist, next-step suggestions (CMD-01, CMD-02, CMD-04)*
- [x] **R10: Robust session ID passing** — Pass session IDs as structured data (method return values, fields) instead of parsing from formatted reply text — *Validated in Phase 1: Context Foundation (CTX-02)*

### Out of Scope

- Cross-project switching within one topic — open a new topic instead; keeps mental model simple
- Migration of old topic data/bindings — old contexts silently degrade to help guidance
- Auto-detection/auto-binding of projects — user explicitly chooses (manual control principle)
- Framework-level session abstraction — only OpenCode is session-aware; lightweight, not a framework
- Webhook communication mode — WebSocket long-connection only (project iron rule)
- User-level authentication — Feishu platform identity (openId) is sufficient
- UnifiedCommand migration completion — separate concern, not blocking this redesign

## Context

**Technical environment:**
- Java 17 + Spring Boot 3.2.1 + COLA 5.0.0, 6-module Maven project
- Feishu SDK 2.5.2 for messaging, cards, WebSocket
- OpenCode server at localhost:4098 (health check: v1.2.27)
- SQLite for persistence (context bindings + app sessions)
- 309 tests passing (192 domain + 55 app + 59 infra + 3 start) — after Phase 2 completion

**Prior work:**
- IM context binding system already built (ImContextRef, ImContextBinding, two-phase binding model)
- ContextSessionOrchestrator exists but runs parallel to TopicState — needs unification
- Card button bugs fixed (dedup key, messageId null, BashApp help) — commit 7fb9855
- Biz exception reply implemented — commit 69db40b
- Comprehensive flow audit identified all 6 broken points documented in CONCERNS.md

**Known issues to address:**
- ~~Session bound to chatId but topic reply creates new threadId → binding lost~~ — Fixed in Phase 1 (CTX-01)
- ~~Plain text in bound topic shows status instead of chatting~~ — Fixed in Phase 2 (UX-01: synthesizeCommandIfNeeded)
- ~~Empty string return creates ghost reply bubbles~~ — Fixed in Phase 2 (UX-02: noReply + empty guard)
- chatnow creates session but ignores prompt content
- Dual state detection (TopicState vs ContextSessionState) causes confusion and redundant queries
- ~~Session ID extracted by parsing formatted markdown text — fragile~~ — Fixed in Phase 1 (CTX-02)

**Codebase health:**
- 80 domain files, 21 infrastructure files — domain is large
- Domain layer has 32 files importing Spring annotations (COLA violation, known, not in scope)
- Multiple DataSource instances for same SQLite DB (known, not in scope unless blocking)

## Constraints

- **Architecture**: Must follow COLA layer rules — domain defines interfaces, infrastructure implements; no app→domain reverse dependency
- **Communication**: WebSocket long-connection only — no webhook code
- **Compatibility**: BotMessageService must not call app-layer services directly
- **Compatibility**: No domain class may depend on feishu-bot-app module
- **Compatibility**: Stateless apps (Help, Time, Bash, History) must continue working unchanged
- **Data**: No migration of old binding data — old contexts degrade silently to help
- **Data**: sessionId=null is valid persisted state (two-phase binding: app context without session)
- **Scope**: Only OpenCode is session-aware — no generic session framework
- **Testing**: All 261 existing tests must continue passing

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Manual control (not auto-magic) | User explicitly picks project/session — predictable, debuggable | — Pending |
| Direct typing in bound topics | Once bound, no prefix needed — natural conversation UX | ✓ Phase 2 |
| Group chat for selection, topic for conversation | Separation of concerns: setup vs. conversation | ✓ Phase 2 |
| Card + command dual entry points | Cards for discoverability, commands for power users | ✓ Phase 3 |
| No cross-project in one topic | Keeps context clean — new topic for new project | — Pending |
| Consolidate to single state model | Eliminate TopicState vs ContextSessionState confusion | — Pending |
| Centralized next-step suggestions | NextStepSuggester service provides contextual guidance after each command | ✓ Phase 2 |
| Status indicator in reply header | 📎 opencode \| ses_xxx shows binding state in every reply | ✓ Phase 2 |
| Structured session ID passing | Eliminate fragile text parsing of session IDs | ✓ Phase 1 |
| Old contexts degrade to help | No migration complexity — clean break | ✓ Phase 1 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

**Phase 1 complete** — Context foundation built. AppExecutionResult DTO, MessageContext resolve-once pipeline, chatId→threadId binding propagation, graceful degradation for unbound topics. 280 tests passing.

**Phase 2 complete** — Command router & conversation UX. Direct typing in bound topics (synthesizeCommandIfNeeded), ghost bubble suppression (noReply + empty guard), status shortcut command, complete alias whitelist, NextStepSuggester service, status indicator (📎 opencode | ses_xxx), actionable error messages with group chat guidance. 309 tests passing.

**Phase 3 complete** — Cards & guided flows. CardContent/CardElement/CardButton domain models (IM-agnostic), FeishuCardRenderer (schema 2.0 JSON), card action context propagation (chatId/topicId/sessionId in button values), WizardManager 3-step onboarding wizard with TTL-cached state machine, UNINITIALIZED auto-trigger for first-time users, SessionInfo + sessions card with last-prompt summaries and relative timestamps, HelpApp migrated from hand-written JSON to CardContent+CardRenderer. 462 tests passing.

---
*Last updated: 2026-04-26 after Phase 3 completion*

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
- [ ] **R2: Context-aware binding** — When session is bound on chatId and reply creates a new topic, automatically migrate/propagate binding to the new threadId context
- [ ] **R3: Direct typing in bound topics** — Plain text (no `/oc` prefix) in an initialized topic is treated as a chat prompt and forwarded to OpenCode
- [ ] **R4: Suppress empty replies** — Async task paths return null (not "") so no ghost bubble appears before streaming card
- [ ] **R5: chatnow executes prompt** — `/oc cn <prompt>` creates session AND forwards the prompt in one step
- [ ] **R6: Manual control flow** — User manually: picks project → picks/creates session → binds to topic → converses. No auto-magic.
- [ ] **R7: Card + command dual entry** — Both interactive card buttons and typed commands work as entry points for project/session selection
- [ ] **R8: Group→Topic conversation model** — Project/session selection happens in group main chat; conversation happens in topic threads
- [ ] **R9: Clean command set** — Redesigned commands reflecting the new flow (connect/bind/unbind/status/chat etc.)
- [ ] **R10: Robust session ID passing** — Pass session IDs as structured data (method return values, fields) instead of parsing from formatted reply text

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
- 261 tests passing (163 domain + 37 app + 58 infra + 3 start)

**Prior work:**
- IM context binding system already built (ImContextRef, ImContextBinding, two-phase binding model)
- ContextSessionOrchestrator exists but runs parallel to TopicState — needs unification
- Card button bugs fixed (dedup key, messageId null, BashApp help) — commit 7fb9855
- Biz exception reply implemented — commit 69db40b
- Comprehensive flow audit identified all 6 broken points documented in CONCERNS.md

**Known issues to address:**
- Session bound to chatId but topic reply creates new threadId → binding lost
- Plain text in bound topic shows status instead of chatting
- Empty string return creates ghost reply bubbles
- chatnow creates session but ignores prompt content
- Dual state detection (TopicState vs ContextSessionState) causes confusion and redundant queries
- Session ID extracted by parsing formatted markdown text — fragile

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
| Direct typing in bound topics | Once bound, no prefix needed — natural conversation UX | — Pending |
| Group chat for selection, topic for conversation | Separation of concerns: setup vs. conversation | — Pending |
| Card + command dual entry points | Cards for discoverability, commands for power users | — Pending |
| No cross-project in one topic | Keeps context clean — new topic for new project | — Pending |
| Consolidate to single state model | Eliminate TopicState vs ContextSessionState confusion | — Pending |
| Structured session ID passing | Eliminate fragile text parsing of session IDs | — Pending |
| Old contexts degrade to help | No migration complexity — clean break | — Pending |

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

---
*Last updated: 2026-04-06 after initialization*

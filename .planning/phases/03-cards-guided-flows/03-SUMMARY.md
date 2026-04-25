---
phase: 03-cards-guided-flows
plan: "01"
subsystem: opencode-cards
tags: [cards, wizard, sessions, interactive, domain-model, gateway]
dependency_graph:
  requires: [02-01, 02-02]
  provides: [card-domain-model, feishu-card-renderer, wizard-flow, sessions-card]
  affects: [opencode-app, help-app, message-listener]
tech_stack:
  added:
    - CardContent / CardElement / CardButton domain models
    - CardRenderer gateway interface (domain)
    - FeishuCardRenderer (infrastructure, schema 2.0 JSON)
    - WizardManager (ConcurrentHashMap + ScheduledExecutorService TTL)
    - SessionInfo value object
  patterns:
    - Handler-sends-card + noReply() (card bypass text pipeline)
    - Prefix-matching CommandWhitelist for wizard_* actions
    - listRecentSessionsStructured() → delegate pattern
key_files:
  created:
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardContent.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardElement.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardButton.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardActionContext.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/StreamingCardManager.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/CardRenderer.java
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card/FeishuCardRenderer.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/WizardManager.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/SessionInfo.java
  modified:
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java (cardToken field)
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeGateway.java (listRecentSessionsStructured)
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java (wizard + sessions card)
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java (whitelist + wizard_*)
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/CommandWhitelist.java (prefix matching)
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java (handleCardAction + cardToken)
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java (listRecentSessionsStructured + delegate)
    - APP_USAGE_GUIDE.md
    - AGENTS.md
decisions:
  - "Card send pattern: handler calls feishuGateway.sendInteractiveMessage() + returns AppExecutionResult.noReply() (bypasses text pipeline)"
  - "WizardManager uses ConcurrentHashMap + ScheduledExecutorService (no Caffeine) with per-entry createdAt TTL"
  - "CommandWhitelist uses prefix-match for wizard_* actions (entry + ':' prefix)"
  - "listRecentSessions() delegates to listRecentSessionsStructured() — single HTTP call source"
  - "CardActionContext.from(MessageContext) extracts chatId/topicId/sessionId for card button values"
  - "Message.cardToken stores event.header.eventId from handleCardAction"
metrics:
  duration: "~75 minutes"
  completed_date: "2026-04-25"
  tasks_completed: 5
  files_changed: 18
---

# Phase 03 Plan 01: Cards-Guided-Flows Summary

**One-liner**: Interactive card infrastructure (CardContent/FeishuCardRenderer), 3-step wizard state machine, and card-first sessions list for OpenCode.

## What Was Built

### Task 1 (commit `3debb7a`)
- **CardContent / CardElement / CardButton** — IM-agnostic domain models with builder API
- **CardActionContext** — context embedding (chatId/topicId/sessionId) in button values
- **CardRenderer** gateway interface in domain; **FeishuCardRenderer** in infrastructure (schema 2.0 JSON)
- **HelpApp** migrated from hand-written JSON map to CardContent + CardRenderer
- **StreamingCardManager** — streaming card state machine (cardId, sequence, TTL)
- 15 tests (CardContentTest, FeishuCardRendererTest, HelpAppCardButtonJsonTest)

### Task 2 (commit `37bfbc7`)
- **Message.cardToken** field added (with `withContent()` copy)
- **MessageListenerGatewayImpl.handleCardAction()** enhanced:
  - Extracts chatId/topicId from `CardActionContext.fromValueMap()` (primary) with fallback to `event.context.openChatId`
  - Extracts cardToken from `event.header.eventId`
- 7 CardActionContextTest tests

### Task 3 (commit `ac824ba`)
- **WizardManager** — 3-step wizard state machine (`SELECT_PROJECT → SELECT_SESSION → CONFIRM → COMPLETED`)
  - ConcurrentHashMap + daemon ScheduledExecutorService for 10-minute TTL
  - `compute()` for atomic state transitions
  - `isWizardActive()`, `start()`, `handleAction()`, `clearWizard()`
- **OpenCodeCommandHandler** updated:
  - 3 new constructor args: `CardRenderer`, `FeishuGateway`, `WizardManager`
  - Wizard interception: non-wizard commands blocked when wizard active
  - `isWizardAction()` prefix check; `handleWizardAction()` delegates to WizardManager
- **OpenCodeApp.getCommandWhitelist()** UNINITIALIZED state includes `wizard_*` actions
- **CommandWhitelist.isCommandAllowedInState()** supports prefix matching (`entry + ":"`)
- 13 WizardManagerTest + 3 new handler tests + whitelist test
- Fixed `OpenCodeExplicitInitializationTest` to use new 9-arg constructor

### Task 4 (commit `5513d30`)
- **SessionInfo** value object (`sessionId`, `title`, `lastPrompt`, `relativeTime`, `projectName`)
- **OpenCodeGateway.listRecentSessionsStructured()** new interface method
- **OpenCodeGatewayImpl**: `parseSessionsStructured()` + `formatSessionsAsText()`; `listRecentSessions()` refactored to delegate
- **OpenCodeCommandHandler.handleSessionsCommand()** — card-first in thread context, text fallback
- **trySendSessionListCard()** — per-session bind buttons + `wizard_new_session:{project}` bottom button
- 4 new tests: card send + fallback + new-session button capture + non-topic

### Task 5 (commit `9ba1c92`)
- Full regression: **349 tests pass** (Domain 227 + App 55 + Infra 64 + Start 3), 1 pre-existing skip
- All `@Deprecated` methods verified — still have callers, none to delete
- **APP_USAGE_GUIDE.md**: updated sessions section (interactive card desc), added wizard step-by-step section, FAQ Q4 + Q6
- **AGENTS.md**: added `card/` directory, `opencode/` subsection with WizardManager + SessionInfo

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] OpenCodeExplicitInitializationTest used old 6-arg constructor**
- **Found during**: Task 3 compile check
- **Issue**: `OpenCodeExplicitInitializationTest` called `new OpenCodeCommandHandler(6 args)` but constructor now requires 9 args
- **Fix**: Added `CardRenderer`, `FeishuGateway`, `WizardManager` mocks; updated constructor call
- **Files modified**: `OpenCodeExplicitInitializationTest.java`
- **Commit**: ac824ba

**2. [Rule 2 - Missing] CommandWhitelist only did exact match; wizard_select_project:feishu-backend would not match wizard_select_project**
- **Found during**: Task 3 code review
- **Issue**: Card button actions arrive as `wizard_select_project:feishu-backend` but whitelist stored `wizard_select_project`
- **Fix**: Added prefix-match logic in `isCommandAllowedInState()` — if `allowedSet` contains `entry`, also allow `entry:suffix`
- **Files modified**: `CommandWhitelist.java`, `OpenCodeAppTest.java`
- **Commit**: ac824ba

## Test Coverage

| Test class | Tests | New in Phase 3 |
|-----------|-------|---------------|
| CardContentTest | 7 | 7 |
| CardActionContextTest | 7 | 7 |
| StreamingCardManagerTest | 10 | 10 |
| WizardManagerTest | 13 | 13 |
| OpenCodeCommandHandlerTest | 34 | 7 (wizard×3 + sessions×4) |
| OpenCodeAppTest | 24 | 5 (whitelist) |
| FeishuCardRendererTest | 5 | 5 |
| HelpAppCardButtonJsonTest | 3 | 3 |
| **Total new** | **57** | |
| **Grand total** | **349** | |

## Self-Check: PASSED

### Files exist:
- `feishu-bot-domain/.../card/CardContent.java` ✅
- `feishu-bot-domain/.../card/CardButton.java` ✅
- `feishu-bot-domain/.../card/CardActionContext.java` ✅
- `feishu-bot-domain/.../gateway/CardRenderer.java` ✅
- `feishu-bot-infrastructure/.../card/FeishuCardRenderer.java` ✅
- `feishu-bot-domain/.../opencode/WizardManager.java` ✅
- `feishu-bot-domain/.../opencode/SessionInfo.java` ✅

### Commits exist:
- `3debb7a` feat(03-01) ✅
- `37bfbc7` feat(03-02) ✅
- `ac824ba` feat(03-03) ✅
- `5513d30` feat(03-04) ✅
- `9ba1c92` docs(03-05) ✅

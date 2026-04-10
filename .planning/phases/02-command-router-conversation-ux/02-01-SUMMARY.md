---
phase: 02-command-router-conversation-ux
plan: 01
subsystem: command-routing
tags: [opencode, whitelist, empty-reply-guard, status-command, group-guidance]

# Dependency graph
requires:
  - phase: 01-context-foundation
    provides: AppExecutionResult.noReply(), MessageContext resolve-once pipeline, CommandWhitelist
provides:
  - Empty string reply guard in BotMessageAppService.sendReply()
  - /oc status shortcut command (session status quick view)
  - Complete whitelist aliases for NON_TOPIC and UNINITIALIZED states
  - Enhanced group chat guidance in NON_TOPIC restriction messages
affects: [02-02-PLAN, direct-input-routing, next-step-suggester, status-indicator]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Empty/blank string guard at reply layer prevents ghost bubbles from any app"
    - "Status shortcut delegates to sessionManager.getCurrentSessionStatus()"

key-files:
  created: []
  modified:
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/topic/TopicCommandValidator.java
    - feishu-bot-app/src/test/java/com/qdw/feishu/app/message/BotMessageAppServiceTest.java
    - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandlerTest.java
    - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeAppTest.java
    - APP_USAGE_GUIDE.md

key-decisions:
  - "Empty guard uses trim().isEmpty() to catch both empty and whitespace-only strings"
  - "status command is a direct shortcut to sessionManager.getCurrentSessionStatus() — no new state logic"
  - "NON_TOPIC message rewritten to emphasize /oc cn as primary action path"

patterns-established:
  - "Reply guard: null OR trim-empty → skip reply at BotMessageAppService layer"
  - "Shortcut commands: delegate to existing sessionManager methods, no domain logic duplication"

requirements-completed: [CMD-01, CMD-02, UX-02, COMPAT-02]

# Metrics
duration: 21min
completed: 2026-04-10
---

# Phase 2 Plan 01: Command Router Foundations Summary

**Empty reply guard + `/oc status` shortcut + complete whitelist aliases + group chat guidance with /oc cn examples**

## Performance

- **Duration:** 21 min
- **Started:** 2026-04-10T16:28:07Z
- **Completed:** 2026-04-10T16:49:00Z
- **Tasks:** 2 (both TDD: RED → GREEN)
- **Files modified:** 8

## Accomplishments
- Ghost bubble prevention: BotMessageAppService.sendReply() guards against null, empty, and blank strings
- New `/oc status` shortcut command — works in INITIALIZED and UNINITIALIZED states
- Complete whitelist coverage: NON_TOPIC now includes `new`, UNINITIALIZED includes `status` + `new`
- Group chat guidance rewritten: NON_TOPIC restriction messages show `/oc cn <问题>` as recommended action
- 286 tests passing (280 baseline + 6 new), 0 failures

## Task Commits

Each task was committed atomically with TDD (RED → GREEN):

1. **Task 1: 空気泡消除 + 空字符串防護** - `26ddba8` (test: RED) → `e9ede3b` (feat: GREEN)
2. **Task 2: status 命令 + 白名単完善 + 群聊引導** - `99c7ec3` (test: RED) → `1e045e5` (feat: GREEN)

## Files Created/Modified
- `BotMessageAppService.java` — Added trim().isEmpty() guard in sendReply()
- `OpenCodeCommandHandler.java` — Added `case "status"` routing to sessionManager.getCurrentSessionStatus()
- `OpenCodeApp.java` — Updated NON_TOPIC whitelist (+new), UNINITIALIZED whitelist (+status, +new), help text (+status)
- `TopicCommandValidator.java` — Rewrote NON_TOPIC restriction message with /oc cn guidance
- `BotMessageAppServiceTest.java` — 2 new tests for empty/blank string suppression
- `OpenCodeCommandHandlerTest.java` — 2 new tests for status command in INITIALIZED/UNINITIALIZED
- `OpenCodeAppTest.java` — 4 new tests for whitelist completeness (NON_TOPIC+new, UNINITIALIZED+status+new)
- `APP_USAGE_GUIDE.md` — Synced command permissions, added status command docs

## Decisions Made
- **Empty guard scope:** Applied at BotMessageAppService (app layer) rather than per-app, so ALL apps benefit from protection. Uses `trim().isEmpty()` to catch whitespace-only strings.
- **Status is a shortcut, not new logic:** `status` delegates directly to `sessionManager.getCurrentSessionStatus(messageContext)` — same as `session status` but without needing to type the intermediate `session` keyword.
- **NON_TOPIC guidance redesign:** Old message was generic ("命令只能在话题中使用"). New message is action-oriented with specific commands the user can run right now.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered
- Mockito strict stubbing flagged unnecessary `getReplyMode()` stubs in new empty-string tests — stubs were never called because the empty guard short-circuits before strategy selection. Removed the unnecessary stubs.
- Maven cross-module compilation required explicit `mvn install -pl feishu-bot-domain` before running app tests in isolation.

## User Setup Required
None — no external service configuration required.

## Known Stubs
None — all changes are fully wired.

## Next Phase Readiness
- Command routing foundation ready for Plan 02 (direct input routing, next-step suggestions, status indicators)
- Empty guard ensures async paths won't produce ghost bubbles regardless of future changes
- Whitelist is now complete with all aliases — Plan 02 can add direct-input routing without whitelist gaps

---
*Phase: 02-command-router-conversation-ux*
*Completed: 2026-04-10*

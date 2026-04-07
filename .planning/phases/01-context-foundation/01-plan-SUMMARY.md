---
phase: 01-context-foundation
plan: 01
subsystem: api
tags: [cola, messagecontext, sqlite-upsert, session-id, binding-propagation, strategy-pattern]

# Dependency graph
requires: []
provides:
  - AppExecutionResult DTO replacing raw String from FishuAppI.execute()
  - MessageContext resolve-once pattern threaded through full pipeline
  - Structured openCodeSessionId in AppExecutionResult (eliminates text parsing)
  - Atomic INSERT ON CONFLICT DO UPDATE binding upsert
  - chatId→threadId binding propagation
  - Graceful degradation for unbound topic contexts
  - 8 new behavioral invariant tests
affects: [02-command-router-ux, 03-cards-guided-flows]

# Tech tracking
tech-stack:
  added: []
  patterns: [resolve-once-thread-everywhere, structured-result-DTO, atomic-upsert, graceful-degradation]

key-files:
  created:
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/AppExecutionResult.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/MessageContext.java
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/context/MessageContextResolver.java
    - feishu-bot-app/src/test/java/com/qdw/feishu/app/context/MessageContextResolverTest.java
  modified:
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java

key-decisions:
  - "AppExecutionResult.text() for simple apps, withSession() for OpenCode — clean separation"
  - "MessageContext resolved once at pipeline entry, threaded as parameter (no ThreadLocal/RequestScope)"
  - "Tasks 2+3 merged into single commit — extractSessionId elimination coupled with MessageContext plumbing"
  - "Binding duplication strategy: chat binding duplicated to thread, not migrated (safer for Phase 1)"
  - "INSERT ON CONFLICT DO UPDATE for atomic upsert, preserving created_at on update"
  - "Graceful degradation checks isThreadContext() and !isExplicitOpenCodeCommand — explicit commands still work in old topics"

patterns-established:
  - "Resolve-once-thread-everywhere: MessageContextResolver resolves IM context once, MessageContext threaded through full chain"
  - "Structured result DTO: FishuAppI.execute() returns AppExecutionResult instead of raw String"
  - "Atomic upsert: INSERT ON CONFLICT DO UPDATE in SQLite binding gateway"
  - "Backward-compatible deprecation: old Message-based overloads marked @Deprecated, new MessageContext overloads added alongside"

requirements-completed: [CTX-01, CTX-02, CTX-03, CTX-04, CTX-05, COMPAT-01]

# Metrics
duration: 143min
completed: 2026-04-07
---

# Phase 1 Plan 1: Context Foundation Summary

**MessageContext resolve-once pipeline with structured AppExecutionResult, atomic binding upsert, chatId→threadId propagation, and graceful degradation for unbound topics — 269 tests passing (8 new behavioral invariants)**

## Performance

- **Duration:** 143 min (~2h 23m)
- **Started:** 2026-04-07T13:10:00Z
- **Completed:** 2026-04-07T15:43:26Z
- **Tasks:** 7 completed (1A, 1B, 2, 3, 4, 5, 6)
- **Files modified:** 20+ source + test files across domain, app, infrastructure, start modules

## Accomplishments

- **AppExecutionResult DTO** replacing raw `String` from `FishuAppI.execute()` — carries structured `openCodeSessionId` and `replyContent` fields, with factory methods `text()`, `noReply()`, `withSession()`
- **MessageContext resolve-once pipeline** — `MessageContextResolver` calls `findBinding()` exactly once at pipeline entry; `MessageContext` threaded through `ReceiveMessageListenerExe` → `OpenCodeMessageAppService` → `BotMessageAppService` → `BotMessageService`
- **Eliminated `extractSessionId()` text parsing** — session IDs now flow structurally via `AppExecutionResult.openCodeSessionId`, never parsed from markdown
- **Atomic binding upsert** — `INSERT ... ON CONFLICT DO UPDATE` in SQLite replaces vulnerable SELECT→INSERT/UPDATE pattern
- **chatId→threadId binding propagation** — when reply creates new thread, full binding (appId + sessionId) copied to thread context
- **Graceful degradation** — unbound threads show guidance text instead of errors; explicit commands still work
- **8 new behavioral invariant tests** covering single-lookup, session ID boundary, thread propagation, unresolved context, and concurrent upsert safety

## Task Commits

Each task was committed atomically:

1. **Task 1A: AppExecutionResult DTO + FishuAppI return type** — `f70c047` (feat)
2. **Task 1B: Wire AppExecutionResult into OpenCode chain** — `52f5f0e` (feat)
3. **Tasks 2+3: MessageContext pipeline + eliminate extractSessionId** — `c5955d4` (feat)
4. **Task 4: Atomic upsert + binding propagation** — `d4e7b87` (feat)
5. **Task 5: Graceful degradation** — `538ae47` (feat)
6. **Task 6: Behavioral invariant tests + COMPAT-01** — `c312509` (test)

## Files Created/Modified

### Created
- `feishu-bot-domain/.../app/AppExecutionResult.java` — Structured execution result DTO
- `feishu-bot-domain/.../model/MessageContext.java` — Request-scoped context (contextRef + binding)
- `feishu-bot-app/.../context/MessageContextResolver.java` — Single-point context resolution
- `feishu-bot-app/.../context/MessageContextResolverTest.java` — Tests A, D + 2 additional

### Modified (source)
- `feishu-bot-domain/.../app/FishuAppI.java` — `execute()` returns `AppExecutionResult`
- `feishu-bot-domain/.../app/BashApp.java`, `TimeApp.java`, `HelpApp.java`, `HistoryApp.java` — Updated return type
- `feishu-bot-domain/.../opencode/OpenCodeApp.java` — Delegates to handler's `AppExecutionResult`
- `feishu-bot-domain/.../opencode/OpenCodeCommandHandler.java` — `handle()` returns `AppExecutionResult`
- `feishu-bot-domain/.../opencode/OpenCodeTaskExecutor.java` — All methods return `AppExecutionResult`
- `feishu-bot-domain/.../message/HandledMessageResult.java` — Carries `AppExecutionResult`
- `feishu-bot-domain/.../service/BotMessageService.java` — `routeMessage(msg, ctx)` overload
- `feishu-bot-domain/.../router/AppRouter.java` — Updated for `AppExecutionResult`
- `feishu-bot-app/.../listener/ReceiveMessageListenerExe.java` — Injects `MessageContextResolver`, resolves once
- `feishu-bot-app/.../message/BotMessageAppService.java` — `handleMessage(msg, ctx)` overload, binding propagation
- `feishu-bot-app/.../opencode/OpenCodeMessageAppService.java` — `tryHandle(msg, ctx)`, `buildStatusFromContext()`, graceful degradation, `progressSessionIfNeeded()` uses structured result
- `feishu-bot-infrastructure/.../gateway/ImContextBindingGatewayImpl.java` — Atomic upsert

### Modified (tests)
- `BashAppTest`, `OpenCodeAppTest`, `OpenCodeCommandHandlerTest`, `OpenCodeExplicitInitializationTest` — `AppExecutionResult` return type
- `BotMessageServiceTest` — `MessageContext` parameter
- `BotMessageAppServiceTest` — `routeMessage(msg, ctx)` stubs, Test C (thread propagation)
- `OpenCodeMessageAppServiceTest` — `AppExecutionResult.withSession()` stubs, Test B, graceful degradation test
- `ReceiveMessageListenerExeTest` — `MessageContextResolver` mock, lenient stubbing
- `ImContextBindingGatewayImplTest` — Test E (concurrent upsert)
- `HelpAppCardButtonJsonTest` — Fixed anonymous `FishuAppI` return type

## Decisions Made

1. **Tasks 2+3 merged** — `extractSessionId()` elimination was tightly coupled with `MessageContext` plumbing since both modify `OpenCodeMessageAppService.progressSessionIfNeeded()`. Single commit avoids inconsistent intermediate state.
2. **Binding duplication, not migration** — Chat binding is duplicated to new thread, original left to become stale. Safer than delete+create which adds failure modes.
3. **Graceful degradation guards explicit commands** — `!isExplicitOpenCodeCommand(message)` check ensures `/opencode projects` still works in old unbound topics while implicit text gets guidance.
4. **No ThreadLocal or RequestScope** — `MessageContext` passed as method parameter, keeping the code explicit and testable.
5. **Backward-compatible deprecation** — Old `handleMessage(Message)`, `tryHandle(Message)`, `supports(Message)`, `routeMessage(Message)` methods kept as `@Deprecated` delegating to new overloads.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] HelpAppCardButtonJsonTest anonymous FishuAppI**
- **Found during:** Task 4 (compilation failure in start module)
- **Issue:** Anonymous `FishuAppI` implementation in test returned `String` instead of `AppExecutionResult` — missed during Task 1A since `feishu-bot-start` tests were not compiled standalone
- **Fix:** Changed return type to `AppExecutionResult.text(null)`
- **Files modified:** `HelpAppCardButtonJsonTest.java`
- **Committed in:** `d4e7b87` (part of Task 4 commit)

**2. [Rule 3 - Blocking] Maven module compilation order for new domain classes**
- **Found during:** Task 2 (Mockito "cannot mock" error in app tests)
- **Issue:** `MessageContext.java` created in domain was not installed to local Maven repo, causing app module test compilation to fail. Surfaced as Mockito error masking a classpath issue.
- **Fix:** `mvn install -pl feishu-bot-domain -DskipTests` before running app tests; full `mvn test` (reactor build) handles this automatically.
- **Files modified:** None (build process understanding)
- **Committed in:** N/A (build process, not code change)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both necessary for correctness. No scope creep.

## Issues Encountered

- **Mockito "Byte Buddy could not instrument" false alarm** — Initially appeared as 14 test failures in `OpenCodeMessageAppServiceTest`. Root cause was Maven module compilation order (domain jar not rebuilt with new `MessageContext` class). Full reactor build resolved it. Spent ~15 min debugging before identifying the root cause.

## Known Stubs

None — all data paths are wired end-to-end. No placeholder values, TODO markers, or unconnected data sources.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **Context foundation complete**: MessageContext resolve-once pattern, structured session IDs, atomic binding, graceful degradation all working
- **269 tests passing** (8 new behavioral invariants ensuring no regressions)
- **Phase 2 can proceed**: correct context binding is now reliable for routing redesign (CMD-01 through CMD-04, UX-01 through UX-03)
- **No blockers**: all 6 requirements (CTX-01 through CTX-05 + COMPAT-01) satisfied

---
*Phase: 01-context-foundation*
*Completed: 2026-04-07*

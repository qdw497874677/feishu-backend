---
phase: 02-command-router-conversation-ux
plan: 02
subsystem: conversation-ux
tags: [opencode, direct-input, next-step-suggestion, status-indicator, text-synthesis]

# Dependency graph
requires:
  - phase: 02-command-router-conversation-ux/01
    provides: Empty reply guard, status command, complete whitelists, group chat guidance
  - phase: 01-context-foundation
    provides: MessageContext resolve-once pipeline, TopicState detection, ImContextBinding
provides:
  - Plain text synthesis in initialized topics (UX-01) — direct typing without /oc chat prefix
  - NextStepSuggester @Component with context-aware command suggestions (CMD-04)
  - Actionable error messages integrated via NextStepSuggester (CMD-03)
  - Status indicator prepended to OpenCode replies (UX-03) — 📎 opencode | ses_xxx
affects: [03-cards-guided-flows, card-actions, onboarding-wizard]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Text synthesis at app layer: OpenCodeMessageAppService rewrites plain text to /opencode chat before routing"
    - "Centralized suggestion service: NextStepSuggester.suggest(command, state, context) appended in OpenCodeCommandHandler"
    - "Status indicator at app layer: BotMessageAppService prepends 📎 line for OpenCode replies only"

key-files:
  created:
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/NextStepSuggester.java
    - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/NextStepSuggesterTest.java
  modified:
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java
    - feishu-bot-app/src/test/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppServiceTest.java
    - feishu-bot-app/src/test/java/com/qdw/feishu/app/message/BotMessageAppServiceTest.java
    - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandlerTest.java
    - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeExplicitInitializationTest.java
    - APP_USAGE_GUIDE.md

key-decisions:
  - "Plain text synthesis at app layer (OpenCodeMessageAppService) keeps domain routing pure"
  - "NextStepSuggester injected into OpenCodeCommandHandler — suggestions appended after command execution"
  - "Status indicator at BotMessageAppService layer — only OpenCode replies get 📎 line, non-OpenCode apps unaffected"
  - "chat/chatnow/cn excluded from suggestions — user is already in conversation flow"
  - "help command excluded from status indicator — help text is already long enough"

patterns-established:
  - "Text synthesis: app-layer preprocessing rewrites message content before routing to domain"
  - "Suggestion lifecycle: command → result → suggest() → append → return enhanced result"
  - "Status prepend: check appId + context binding → build 📎 line → prepend to reply content"

requirements-completed: [UX-01, CMD-04, CMD-03, UX-03]

# Metrics
duration: ~35min
completed: 2026-04-10
---

# Phase 2 Plan 02: Conversation UX Summary

**Plain text synthesis routes direct typing to OpenCode chat, NextStepSuggester appends context-aware command suggestions, and 📎 status indicator shows active binding in every OpenCode reply**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-04-10T17:00:00Z
- **Completed:** 2026-04-10T17:35:00Z
- **Tasks:** 3 (all TDD: RED → GREEN)
- **Files modified:** 10

## Accomplishments
- Direct typing in initialized topics: plain text "帮我写代码" auto-synthesized to `/opencode chat 帮我写代码` and routed correctly
- NextStepSuggester service: centralized suggestion engine covers all commands (projects→sessions→sc→chat flow)
- Status indicator: every OpenCode reply starts with `📎 opencode | ses_xxx` showing active binding
- Actionable error context: unknown commands and state-restricted operations include next-step suggestions
- 309 tests passing (286 baseline + 23 new), 0 failures

## Task Commits

Each task was committed atomically with TDD (RED → GREEN):

1. **Task 1: Direct input routing — plain text synthesis (UX-01)** - `39aba49` (test: RED) → `d81ed49` (feat: GREEN)
2. **Task 2: NextStepSuggester + actionable errors (CMD-04, CMD-03)** - `60ba60f` (test: RED) → `7f3e733` (feat: GREEN)
3. **Task 3: Status indicator (UX-03)** - `b2e44d5` (test: RED) → `9e8e423` (feat: GREEN)

## Files Created/Modified

### Created
- `NextStepSuggester.java` — @Component with `suggest(command, state, context)` returning context-aware suggestions per command
- `NextStepSuggesterTest.java` — 12 unit tests covering all command→suggestion mappings

### Modified (Production)
- `OpenCodeMessageAppService.java` — Added `synthesizeCommandIfNeeded()`: rewrites plain text to `/opencode chat <text>` in INITIALIZED topics
- `BotMessageAppService.java` — Added `prependStatusIndicator()` + `buildStatusLine()` + injected `OpenCodeSessionManager`
- `OpenCodeCommandHandler.java` — Injected `NextStepSuggester`, added `appendNextStepSuggestion()` after command execution

### Modified (Tests)
- `OpenCodeMessageAppServiceTest.java` — 4 new tests: synthesis in initialized topic, no-synthesis for uninitialized/non-topic/explicit-command
- `BotMessageAppServiceTest.java` — 5 new tests: status line present/absent, unbound status, non-OpenCode exclusion, null reply exclusion
- `OpenCodeCommandHandlerTest.java` — 2 new integration tests + updated existing assertEquals to startsWith for suggestion-appended results
- `OpenCodeExplicitInitializationTest.java` — Constructor updated for NextStepSuggester parameter

### Modified (Docs)
- `APP_USAGE_GUIDE.md` — Documented direct text input, next-step suggestions, status indicator UX enhancements

## Decisions Made

1. **Text synthesis at app layer:** `synthesizeCommandIfNeeded()` lives in `OpenCodeMessageAppService` (app layer), not in domain. This keeps the domain `OpenCodeCommandHandler` switch-case routing pure — it only sees well-formed commands.

2. **NextStepSuggester injection point:** Injected into `OpenCodeCommandHandler` rather than `BotMessageAppService`. Suggestions are command-specific domain knowledge, so they belong in the domain layer close to command handling.

3. **Status indicator at app layer:** `prependStatusIndicator()` in `BotMessageAppService` checks `app.getAppId().equals("opencode")` — clean separation means non-OpenCode apps are never affected. The app layer is the right place since it orchestrates reply assembly.

4. **chat/chatnow/cn excluded from suggestions:** When the user is already in the conversation flow, "next step" suggestions are noise. These commands return null from `NextStepSuggester.suggest()`.

5. **help command excluded from status indicator:** Help text is already comprehensive. Adding a status line would add visual clutter without value.

6. **Existing test migration to startsWith:** After NextStepSuggester integration, existing tests using `assertEquals(exactString, result.getReplyContent())` broke because suggestions are appended. Changed to `assertTrue(result.startsWith(...))` — this preserves the intent (correct command output) while accommodating the new suffix.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] OpenCodeExplicitInitializationTest constructor mismatch**
- **Found during:** Task 2 (NextStepSuggester injection into OpenCodeCommandHandler)
- **Issue:** `OpenCodeExplicitInitializationTest` manually constructs `OpenCodeCommandHandler` with 3 args; adding `NextStepSuggester` as 4th constructor param broke compilation
- **Fix:** Updated test constructor call to include `mock(NextStepSuggester.class)` as 4th argument
- **Files modified:** `OpenCodeExplicitInitializationTest.java`
- **Committed in:** `7f3e733` (Task 2 GREEN commit)

**2. [Rule 1 - Bug] Existing assertEquals tests broke after suggestion appending**
- **Found during:** Task 2 (NextStepSuggester integration)
- **Issue:** Tests like `should_returnProjectList_when_projectsCommand` used `assertEquals(exact, result.getReplyContent())` but NextStepSuggester now appends `\n\n---\n💡 下一步：...` to the reply
- **Fix:** Changed to `assertTrue(result.getReplyContent().startsWith(expectedPrefix))` — preserves verification of command output while accommodating the appended suggestion
- **Files modified:** `OpenCodeCommandHandlerTest.java`
- **Committed in:** `7f3e733` (Task 2 GREEN commit)

**3. [Rule 3 - Blocking] BotMessageAppService constructor needed OpenCodeSessionManager**
- **Found during:** Task 3 (Status indicator)
- **Issue:** `BotMessageAppService` had no `OpenCodeSessionManager` dependency; needed injection for `getSessionId(messageContext)` to display ses_xxx in status line
- **Fix:** Added constructor parameter + field. COLA compliant (app → domain dependency)
- **Files modified:** `BotMessageAppService.java`, `BotMessageAppServiceTest.java`
- **Committed in:** `9e8e423` (Task 3 GREEN commit)

---

**Total deviations:** 3 auto-fixed (1 bug fix, 2 blocking fixes)
**Impact on plan:** All auto-fixes necessary for correctness and compilation. No scope creep.

## Issues Encountered
- Mockito strict stubbing caught unnecessary `openCodeApp.getAppId()/getAppAliases()` stubs in synthesis tests — the `handleMessage(Message, MessageContext)` path doesn't call `isExplicitOpenCodeCommand()` so those stubs were never invoked. Removed them.
- `buildStatusLine()` uses `openCodeSessionManager.getSessionId(messageContext)` which internally calls `contextSessionOrchestrator.loadStatus()` — tests needed correct Mockito generic matchers for `TypeToken<OpenCodeSessionData>`.

## User Setup Required
None — no external service configuration required.

## Known Stubs
None — all changes are fully wired with real data flows.

## Next Phase Readiness
- Phase 2 is now fully complete (Plan 01 + Plan 02). All 8 requirements delivered.
- End-to-end conversation UX works: direct typing → AI response with status indicator and next-step suggestions
- Phase 3 (Cards & Guided Flows) can build on the stable command routing and context infrastructure
- Card button actions can leverage the same `MessageContext` pipeline for context-aware operations
- The NextStepSuggester pattern can be extended for card-based guided flows

## Self-Check: PASSED

- All 6 key files exist on disk
- All 6 commit hashes verified in git log
- 309 tests passing (192 domain + 55 app + 59 infra + 3 start), 0 failures
- BUILD SUCCESS

---
*Phase: 02-command-router-conversation-ux*
*Completed: 2026-04-10*

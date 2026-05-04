---
phase: 04-code-cleanup-and-refactoring
plan: 01
subsystem: [security, infra]
tags: [spring-profile, log-sanitization, sqlite, datasource, optimistic-lock]

requires:
  - phase: 03-cards-guided-flows
    provides: domain model, app layer, infrastructure gateways
provides:
  - Production-safe test controller (@Profile without "default")
  - DEBUG-level message content logging across all app/domain/infra layers
  - Production Spring profile (application-prod.yml)
  - Shared SQLite DataSource bean (SQLiteConfig)
  - Optimistic lock without redundant SELECT (updateSession, updateState)
affects: [04-02, 04-03, 04-04]

tech-stack:
  added: []
  patterns: [shared-datasource-bean, debug-only-sensitive-logging, single-sql-optimistic-lock]

key-files:
  created:
    - feishu-bot-start/src/main/resources/application-prod.yml
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/SQLiteConfig.java
  modified:
    - feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java
    - feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java
    - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HistoryApp.java
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java
    - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java

key-decisions:
  - "Single constructor for gateways (DataSource injection) instead of dual constructors — tests create their own DataSource inline"
  - "Kept getState() read in updateState() for state transition validation; embedded version check in UPDATE WHERE"

patterns-established:
  - "Shared DataSource bean: SQLiteConfig provides sqliteDataSource, gateways inject via @Qualifier"
  - "Sensitive data at DEBUG: message content, reply content, user prompts logged only at DEBUG; messageId at INFO for traceability"

requirements-completed: [V2-03]

duration: 8min
completed: 2026-05-04
---

# Phase 04: Code Cleanup Summary — Plan 01

**Security hardening (profile-restricted test controller, DEBUG-only sensitive logs) and data integrity (shared SQLite DataSource, single-SQL optimistic lock)**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-04T14:25:00Z
- **Completed:** 2026-05-04T14:36:37Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments
- MessageTestController no longer active without explicit Spring profile — production-safe
- All message content logging downgraded to DEBUG across 8 files; messageId at INFO for traceability
- Production profile (application-prod.yml) with WARN root and INFO for com.qdw.feishu
- Shared SQLite DataSource eliminates duplicate connection pools to same DB
- Optimistic lock in updateSession()/updateState() uses single UPDATE WHERE instead of SELECT+UPDATE

## Task Commits

1. **Task 1: Security Hardening (D-05, D-06, D-07)** - `7a2f671` (feat)
2. **Task 2: Data Integrity Fixes (D-14, D-15, D-16)** - `e84006c` (feat)

## Files Created/Modified
- `feishu-bot-start/src/main/resources/application-prod.yml` - Production Spring profile with safe log levels
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/SQLiteConfig.java` - Shared SQLite DataSource bean
- `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java` - Removed "default" from @Profile
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java` - DEBUG-level message content
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java` - DEBUG-level content, removed @Deprecated
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java` - DEBUG-level prompt, log length only
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java` - DEBUG-level content
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java` - DEBUG-level content
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HistoryApp.java` - DEBUG-level content
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java` - DEBUG-level reply/message content
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java` - Shared DataSource, removed createDataSource()
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java` - Shared DataSource, single-SQL optimistic lock

## Decisions Made
- Used single constructor with DataSource injection for gateways (plan suggested dual constructors; tests create inline DataSource instead — simpler, same effect)
- Kept getState() call in updateState() for state transition validation since it serves a different purpose than version checking

## Deviations from Plan

None — plan executed as written with minor implementation differences noted in Decisions.

## Issues Encountered
None

## Next Phase Readiness
- All 345 tests pass (BUILD SUCCESS)
- Security hardening complete — safe for production deployment
- Data integrity improved — shared DataSource, atomic operations
- Ready for 04-02 (Spring removal from domain) which modifies overlapping files

---
*Phase: 04-code-cleanup-and-refactoring*
*Completed: 2026-05-04*

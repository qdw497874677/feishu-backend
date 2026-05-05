# Codebase Concerns

**Analysis Date:** 2026-04-06

## Tech Debt

### Empty String Return from Async Tasks Creates Ghost Reply Bubbles

- Issue: `OpenCodeTaskExecutor.executeTask()` returns `""` (empty string) at line 147 after dispatching async work. This empty string propagates through `BotMessageAppService.sendReply()` to `TopicReplyStrategy.reply()`, which calls `feishuGateway.sendMessage(message, "", topicId)` — creating a visible empty reply bubble in the Feishu chat before the actual async result arrives.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java` (line 147), `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java` (lines 52-63)
- Impact: Users see an empty message bubble followed by the actual response. Confusing UX.
- Fix approach: Return `null` instead of `""` from `executeTask()`. The `sendReply()` method already handles `null` correctly — it skips the reply entirely when replyContent is null (lines 53-56). This is the existing convention for "I'll reply asynchronously." Alternatively, add an empty-string check alongside the null check in `sendReply()`.

### chatnow/cn Creates Session But Doesn't Execute the Prompt

- Issue: `OpenCodeCommandHandler.handleChatNowCommand()` at lines 282-307 calls `taskExecutor.createSessionOnly(message)` which only creates a session and binds it. The original prompt text is never extracted or passed to any execution method. The user types `/oc cn 帮我写代码` expecting immediate AI interaction, but only receives a "session created" confirmation card. They must then send a separate `chat` command.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java` (lines 282-307), `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java` (lines 90-114)
- Impact: Breaks the documented "one-step quick start" workflow. The help text explicitly promises: "系统会自动创建会话并绑定到话题" AND "返回会话信息 + 对话结果". Only the first half works.
- Fix approach: In `handleChatNowCommand()`, after session creation, extract the prompt from `parts[2..]` and pass it to `taskExecutor.executeWithAutoSession(message, prompt)` or `executeTask(message, prompt, sessionId)`.

### Plain Text in Bound Topic Shows Status Instead of Auto-Chatting

- Issue: When a user types plain text (no `/` prefix) in a topic bound to OpenCode with an active session, `OpenCodeMessageAppService.handleMessageInternal()` routes through `handleOpenCodeResult()` → `BotMessageAppService.handleMessage()` → `BotMessageService.routeMessage()`. The `routeImplicitMessage()` correctly resolves to `OpenCodeApp`. But `OpenCodeApp.execute()` extracts subCommand from parts[1] and enters the switch-case routing which doesn't handle plain text. The message content like "帮我重构代码" is parsed as `parts = ["帮我重构代码"]` with `parts.length < 2`, so it calls `sessionManager.getCurrentSessionStatus(message)` — showing session status info instead of forwarding the text as a chat prompt.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java` (lines 170-197), `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java` (line 105)
- Impact: The documented UX of "直接输入问题（无需命令前缀）" is broken. Users must always prefix with `chat`.
- Fix approach: In `OpenCodeApp.execute()`, when `parts.length < 2` AND in an initialized topic, treat the entire content as a chat prompt and delegate to `taskExecutor.executeWithAutoSession(message, content)` instead of showing session status. Alternatively, add a pre-processing step in `OpenCodeMessageAppService` that wraps plain text as a synthetic chat command before routing.

### Dual State Detection Creates Semantic Confusion

- Issue: Two parallel state detection systems exist: `TopicState` (3 values: NON_TOPIC, UNINITIALIZED, INITIALIZED) used by `OpenCodeSessionManager.detectTopicState()` in the domain layer, and `ContextSessionState` (4 values: UNBOUND, BOUND_TO_OTHER_APP, IN_APP_NO_SESSION, IN_APP_WITH_SESSION) used by `ContextSessionOrchestrator.loadStatus()` in the app layer. Both query the same underlying `ImContextBindingGateway` and `AppSessionGateway` but produce different state enums with different granularity. `OpenCodeApp.execute()` uses `TopicState`, while `OpenCodeMessageAppService.handleMessageInternal()` uses `ContextSessionState` on the same message in the same request path.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/topic/TopicState.java`, `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/ContextSessionState.java`, `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java` (lines 68-75), `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImpl.java` (lines 46-81)
- Impact: Redundant DB queries on every message (each system independently calls `bindingGateway.findBinding()`). Potential for inconsistent state decisions between the two code paths. Hard to reason about behavior — developers must understand both systems.
- Fix approach: Consolidate to a single state detection mechanism. Either make `TopicState` a derivation of `ContextSessionState` (map IN_APP_WITH_SESSION→INITIALIZED, IN_APP_NO_SESSION→UNINITIALIZED, UNBOUND→NON_TOPIC), or remove `TopicState` and use `ContextSessionState` everywhere. The latter is more expressive (BOUND_TO_OTHER_APP has no TopicState equivalent).

### Deprecated API in Active Use

- Issue: `FishuAppI.execute(Message)` is marked `@Deprecated` in favor of `execute(UnifiedCommand)`, but every app implementation (`OpenCodeApp`, `BashApp`, `TimeApp`, `HelpApp`, `HistoryApp`) only implements the deprecated method. The new `execute(UnifiedCommand)` default implementation converts back to a `Message` and calls the deprecated method, creating a circular bridge pattern.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java` (lines 28-39)
- Impact: The migration to `UnifiedCommand` is incomplete. Both APIs coexist without clear migration path. The default `createMessage()` bridge loses data (no chatId, no eventId, no receiveTime).
- Fix approach: Either complete the migration by implementing `execute(UnifiedCommand)` in all apps and removing the deprecated method, or remove the `@Deprecated` annotation and accept `Message` as the primary API. Half-migrated interfaces create confusion.

---

## Known Bugs

### OpenCode Context Mismatch: Session Binding vs Topic Reply Context

- Symptoms: When a user sends `/oc cn` in a group chat (no existing topic), the session is created and bound. But the binding happens on the **chatId** context (because there's no topicId yet). When the reply is sent via `TopicReplyStrategy`, Feishu creates a **new thread** with a new threadId. Subsequent messages in that thread resolve to `feishu:thread:<threadId>` context, which has NO binding — causing the system to not recognize the established session.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/feishu/FeishuContextResolver.java` (lines 35-57), `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java` (lines 65-78), `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java` (lines 106-107)
- Trigger: Send `/oc cn` in a group chat where no topic exists yet.
- Workaround: The `persistBindingIfNeeded()` method in `BotMessageAppService` (lines 65-78) attempts to fix this by binding the new `persistedThreadId` from `SendResult`. However, this binding uses `appId` only (null sessionId) via `bindingGateway.bind(contextRef, decision.getAppId(), null)` — it doesn't carry over the session that was just created on the chatId context. The session data exists only on the original chatId context.

### Card Button Actions Lack Conversation Context

- Symptoms: When users click interactive card buttons (from streaming cards or help cards), the `CardCommandAdapter` extracts `openId` and `messageId` but NOT `topicId` or `chatId`. The resulting `UnifiedCommand` has null topicId.
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardCommandAdapter.java` (lines 17-39), `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/UnifiedCommand.java`
- Trigger: Click any interactive button on a card in a Feishu chat.
- Workaround: None currently. Card actions that require session context (like "continue session") will fail because `FeishuContextResolver.resolve()` will throw `IllegalArgumentException` with no topicId or chatId.

---

## Security Considerations

### Test Controller Active in Default Profile

- Risk: `MessageTestController` at `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java` is annotated with `@Profile({"dev", "test", "default"})`. The `default` profile means this controller is active in production if no explicit profile is set. It exposes `/test/message`, `/test/message/help`, `/test/message/time`, and `/test/message/bash` endpoints that can execute arbitrary bot commands without authentication.
- Files: `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java` (line 23)
- Current mitigation: None. The controller accepts arbitrary content via `@RequestParam String content` and the `/test/message/bash` endpoint passes user input directly to `BashApp`.
- Recommendations: Remove `"default"` from `@Profile`, or restrict to `"dev"` only. Add authentication or bind to localhost-only. Consider removing entirely — integration tests should not use HTTP endpoints on a production codebase.

### Message Content Logged at INFO Level

- Risk: `ReceiveMessageListenerExe.execute()` logs `message.getDisplayContent()` at INFO level on every incoming message. This logs all user message content to the application log file. If messages contain sensitive data (API keys, passwords, personal information), they are persisted in plaintext in `/tmp/feishu-run.log`.
- Files: `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java` (line 46), also `OpenCodeApp.execute()` at `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java` (line 174)
- Current mitigation: None. The log file is at `/tmp/feishu-run.log` which may be world-readable.
- Recommendations: Reduce message content logging to DEBUG level. Log a truncated hash or content length at INFO. For OpenCode, prompts may contain code with embedded secrets.

### Prompt Content Logged Before Async Execution

- Risk: `OpenCodeTaskExecutor.executeTask()` logs `prompt='{}'` at INFO level (line 136). This captures the full user prompt including any code snippets, credentials, or sensitive instructions being sent to OpenCode.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java` (line 136)
- Current mitigation: None.
- Recommendations: Log prompt length only at INFO, full content at DEBUG.

---

## Performance Bottlenecks

### Redundant Database Queries in Message Processing

- Problem: For each incoming message in an OpenCode-bound topic, the same `ImContextBindingGateway.findBinding()` SQLite query is executed **multiple times** in a single request path: once in `OpenCodeMessageAppService.supports()/tryHandle()`, once in `ContextSessionOrchestratorImpl.loadStatus()`, once in `BotMessageService.routeMessage()`, and once in `OpenCodeSessionManager.detectTopicState()`. Each call results in a separate SQLite query.
- Files: `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java` (lines 48-57, 61-68), `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java` (lines 68-75), `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java` (lines 33, 65)
- Cause: No caching layer, no request-scoped context to share resolved state.
- Improvement path: Introduce a request-scoped cache for binding lookups, or resolve the context once early in the pipeline and thread it through as a parameter.

### Synchronized Flush Buffer in Streaming Handler

- Problem: `OpenCodeStreamingHandler.flushBuffer()` is synchronized on the entire handler instance (line 139), not per-session. Multiple concurrent sessions sharing the same handler will serialize all flush operations.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeStreamingHandler.java` (line 139)
- Cause: Method-level `synchronized` keyword instead of per-session locking.
- Improvement path: Use per-session locks (e.g., `ConcurrentHashMap<String, ReentrantLock>`) or make the method lock-free using atomic operations on the buffer.

---

## Fragile Areas

### Session ID Extraction via Text Parsing

- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeResponseFormatter.java` (lines 60-129), `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java` (lines 184-201)
- Why fragile: Two separate session ID extraction mechanisms exist:
  1. `OpenCodeResponseFormatter.extractSessionId()` parses JSON or falls back to string matching for `ses_` prefix from OpenCode API output.
  2. `OpenCodeMessageAppService.extractSessionId()` searches for `"Session ID: \`"` in the **formatted reply content** — a user-facing markdown string. If the format of `buildSessionInitializedInfo()` or `buildSessionCreatedResponse()` changes (e.g., emoji or wording change), session binding silently breaks.
- Safe modification: Any change to response formatting methods (`buildSessionInitializedInfo`, `buildSessionCreatedResponse`, `buildChatStatusWithSession`) must preserve the exact string `"Session ID: \`"` followed by the session ID and a closing backtick.
- Test coverage: `OpenCodeSessionManagerTest` covers session operations but not the fragile text extraction.

### OpenCode Command Parsing Relies on String Split with Fixed Array Indices

- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java` (lines 171-173), `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java` (lines 49, 65-76, 177-210)
- Why fragile: Commands are parsed by splitting content on whitespace with `content.split("\\s+", 3)`. The `parts[0]` is the `/opencode` prefix, `parts[1]` is the subcommand, `parts[2]` is the remaining content. But `OpenCodeApp.execute()` does the split while `OpenCodeCommandHandler.handle()` receives the already-split parts. If the split limit changes in one place but not the other, subcommand routing breaks silently.
- Safe modification: When modifying command parsing, always verify both `OpenCodeApp.execute()` and `OpenCodeCommandHandler.handle()` use consistent split semantics.
- Test coverage: `OpenCodeCommandHandlerTest` and `OpenCodeAppTest` exist but may not cover all edge cases of split behavior.

---

## Architecture Violations

### Domain Layer Depends on Spring Framework

- Issue: 32 files in `feishu-bot-domain/src/main/java/` import `org.springframework.*` annotations. Every domain class uses `@Component`, `@Service`, or `@Autowired`. This violates the COLA principle that the domain layer should be framework-agnostic.
- Files (notable examples):
  - `feishu-bot-domain/.../service/BotMessageService.java`: `@Service`
  - `feishu-bot-domain/.../app/BashApp.java`: `@Component`, `@Async`
  - `feishu-bot-domain/.../app/HelpApp.java`: `@Autowired`, `@Lazy`
  - `feishu-bot-domain/.../config/FeishuReplyProperties.java`: `@ConfigurationProperties`
  - `feishu-bot-domain/.../config/CardProperties.java`: `@ConfigurationProperties`
  - `feishu-bot-domain/.../opencode/OpenCodeTaskExecutor.java`: `@Async("opencodeExecutor")`
  - `feishu-bot-domain/.../core/AppRegistry.java`: `@Autowired`, `@Lazy`
- Impact: Domain layer cannot be tested without Spring context. Domain objects cannot be reused in non-Spring environments. Violates the explicit COLA architecture constraint from AGENTS.md.
- Fix approach: Move `@Component`/`@Service` annotations to a `DomainServiceConfig` `@Configuration` class in infrastructure. Use constructor injection exclusively (no `@Autowired`). Move `@ConfigurationProperties` classes to infrastructure. Move `@Async` to infrastructure wrapper classes.

### BotRoutingDecision Leaks App Reference

- Issue: `BotRoutingDecision` at `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/BotRoutingDecision.java` holds a direct `FishuAppI app` reference. This couples the routing decision (data) to the application instance (behavior), making the decision object a carrier of executable code rather than a pure data transfer. Callers can invoke `decision.getApp().execute()` which bypasses any orchestration.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/BotRoutingDecision.java` (line 16), consumed by `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java` (line 45-46)
- Impact: The app layer directly calls `decision.getApp().execute(message)` which skips any validation, decoration, or interception that a proper orchestration layer might provide. Also prevents serialization of routing decisions.
- Fix approach: Remove `FishuAppI app` from `BotRoutingDecision`. Keep only `appId` and `persistBinding`. The consumer (`BotMessageAppService`) should resolve the app from `AppRegistry` using the `appId`.

---

## Data Integrity

### Non-Atomic Read-Check-Write in SQLite Binding Operations

- Problem: The `ImContextBindingGatewayImpl.bind()` method at lines 284-347 performs a non-atomic read-then-write pattern: it calls `findBinding()` (SELECT), checks the result, then either UPDATEs or INSERTs. Under concurrent access (two messages arriving for the same context simultaneously), two threads could both read "no existing binding" and both attempt INSERT, causing a constraint violation on the PRIMARY KEY.
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java` (lines 284-347)
- Current mitigation: SQLite's built-in file locking provides some protection in single-connection scenarios, but `JdbcTemplate` with connection pooling may use multiple connections.
- Fix approach: Use `INSERT OR REPLACE` (SQLite upsert) instead of separate SELECT + INSERT/UPDATE. Or wrap the entire bind operation in a transaction.

### Optimistic Lock Race Condition in Session Updates

- Problem: `AppSessionGatewayImpl.updateSession()` at lines 264-298 performs a version check: first queries `getVersion()` (separate SELECT), compares, then does `UPDATE ... WHERE version = ?`. The version could change between the SELECT and the UPDATE, making the initial version check meaningless — the real protection is only the WHERE clause in the UPDATE. The separate `getVersion()` call adds an extra DB round-trip for no safety benefit.
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java` (lines 264-298)
- Current mitigation: The WHERE clause in the UPDATE provides the actual optimistic lock guarantee, making the separate SELECT redundant but harmless.
- Fix approach: Remove the preliminary `getVersion()` check. Just execute the UPDATE with the WHERE clause and check `updated == 0` for conflict detection. This reduces from 2 queries to 1.

### Multiple DataSource Instances for Same Database

- Problem: Both `AppSessionGatewayImpl` and `ImContextBindingGatewayImpl` create their own independent `DataSource` instances pointing to the same SQLite database file. Each creates its own `JdbcTemplate` with its own connection pool. SQLite allows only one writer at a time — multiple connections from different pools can cause `SQLITE_BUSY` errors or lock contention.
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java` (lines 51-58, 78-86), `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java` (lines 55-59, 87-95)
- Fix approach: Extract a shared `@Bean DataSource sqliteDataSource()` in a configuration class. Inject the shared DataSource into both gateways.

---

## Missing Critical Features

### No Session Cleanup / Expiration

- Problem: Sessions in `app_session` table are created but never automatically cleaned up. The `expires_at` column exists in the schema but is never populated (always NULL). Over time, the SQLite database will grow indefinitely. There is a `cleanupSessions()` method but nothing calls it.
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java` (lines 376-391, schema at line 100-112)
- Blocks: Long-running deployments will accumulate stale sessions.

### No chatId Propagation from Card Events

- Problem: Feishu card action callbacks contain the chatId where the card was displayed, but `CardCommandAdapter` does not extract it. The `UnifiedCommand` has no `chatId` field at all. Any card-initiated action that needs to resolve IM context will fail because `FeishuContextResolver` needs either topicId or chatId.
- Files: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardCommandAdapter.java`, `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/UnifiedCommand.java`
- Blocks: Card button interactions for session management, project selection, or any context-aware operation.

---

## Test Coverage Gaps

### No Tests for Plain Text Routing in Bound Topics

- What's not tested: The complete flow when a user sends plain text (no `/` prefix) in an OpenCode-bound topic. The interaction between `OpenCodeMessageAppService.tryHandle()`, `BotMessageService.routeImplicitMessage()`, and `OpenCodeApp.execute()` with non-command content.
- Files: `feishu-bot-app/src/test/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppServiceTest.java`, `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeAppTest.java`
- Risk: The auto-chat feature (documented as working) is actually broken — this is the exact bug described above. Tests would have caught it.
- Priority: High — this is a core user workflow.

### No Integration Tests for Context Migration (chatId→threadId)

- What's not tested: The scenario where a session is created on a chatId context and then needs to be accessible from a threadId context after the first reply creates a topic.
- Files: No test file covers this cross-context scenario.
- Risk: The core context mismatch bug is undetected.
- Priority: High — breaks the primary flow for new topic creation.

### No Tests for Card Action Processing Pipeline

- What's not tested: `CardCommandAdapter.adapt()`, `EventProcessor.process()` with card events, and the full card action→command→execution flow.
- Files: No test exists for `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardCommandAdapter.java` or `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/processor/EventProcessor.java`
- Risk: Card interactions may silently fail or produce incorrect routing decisions.
- Priority: Medium — card features are secondary to message-based interactions.

### No Tests for Empty String Reply Behavior

- What's not tested: The behavior when `FishuAppI.execute()` returns `""` (empty string). Does the reply strategy send an empty message? Does it create an empty bubble?
- Files: `feishu-bot-app/src/test/java/com/qdw/feishu/app/message/BotMessageAppServiceTest.java`
- Risk: Multiple code paths return `""` — 9 locations across the codebase. Each is potentially a ghost bubble.
- Priority: Medium — affects UX quality.

### No Tests for Concurrent Session Access

- What's not tested: Two concurrent messages to the same topic racing through `saveSession()`, `clearSession()`, and `updateSession()` paths.
- Files: `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImplTest.java`, `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImplTest.java`
- Risk: Data corruption or constraint violations under concurrent access.
- Priority: Medium — low traffic currently, but will break at scale.

---

## Scaling Limits

### In-Memory Maps in OpenCodeStreamingHandler

- Current capacity: `ConcurrentHashMap` instances (`textBuffers`, `sessionToTopicMap`, `sessionToMessageMap`, `flushTasks`, `lastFlushTime`, `sessionToCardMap`, `fallbackSessions`) grow unbounded per active session.
- Limit: Each active streaming session holds a `StringBuilder` buffer, a `Message` object, and a `ScheduledFuture`. If sessions are not properly unregistered (e.g., exception in async path), these maps leak indefinitely.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeStreamingHandler.java` (lines 38-44)
- Scaling path: Add a TTL-based eviction mechanism or periodic cleanup task. Ensure `unregisterSession()` is called in finally blocks.

### Single ScheduledThreadPool with 2 Threads

- Current capacity: `Executors.newScheduledThreadPool(2)` in `OpenCodeStreamingHandler` (line 55). All streaming sessions share these 2 scheduler threads for flush operations.
- Limit: With >2 concurrent streaming sessions, flush tasks queue up, causing delayed streaming updates.
- Files: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeStreamingHandler.java` (line 55)
- Scaling path: Increase pool size proportional to expected concurrent sessions.

---

## Dependencies at Risk

### Stale Worktrees Accumulating

- Risk: The `.worktrees/` directory contains 3 worktrees (`card-impl`, `architecture-refactoring`, `im-context-binding-refined`) with full source copies. These are stale development branches that inflate the repository size and can confuse code search tools (glob/grep results include worktree copies).
- Impact: Glob results return duplicated file lists. Developers may accidentally edit worktree files instead of main files.
- Files: `.worktrees/card-impl/`, `.worktrees/architecture-refactoring/`, `.worktrees/im-context-binding-refined/`
- Migration plan: Clean up completed worktrees with `git worktree remove`. Add `.worktrees/` to search exclusion patterns.

---

*Concerns audit: 2026-04-06*

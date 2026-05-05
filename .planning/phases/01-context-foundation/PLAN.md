# Phase 1: Context Foundation — Execution Plan

**Created:** 2026-04-07
**Revised:** 2026-04-07 (rev3: addressing oracle review — 2 critical, 5 major, 3 minor fixes)
**Phase:** 01-context-foundation
**Goal:** Fix the data flow layer — context propagation from chatId to threadId, structured session ID passing, request-scoped caching, and graceful degradation. After this phase, context binding is reliable and performant.
**Requirements:** CTX-01, CTX-02, CTX-03, CTX-04, CTX-05, COMPAT-01

---

## Session ID Glossary

Two distinct session ID types exist in this codebase. Every task and test must use the correct one:

| Term | Type | Example | Where stored | Who creates it |
|------|------|---------|-------------|----------------|
| **Internal appSessionId** | Auto-generated UUID | `"a1b2c3d4-..."` | `app_session.session_id` column; `ImContextBinding.sessionId` field | `AppSessionGateway.createSession()` |
| **External openCodeSessionId** | OpenCode server ID | `"ses_abc123"` | `app_session.data` JSON (`OpenCodeSessionData.openCodeSessionId`) | OpenCode server API |

**Rule:** `AppExecutionResult.openCodeSessionId` carries the **external openCodeSessionId** (what the user sees, what `saveSession()` accepts). The internal appSessionId is never exposed to apps — it is managed by `OpenCodeSessionManager.saveSession()` which creates/updates the internal session and updates `ImContextBinding` with the internal ID.

---

## Session ID Naming Convention (Enforced in This Phase)

All methods **touched in this phase** that deal with session IDs must follow these naming rules:

| Returns/Accepts | Naming | Example |
|-----------------|--------|---------|
| External OpenCode session ID (`ses_xxx`) | `openCodeSessionId` | `getOpenCodeSessionId()`, `saveSession(ctx, openCodeSessionId)` |
| Internal app session UUID | `appSessionId` or `internalSessionId` | `getBoundSessionId()` (on `MessageContext`), `binding.getSessionId()` |
| Ambiguous — could be either | **Prohibited** | ❌ `getSessionId()` without qualifier |

**Enforcement:**
- New methods: must follow the convention
- Modified methods: rename if the change is small; otherwise add explicit Javadoc clarifying which session ID type
- Untouched methods: leave as-is (defer renaming to V2-03 state model consolidation)
- `OpenCodeSessionManager.getSessionId()` returns **external** openCodeSessionId — if renamed in this phase, rename to `getOpenCodeSessionId()`. If renaming is too invasive (many callers), add Javadoc: `@return the external OpenCode session ID (ses_xxx), not the internal app session UUID`

---

## Task Breakdown

### Task 1A: Create `AppExecutionResult` DTO and migrate `FishuAppI.execute()` return type (compile-level)

**Requirement:** CTX-02
**Scope:** Interface/DTO creation + compile-fix for all callers. No behavioral changes.

**Files to create:**
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/AppExecutionResult.java`

**Files to modify (compile-fix only):**
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/BashApp.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HistoryApp.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/HandledMessageResult.java`
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java`

**Implementation details:**

1. **Create `AppExecutionResult`** in `domain/app/`:
   ```java
   public class AppExecutionResult {
       private final String replyContent;          // nullable — null = "I'll reply async, skip reply"
       private final String openCodeSessionId;     // nullable — external OpenCode session ID (ses_xxx)
       private final boolean sessionCreated;        // true if new session was created

       // Static factories:
       public static AppExecutionResult text(String content)
       public static AppExecutionResult noReply()
       public static AppExecutionResult withSession(String content, String openCodeSessionId, boolean created)
   }
   ```

   **Field naming rationale:** `openCodeSessionId` (not `sessionId`) to avoid confusion with internal appSessionId. Only OpenCode produces this value. Simple apps use `AppExecutionResult.text()` and never touch session fields.

2. **Change `FishuAppI.execute(Message)` return type** from `String` to `AppExecutionResult`. Remove `@Deprecated` annotation.

3. **Update `execute(UnifiedCommand)` bridge method — treat `noReply()` as success, not failure:**
   ```java
   default BizResult execute(UnifiedCommand command) {
       Message message = createMessage(command);
       AppExecutionResult result = execute(message);
       if (result == null) {
           return BizResult.failure("应用返回空结果");
       }
       if (result.getReplyContent() == null) {
           // noReply() is a valid async response, not a failure.
           // Card actions should not trigger async OpenCode paths currently,
           // but if they do, treat as successful empty result.
           return BizResult.success();
       }
       return BizResult.of(result.getReplyContent());
   }
   ```
   The bridge **still returns `BizResult`** — no change to `UnifiedCommand` flow in Phase 1. `noReply()` maps to `BizResult.success()` (not failure) to avoid breaking async/card flows.
   
   **Note:** When Task 2 changes the signature to `execute(Message, MessageContext)`, this bridge must be updated to pass `MessageContext.unresolved()` since card events don't go through `MessageContextResolver`. Task 2 handles this update.

4. **Update simple app implementations** (`BashApp`, `TimeApp`, `HelpApp`, `HistoryApp`): wrap return values with `AppExecutionResult.text(...)`.

5. **Update `HandledMessageResult`** to carry `AppExecutionResult` instead of raw `String replyContent`. Add convenience `getReplyContent()` that delegates to `executionResult.getReplyContent()`.

6. **Update `BotMessageAppService.handleMessage()`** to consume `AppExecutionResult` from `app.execute()` and pass it through `HandledMessageResult`.

**Acceptance criteria:**
- `FishuAppI.execute()` returns `AppExecutionResult`
- `execute(UnifiedCommand)` bridge: `noReply()` → `BizResult.success()`, `null` → `BizResult.failure()`
- All existing callers compile and function correctly
- `AppExecutionResult.text()` factory covers simple apps with zero behavior change

**Dependencies:** None — foundational change.

---

### Task 1B: Wire `AppExecutionResult` into OpenCode execution chain (behavioral)

**Requirement:** CTX-02
**Scope:** OpenCode-specific use of `AppExecutionResult` — structured session ID passing from commands and async execution.

**Files to modify:**
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeResponseFormatter.java`

**Implementation details:**

1. **Update `OpenCodeApp.execute()`** to delegate to `OpenCodeCommandHandler` which now returns `AppExecutionResult`.

2. **Update `OpenCodeCommandHandler.handle()`** to return `AppExecutionResult` instead of `String`:
   - Session-connecting commands (`sc`): `AppExecutionResult.withSession(content, openCodeSessionId, false)`
   - Session-creating commands (`chatnow`): `AppExecutionResult.withSession(content, openCodeSessionId, true)`
   - Info commands (`projects`, `sessions`, `help`): `AppExecutionResult.text(content)`

3. **Update `OpenCodeTaskExecutor`**: return `AppExecutionResult.noReply()` instead of `""`. This introduces the structured no-reply semantic used by Phase 2 to eliminate ghost bubbles; Phase 1 does NOT enforce suppression in the reply path — that is Phase 2 scope.

4. **Refactor `OpenCodeTaskExecutor.executeAsync()` to return structured session ID** instead of parsing from text:
   - `OpenCodeGateway.sendMessage()` / `createSession()` already return raw results. Instead of passing raw text to `responseFormatter.extractSessionId()`, extract the session ID from the gateway's structured response (JSON field `session_id`).
   - After extraction, call `sessionManager.saveSession()` with the structured `openCodeSessionId`.
   - **If** the gateway returns unstructured text (raw CLI output), keep `OpenCodeResponseFormatter.extractSessionId()` as a **fallback** but log a warning when it's used. Add a TODO for Phase 2 to make the gateway always return structured data.

5. **Document the async session ID boundary clearly:**
   - Synchronous path: `OpenCodeCommandHandler` → `AppExecutionResult.openCodeSessionId` → `progressSessionIfNeeded()` → `saveSession()`
   - Asynchronous path: `OpenCodeTaskExecutor.executeAsync()` → extracts `openCodeSessionId` from gateway response → `saveSession()` directly (bypasses `AppExecutionResult` since no caller to return to)
   - Both paths use the same `saveSession(ImContextRef, openCodeSessionId)` endpoint.

**Acceptance criteria:**
- `OpenCodeCommandHandler` returns `AppExecutionResult` with structured session data
- `OpenCodeTaskExecutor` returns `AppExecutionResult.noReply()` for async paths
- Async session ID extraction uses structured gateway response where possible, text parsing as fallback only
- `OpenCodeResponseFormatter.extractSessionId()` is retained as fallback but not the primary path

**Dependencies:** Task 1A (needs `AppExecutionResult` class).

---

### Task 2: Create `MessageContext` and `MessageContextResolver`; implement request-scoped caching

**Requirement:** CTX-03, CTX-04
**Files to create:**
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/MessageContext.java`
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/context/MessageContextResolver.java`

**Files to modify:**
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java`
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java`
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java`
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImpl.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java`

**Implementation details:**

1. **Create `MessageContext`** in `domain/model/`:
   ```java
   public class MessageContext {
       private final ImContextRef contextRef;             // non-null
       private final ImContextBinding binding;            // nullable — null if unbound
       // No generic AppSession field — app-specific session resolved on demand

       // Convenience methods:
       public boolean isThreadContext()   // contextRef.contextType == "thread"
       public boolean isChatContext()     // contextRef.contextType == "chat"
       public boolean isBound()          // binding != null
       public boolean isBoundToApp(String appId) // binding != null && binding.isForApp(appId)
       public Optional<String> getBoundAppId()
       public Optional<String> getBoundSessionId() // internal appSessionId from binding

       // Static factories:
       public static MessageContext unresolved()   // no context at all (e.g., card event without chatId)
       public static MessageContext of(ImContextRef ref, ImContextBinding binding)
   }
   ```

   **Design decision:** No `AppSession<T>` field. The session model is generic (`AppSession<OpenCodeSessionData>`) and app-scoped. Forcing it into a shared `MessageContext` would require type parameters or raw types. Instead, app-specific session data is loaded on-demand by `OpenCodeSessionManager` using the `binding.getSessionId()` from `MessageContext`. This keeps `MessageContext` simple and reusable for non-session-aware apps.

2. **Create `MessageContextResolver`** in `app/context/` (new package):
   ```java
   @Component
   public class MessageContextResolver {
       private final ImContextBindingGateway bindingGateway;

       public MessageContext resolve(Message message) {
           try {
               ImContextRef contextRef = FeishuContextResolver.resolve(message);
               Optional<ImContextBinding> binding = bindingGateway.findBinding(contextRef);
               return MessageContext.of(contextRef, binding.orElse(null));
           } catch (IllegalArgumentException e) {
               // Card events or messages without chatId/topicId
               return MessageContext.unresolved();
           }
       }
   }
   ```
   This is the **single point** where `findBinding()` is called for the normal routing path. Encapsulating resolution avoids injecting `ImContextBindingGateway` directly into `ReceiveMessageListenerExe`. The `unresolved()` fallback handles edge cases where context cannot be determined (e.g., card events missing chatId).

3. **Resolve `MessageContext` once at pipeline entry** — in `ReceiveMessageListenerExe.execute()`:
   ```java
   MessageContext messageContext = messageContextResolver.resolve(message);
   if (!openCodeMessageAppService.tryHandle(message, messageContext)) {
       botMessageAppService.handleMessage(message, messageContext);
   }
   ```

4. **Thread `MessageContext` through the pipeline:**
   - `OpenCodeMessageAppService.tryHandle(message, messageContext)` — replaces internal `resolveContext()` + `loadStatus()` calls
   - `OpenCodeMessageAppService.handleMessageInternal(message, messageContext)` — uses `messageContext.getBinding()` directly
   - `BotMessageAppService.handleMessage(message, messageContext)` — passes to routing
   - `BotMessageService.routeMessage(message, messageContext)` — uses `messageContext.getBinding()` instead of calling `bindingGateway.findBinding()` again

5. **Refactor `ContextSessionOrchestratorImpl.loadStatus()`** to accept optional pre-resolved binding:
   ```java
   public <T> ContextSessionStatus<T> loadStatus(ImContextRef contextRef, String appId,
                                                   TypeToken<T> typeToken,
                                                   Optional<ImContextBinding> preResolvedBinding)
   ```
   When `preResolvedBinding` is provided, skip the `findBinding()` call. Keep the old signature as an overload for backward compat.

6. **Refactor `ContextSessionOrchestratorImpl.assertWritableForApp()`** to accept optional pre-resolved binding. Currently does its own `findBinding()` — use cached binding from `MessageContext` when available in the routing path.

7. **Thread `MessageContext` into the OpenCode domain execution chain:**

   The plan MUST ensure `MessageContext` reaches `OpenCodeApp`, `OpenCodeCommandHandler`, and `OpenCodeSessionManager` read-path methods. Without this, CTX-03 (one binding lookup per message) is not achieved in the most important path.

   **a) Change `FishuAppI.execute()` signature** to accept `MessageContext`:
   ```java
   AppExecutionResult execute(Message message, MessageContext messageContext);
   ```
   - For simple apps (`BashApp`, `TimeApp`, `HelpApp`, `HistoryApp`): the `messageContext` parameter is ignored — these apps don't use binding/session info. Their implementations simply add the parameter and don't reference it.
   - For `OpenCodeApp`: passes `messageContext` to `OpenCodeCommandHandler`.
   - The old `execute(Message)` is removed (not kept as overload) since all callers are updated in this phase.

   **b) Change `OpenCodeCommandHandler.handle()` signature:**
   ```java
   AppExecutionResult handle(Message message, MessageContext messageContext,
                              String subCommand, String[] parts, CommandWhitelist whitelist)
   ```
   - `messageContext` is forwarded to `OpenCodeSessionManager` read methods.

   **c) Refactor `OpenCodeApp.execute()` to use `MessageContext`:**
   - Replace `sessionManager.detectTopicState(message)` → `sessionManager.detectTopicState(messageContext)`
   - Replace `sessionManager.isTopicInitialized(message)` (in `isTopicInitialized()` override) → `sessionManager.isTopicInitialized(messageContext)`

   **d) Refactor `OpenCodeSessionManager` read-path methods** to accept `MessageContext`:
   - `detectTopicState(MessageContext)` — derive from `messageContext.isBound()` + `messageContext.getBoundSessionId()` instead of calling `bindingGateway.findBinding()`
   - `isTopicInitialized(MessageContext)` — same derivation
   - `getCurrentSessionStatus(MessageContext)` — use binding from context, only call `appSessionGateway.getSession()` for session data
   - `getSessionId(MessageContext)` — use binding from context to find app session, extract external openCodeSessionId
   - `isExplicitlyInitialized(MessageContext)` — use binding from context to find app session, check explicitly_initialized flag

   **Keep old `Message`-based overloads** as `@Deprecated` for write-path callers and callers not yet in the MessageContext pipeline (e.g., `OpenCodeTaskExecutor.executeAsync()` which runs in a separate thread). These overloads call `findBinding()` internally.

   **e) Update `OpenCodeCommandHandler` methods that call `sessionManager.getSessionId(message)`:**
   - `handleChatCommand()` → `sessionManager.getSessionId(messageContext)`
   - `handleChatNowCommand()` → `sessionManager.getSessionId(messageContext)`
   - `handleResetCommand()` → `sessionManager.getSessionId(messageContext)` (read path; the subsequent `clearSession()` may re-query)

   **Note:** Write-path methods (`saveSession`, `clearSession`, `setExplicitlyInitialized`) continue to call `findBinding()` directly because they may run after mutations and need fresh state. The "once per message" guarantee applies to the **read/routing path**, not mutation paths.

8. **Deprecate/remove `supports(Message)` on `OpenCodeMessageAppService`:** Since `tryHandle()` is always called (not `supports()` first), and `tryHandle()` now receives `MessageContext`, the standalone `supports()` is unused in the request path. Mark it `@Deprecated` or inline its logic into `tryHandle()`.

   **Also update `FishuAppI.isTopicInitialized()`**: This default method currently delegates to `sessionManager.isTopicInitialized(message)`. Change it to accept `MessageContext`:
   ```java
   default boolean isTopicInitialized(Message message, MessageContext messageContext) {
       return false; // default for non-session apps
   }
   ```
   `OpenCodeApp` overrides this to call `sessionManager.isTopicInitialized(messageContext)`. The caller (`BotMessageService` or `OpenCodeMessageAppService`) passes the `MessageContext` it already has.

9. **Remove redundant `findBinding()` calls** from (method names, not line numbers — lines will drift during edits):
   - `BotMessageService.routeExplicitCommand()` and `routeImplicitMessage()` — now receive `messageContext` with pre-resolved binding
   - `OpenCodeMessageAppService.tryHandle()` — now receives `messageContext`, no more `contextSessionOrchestrator.loadStatus()` for binding lookup
   - `OpenCodeSessionManager.detectTopicState()`, `isTopicInitialized()`, `getCurrentSessionStatus()`, `getSessionId()`, `isExplicitlyInitialized()` — all read-path methods now derive from `messageContext`
   - `OpenCodeCommandHandler` methods that called `sessionManager.getSessionId(message)` — now use `messageContext` overloads
   - `ContextSessionOrchestratorImpl.loadStatus()` — when called with `preResolvedBinding` (item 5), skips `findBinding()`

**Acceptance criteria:**
- `ImContextBindingGateway.findBinding()` is called exactly once in the normal read/routing path per message (via `MessageContextResolver`)
- `MessageContext` is threaded through the FULL chain: `ReceiveMessageListenerExe` → `OpenCodeMessageAppService` → `BotMessageAppService` → `BotMessageService` → `OpenCodeApp.execute()` → `OpenCodeCommandHandler.handle()` → `OpenCodeSessionManager` read methods
- Write-path methods that need fresh state may call `findBinding()` again (documented exception)
- `MessageContext.isThreadContext()` and `isChatContext()` are available for topic/chat distinction
- No `ThreadLocal` or Spring `RequestScope` usage
- IM binding and app session remain independent storage (separate gateways)
- `supports(Message)` is deprecated or removed
- Old `Message`-based overloads in `OpenCodeSessionManager` are `@Deprecated` (kept for async/write paths)

**Dependencies:** Task 1A (needs `AppExecutionResult` for the pipeline signature changes to compile together).

---

### Task 3: Eliminate fragile `extractSessionId()` text parsing

**Requirement:** CTX-02 (completion)
**Files to modify:**
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java`

**Implementation details:**

1. **Remove `extractSessionId(String replyContent)` method** (lines 184-201) entirely.

2. **Refactor `progressSessionIfNeeded()`** to read `openCodeSessionId` from `AppExecutionResult` and bind to the correct context (new thread if created):
   ```java
   private void progressSessionIfNeeded(Message message, HandledMessageResult result) {
       AppExecutionResult execResult = result.getExecutionResult();
       if (execResult == null || execResult.getOpenCodeSessionId() == null) {
           return;
       }
       // Determine the correct context to bind the session to:
       // If the reply created a new thread, bind to that thread (not the original chat)
       SendResult sendResult = result.getSendResult();
       ImContextRef targetContext;
       if (sendResult != null && sendResult.getThreadId() != null && !sendResult.getThreadId().isEmpty()) {
           // Reply created a new topic — bind session to the new thread context
           targetContext = ImContextRef.feishuThread(sendResult.getThreadId());
       } else {
           // No new thread — bind to whatever context we already have
           targetContext = FeishuContextResolver.resolve(message);
       }
       // saveSession() handles the internal-to-external session ID mapping
       openCodeSessionManager.saveSession(targetContext, execResult.getOpenCodeSessionId());
   }
   ```

   **Critical for CTX-01:** When a user sends `/oc cn` in a group chat (no existing topic), the reply creates a new thread. `saveSession()` must bind the session to the **new thread context**, not the original chat context. Using `sendResult.getThreadId()` ensures the binding lands on the right context. This is the exact fix for the known bug documented in CONCERNS.md ("OpenCode Context Mismatch: Session Binding vs Topic Reply Context").

3. **Note on session ID flow:**
   - `OpenCodeCommandHandler` extracts external `openCodeSessionId` from OpenCode API response
   - Sets it on `AppExecutionResult.openCodeSessionId`
   - `progressSessionIfNeeded()` determines the correct target context (new thread if reply created one)
   - Calls `openCodeSessionManager.saveSession(targetContext, openCodeSessionId)` (the overload that accepts `ImContextRef` directly — already exists at line 156)
   - `saveSession()` creates internal `AppSession<OpenCodeSessionData>` with auto-generated UUID, stores `openCodeSessionId` in session data, and binds the internal UUID to `ImContextBinding` on the target context
   - No code ever parses session IDs from formatted markdown

**Acceptance criteria:**
- `extractSessionId()` method is deleted
- Session binding works via structured `AppExecutionResult.openCodeSessionId` field
- No code in the project searches for `"Session ID: \`"` pattern
- Internal vs external session ID boundary remains clean

**Dependencies:** Task 1A (needs `AppExecutionResult`), Task 1B (needs OpenCode chain wired), Task 2 (touches same file `OpenCodeMessageAppService` — must coordinate).

---

### Task 4: Implement chatId→threadId binding propagation

**Requirement:** CTX-01
**Files to modify:**
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java`
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java`

**Implementation details:**

1. **Enhance `persistBindingIfNeeded()` in `BotMessageAppService`** (currently lines 65-78):
   - After `SendResult` returns with a new `threadId`:
     - Read the **full binding** from `MessageContext.getBinding()` (resolved once at pipeline entry)
     - Copy the full binding (appId + internal sessionId) to the new `ImContextRef.feishuThread(threadId)`
   
2. **Binding copy logic:**
   ```java
   private void persistBindingIfNeeded(Message message, SendResult sendResult,
                                        BotRoutingDecision decision, MessageContext messageContext) {
       if (decision == null || !decision.shouldPersistBinding() || sendResult == null || !sendResult.isSuccess()) {
           return;
       }
       String persistedThreadId = sendResult.getThreadId();
       if (persistedThreadId == null || persistedThreadId.isEmpty()) {
           return;
       }

       // Copy the existing binding (appId + internal sessionId) to the new thread context
       String appId = decision.getAppId();
       String internalSessionId = null;

       if (messageContext.getBinding() != null && messageContext.getBinding().isForApp(appId)) {
           internalSessionId = messageContext.getBinding().getSessionId();
       }
       // If internalSessionId is null here, that's fine:
       //   - For pre-existing sessions: the binding already has the session ID
       //   - For newly created sessions: progressSessionIfNeeded() in OpenCodeMessageAppService
       //     will call saveSession(threadRef, openCodeSessionId) which creates the internal
       //     session AND binds it to the same thread context (using sendResult.getThreadId())
       // So this propagation handles "copy existing binding to thread",
       // and progressSessionIfNeeded() handles "bind new session to thread".

       ImContextRef threadRef = ImContextRef.feishuThread(persistedThreadId);
       bindingGateway.bind(threadRef, appId, internalSessionId);
       log.info("Propagated binding to new thread: {} -> (app={}, session={})",
                persistedThreadId, appId, internalSessionId);
   }
   ```

   **Interplay with Task 3 (`progressSessionIfNeeded`):** For the "new session + new thread" scenario (e.g., `/oc cn` in group chat):
   1. `persistBindingIfNeeded()` runs first — copies app context (appId + null sessionId) to thread
   2. `progressSessionIfNeeded()` runs after — creates internal session, rebinds thread to (appId + new internal sessionId) via `saveSession(threadRef, openCodeSessionId)`
   
   Both use the same `sendResult.getThreadId()` to target the new thread. The second call upgrades the binding from null-session to active-session. This is exactly how the two-phase binding model works: enter app context → activate session.

3. **Harden `ImContextBindingGatewayImpl.bind()`** — change the read-then-write pattern (SELECT → INSERT/UPDATE) to use SQLite `INSERT ... ON CONFLICT DO UPDATE`:
   ```sql
   INSERT INTO im_context_binding (context_key, platform, context_type, context_id, app_id, session_id, created_at, last_active_at)
   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
   ON CONFLICT(context_key) DO UPDATE SET
       app_id = excluded.app_id,
       session_id = excluded.session_id,
       last_active_at = excluded.last_active_at
   ```
   This is atomic, preserves `created_at` on update (not overwritten), and avoids the delete+insert semantics of `INSERT OR REPLACE` which would reset `created_at` and interfere with no-change optimizations. SQLite 3.24+ supports this syntax (all modern SQLite versions).

4. **Binding duplication strategy (clarification of D-03):** The chat binding is **duplicated** to the new thread, NOT migrated (not deleted from chat). The original chatId binding remains and becomes stale naturally — it is not actively removed. This is safer for Phase 1 because:
   - Removing chat binding during thread creation adds a mutation that could fail
   - Group chat messages from the same chat may still need the chat binding for routing before the user enters the topic
   - Stale chat bindings are harmless: they point to the same app, and if a user sends a new command in the group chat, it will be overwritten by the new binding

   **Decision update:** D-03 in `01-CONTEXT.md` should be updated from "NOT preserved" to "duplicated to thread, chat binding left to become stale." This is a documentation-only change to align with actual behavior.

**Acceptance criteria:**
- After a reply creates a new topic (threadId), the full binding (appId + internal sessionId) is copied to the threadId context
- Original chat binding is left intact (duplicated, not migrated)
- `ImContextBindingGatewayImpl.bind()` uses `INSERT ... ON CONFLICT DO UPDATE` (atomic upsert, preserves `created_at`)
- Subsequent messages in the topic find the binding without re-binding
- The copy happens synchronously in the reply path (not deferred)

**Dependencies:** Task 2 (needs `MessageContext` for original binding).

---

### Task 5: Implement graceful degradation for old/unbound topics

**Requirement:** CTX-05
**Files to modify:**
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java`

**Implementation details:**

**Design decision:** Graceful degradation is handled in `OpenCodeMessageAppService` (app layer), NOT in `BotMessageService` (domain). The app layer already intercepts messages before routing — adding degradation there avoids overloading `BotRoutingDecision` with synthetic reply fields. `BotMessageService.routeImplicitMessage()` continues to route unbound contexts to help for non-OpenCode cases.

1. **In `OpenCodeMessageAppService.handleMessageInternal()`**, when `UNBOUND` and `messageContext.isThreadContext()`:
   - Return a one-line text guidance via `sendGuidance()` instead of entering app context:
     ```java
     if (status.getState() == ContextSessionState.UNBOUND && messageContext.isThreadContext()) {
         log.debug("Graceful degradation for unbound topic {}: no binding found",
                   messageContext.getContextRef().toStorageKey());
         return sendGuidance(message, 
             "该话题未绑定 OpenCode 会话。请在群聊中使用 /oc projects 开始绑定。");
     }
     ```
   - When `UNBOUND` and `messageContext.isChatContext()`: keep current behavior (enter app context, route to `BotMessageAppService`).

2. **`BotMessageService.routeImplicitMessage()` unchanged**: continues to route unbound chat contexts to help. The thread context degradation is handled at the app layer before routing is reached.

3. **For non-OpenCode implicit messages in unbound threads**: `BotMessageService.routeImplicitMessage()` still routes to help. This is acceptable — only OpenCode topics need the targeted degradation guidance because only OpenCode binds topics.

**Acceptance criteria:**
- Old OpenCode topics with no binding show one-line guidance text, not an error
- Guidance message suggests using `/oc projects` in group chat
- Degradation logged at DEBUG level (not WARN/INFO)
- No exceptions thrown for unbound topic access
- Chat contexts still enter app context normally
- Non-OpenCode unbound threads still route to help (existing behavior)

**Dependencies:** Task 2 (uses `MessageContext.isThreadContext()`).

---

### Task 6: Update all tests for COMPAT-01 compliance

**Requirement:** COMPAT-01
**Test files affected by module:**

**Domain app tests (FishuAppI.execute return type change):**
- `feishu-bot-domain/src/test/.../domain/app/BashAppTest.java`
- `feishu-bot-domain/src/test/.../domain/opencode/OpenCodeAppTest.java`
- `feishu-bot-domain/src/test/.../domain/opencode/OpenCodeCommandHandlerTest.java`
- `feishu-bot-domain/src/test/.../domain/opencode/OpenCodeExplicitInitializationTest.java`
- `feishu-bot-domain/src/test/.../domain/opencode/OpenCodeSessionManagerTest.java`

**Domain routing tests (routeMessage signature change):**
- `feishu-bot-domain/src/test/.../domain/service/BotMessageServiceTest.java`

**App service tests (handleMessage signature + HandledMessageResult):**
- `feishu-bot-app/src/test/.../app/message/BotMessageAppServiceTest.java`
- `feishu-bot-app/src/test/.../app/opencode/OpenCodeMessageAppServiceTest.java`
- `feishu-bot-app/src/test/.../app/listener/ReceiveMessageListenerExeTest.java`
- `feishu-bot-app/src/test/.../app/session/ContextSessionOrchestratorImplTest.java`

**Infrastructure tests (bind() upsert change):**
- `feishu-bot-infrastructure/src/test/.../gateway/ImContextBindingGatewayImplTest.java`
- `feishu-bot-infrastructure/src/test/.../gateway/AppSessionGatewayImplTest.java`

**Other tests (verify no breakage):**
- `feishu-bot-domain/src/test/.../domain/opencode/OpenCodeStreamingHandlerTest.java`
- `feishu-bot-domain/src/test/.../domain/opencode/OpenCodeEventTest.java`
- `feishu-bot-domain/src/test/.../domain/card/StreamingCardManagerTest.java`
- `feishu-bot-domain/src/test/.../domain/model/ImContextBindingTest.java`
- `feishu-bot-domain/src/test/.../domain/validation/CommandWhitelistValidatorTest.java`
- `feishu-bot-domain/src/test/.../domain/history/BashHistoryManagerTest.java`
- `feishu-bot-infrastructure/src/test/.../gateway/FeishuGatewayImplTest.java`
- `feishu-bot-infrastructure/src/test/.../gateway/CardGatewayImplTest.java`
- `feishu-bot-start/src/test/.../HelpAppCardButtonJsonTest.java`

**Implementation details:**

1. **Update test mocks** returning `String` to return `AppExecutionResult`:
   ```java
   // Before:
   when(app.execute(any(Message.class))).thenReturn("Hello!");
   // After:
   when(app.execute(any(Message.class))).thenReturn(AppExecutionResult.text("Hello!"));
   ```

2. **Update `HandledMessageResult` assertions**: use `getExecutionResult().getReplyContent()` or the convenience `getReplyContent()` delegate.

3. **Update `BotMessageService.routeMessage()` tests** to pass `MessageContext` parameter.

4. **Update `ReceiveMessageListenerExeTest`** to inject `MessageContextResolver` and verify resolution.

5. **Update `OpenCodeMessageAppServiceTest`** for new `tryHandle(message, messageContext)` signature.

6. **Update `ImContextBindingGatewayImplTest`** to verify `INSERT ... ON CONFLICT DO UPDATE` behavior.

7. **Add new tests for new classes:**
   - `AppExecutionResult` factory methods and field access
   - `MessageContext` creation, `isThreadContext()`/`isChatContext()`, convenience methods
   - `MessageContextResolver.resolve()` — happy path + no-context fallback
   - Binding propagation: chatId→threadId with full binding copy
   - Graceful degradation: unbound thread → guidance; unbound chat → help

8. **Add 5 targeted behavioral invariant tests (CRITICAL — these catch the exact regressions most likely to occur):**

   **Test A — Single binding lookup in routing path:**
   ```java
   @Test
   void should_callFindBindingExactlyOnce_when_routingNormalMessage() {
       // Verify bindingGateway.findBinding() is called exactly once
       // during a full message processing pipeline (entry → route → execute → reply).
       // Use Mockito verify(bindingGateway, times(1)).findBinding(any());
   }
   ```

   **Test B — External vs internal session ID boundary:**
   ```java
   @Test
   void should_exposeExternalSessionId_when_appExecutionResultReturned() {
       // Given: OpenCodeCommandHandler returns AppExecutionResult.withSession(content, "ses_abc123", true)
       // When: progressSessionIfNeeded() processes the result
       // Then: saveSession() is called with openCodeSessionId = "ses_abc123"
       // AND: ImContextBinding.sessionId stores the internal UUID (not "ses_abc123")
       // AND: AppSession.data.openCodeSessionId == "ses_abc123"
   }
   ```

   **Test C — Thread propagation for newly created topic:**
   ```java
   @Test
   void should_bindToNewThread_when_replyCreatesNewTopic() {
       // Given: message in chat context, sendResult returns threadId = "t_new123"
       // When: persistBindingIfNeeded() runs
       // Then: bindingGateway.bind() called with ImContextRef.feishuThread("t_new123")
       // AND: original chat binding still exists (not deleted)
   }
   ```

   **Test D — Unresolved context (card events without chatId):**
   ```java
   @Test
   void should_returnUnresolvedContext_when_messageHasNoChatIdOrTopicId() {
       // Given: message with no chatId and no topicId (e.g., card event)
       // When: MessageContextResolver.resolve(message)
       // Then: returns MessageContext.unresolved()
       // AND: messageContext.isBound() == false
       // AND: no bindingGateway calls made
   }
   ```

   **Test E — Concurrent bind/upsert safety:**
   ```java
   @Test
   void should_notThrowOnConcurrentBind_when_twoThreadsBindSameContext() {
       // Given: two threads calling bind() on the same ImContextRef simultaneously
       // When: both execute
       // Then: no exception thrown; final state has one of the two bindings
       // Verifies INSERT ON CONFLICT DO UPDATE semantics
   }
   ```

9. **Run `mvn test` after each task** (not only at the end). Fix incrementally.

**Acceptance criteria:**
- All 261 existing tests pass (zero regressions)
- New tests cover all new classes and behaviors
- 5 targeted behavioral invariant tests (A-E) pass
- Test count ≥ 266

**Dependencies:** Tasks 1A-5 (all implementation tasks must be complete before final test pass). However, tests should be updated incrementally per-task, not deferred entirely.

---

## Task Dependency Graph

```
Task 1A: AppExecutionResult DTO + FishuAppI return type (compile-level)
    │
    ├──► Task 1B: Wire AppExecutionResult into OpenCode chain (behavioral)
    │
    └──► Task 2: MessageContext + MessageContextResolver + request-scoped caching
                  (includes threading MessageContext into OpenCode execution chain)
            │
            ├──► Task 3: Eliminate extractSessionId() [coupled with Task 2: touches same file]
            │
            ├──► Task 4: chatId→threadId binding propagation + SQLite upsert
            │
            └──► Task 5: Graceful degradation
                    │
                    └──► Task 6: Final test pass + new tests (including 5 behavioral invariant tests)
```

**Execution order:**
1. **Task 1A** — foundational compile-level change: all others depend on it
2. **Task 1B** — OpenCode behavioral wiring: depends on 1A, can run before Task 2
3. **Task 2** — MessageContext plumbing + threading into full chain: most other tasks depend on it
4. **Task 3** — coupled with Task 2 (touches `OpenCodeMessageAppService`): execute right after Task 2
5. **Tasks 4 + 5** — parallel: both depend on Task 2 but independent of each other
6. **Task 6** — final pass: depends on all above

**Rationale:** Tasks 1A and 1B are split to separate compile-fix risk from behavioral risk. Tasks 2 and 3 are NOT parallel — they both modify `OpenCodeMessageAppService` and the pipeline signatures. Doing them sequentially avoids merge conflicts and double-rework.

---

## Requirement Coverage

| Requirement | Task(s) | Verification |
|-------------|---------|--------------|
| CTX-01 | Tasks 3, 4 | After reply creates topic, binding exists on threadId with full internal sessionId. Task 4 copies existing binding; Task 3 binds new sessions to new thread via `progressSessionIfNeeded()`. Chat binding left intact (duplicate, not migrate). |
| CTX-02 | Tasks 1A, 1B, 3 | `extractSessionId()` in `OpenCodeMessageAppService` deleted; openCodeSessionId from `AppExecutionResult` field. Async path (`OpenCodeTaskExecutor.executeAsync()`) uses structured gateway response where possible, `ResponseFormatter.extractSessionId()` as fallback. |
| CTX-03 | Task 2 | `findBinding()` called once in routing path; `MessageContext` threaded through FULL chain including `OpenCodeApp.execute()` → `OpenCodeCommandHandler.handle()` → `OpenCodeSessionManager` read methods. Verified by Test A. |
| CTX-04 | Task 2 | `ImContextBindingGateway` and `AppSessionGateway` remain separate; `MessageContext` has no `AppSession` field |
| CTX-05 | Task 5 | Unbound threads get guidance text at DEBUG log level, no errors |
| COMPAT-01 | Task 6 | 266+ tests pass (261 existing + 5 behavioral invariant tests), zero regressions |

---

## Risk Mitigation

| Risk | Mitigation | Owner |
|------|------------|-------|
| `FishuAppI.execute()` return type change breaks many callers | `AppExecutionResult.text()` factory makes simple apps a one-line change; compile errors guide completeness. Split into 1A (compile) + 1B (behavioral) to reduce blast radius. | Task 1A |
| `execute(UnifiedCommand)` bridge breaks | Bridge still returns `BizResult`; `noReply()` → `BizResult.success()` (not failure); `null` → `BizResult.failure()`; card flow tested | Task 1A |
| Async session ID parsing not fully eliminated | `OpenCodeResponseFormatter.extractSessionId()` retained as fallback; primary path uses structured gateway response; warning logged on fallback usage | Task 1B |
| `MessageContext` threading touches many signatures | `MessageContextResolver` encapsulates resolution; follow compilation errors from `ReceiveMessageListenerExe` downward; old `Message`-based overloads kept as `@Deprecated` for gradual migration | Task 2 |
| Binding propagation race (concurrent messages) | `INSERT ... ON CONFLICT DO UPDATE` in SQLite binding (atomic upsert, preserves `created_at`) | Task 4 |
| Chat binding duplication causes stale data | Stale chat bindings are harmless (same app); overwritten on next group chat command; documented as intentional | Task 4 |
| Test count regression | Run `mvn test` after each task; 5 behavioral invariant tests verify exact regression-prone areas | Task 6 |
| Internal vs external session ID confusion | Glossary + naming convention enforced on touched APIs; `getSessionId()` → `getOpenCodeSessionId()` or Javadoc clarification | All tasks |

---

## Out of Scope for This Phase

- Ghost bubble elimination (UX-02) — Task 1 introduces `noReply()` semantic as groundwork; reply-path suppression enforcement is Phase 2
- Direct typing without prefix (UX-01) — Phase 2
- New command set (CMD-01) — Phase 2
- Card button context propagation (CARD-01) — Phase 3
- State model consolidation (TopicState vs ContextSessionState) — deferred to V2-03
- `OpenCodeSessionManager` write-path caching — write methods may call `findBinding()` after mutations; full elimination is optimization, not Phase 1 scope

---

*Plan created: 2026-04-07*
*Revised: 2026-04-07 (rev3: oracle review — 2 critical, 5 major, 3 minor fixes)*
*Phase: 01-context-foundation*

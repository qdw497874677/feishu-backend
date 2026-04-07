# Technology Stack — Interactive Flow Redesign Patterns

**Project:** OpenCode Interactive Flow Redesign
**Researched:** 2026-04-07
**Focus:** State machine, conversation flow, context propagation, and command routing patterns for the existing Java 17 / Spring Boot 3.2.1 / COLA stack

> **Scope boundary:** This document covers *patterns and approaches*, not new libraries. The existing stack (Java 17, Spring Boot 3.2.1, COLA 5.0.0, Feishu SDK 2.5.2, SQLite) is fixed. The question is how to structure the redesigned flow logic within it.

---

## Recommended Patterns

### 1. State Model: Unified Enum State Machine (not Spring Statemachine)

| Aspect | Decision |
|--------|----------|
| Pattern | Hand-rolled enum-based state machine with explicit transition table |
| Confidence | **HIGH** — evidence from codebase analysis + framework evaluation |

**What:** Replace the dual `TopicState` (3 values) / `ContextSessionState` (4 values) with a single `ConversationState` enum that covers the full lifecycle. Define a `ConversationStateResolver` that computes this state once per request from the persisted bindings.

**Why NOT Spring Statemachine (SSM):**
- SSM is designed for complex workflows with hierarchical/parallel states, guards, actions, and event-driven transitions. Our state space is small (4-5 states) and transitions are simple.
- SSM requires either session-scoped or request-scoped machine instances with persistence/restore cycle per message — heavyweight for a chatbot that processes one message → resolves state → routes → responds. (Context7 docs confirm SSM uses `StateMachineFactory.create(machineId)` + `StateMachinePersister` to save/restore context for each request. That's a lot of machinery for 5 states.)
- SSM introduces a framework dependency in the domain layer, violating COLA's framework-agnostic domain principle.
- The existing codebase already has the right primitives: `ImContextBinding` + `AppSession` in SQLite. The problem is not missing state machine infrastructure — it's that two parallel state detection systems query the same data independently.

**Why enum-based state machine:**
- State derivation is a pure function: `f(ImContextBinding, AppSession) → ConversationState`. No mutable state machine instance needed.
- Transition validation is a simple lookup table: `Map<ConversationState, Set<Command>> allowedCommands`.
- Fits naturally into domain layer without framework dependencies.
- The State Pattern (encapsulating behavior per state in objects, as described by the Amio.io chatbot architecture article) is the sweet spot — each state knows its allowed transitions and behavior, but without the heavyweight SSM framework.

**Recommended state enum:**

```java
public enum ConversationState {
    /** No IM context (no topicId, no chatId — shouldn't happen in normal flow) */
    NO_CONTEXT,
    
    /** In group chat, no topic — can browse projects, start chatnow */
    GROUP_CHAT,
    
    /** In topic, no app binding — fresh topic */
    TOPIC_UNBOUND,
    
    /** In topic, bound to opencode, no session yet */
    TOPIC_BOUND_NO_SESSION,
    
    /** In topic, bound to opencode, session active — full conversation mode */
    TOPIC_ACTIVE_SESSION,
    
    /** In topic, bound to different app — reject */
    TOPIC_BOUND_OTHER_APP
}
```

**Key insight:** This merges `TopicState` (NON_TOPIC / UNINITIALIZED / INITIALIZED) with `ContextSessionState` (UNBOUND / BOUND_TO_OTHER_APP / IN_APP_NO_SESSION / IN_APP_WITH_SESSION) into one enum that captures the full picture. The state is derived once, early in the pipeline, from a single `bindingGateway.findBinding()` call.

**Sources:**
- Amio.io State Pattern for chatbots (https://www.amio.io/blog/chatbots-diary-enter-the-state-pattern) — MEDIUM confidence, community pattern
- Spring Statemachine docs via Context7 — HIGH confidence for SSM's own capabilities
- Codebase analysis of current dual-state system — HIGH confidence

---

### 2. Context Propagation: Request-Scoped MessageContext Object

| Aspect | Decision |
|--------|----------|
| Pattern | Explicit context object resolved once, threaded through the pipeline as a method parameter |
| Confidence | **HIGH** — established Spring pattern, addresses documented performance concern |

**What:** Create a `MessageContext` value object that captures all resolved state for a single message processing cycle. Resolve it once at the adapter/app boundary, pass it explicitly through the call chain.

**Why:**
- The #1 performance concern in CONCERNS.md is redundant DB queries: `findBinding()` is called 4+ times per message across `OpenCodeMessageAppService.supports()`, `ContextSessionOrchestratorImpl.loadStatus()`, `BotMessageService.routeMessage()`, and `OpenCodeSessionManager.detectTopicState()`.
- A request-scoped Spring bean (`@Scope("request")`) would work for HTTP requests but the Feishu bot receives messages via WebSocket listener, not HTTP. There's no HTTP request scope. Using `@Scope("prototype")` with manual management adds complexity.
- Explicit parameter passing is the simplest, most testable approach. The Medium article on context propagation (2025-11) confirms that explicit parameter passing is preferred when the number of contextual fields is limited and when working outside HTTP request scope (async threads, WebSocket handlers).
- This also solves the async context propagation issue documented for the `opencodeExecutor` thread pool — the context object is passed as a parameter to async methods, not stored in `ThreadLocal`.

**Recommended structure:**

```java
@Value
public class MessageContext {
    Message message;
    ImContextRef contextRef;        // nullable — no context for malformed messages
    ConversationState state;
    String boundAppId;              // nullable
    String boundSessionId;          // nullable (OpenCode session ID)
    
    /** Resolve from message + gateway lookups — called ONCE per message */
    public static MessageContext resolve(Message message, 
                                          ImContextBindingGateway bindingGateway,
                                          AppSessionGateway sessionGateway) {
        // Single findBinding() call → derive everything
    }
}
```

**What NOT to do:**
- ❌ Don't use `ThreadLocal` or `RequestContextHolder` — the bot operates on WebSocket threads and `@Async` executor threads. ThreadLocal doesn't propagate automatically to async threads (confirmed by Spring docs and the Medium article on context propagation). While `TaskDecorator` can bridge this gap, explicit passing is simpler and more transparent.
- ❌ Don't use Spring `@Scope("request")` — no HTTP request in the WebSocket message path.
- ❌ Don't resolve state lazily at each call site — that's the current broken pattern causing 4x DB queries.

**Sources:**
- Medium: "Request Context Propagation in Spring Boot Async Threads" (2025-11) — MEDIUM confidence, confirms explicit passing is preferred for non-HTTP contexts
- CONCERNS.md redundant DB queries analysis — HIGH confidence (direct codebase evidence)
- Spring docs on scope limitations outside DispatcherServlet — HIGH confidence

---

### 3. Command Routing: Strategy Map with Guard Validation

| Aspect | Decision |
|--------|----------|
| Pattern | Registry-based command dispatch with state-aware guards |
| Confidence | **HIGH** — natural evolution of existing patterns in codebase |

**What:** Replace the monolithic `switch(subCommand)` in `OpenCodeCommandHandler.handle()` (currently 77 lines of switch-case + delegation) with a map of command handlers keyed by command name/alias. Each handler declares its state requirements (which `ConversationState` values it's valid in).

**Why:**
- The current `switch` statement in `OpenCodeCommandHandler` mixes command validation (whitelist check) with routing (switch dispatch). They should be separate concerns.
- The current `CommandWhitelist` per `TopicState` in `OpenCodeApp.getCommandWhitelist()` duplicates routing knowledge — it says "this command is allowed in this state" but the switch statement separately defines "this command calls this handler." Adding a new command requires updating both.
- A command handler registry with state guards co-locates the "what" (handler logic) with the "when" (allowed states), making it impossible for them to drift apart.

**Recommended structure:**

```java
public interface SubCommandHandler {
    /** Which sub-command strings trigger this handler */
    Set<String> commandNames();
    
    /** Which conversation states allow this handler */
    Set<ConversationState> allowedStates();
    
    /** Execute the command */
    String execute(MessageContext context, String[] args);
}
```

```java
@Component
public class ChatSubCommandHandler implements SubCommandHandler {
    @Override
    public Set<String> commandNames() {
        return Set.of("chat");
    }
    
    @Override
    public Set<ConversationState> allowedStates() {
        return Set.of(TOPIC_ACTIVE_SESSION, TOPIC_BOUND_NO_SESSION);
    }
    
    @Override
    public String execute(MessageContext ctx, String[] args) {
        // chat logic
    }
}
```

Then the command router becomes trivial:

```java
@Component
public class OpenCodeCommandRouter {
    private final Map<String, SubCommandHandler> handlers; // built from all @Component SubCommandHandlers
    
    public String route(MessageContext ctx, String subCommand, String[] args) {
        SubCommandHandler handler = handlers.get(subCommand);
        if (handler == null) return buildUnknownCommandResponse(subCommand);
        if (!handler.allowedStates().contains(ctx.getState())) return buildStateRejection(ctx, handler);
        return handler.execute(ctx, args);
    }
}
```

**What NOT to do:**
- ❌ Don't use Chain of Responsibility here. CoR is for "pass until someone handles it" — our commands have known, deterministic handlers. A direct map lookup is O(1) vs CoR's O(n).
- ❌ Don't keep separate `CommandWhitelist` objects. The handler itself declares its allowed states — one source of truth.
- ❌ Don't use annotation-based routing (like `@CommandMapping`). It's over-engineered for ~10 sub-commands and adds reflection complexity.

**Sources:**
- Codebase analysis of `OpenCodeCommandHandler.handle()` and `OpenCodeApp.getCommandWhitelist()` — HIGH confidence
- Refactoring.guru Chain of Responsibility analysis (confirms it's wrong fit for deterministic routing) — MEDIUM confidence
- java-design-patterns.com Pipeline Pattern analysis — MEDIUM confidence (pipeline is for sequential processing, not routing)

---

### 4. Context Binding Lifecycle: Two-Phase Bind with Migration

| Aspect | Decision |
|--------|----------|
| Pattern | Explicit bind-on-reply with chatId→threadId migration |
| Confidence | **HIGH** — directly addresses the #1 bug in CONCERNS.md |

**What:** When a command creates a session in group chat (chatId context) and the reply creates a new topic (generating a new threadId), the system must migrate the binding from `feishu:chat:chatId` to `feishu:thread:threadId` in the reply callback.

**Why:**
- The documented context mismatch bug: session bound to chatId, but subsequent messages in the newly-created topic resolve to `feishu:thread:threadId` which has no binding.
- The current `persistBindingIfNeeded()` in `BotMessageAppService` already extracts `sendResult.getThreadId()` but binds with `null` sessionId — it doesn't migrate the session data from the original chatId binding.

**Recommended approach:**

```java
// In the app-layer message handler, after sending a reply:
private void migrateBindingIfNeeded(MessageContext originalCtx, SendResult sendResult) {
    if (sendResult.getThreadId() == null) return;
    
    ImContextRef newContext = ImContextRef.feishuThread(sendResult.getThreadId());
    ImContextRef originalContext = originalCtx.getContextRef();
    
    // Only migrate if contexts differ (chat→thread transition)
    if (originalContext != null && !originalContext.equals(newContext)) {
        // Copy the full binding (appId + sessionId) to the new context
        bindingGateway.bind(newContext, originalCtx.getBoundAppId(), originalCtx.getBoundSessionId());
        log.info("Migrated binding: {} → {}", originalContext.toStorageKey(), newContext.toStorageKey());
    }
}
```

**What NOT to do:**
- ❌ Don't keep both bindings alive — the chatId binding becomes stale and confusing.
- ❌ Don't rely on the session manager to "discover" the session by searching all bindings — that's O(n) and fragile.
- ❌ Don't try to predict the threadId before sending the reply — Feishu assigns it, we can only read it from `SendResult`.

**Sources:**
- CONCERNS.md "OpenCode Context Mismatch" bug documentation — HIGH confidence (direct bug report)
- `BotMessageAppService.persistBindingIfNeeded()` line 65-78 — HIGH confidence (existing partial fix)

---

### 5. Plain Text Routing: Implicit Chat as Default in Active Sessions

| Aspect | Decision |
|--------|----------|
| Pattern | State-driven default behavior: if state is TOPIC_ACTIVE_SESSION and input is not a command, treat as chat |
| Confidence | **HIGH** — directly addresses R3 requirement |

**What:** In the message processing pipeline, after resolving `MessageContext`, if the state is `TOPIC_ACTIVE_SESSION` and the message content doesn't start with `/`, treat the entire content as a chat prompt. Don't route through the command handler at all.

**Why:**
- The current bug: plain text in a bound topic hits `OpenCodeApp.execute()`, which splits on whitespace, finds `parts.length < 2`, and falls into `sessionManager.getCurrentSessionStatus()` — showing status instead of chatting.
- This is the #1 UX requirement (R3): "Plain text in an initialized topic is treated as a chat prompt."
- The fix should happen *before* the command routing layer, not inside it. The command router handles commands; plain text is not a command.

**Recommended approach:**

```java
// In the app-layer OpenCode message handler:
if (ctx.getState() == ConversationState.TOPIC_ACTIVE_SESSION && !isCommand(message)) {
    String prompt = message.getContent().trim();
    return taskExecutor.executeWithAutoSession(message, prompt);
}
// Otherwise, route through command handler
```

**What NOT to do:**
- ❌ Don't "synthesize" a fake chat command by prepending `chat` to the content and re-routing — that's fragile and adds a layer of indirection.
- ❌ Don't check this inside `OpenCodeApp.execute()` — the app shouldn't need to know about routing rules. The pipeline layer (app service) decides what to do with non-command text.
- ❌ Don't allow plain text forwarding in any state other than `TOPIC_ACTIVE_SESSION` — in `TOPIC_BOUND_NO_SESSION`, plain text should trigger initialization guidance.

**Sources:**
- PROJECT.md R3 requirement — HIGH confidence
- CONCERNS.md "Plain Text Shows Status Instead of Auto-Chatting" — HIGH confidence
- Codebase analysis of `OpenCodeApp.execute()` line 177 — HIGH confidence

---

### 6. Async Reply Suppression: Null-Return Convention

| Aspect | Decision |
|--------|----------|
| Pattern | Return `null` from execute() for async paths; guard against empty string at reply layer |
| Confidence | **HIGH** — existing convention partially implemented |

**What:** Establish a clear contract: `FishuAppI.execute()` returns `null` to mean "I will reply asynchronously via cards/streaming, don't send a synchronous text reply." Enforce this at the `sendReply()` call site.

**Why:**
- The ghost bubble bug: `OpenCodeTaskExecutor.executeTask()` returns `""` which propagates to `feishuGateway.sendMessage()` creating an empty reply bubble.
- `BotMessageAppService.sendReply()` already handles `null` correctly (skips reply at line 53-56) but doesn't check for empty string.
- The fix is two-fold: (1) async paths return `null`, (2) `sendReply()` also rejects empty strings as a safety net.

**Recommended change:**

```java
private SendResult sendReply(Message message, FishuAppI app, String replyContent) {
    if (replyContent == null || replyContent.isBlank()) {
        log.debug("App {} returned null/blank, skipping reply", app.getAppId());
        return SendResult.success(null);
    }
    // ... existing reply logic
}
```

**What NOT to do:**
- ❌ Don't return a sentinel string like `"__ASYNC__"` — introduces magic values.
- ❌ Don't change the return type to `Optional<String>` — breaks the `FishuAppI` interface contract for all apps, large blast radius for a simple fix.

**Sources:**
- CONCERNS.md "Empty String Return from Async Tasks Creates Ghost Reply Bubbles" — HIGH confidence
- `BotMessageAppService.sendReply()` existing null handling — HIGH confidence

---

### 7. Session ID Passing: Structured Return Objects (not Text Parsing)

| Aspect | Decision |
|--------|----------|
| Pattern | Return structured result objects from task execution, not formatted strings |
| Confidence | **HIGH** — directly addresses R10 and documented fragility |

**What:** Replace the pattern where `OpenCodeMessageAppService.extractSessionId()` parses `"Session ID: \`"` from formatted reply markdown. Instead, task executors return a structured result that includes `sessionId` as a first-class field.

**Why:**
- The current extraction method (`replyContent.indexOf("Session ID: \`")`) breaks if the response formatting changes even slightly — documented as a critical fragile area in CONCERNS.md.
- Two separate extraction mechanisms exist (`OpenCodeResponseFormatter.extractSessionId()` and `OpenCodeMessageAppService.extractSessionId()`), creating confusion about which one is used where.

**Recommended approach:**

```java
@Value
public class OpenCodeExecutionResult {
    String replyContent;    // formatted text for display (nullable for async)
    String sessionId;       // structured session ID (nullable if no session created)
    boolean async;          // true if response will come via streaming card
}
```

The task executor returns `OpenCodeExecutionResult` instead of `String`. The app service reads `result.getSessionId()` directly instead of parsing markdown.

**What NOT to do:**
- ❌ Don't keep both extraction paths — pick one structured approach and remove the text-parsing fallback.
- ❌ Don't embed session IDs in response formatting and then extract them — the formatting is for humans, data passing is for code.

**Sources:**
- CONCERNS.md "Session ID Extraction via Text Parsing" — HIGH confidence
- PROJECT.md R10 "Robust session ID passing" — HIGH confidence

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| State machine | Hand-rolled enum | Spring Statemachine | Overkill for 5 states; adds framework dep to domain; requires persist/restore per message; violates COLA |
| State machine | Hand-rolled enum | Stateless4j / EasyFlow | Extra dependency for simple state derivation; our states are computed, not transitioned |
| Context propagation | Explicit parameter | Spring `@Scope("request")` | No HTTP request scope in WebSocket path; doesn't cross async boundaries |
| Context propagation | Explicit parameter | `ThreadLocal` + `TaskDecorator` | Works but implicit; easy to forget cleanup; harder to test; can leak between pooled threads |
| Command routing | Strategy map (direct lookup) | Chain of Responsibility | CoR is O(n) and designed for "unknown handler" — we know exactly which handler each command maps to |
| Command routing | Strategy map (direct lookup) | Spring Integration MessageRouter | Brings in spring-integration dependency; designed for enterprise integration, not command dispatch |
| Command routing | Strategy map (direct lookup) | Annotation-based (`@CommandMapping`) | Over-engineered for ~10 commands; adds reflection/proxy complexity; harder to debug |
| Session ID passing | Structured return object | Keep text parsing + add more tests | Fragile by design; tests can't prevent formatting drift in other code paths |

---

## Pattern Integration Map

How these patterns work together in a single message flow:

```
Message arrives (WebSocket)
    │
    ▼
[Adapter Layer] FeishuEventListener
    │  Parse raw event → Message
    ▼
[App Layer] MessageContext.resolve(message, gateways)
    │  ONE database query → derives ConversationState
    │  MessageContext: { message, contextRef, state, appId, sessionId }
    ▼
[App Layer] Route by state
    ├── TOPIC_ACTIVE_SESSION + no command prefix → plain text chat (Pattern 5)
    ├── Explicit /oc command → SubCommandHandler lookup (Pattern 3)
    ├── GROUP_CHAT implicit → help guidance
    └── TOPIC_BOUND_OTHER_APP → rejection
    │
    ▼
[Domain Layer] SubCommandHandler.execute(context, args)
    │  Returns OpenCodeExecutionResult (Pattern 7)
    │  Async paths return result.async=true, replyContent=null (Pattern 6)
    ▼
[App Layer] Post-processing
    │  Save session from result.sessionId (Pattern 7)
    │  Migrate binding if threadId changed (Pattern 4)
    │  Send reply if replyContent is non-null/non-blank (Pattern 6)
    ▼
Done (1 DB read, at most 1-2 DB writes)
```

---

## COLA Layer Placement

| Component | Layer | Rationale |
|-----------|-------|-----------|
| `ConversationState` enum | domain | Core domain concept — the state of a conversation |
| `MessageContext` value object | domain | Pure data holder, no framework dependencies |
| `MessageContextResolver` | app | Orchestrates gateway calls to build MessageContext — app-layer coordination |
| `SubCommandHandler` interface | domain | Domain contract for command behavior |
| `SubCommandHandler` implementations | domain (in `opencode/` package) | Business logic lives in domain |
| `OpenCodeCommandRouter` | domain | Lookup + state guard validation — domain routing logic |
| Binding migration logic | app | Cross-cutting coordination between gateways — app-layer orchestration |
| `OpenCodeExecutionResult` | domain | Return type from domain execution |

---

## No New Dependencies Required

All patterns use existing stack:
- **Java 17** enums, records, sealed types (if needed), `Map.of()`, `Set.of()`
- **Spring Boot 3.2.1** `@Component` for auto-discovery of SubCommandHandler implementations
- **Lombok** `@Value` for immutable value objects
- **SQLite** via existing `ImContextBindingGateway` and `AppSessionGateway`
- **No new Maven dependencies**

---

## Migration Safety

| Current Component | Action | Risk |
|-------------------|--------|------|
| `TopicState` enum | Remove after unification | LOW — used only in `OpenCodeApp` and `OpenCodeSessionManager` |
| `ContextSessionState` enum | Remove after unification | LOW — used only in `ContextSessionOrchestrator` pipeline |
| `OpenCodeCommandHandler.handle()` switch statement | Replace with router + individual handlers | MEDIUM — large method, many test cases |
| `OpenCodeApp.getCommandWhitelist()` | Remove — guards move to handlers | LOW — coupled to `TopicState` which is being removed |
| `OpenCodeMessageAppService.extractSessionId()` | Remove — replaced by structured result | LOW — single call site |
| `BotMessageService.routeMessage()` | Simplify — state resolution moves to `MessageContext` | MEDIUM — central routing, many tests |

**Test impact:** All 261 existing tests must continue passing. The recommended approach is to build the new patterns alongside the old ones, then swap at the routing layer, then remove old code. This allows incremental migration without a big-bang rewrite.

---

## Sources

| Source | Type | Confidence | Used For |
|--------|------|------------|----------|
| Codebase analysis (OpenCodeApp, BotMessageService, etc.) | Primary | HIGH | Current architecture understanding |
| CONCERNS.md documented issues | Primary | HIGH | Problem identification |
| PROJECT.md requirements R1-R10 | Primary | HIGH | Target behavior definition |
| Spring Statemachine docs (Context7) | Official docs | HIGH | SSM capability evaluation → decided against |
| Amio.io "Enter the State Pattern" (2025-07) | Blog | MEDIUM | Chatbot state pattern validation |
| Medium "Request Context Propagation in Spring Boot Async Threads" (2025-11) | Blog | MEDIUM | Context propagation pattern selection |
| java-design-patterns.com Pipeline Pattern | Reference | MEDIUM | Pattern comparison (pipeline vs routing) |
| Refactoring.guru Chain of Responsibility | Reference | MEDIUM | Pattern comparison (CoR vs direct lookup) |

---

*Research completed: 2026-04-07*

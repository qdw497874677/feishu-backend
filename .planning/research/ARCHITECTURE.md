# Architecture Patterns

**Domain:** Chatbot conversation flow redesign
**Researched:** 2026-04-07

## Recommended Architecture

### Message Processing Pipeline (Redesigned)

```
┌─────────────────────────────────────────────────────────────────┐
│                        ADAPTER LAYER                            │
│  FeishuEventListener → MessageEventParser → Message             │
│  P2CardActionTrigger → CardCommandAdapter → Message             │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                         APP LAYER                               │
│                                                                 │
│  ┌──────────────────────────────────────────┐                   │
│  │ MessageContextResolver                   │                   │
│  │   resolve(Message) → MessageContext      │ ← ONE DB query    │
│  │   { contextRef, state, appId, sessionId }│                   │
│  └──────────────────┬───────────────────────┘                   │
│                     │                                           │
│  ┌──────────────────▼───────────────────────┐                   │
│  │ Message Dispatcher (app service)         │                   │
│  │                                          │                   │
│  │   state=TOPIC_ACTIVE + plain text        │                   │
│  │     → direct chat forwarding             │                   │
│  │                                          │                   │
│  │   state=* + /oc command                  │                   │
│  │     → OpenCodeCommandRouter              │                   │
│  │                                          │                   │
│  │   state=* + /other command               │                   │
│  │     → BotMessageService (existing)       │                   │
│  │                                          │                   │
│  │   state=TOPIC_BOUND_OTHER_APP            │                   │
│  │     → rejection                          │                   │
│  └──────────────────┬───────────────────────┘                   │
│                     │                                           │
│  ┌──────────────────▼───────────────────────┐                   │
│  │ Post-Processing                          │                   │
│  │   - Persist session from structured result│                  │
│  │   - Migrate binding (chatId→threadId)    │                   │
│  │   - Send reply (skip if null/blank)      │                   │
│  └──────────────────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                        DOMAIN LAYER                             │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐        │
│  │ ConversationState (enum)                            │        │
│  │   NO_CONTEXT | GROUP_CHAT | TOPIC_UNBOUND           │        │
│  │   TOPIC_BOUND_NO_SESSION | TOPIC_ACTIVE_SESSION     │        │
│  │   TOPIC_BOUND_OTHER_APP                             │        │
│  └─────────────────────────────────────────────────────┘        │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐        │
│  │ MessageContext (value object)                       │        │
│  │   message, contextRef, state, boundAppId,           │        │
│  │   boundSessionId                                    │        │
│  └─────────────────────────────────────────────────────┘        │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐        │
│  │ SubCommandHandler (interface)                       │        │
│  │   commandNames(), allowedStates(), execute(ctx)     │        │
│  │                                                     │        │
│  │   Implementations (in opencode/ package):           │        │
│  │     ChatHandler, ChatNowHandler, ProjectsHandler,   │        │
│  │     SessionsHandler, BindHandler, ResetHandler,     │        │
│  │     NewHandler, ConnectHandler, CommandsHandler      │        │
│  └─────────────────────────────────────────────────────┘        │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐        │
│  │ OpenCodeExecutionResult (value object)              │        │
│  │   replyContent, sessionId, async                    │        │
│  └─────────────────────────────────────────────────────┘        │
│                                                                 │
│  ┌─────────────────────────────────────────────────────┐        │
│  │ Gateway Interfaces (unchanged)                      │        │
│  │   FeishuGateway, ImContextBindingGateway,           │        │
│  │   AppSessionGateway, OpenCodeGateway                │        │
│  └─────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                     INFRASTRUCTURE LAYER                        │
│                                                                 │
│  Gateway implementations (unchanged)                            │
│  Reply strategies (unchanged)                                   │
│  Feishu SDK integration (unchanged)                             │
└─────────────────────────────────────────────────────────────────┘
```

### Component Boundaries

| Component | Responsibility | Layer | Communicates With |
|-----------|---------------|-------|-------------------|
| `MessageContextResolver` | Resolve binding + session → `MessageContext` (1 DB query) | app | `ImContextBindingGateway`, `AppSessionGateway` |
| `ConversationState` | Enum representing all possible conversation states | domain | Used by all routing/validation logic |
| `MessageContext` | Immutable snapshot of resolved state for current message | domain | Passed through entire pipeline |
| `OpenCodeCommandRouter` | Map sub-command → handler, validate state guards | domain | `SubCommandHandler` implementations |
| `SubCommandHandler` impls | Individual command logic (chat, projects, sessions, etc.) | domain | `OpenCodeGateway`, `OpenCodeTaskExecutor`, `OpenCodeSessionManager` |
| `OpenCodeExecutionResult` | Structured result from command execution | domain | Returned by handlers, consumed by app layer |
| `BotMessageService` | Routing for non-OpenCode apps (help, time, bash, history) | domain | `AppRegistry`, `ImContextBindingGateway` |
| `BotMessageAppService` | Reply sending, binding persistence | app | `ReplyStrategyFactory`, `FeishuGateway` |

### Data Flow

```
Message in
    │
    ├─► FeishuContextResolver.resolve(message) → ImContextRef
    │
    ├─► bindingGateway.findBinding(contextRef) → Optional<ImContextBinding>
    │     (if binding exists and has sessionId)
    │     └─► sessionGateway.getSession(appId, sessionId) → Optional<AppSession>
    │
    ├─► ConversationState derived from: contextRef type + binding presence + session presence
    │
    ├─► MessageContext assembled: { message, contextRef, state, appId, sessionId }
    │
    ├─► Routing decision based on state + message content type
    │
    ├─► Handler execution → OpenCodeExecutionResult { replyContent, sessionId, async }
    │
    ├─► Session persistence (if result.sessionId != null)
    │
    ├─► Binding migration (if reply created new threadId ≠ original contextId)
    │
    └─► Reply sent (if replyContent is non-null and non-blank)
```

## Patterns to Follow

### Pattern 1: Derive State, Don't Track It

**What:** State is computed from persisted data at the start of each message, not maintained as mutable state across messages.

**When:** Every message processing cycle.

**Why:** The chatbot is inherently stateless between messages (no in-memory conversation object). State is reconstructed from DB on each message. This is correct — don't fight it by introducing in-memory state tracking that can drift from persisted data.

```java
// State derivation — pure function, no side effects
public static ConversationState derive(ImContextRef contextRef, 
                                        Optional<ImContextBinding> binding,
                                        Optional<AppSession<?>> session) {
    if (contextRef == null) return NO_CONTEXT;
    if (contextRef.isChat()) return GROUP_CHAT;
    // contextRef is a thread
    if (binding.isEmpty()) return TOPIC_UNBOUND;
    if (!binding.get().isForApp("opencode")) return TOPIC_BOUND_OTHER_APP;
    if (binding.get().getSessionId() == null || session.isEmpty()) return TOPIC_BOUND_NO_SESSION;
    return TOPIC_ACTIVE_SESSION;
}
```

### Pattern 2: One Resolution, Many Consumers

**What:** Resolve the message context exactly once. Every downstream component receives the pre-resolved `MessageContext` as a parameter.

**When:** At the boundary between adapter and app layers.

**Why:** Eliminates the N-query problem where each component independently calls `findBinding()`.

```java
// App layer entry point
public SendResult handleMessage(Message message) {
    MessageContext ctx = contextResolver.resolve(message);
    // All subsequent calls receive ctx, never re-query the DB
    return dispatcher.dispatch(ctx);
}
```

### Pattern 3: Co-Located Guards

**What:** Each command handler declares its own state requirements. The router validates guards before execution.

**When:** Adding or modifying any sub-command.

**Why:** Impossible for command routing and state validation to drift apart. Adding a new command requires implementing one interface — no separate whitelist to update.

```java
@Component
public class ChatSubCommandHandler implements SubCommandHandler {
    @Override
    public Set<String> commandNames() { return Set.of("chat"); }
    
    @Override
    public Set<ConversationState> allowedStates() { 
        return Set.of(TOPIC_ACTIVE_SESSION, TOPIC_BOUND_NO_SESSION); 
    }
    
    @Override
    public String execute(MessageContext ctx, String[] args) { ... }
}
```

## Anti-Patterns to Avoid

### Anti-Pattern 1: Dual State Systems

**What:** Having two parallel enums (`TopicState` and `ContextSessionState`) that represent overlapping concepts from the same underlying data.

**Why bad:** Causes redundant DB queries (each system queries independently), semantic confusion (developers must understand both), and potential inconsistency (the two systems can disagree on the "current state" of the same message).

**Instead:** One unified `ConversationState` enum with one resolver.

### Anti-Pattern 2: Text-Parsing for Data Passing

**What:** Embedding structured data (session IDs) in formatted user-facing text, then parsing it back out with `indexOf("Session ID: \`")`.

**Why bad:** Any formatting change silently breaks data extraction. Two separate extraction mechanisms exist, creating confusion about which one is canonical.

**Instead:** Return structured result objects (`OpenCodeExecutionResult`) with explicit fields for programmatic data.

### Anti-Pattern 3: Monolithic Switch-Case Routing

**What:** A single 77-line switch statement that routes all sub-commands, with a separate `CommandWhitelist` system that must be kept in sync.

**Why bad:** Adding a command requires changes in two places. The switch and whitelist can drift apart. Testing requires large test methods that cover all branches.

**Instead:** Registry of `SubCommandHandler` beans, auto-discovered by Spring `@Component` scanning. Each handler is independently testable.

### Anti-Pattern 4: Late Context Resolution

**What:** Resolving the IM context (calling `FeishuContextResolver.resolve()`) at multiple points deep in the call chain rather than once at the top.

**Why bad:** Each resolution triggers a gateway lookup. The same `resolve()` → `findBinding()` chain executes 4+ times per message. Also makes it hard to reason about what state the system is in at any given point.

**Instead:** Resolve once at the pipeline entry, pass `MessageContext` explicitly.

## Scalability Considerations

| Concern | Current (low traffic) | At scale |
|---------|----------------------|----------|
| DB queries per message | 4-6 (redundant) | 1-2 with MessageContext pattern |
| State consistency | Possible drift between dual systems | Single source of truth |
| Command handler addition | 2 files to change (switch + whitelist) | 1 file (new SubCommandHandler) |
| Testing complexity | Large monolithic test classes | Small, focused per-handler tests |
| Memory per message | Multiple intermediate objects | Single MessageContext + result |

---

*Architecture research completed: 2026-04-07*

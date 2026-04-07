# Phase 1: Context Foundation - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Fix the data flow layer — context propagation from chatId to threadId, structured session ID passing, request-scoped caching, and graceful degradation. After this phase, context binding is reliable and performant. No new commands, no UI changes, no card interactions.

Requirements: CTX-01, CTX-02, CTX-03, CTX-04, CTX-05, COMPAT-01

</domain>

<decisions>
## Implementation Decisions

### Binding Propagation (CTX-01)
- **D-01:** When a reply creates a new topic (SendResult returns threadId), immediately copy the full binding (appId + sessionId) from chatId context to the new threadId context. Synchronous, in the reply path.
- **D-02:** Copy happens in `BotMessageAppService.sendReply()` right after `SendResult` is received. Not lazy/deferred — the next message in the topic must find the binding.
- **D-03:** The original chatId binding is NOT preserved (replaced/migrated, not duplicated).

### Request-Scoped Caching (CTX-03)
- **D-04:** Introduce a `MessageContext` object that holds `ImContextRef`, `ImContextBinding` (nullable), and `AppSession` (nullable). Resolved once at the pipeline entry point, passed as a method parameter through the entire processing chain.
- **D-05:** Explicit parameter passing (not ThreadLocal, not Spring RequestScope). This keeps it testable, async-safe, and framework-agnostic.
- **D-06:** Every downstream consumer (`BotMessageService`, `OpenCodeMessageAppService`, `OpenCodeApp`, `OpenCodeSessionManager`) receives `MessageContext` instead of independently calling `findBinding()`.

### Graceful Degradation (CTX-05)
- **D-07:** Old/unbound topics receive a one-line text reply: guidance to use `/oc projects` in the group chat to start. No card, no button — simple text.
- **D-08:** Degradation events are logged at DEBUG level with threadId and degradation reason. No WARN/INFO — this is expected behavior for old topics.

### Structured sessionId Passing (CTX-02)
- **D-09:** Create a unified result DTO that ALL apps return from `execute()`. `FishuAppI.execute()` return type changes from `String` to a result object (e.g., `AppExecutionResult`) containing at minimum: `replyContent` (String), plus optional fields like `sessionId`, `sessionCreated` (boolean), metadata.
- **D-10:** This is a breaking change to the `FishuAppI` interface. All 5 app implementations (BashApp, TimeApp, HelpApp, HistoryApp, OpenCodeApp) must be updated. Tests that mock or assert on `execute()` return values must be updated.
- **D-11:** `OpenCodeMessageAppService.extractSessionId()` (the fragile text-parsing method) is eliminated entirely. Session ID comes from the result object's structured field.

### IM Binding vs App Session Independence (CTX-04)
- **D-12:** `ImContextBinding` (IM layer) and `AppSession` (app layer) remain independent storage. They are read together into `MessageContext` for convenience but managed by separate gateways (`ImContextBindingGateway` and `AppSessionGateway`). No schema merge.

### Agent's Discretion
- `MessageContext` field naming and exact class location (domain model package recommended)
- `AppExecutionResult` field set beyond replyContent + sessionId (could include `replyMode`, `shouldPersistBinding`, etc. if useful)
- Whether to introduce a convenience factory/builder for `AppExecutionResult` in simple apps (e.g., `AppExecutionResult.text("Hello")`)
- Exact wording of the degradation guidance message
- Whether to also fix the redundant `getVersion()` check in `AppSessionGatewayImpl.updateSession()` (opportunistic cleanup)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Context binding system
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/ImContextRef.java` — Value object for platform-agnostic context identification
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/ImContextBinding.java` — Binding model mapping context → (appId, sessionId)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/ImContextBindingGateway.java` — Binding persistence interface
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java` — SQLite binding implementation (has non-atomic read-write concern)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/feishu/FeishuContextResolver.java` — Message → ImContextRef resolution

### Session system
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/AppSessionGateway.java` — Session persistence interface
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java` — SQLite session implementation
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestrator.java` — Two-phase binding orchestrator interface
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImpl.java` — Orchestrator implementation

### Message processing pipeline
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java` — Pipeline entry point (MessageContext creation point)
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java` — App execution + reply orchestration (binding propagation point)
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java` — OpenCode orchestration (has fragile extractSessionId() to eliminate)

### App interface
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java` — App interface (execute() return type change)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java` — Most complex app implementation
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java` — Async execution (returns "" causing ghost bubbles)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java` — Command routing

### Codebase analysis
- `.planning/codebase/CONCERNS.md` — All 6 broken points documented with file locations and fix approaches
- `.planning/codebase/ARCHITECTURE.md` — Data flow diagrams and key abstractions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ImContextRef` + `ImContextBinding`: Already platform-agnostic value objects — extend, don't replace
- `ContextSessionOrchestrator`: Two-phase binding model (enterAppContext → activateSession) — works well, keep it
- `FeishuContextResolver`: topicId-preferred resolution — correct behavior, just needs MessageContext integration
- `ReplyStrategyFactory` + strategies: Reply dispatch works — just needs to handle the new result DTO instead of raw String

### Established Patterns
- **Gateway pattern**: Domain defines interface, infrastructure implements — all new gateways follow this
- **Strategy pattern**: ReplyStrategyFactory with EnumMap — any new reply behavior uses this
- **Constructor injection**: Universal — no field or setter injection
- **Anti-corruption layer**: MessageEventParserImpl isolates SDK — maintain this boundary
- **COLA layers**: adapter → app → domain ← infrastructure — all changes must respect layer dependencies

### Integration Points
- `ReceiveMessageListenerExe.execute()` — MessageContext creation happens here (pipeline entry)
- `BotMessageAppService.sendReply()` — Binding propagation happens here (after SendResult)
- `FishuAppI.execute()` — Return type change (String → AppExecutionResult) affects all callers
- `BotMessageService.routeMessage()` — Must accept MessageContext parameter
- `OpenCodeMessageAppService.tryHandle()` / `handleMessageInternal()` — Must use MessageContext instead of independent binding lookup

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. The key constraint is that all 261 existing tests must continue passing (COMPAT-01).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 01-context-foundation*
*Context gathered: 2026-04-07*

# Architecture

**Analysis Date:** 2026-04-06

## Pattern Overview

**Overall:** COLA (Clean Object-oriented and Layered Architecture) — 6-module Maven multi-module project

**Key Characteristics:**
- Strict 4-layer separation: adapter → app → domain ← infrastructure
- Domain-defined interfaces (Gateway pattern) with infrastructure implementations
- Strategy pattern for reply handling, eliminating if-else branching
- Anti-corruption layer isolating Feishu SDK from domain logic
- Two-phase IM context binding model for session-aware applications
- WebSocket long-connection communication (webhook explicitly forbidden)

## Layers

**Start Layer (feishu-bot-start):**
- Purpose: Application bootstrap and configuration
- Location: `feishu-bot-start/src/main/java/com/qdw/feishu/`
- Contains: `Application.java`, `application.yml`
- Depends on: All other modules (transitively)
- Used by: Nothing (entry point)
- Key file: `feishu-bot-start/src/main/java/com/qdw/feishu/Application.java`

**Adapter Layer (feishu-bot-adapter):**
- Purpose: External event ingestion — listens to Feishu WebSocket events and dispatches to app layer
- Location: `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/`
- Contains: Event listeners, exception handlers, test controllers
- Depends on: app (ReceiveMessageListenerExe), domain (MessageListenerGateway, FeishuConfig, Message)
- Used by: start (component scanning)
- Key files:
  - `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/FeishuEventListener.java` — ApplicationRunner that starts WebSocket listener on boot
  - `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/exception/GlobalExceptionHandler.java` — centralized exception handling
  - `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java` — REST endpoint for manual testing

**App Layer (feishu-bot-app):**
- Purpose: Use case orchestration, session management, message routing coordination
- Location: `feishu-bot-app/src/main/java/com/qdw/feishu/app/`
- Contains: Message listener executor, app services, session orchestrator, command router
- Depends on: domain (all domain interfaces and models), client (DTO)
- Used by: adapter (FeishuEventListener → ReceiveMessageListenerExe)
- Key files:
  - `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java` — entry point for all incoming messages; async; deduplicates, routes to OpenCode or general bot
  - `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java` — orchestrates routing decision → app execution → reply strategy → binding persistence
  - `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java` — specialized handler for OpenCode with context-session awareness
  - `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestrator.java` — interface for two-phase context-session binding
  - `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImpl.java` — coordinates ImContextBindingGateway + AppSessionGateway

**Domain Layer (feishu-bot-domain):**
- Purpose: Core business logic, domain models, gateway interfaces, app implementations
- Location: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/`
- Contains: Domain entities, app implementations, gateway interfaces, strategy interfaces, routing, validation, session models
- Depends on: client (DTO), COLA framework
- Used by: app, infrastructure
- Key packages:
  - `domain/app/` — FishuAppI interface + concrete apps (BashApp, TimeApp, HelpApp, HistoryApp)
  - `domain/opencode/` — OpenCodeApp + its command handler, session manager, task executor, streaming handler
  - `domain/gateway/` — All gateway interfaces (FeishuGateway, MessageListenerGateway, ImContextBindingGateway, AppSessionGateway, etc.)
  - `domain/message/` — Message entity, SendResult, BotRoutingDecision, HandledMessageResult
  - `domain/reply/` — ReplyStrategy interface + ReplyStrategyFactory
  - `domain/service/` — BotMessageService (routing), MessageDeduplicator
  - `domain/model/` — ImContextBinding, ImContextRef, BindingResult
  - `domain/session/` — AppSession, ContextSessionState, TypeToken
  - `domain/card/` — StreamingCardManager
  - `domain/command/` — UnifiedCommand, CommandWhitelist, CommandWhitelistValidator
  - `domain/adapter/` — CommandAdapter, ResponseAdapter interfaces
  - `domain/router/` — AppRouter, UnifiedCommandRouter
  - `domain/feishu/` — FeishuContextResolver (utility class)
  - `domain/topic/` — TopicState, TopicCommandValidator
  - `domain/processor/` — EventProcessor (unified event→command→route→respond pipeline)

**Infrastructure Layer (feishu-bot-infrastructure):**
- Purpose: Gateway implementations, SDK integration, persistence, strategy implementations
- Location: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/`
- Contains: Gateway impls, reply strategies, parser impl, config, adapters
- Depends on: domain (implements interfaces), Feishu SDK, SQLite
- Used by: Spring DI (auto-wired into domain/app layer via interface)
- Key files:
  - `infrastructure/gateway/MessageListenerGatewayImpl.java` — WebSocket client + EventDispatcher; handles P2MessageReceiveV1 + P2CardActionTrigger
  - `infrastructure/gateway/FeishuGatewayImpl.java` — Feishu REST API calls (sendMessage, sendDirectReply, addReaction, etc.)
  - `infrastructure/gateway/ImContextBindingGatewayImpl.java` — SQLite-backed IM context binding persistence
  - `infrastructure/gateway/AppSessionGatewayImpl.java` — SQLite-backed app session persistence
  - `infrastructure/gateway/OpenCodeGatewayImpl.java` — HTTP client to OpenCode server API
  - `infrastructure/gateway/OpenCodeEventGatewayImpl.java` — SSE client for OpenCode streaming events
  - `infrastructure/gateway/CardGatewayImpl.java` — Feishu CardKit API for streaming card updates
  - `infrastructure/parser/MessageEventParserImpl.java` — anti-corruption layer: P2MessageReceiveV1 → Message
  - `infrastructure/reply/DirectReplyStrategy.java` — sends reply without creating topic
  - `infrastructure/reply/TopicReplyStrategy.java` — sends reply within topic thread
  - `infrastructure/reply/DefaultReplyStrategy.java` — fallback, passes topicId through to gateway
  - `infrastructure/config/DomainServiceConfig.java` — wires ReplyStrategyFactory bean
  - `infrastructure/adapter/CardCommandAdapter.java` — adapts card events to UnifiedCommand
  - `infrastructure/adapter/MessageCommandAdapter.java` — adapts messages to UnifiedCommand

**Client Layer (feishu-bot-client):**
- Purpose: DTO and API contracts
- Location: `feishu-bot-client/src/main/java/com/qdw/feishu/client/`
- Contains: MessageServiceI, ReceiveMessageCmd, ReceiveMessageQry
- Depends on: Nothing
- Used by: app, domain

## Data Flow

**Primary Message Flow (text message from Feishu):**

1. Feishu pushes WebSocket event → `MessageListenerGatewayImpl` receives `P2MessageReceiveV1`
2. `MessageEventParserImpl.parse()` converts SDK event → domain `Message` (anti-corruption layer)
3. `MessageListenerGatewayImpl` calls `messageHandler.accept(message)` → `ReceiveMessageListenerExe.execute()`
4. `ReceiveMessageListenerExe` (async) deduplicates via `MessageDeduplicator.isProcessed(eventId)`
5. Tries `OpenCodeMessageAppService.tryHandle(message)` first — returns true if OpenCode should handle
6. Falls back to `BotMessageAppService.handleMessage(message)` for general commands
7. `BotMessageAppService` calls `BotMessageService.routeMessage()` → returns `BotRoutingDecision` (app + shouldPersistBinding)
8. Executes `app.execute(message)` → gets reply content string
9. Selects `ReplyStrategy` via `ReplyStrategyFactory.getStrategy(app.getReplyMode())`
10. `strategy.reply(message, content, topicId)` → calls `FeishuGateway.sendMessage()` or `sendDirectReply()`
11. Persists IM context binding if `decision.shouldPersistBinding()` is true

**OpenCode Message Flow (context-aware):**

1. `OpenCodeMessageAppService.tryHandle()` resolves `ImContextRef` from message via `FeishuContextResolver`
2. Loads `ContextSessionStatus` via `ContextSessionOrchestrator.loadStatus(contextRef, "opencode", typeToken)`
3. Status determines routing:
   - `UNBOUND` → enters app context, then delegates to `BotMessageAppService`
   - `BOUND_TO_OTHER_APP` → rejects with error
   - `IN_APP_NO_SESSION` + chat command → shows guidance
   - `IN_APP_WITH_SESSION` or explicit command → delegates to `BotMessageAppService` → `OpenCodeApp.execute()`
4. `OpenCodeApp.execute()` detects `TopicState` via `OpenCodeSessionManager.detectTopicState()`
5. Gets `CommandWhitelist` for state, delegates to `OpenCodeCommandHandler.handle()`
6. `OpenCodeCommandHandler` validates command against whitelist, then routes to specific handler
7. For `chat`/`chatnow` → `OpenCodeTaskExecutor.executeTask()` → `@Async` → `OpenCodeGateway.executeCommand()`
8. Response sent via `FeishuGateway.sendMessage()`, session saved via `OpenCodeSessionManager.saveSession()`

**Card Action Flow (button click):**

1. Feishu pushes `P2CardActionTrigger` event to WebSocket
2. `MessageListenerGatewayImpl.handleCardAction()` extracts `action` value from card button
3. Constructs pseudo-`Message` with `content = "/" + action`, synthetic `eventId`, `sender`, `chatId`
4. Passes pseudo-Message through same `messageHandler.accept(message)` pipeline
5. Processed identically to text messages from that point

**Streaming Response Flow (OpenCode SSE):**

1. `OpenCodeTaskExecutor.executeTask()` registers session with `OpenCodeStreamingHandler`
2. `OpenCodeStreamingHandler.registerSession()` creates streaming card via `StreamingCardManager.createAndSend()`
3. SSE events from `OpenCodeEventGatewayImpl` → `OpenCodeStreamingHandler.handleEvent()`
4. Text deltas accumulated in buffer, flushed every 2s via `StreamingCardManager.update()` (CardKit API)
5. On session complete: final flush + card cleanup
6. Fallback mode: if card creation fails, falls back to regular `FeishuGateway.sendMessage()` for updates

**State Management:**
- **Message deduplication:** `MessageDeduplicator` (in-memory, eventId-based)
- **IM context binding:** `ImContextBindingGateway` → SQLite persistence (`feishu-bot-start/data/`)
- **App sessions:** `AppSessionGateway` → SQLite persistence with optimistic locking (version field)
- **Streaming state:** `OpenCodeStreamingHandler` (in-memory ConcurrentHashMaps: session→card, session→buffer, etc.)

## Key Abstractions

**FishuAppI (Application Interface):**
- Purpose: Pluggable application contract — all bot commands implement this
- Examples: `domain/app/BashApp.java`, `domain/app/TimeApp.java`, `domain/app/HelpApp.java`, `domain/app/HistoryApp.java`, `domain/opencode/OpenCodeApp.java`
- Pattern: Strategy pattern with Spring auto-discovery. All `@Component` implementations of `FishuAppI` are auto-collected into `AppRegistry` via constructor injection of `List<FishuAppI>`.
- Key methods: `getAppId()`, `execute(Message)`, `getReplyMode()`, `getAppAliases()`, `getCommandWhitelist(TopicState)`, `isTopicInitialized(Message)`

**AppRegistry:**
- Purpose: Central registry of all applications, provides lookup by appId
- Location: `domain/core/AppRegistry.java`
- Pattern: Auto-populated via Spring DI, stores `Map<String, FishuAppI>` indexed by appId

**ImContextRef + ImContextBinding:**
- Purpose: Platform-agnostic IM conversation context identification and binding
- `ImContextRef` (`domain/model/ImContextRef.java`): Value object — `(platform, contextType, contextId)` e.g., `feishu:thread:xxx` or `feishu:chat:xxx`
- `ImContextBinding` (`domain/model/ImContextBinding.java`): Maps ImContextRef → `(appId, sessionId)` with timestamps. Supports two-phase binding (null sessionId = app context only)
- `FeishuContextResolver` (`domain/feishu/FeishuContextResolver.java`): Utility to resolve `Message` → `ImContextRef` (topicId preferred over chatId)

**ContextSessionOrchestrator:**
- Purpose: Coordinates binding gateway + session gateway for unified context-session state
- Location: `app/session/ContextSessionOrchestrator.java` (interface), `app/session/ContextSessionOrchestratorImpl.java` (impl)
- Pattern: Two-phase binding model:
  1. `enterAppContext()` — bind with null sessionId
  2. `activateSession()` — update binding with concrete sessionId
- States: `UNBOUND` → `IN_APP_NO_SESSION` → `IN_APP_WITH_SESSION` (also: `BOUND_TO_OTHER_APP`, dangling detection)

**ReplyStrategy + ReplyStrategyFactory:**
- Purpose: Polymorphic reply dispatch without if-else
- Location: `domain/reply/ReplyStrategy.java` (interface), `domain/reply/ReplyStrategyFactory.java` (factory), strategies in `infrastructure/reply/`
- Pattern: Spring collects all `ReplyStrategy` beans, `DomainServiceConfig` wires them into `ReplyStrategyFactory`. Factory uses `EnumMap<ReplyMode, ReplyStrategy>`.
- Modes: `DIRECT` (no topic), `TOPIC` (within thread), `DEFAULT` (passthrough)

**Gateway Pattern:**
- Purpose: Domain defines interfaces, infrastructure implements — inverted dependency
- Interface examples: `domain/gateway/FeishuGateway.java`, `domain/gateway/MessageListenerGateway.java`, `domain/gateway/ImContextBindingGateway.java`, `domain/gateway/AppSessionGateway.java`, `domain/gateway/OpenCodeGateway.java`, `domain/gateway/CardGateway.java`, `domain/gateway/MessageEventParser.java`
- Implementation: All in `infrastructure/gateway/` with `@Component` annotation

**UnifiedCommand + EventProcessor:**
- Purpose: Unified command model for both message and card events
- `UnifiedCommand` (`domain/command/UnifiedCommand.java`): Normalized command with `appId`, `subCommand`, `args[]`, `source` (MESSAGE or CARD)
- `EventProcessor` (`domain/processor/EventProcessor.java`): Pipeline: `event → CommandAdapter → UnifiedCommand → UnifiedCommandRouter → BizResult → ResponseAdapter`
- Currently coexists with the legacy `Message`-based flow; apps support both `execute(Message)` and `execute(UnifiedCommand)` via `FishuAppI`

## Entry Points

**Application Startup:**
- Location: `feishu-bot-start/src/main/java/com/qdw/feishu/Application.java`
- Triggers: `SpringApplication.run()` → Spring Boot auto-configuration
- Responsibilities: Component scanning, bean wiring

**WebSocket Event Listener:**
- Location: `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/FeishuEventListener.java`
- Triggers: `ApplicationRunner.run()` after Spring context initialization
- Responsibilities: Starts `MessageListenerGateway.startListening()` with `ReceiveMessageListenerExe::execute` as handler
- Condition: `@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")`

**WebSocket Message Handler:**
- Location: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java`
- Triggers: Feishu SDK `EventDispatcher` dispatches `P2MessageReceiveV1` and `P2CardActionTrigger` events
- Responsibilities: Parse events via anti-corruption layer, construct pseudo-Messages for card actions, delegate to `messageHandler`

## Error Handling

**Strategy:** Layered exception hierarchy with business vs. system exception distinction

**Patterns:**
- `MessageBizException` (`domain/exception/MessageBizException.java`): Business-level errors (e.g., cross-app command rejection). Caught by `ReceiveMessageListenerExe` and sent as user-facing error reply via `FeishuGateway.sendMessage()`
- `MessageInvalidException` (`domain/exception/MessageInvalidException.java`): Validation failures
- `MessageSysException` (`domain/exception/MessageSysException.java`): System-level errors
- `ConnectionLostException` (`domain/exception/ConnectionLostException.java`): WebSocket disconnection
- `OptimisticLockException` (`domain/exception/OptimisticLockException.java`): Concurrent session update conflict
- `GlobalExceptionHandler` (`adapter/exception/GlobalExceptionHandler.java`): Centralized handler for REST endpoints
- `Message.validate()`: Domain entity self-validation (content not empty, length < 5000)
- All exceptions are logged; business exceptions result in user-facing replies

## Cross-Cutting Concerns

**Logging:**
- SLF4J with Lombok `@Slf4j`
- Structured log messages with context (eventId, sessionId, topicId)
- Levels: ERROR for failures, WARN for degradations, INFO for key lifecycle events, DEBUG for routing details, TRACE for SDK raw data

**Validation:**
- `Message.validate()` — entity-level validation
- `TopicCommandValidator` — validates commands against `CommandWhitelist` per `TopicState`
- `CommandWhitelist` — defines allowed commands per state (NON_TOPIC, UNINITIALIZED, INITIALIZED)
- Apps define their own whitelists via `FishuAppI.getCommandWhitelist(TopicState)`

**Authentication:**
- Feishu SDK handles OAuth via appId/appSecret from environment variables
- No user-level authentication beyond Feishu platform identity (openId from sender)

**Async Processing:**
- `ReceiveMessageListenerExe.execute()` — `@Async` to avoid blocking WebSocket thread
- `OpenCodeTaskExecutor.executeAsync()` — `@Async("opencodeExecutor")` for long-running OpenCode interactions
- `AsyncConfig` defines thread pool configuration

**Deduplication:**
- `MessageDeduplicator` in domain/service — in-memory eventId tracking
- Card events get synthetic `"card-" + eventId` prefix

**Context Resolution:**
- `FeishuContextResolver.resolve(Message)` — static utility, topicId → `ImContextRef.feishuThread()`, chatId → `ImContextRef.feishuChat()`
- Used consistently across `BotMessageService`, `OpenCodeSessionManager`, `OpenCodeMessageAppService`

---

*Architecture analysis: 2026-04-06*

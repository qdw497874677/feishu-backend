<!-- GSD:project-start source:PROJECT.md -->
## Project

**OpenCode Interactive Flow Redesign**

A full redesign of the OpenCode assistant's interactive flow within the Feishu chatbot. The current flow has 6 critical broken points — context mismatch between chatId and threadId, plain text not treated as chat, ghost empty reply bubbles, chatnow not executing prompts, dual state detection confusion, and fragile session ID extraction via text parsing. This redesign replaces the broken state machine, command set, and interaction model with a clean manual-control flow: user selects project → selects/creates session → binds to topic → converses directly.

**Core Value:** A user in a bound topic can type plain text and get an AI response — no command prefix, no broken context, no ghost bubbles.

### Constraints

- **Architecture**: Must follow COLA layer rules — domain defines interfaces, infrastructure implements; no app→domain reverse dependency
- **Communication**: WebSocket long-connection only — no webhook code
- **Compatibility**: BotMessageService must not call app-layer services directly
- **Compatibility**: No domain class may depend on feishu-bot-app module
- **Compatibility**: Stateless apps (Help, Time, Bash, History) must continue working unchanged
- **Data**: No migration of old binding data — old contexts degrade silently to help
- **Data**: sessionId=null is valid persisted state (two-phase binding: app context without session)
- **Scope**: Only OpenCode is session-aware — no generic session framework
- **Testing**: All 261 existing tests must continue passing
<!-- GSD:project-end -->

<!-- GSD:stack-start source:codebase/STACK.md -->
## Technology Stack

## Languages
- Java 17 - All application code across 6 Maven modules
- YAML - Configuration (`application.yml`, `application-dev.yml`, `application-test.yml`)
- Bash - Build/deploy scripts (`start-feishu.sh`, `run-local.sh`)
- SQL (SQLite dialect) - Schema definitions inline in Java code
## Runtime
- JDK 17 (OpenJDK)
- Requires `--add-opens` JVM flags for reflection access (configured in `feishu-bot-start/pom.xml`)
- Apache Maven (multi-module POM)
- Lockfile: Not present (Maven uses version ranges in BOM imports)
## Frameworks
- Spring Boot `3.2.1` - Application framework and DI container
- COLA Components `5.0.0` (Alibaba) - Clean Object-oriented Layered Architecture
- Spring Boot Test (JUnit 5 + Mockito) - Unit and integration tests
- No additional test frameworks detected
- Maven Compiler Plugin `3.11.0` - Java compilation
- Spring Boot Maven Plugin - Fat JAR packaging and `spring-boot:run`
- Main class: `com.qdw.feishu.Application` (in `feishu-bot-start`)
## Module Structure
## Key Dependencies
- `com.larksuite.oapi:oapi-sdk:2.5.2` - Feishu/Lark Open API SDK (messaging, cards, reactions, WebSocket)
- `com.alibaba.cola:cola-components-bom:5.0.0` - COLA architecture framework BOM
- `org.xerial:sqlite-jdbc:3.42.0.0` - SQLite JDBC driver for local persistence
- `spring-boot-starter-web` - Embedded Tomcat, REST API support
- `spring-boot-starter-webflux` - WebClient for SSE (Server-Sent Events) support with OpenCode
- `spring-boot-starter-data-jdbc` - JdbcTemplate for SQLite access
- `spring-boot-starter-validation` - Bean Validation (Jakarta)
- `spring-boot-starter-aop` - AOP support (COLA catchlog)
- `com.fasterxml.jackson.core:jackson-databind` - JSON serialization/deserialization
- `com.google.code.gson:gson` - Secondary JSON library (adapter layer)
- `org.projectlombok:lombok:1.18.30` - Boilerplate reduction (`@Data`, `@Slf4j`, `@NoArgsConstructor`)
- `org.slf4j:slf4j-api:2.0.9` - Logging facade
## Configuration
- `FEISHU_APPID` - Feishu application ID
- `FEISHU_APPSECRET` - Feishu application secret
- `FEISHU_ENCRYPT_KEY` - Event encryption key (optional)
- `FEISHU_VERIFICATION_TOKEN` - Event verification token (optional)
- `OPencode_SERVER_URL` - OpenCode server endpoint (default: `http://localhost:4098`)
- `OPencode_USERNAME` - OpenCode HTTP Basic Auth username (default: `opencode`)
- `OPencode_SERVER_PASSWORD` - OpenCode HTTP Basic Auth password
- `OPencode_PROJECT_ROOT` - Default project root directory
- `feishu-bot-start/src/main/resources/application.yml` - Main configuration (port 8080)
- `feishu-bot-start/src/main/resources/application-dev.yml` - Dev profile (port 17777)
- `feishu-bot-start/src/test/resources/application-test.yml` - Test profile (random port)
- `FeishuProperties` (`feishu-bot-infrastructure/.../config/FeishuProperties.java`) - `@ConfigurationProperties(prefix = "feishu")`
- `OpenCodeProperties` (`feishu-bot-infrastructure/.../config/OpenCodeProperties.java`) - `@ConfigurationProperties(prefix = "opencode")`
- `CardProperties` (`feishu-bot-domain/.../config/CardProperties.java`) - Card streaming configuration
- `FeishuReplyProperties` (`feishu-bot-domain/.../config/FeishuReplyProperties.java`) - Reply mode configuration
- `pom.xml` (root) - Parent POM, dependency management
- DataSource auto-configuration is **excluded**: `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})`
## Thread Pools
- `bashExecutor` - For BashApp command execution
- `opencodeExecutor` - For OpenCode async operations
- `feishu-ws-listener` - Dedicated thread for Feishu WebSocket connection
## Key Design Patterns
- Interface: `ReplyStrategy` (`feishu-bot-domain/.../reply/ReplyStrategy.java`)
- Factory: `ReplyStrategyFactory` (`feishu-bot-domain/.../reply/ReplyStrategyFactory.java`)
- Implementations in `feishu-bot-infrastructure/.../reply/`:
- Interface: `MessageEventParser` (`feishu-bot-domain/.../gateway/MessageEventParser.java`)
- Implementation: `MessageEventParserImpl` (`feishu-bot-infrastructure/.../parser/MessageEventParserImpl.java`)
- Purpose: Isolates Feishu SDK types (`P2MessageReceiveV1`) from domain model (`Message`)
- Interfaces defined in `feishu-bot-domain/.../gateway/`
- Implementations in `feishu-bot-infrastructure/.../gateway/`
- 7 gateway pairs:
- `CommandAdapter` / `ResponseAdapter` interfaces in `feishu-bot-domain/.../adapter/`
- Implementations in `feishu-bot-infrastructure/.../adapter/`:
- Interface: `FishuAppI` (`feishu-bot-domain/.../app/FishuAppI.java`)
- Registry: `AppRegistry` (`feishu-bot-domain/.../core/AppRegistry.java`)
- All apps auto-registered via Spring `@Component` scanning
- 5 apps: `BashApp`, `TimeApp`, `HelpApp`, `HistoryApp`, `OpenCodeApp`
## Platform Requirements
- JDK 17+
- Maven 3.x
- Network access to Feishu API servers (`open.feishu.cn`)
- Optional: OpenCode server running on `localhost:4098`
- Optional: SQLite (embedded, no separate install needed)
- JDK 17 runtime
- Outbound network access to Feishu WebSocket (`msg-frontner.feishu.cn`)
- Outbound network access to Feishu API (`open.feishu.cn`)
- Optional: OpenCode server for AI coding assistant features
- No inbound ports required (WebSocket long-connection model, NOT webhook)
- Log output: `/tmp/feishu-run.log`
- Data directory: `data/` (SQLite DB files)
- Fat JAR via `spring-boot-maven-plugin`
- Started via: `mvn spring-boot:run` (dev) or `java -jar feishu-bot-start-*.jar` (prod)
- Launch script: `start-feishu.sh` (sets env vars, kills old process, starts in background)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

## Naming Patterns
- PascalCase Java classes: `BotMessageService.java`, `OpenCodeCommandHandler.java`
- Interface suffix `-I` or `-Gateway`: `FishuAppI.java`, `FeishuGateway.java`
- Implementation suffix `-Impl`: `FeishuGatewayImpl.java`, `MessageEventParserImpl.java`
- Test suffix `Test`: `BotMessageServiceTest.java`
- Enums: PascalCase without suffix: `TopicState.java`, `ReplyMode.java`, `SessionState.java`
- camelCase consistently: `routeMessage()`, `handleChatCommand()`, `extractSessionId()`
- Boolean getters: `isTopicInitialized()`, `isExplicitCommand()`, `shouldPersistBinding()`
- Handler methods: `handle()`, `handleEvent()`, `handleConnect()`
- Builder methods: `buildConnectGuide()`, `buildInitializationGuide()`
- Private helpers: `extractAppId()`, `resolveContextRef()`
- camelCase: `topicId`, `sessionId`, `replyContent`, `contextRef`
- Constants: `UPPER_SNAKE_CASE`: `MAX_RETRIES`, `DEFAULT_SESSION_LIMIT`, `APP_ID`
- Package-level constants use `static final`: `EXECUTE_TIMEOUT`, `FLUSH_INTERVAL_MS`
- PascalCase: `BotRoutingDecision`, `ImContextBinding`, `OpenCodeSessionData`
- Enum values: `UPPER_SNAKE_CASE`: `NON_TOPIC`, `UNINITIALIZED`, `INITIALIZED`
- All lowercase, domain-driven: `com.qdw.feishu.domain.opencode`, `com.qdw.feishu.infrastructure.gateway`
## Code Style
- No explicit formatter configured (no `.prettierrc`, `.editorconfig`, or formatter plugin in `pom.xml`)
- 4-space indentation (de facto standard from code examination)
- Opening braces on same line
- Max line length ~120 characters (observed)
- No explicit linting tool (no Checkstyle, SpotBugs, or PMD in `pom.xml`)
- COLA framework provides some architectural guardrails via `cola-component-*`
- Used universally: `@Slf4j`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Domain entities: `@Data @NoArgsConstructor` (e.g., `Message.java`)
- Value objects: `@Data @AllArgsConstructor` (e.g., `BotRoutingDecision.java`)
## Import Organization
- None. All imports are fully qualified package paths.
## Error Handling
- `MessageBizException` extends COLA `BizException` — business rule violations
- `MessageSysException` extends COLA `SysException` — system/infrastructure failures  
- `MessageInvalidException` — invalid message content
- `ConnectionLostException` — WebSocket connection issues
- `OptimisticLockException` — concurrent session update conflicts
## Logging
- `root`: INFO
- `com.qdw.feishu`: DEBUG
- `com.alibaba.cola`: INFO
- INFO for key business operations: message received, session created/bound, reply sent
- DEBUG for diagnostic: binding lookups, context resolution, JSON parsing fallbacks
- WARN for recoverable issues: missing config, fallback behavior, failed reactions
- ERROR for failures: exception stack traces, failed API calls
- `ReceiveMessageListenerExe.java:46` — `log.info("消息内容: {}", message.getDisplayContent())`
- `FeishuGatewayImpl.java:81,115,146,187,216,238` — logs reply content in full at INFO
- `OpenCodeApp.java:174` — `log.info("OpenCodeApp.execute: content='{}'", content)`
- Should be DEBUG for privacy/compliance
## Comments
- Javadoc on public interfaces and key domain methods (e.g., `FishuAppI`, `ReplyStrategy`)
- Inline comments for complex state machine logic (e.g., `OpenCodeCommandHandler` switch statement)
- Class-level Javadoc on gateway implementations explaining schema migration strategy
## Function Design
- Most methods: 5-30 lines (good)
- Largest methods: `FeishuGatewayImpl.listMessages()` ~80 lines (should be refactored)
- Largest classes: `OpenCodeGatewayImpl` (889 lines), `FeishuGatewayImpl` (502 lines), `OpenCodeCommandHandler` (497 lines) — above the 300-line guideline in AGENTS.md
- Constructor injection exclusively (no field injection, no setter injection)
- Method parameters: generally 2-4 (acceptable)
- Complex input wrapped in domain objects (`Message`, `UnifiedCommand`)
- `String` for app execution results (legacy `execute(Message)` pattern)
- `BizResult` for new `execute(UnifiedCommand)` pattern
- `Optional<T>` for nullable lookups (gateway queries, session resolution)
- `SendResult` for Feishu API operations
## Module Design
- Constructor-based injection universally
- `@Component` / `@Service` annotations in domain layer (architectural concern W1)
- `@Bean` factory methods in `DomainServiceConfig.java` for strategy factories
## Spring Stereotypes in Domain Layer (W1)
- `BotMessageService.java` — `@Service`
- All app implementations — `@Component` (BashApp, HelpApp, TimeApp, HistoryApp, OpenCodeApp)
- `OpenCodeSessionManager.java` — `@Component`
- `OpenCodeCommandHandler.java` — `@Component`
- `AppRegistry.java` — `@Component`
- Config classes — `@ConfigurationProperties`
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

## Pattern Overview
- Strict 4-layer separation: adapter → app → domain ← infrastructure
- Domain-defined interfaces (Gateway pattern) with infrastructure implementations
- Strategy pattern for reply handling, eliminating if-else branching
- Anti-corruption layer isolating Feishu SDK from domain logic
- Two-phase IM context binding model for session-aware applications
- WebSocket long-connection communication (webhook explicitly forbidden)
## Layers
- Purpose: Application bootstrap and configuration
- Location: `feishu-bot-start/src/main/java/com/qdw/feishu/`
- Contains: `Application.java`, `application.yml`
- Depends on: All other modules (transitively)
- Used by: Nothing (entry point)
- Key file: `feishu-bot-start/src/main/java/com/qdw/feishu/Application.java`
- Purpose: External event ingestion — listens to Feishu WebSocket events and dispatches to app layer
- Location: `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/`
- Contains: Event listeners, exception handlers, test controllers
- Depends on: app (ReceiveMessageListenerExe), domain (MessageListenerGateway, FeishuConfig, Message)
- Used by: start (component scanning)
- Key files:
- Purpose: Use case orchestration, session management, message routing coordination
- Location: `feishu-bot-app/src/main/java/com/qdw/feishu/app/`
- Contains: Message listener executor, app services, session orchestrator, command router
- Depends on: domain (all domain interfaces and models), client (DTO)
- Used by: adapter (FeishuEventListener → ReceiveMessageListenerExe)
- Key files:
- Purpose: Core business logic, domain models, gateway interfaces, app implementations
- Location: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/`
- Contains: Domain entities, app implementations, gateway interfaces, strategy interfaces, routing, validation, session models
- Depends on: client (DTO), COLA framework
- Used by: app, infrastructure
- Key packages:
- Purpose: Gateway implementations, SDK integration, persistence, strategy implementations
- Location: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/`
- Contains: Gateway impls, reply strategies, parser impl, config, adapters
- Depends on: domain (implements interfaces), Feishu SDK, SQLite
- Used by: Spring DI (auto-wired into domain/app layer via interface)
- Key files:
- Purpose: DTO and API contracts
- Location: `feishu-bot-client/src/main/java/com/qdw/feishu/client/`
- Contains: MessageServiceI, ReceiveMessageCmd, ReceiveMessageQry
- Depends on: Nothing
- Used by: app, domain
## Data Flow
- **Message deduplication:** `MessageDeduplicator` (in-memory, eventId-based)
- **IM context binding:** `ImContextBindingGateway` → SQLite persistence (`feishu-bot-start/data/`)
- **App sessions:** `AppSessionGateway` → SQLite persistence with optimistic locking (version field)
- **Streaming state:** `OpenCodeStreamingHandler` (in-memory ConcurrentHashMaps: session→card, session→buffer, etc.)
## Key Abstractions
- Purpose: Pluggable application contract — all bot commands implement this
- Examples: `domain/app/BashApp.java`, `domain/app/TimeApp.java`, `domain/app/HelpApp.java`, `domain/app/HistoryApp.java`, `domain/opencode/OpenCodeApp.java`
- Pattern: Strategy pattern with Spring auto-discovery. All `@Component` implementations of `FishuAppI` are auto-collected into `AppRegistry` via constructor injection of `List<FishuAppI>`.
- Key methods: `getAppId()`, `execute(Message)`, `getReplyMode()`, `getAppAliases()`, `getCommandWhitelist(TopicState)`, `isTopicInitialized(Message)`
- Purpose: Central registry of all applications, provides lookup by appId
- Location: `domain/core/AppRegistry.java`
- Pattern: Auto-populated via Spring DI, stores `Map<String, FishuAppI>` indexed by appId
- Purpose: Platform-agnostic IM conversation context identification and binding
- `ImContextRef` (`domain/model/ImContextRef.java`): Value object — `(platform, contextType, contextId)` e.g., `feishu:thread:xxx` or `feishu:chat:xxx`
- `ImContextBinding` (`domain/model/ImContextBinding.java`): Maps ImContextRef → `(appId, sessionId)` with timestamps. Supports two-phase binding (null sessionId = app context only)
- `FeishuContextResolver` (`domain/feishu/FeishuContextResolver.java`): Utility to resolve `Message` → `ImContextRef` (topicId preferred over chatId)
- Purpose: Coordinates binding gateway + session gateway for unified context-session state
- Location: `app/session/ContextSessionOrchestrator.java` (interface), `app/session/ContextSessionOrchestratorImpl.java` (impl)
- Pattern: Two-phase binding model:
- States: `UNBOUND` → `IN_APP_NO_SESSION` → `IN_APP_WITH_SESSION` (also: `BOUND_TO_OTHER_APP`, dangling detection)
- Purpose: Polymorphic reply dispatch without if-else
- Location: `domain/reply/ReplyStrategy.java` (interface), `domain/reply/ReplyStrategyFactory.java` (factory), strategies in `infrastructure/reply/`
- Pattern: Spring collects all `ReplyStrategy` beans, `DomainServiceConfig` wires them into `ReplyStrategyFactory`. Factory uses `EnumMap<ReplyMode, ReplyStrategy>`.
- Modes: `DIRECT` (no topic), `TOPIC` (within thread), `DEFAULT` (passthrough)
- Purpose: Domain defines interfaces, infrastructure implements — inverted dependency
- Interface examples: `domain/gateway/FeishuGateway.java`, `domain/gateway/MessageListenerGateway.java`, `domain/gateway/ImContextBindingGateway.java`, `domain/gateway/AppSessionGateway.java`, `domain/gateway/OpenCodeGateway.java`, `domain/gateway/CardGateway.java`, `domain/gateway/MessageEventParser.java`
- Implementation: All in `infrastructure/gateway/` with `@Component` annotation
- Purpose: Unified command model for both message and card events
- `UnifiedCommand` (`domain/command/UnifiedCommand.java`): Normalized command with `appId`, `subCommand`, `args[]`, `source` (MESSAGE or CARD)
- `EventProcessor` (`domain/processor/EventProcessor.java`): Pipeline: `event → CommandAdapter → UnifiedCommand → UnifiedCommandRouter → BizResult → ResponseAdapter`
- Currently coexists with the legacy `Message`-based flow; apps support both `execute(Message)` and `execute(UnifiedCommand)` via `FishuAppI`
## Entry Points
- Location: `feishu-bot-start/src/main/java/com/qdw/feishu/Application.java`
- Triggers: `SpringApplication.run()` → Spring Boot auto-configuration
- Responsibilities: Component scanning, bean wiring
- Location: `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/FeishuEventListener.java`
- Triggers: `ApplicationRunner.run()` after Spring context initialization
- Responsibilities: Starts `MessageListenerGateway.startListening()` with `ReceiveMessageListenerExe::execute` as handler
- Condition: `@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")`
- Location: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java`
- Triggers: Feishu SDK `EventDispatcher` dispatches `P2MessageReceiveV1` and `P2CardActionTrigger` events
- Responsibilities: Parse events via anti-corruption layer, construct pseudo-Messages for card actions, delegate to `messageHandler`
## Error Handling
- `MessageBizException` (`domain/exception/MessageBizException.java`): Business-level errors (e.g., cross-app command rejection). Caught by `ReceiveMessageListenerExe` and sent as user-facing error reply via `FeishuGateway.sendMessage()`
- `MessageInvalidException` (`domain/exception/MessageInvalidException.java`): Validation failures
- `MessageSysException` (`domain/exception/MessageSysException.java`): System-level errors
- `ConnectionLostException` (`domain/exception/ConnectionLostException.java`): WebSocket disconnection
- `OptimisticLockException` (`domain/exception/OptimisticLockException.java`): Concurrent session update conflict
- `GlobalExceptionHandler` (`adapter/exception/GlobalExceptionHandler.java`): Centralized handler for REST endpoints
- `Message.validate()`: Domain entity self-validation (content not empty, length < 5000)
- All exceptions are logged; business exceptions result in user-facing replies
## Cross-Cutting Concerns
- SLF4J with Lombok `@Slf4j`
- Structured log messages with context (eventId, sessionId, topicId)
- Levels: ERROR for failures, WARN for degradations, INFO for key lifecycle events, DEBUG for routing details, TRACE for SDK raw data
- `Message.validate()` — entity-level validation
- `TopicCommandValidator` — validates commands against `CommandWhitelist` per `TopicState`
- `CommandWhitelist` — defines allowed commands per state (NON_TOPIC, UNINITIALIZED, INITIALIZED)
- Apps define their own whitelists via `FishuAppI.getCommandWhitelist(TopicState)`
- Feishu SDK handles OAuth via appId/appSecret from environment variables
- No user-level authentication beyond Feishu platform identity (openId from sender)
- `ReceiveMessageListenerExe.execute()` — `@Async` to avoid blocking WebSocket thread
- `OpenCodeTaskExecutor.executeAsync()` — `@Async("opencodeExecutor")` for long-running OpenCode interactions
- `AsyncConfig` defines thread pool configuration
- `MessageDeduplicator` in domain/service — in-memory eventId tracking
- Card events get synthetic `"card-" + eventId` prefix
- `FeishuContextResolver.resolve(Message)` — static utility, topicId → `ImContextRef.feishuThread()`, chatId → `ImContextRef.feishuChat()`
- Used consistently across `BotMessageService`, `OpenCodeSessionManager`, `OpenCodeMessageAppService`
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->

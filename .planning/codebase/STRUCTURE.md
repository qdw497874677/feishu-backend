# Codebase Structure

**Analysis Date:** 2026-04-06

## Directory Layout

```
feishu-backend/
├── feishu-bot-start/                   # Startup & configuration module
│   ├── src/main/java/com/qdw/feishu/
│   │   └── Application.java           # Spring Boot entry point
│   ├── src/main/resources/
│   │   └── application.yml            # Spring config
│   ├── src/test/                       # Integration tests
│   └── data/                           # SQLite database files (runtime)
│
├── feishu-bot-adapter/                 # Adapter layer — event listeners
│   └── src/main/java/com/qdw/feishu/adapter/
│       ├── listener/
│       │   └── FeishuEventListener.java
│       ├── exception/
│       │   └── GlobalExceptionHandler.java
│       └── test/
│           └── MessageTestController.java
│
├── feishu-bot-app/                     # Application layer — use case orchestration
│   ├── src/main/java/com/qdw/feishu/app/
│   │   ├── listener/
│   │   │   └── ReceiveMessageListenerExe.java
│   │   ├── message/
│   │   │   └── BotMessageAppService.java
│   │   ├── opencode/
│   │   │   └── OpenCodeMessageAppService.java
│   │   ├── session/
│   │   │   ├── ContextSessionOrchestrator.java
│   │   │   ├── ContextSessionOrchestratorImpl.java
│   │   │   └── ContextSessionStatus.java
│   │   ├── executor/
│   │   │   └── CommandExecutorI.java
│   │   └── router/
│   │       └── CommandRouter.java
│   └── src/test/java/com/qdw/feishu/app/
│       ├── listener/
│       ├── message/
│       ├── opencode/
│       └── session/
│
├── feishu-bot-domain/                  # Domain layer — core business logic
│   ├── src/main/java/com/qdw/feishu/domain/
│   │   ├── app/                        # App implementations (BashApp, TimeApp, etc.)
│   │   ├── opencode/                   # OpenCode subsystem
│   │   │   ├── router/                 # State-aware command routing
│   │   │   ├── OpenCodeApp.java
│   │   │   ├── OpenCodeCommandHandler.java
│   │   │   ├── OpenCodeSessionManager.java
│   │   │   ├── OpenCodeTaskExecutor.java
│   │   │   ├── OpenCodeStreamingHandler.java
│   │   │   ├── OpenCodeResponseFormatter.java
│   │   │   └── OpenCodeEvent.java
│   │   ├── message/                    # Message domain entities
│   │   ├── model/                      # Value objects (ImContextBinding, ImContextRef)
│   │   │   └── opencode/              # OpenCode-specific session data
│   │   ├── gateway/                    # Gateway interfaces
│   │   ├── reply/                      # Reply strategy interfaces
│   │   ├── service/                    # Domain services
│   │   ├── session/                    # Generic session model
│   │   ├── command/                    # Unified command model
│   │   ├── adapter/                    # Command/Response adapter interfaces
│   │   ├── router/                     # App routing
│   │   ├── processor/                  # Event processor pipeline
│   │   ├── card/                       # Streaming card manager
│   │   ├── topic/                      # Topic state & validation
│   │   ├── history/                    # Bash history management
│   │   ├── config/                     # Domain config interfaces
│   │   ├── core/                       # Core types (AppRegistry, ReplyMode)
│   │   ├── feishu/                     # Feishu-specific context resolver
│   │   ├── exception/                  # Domain exceptions
│   │   └── result/                     # BizResult type
│   └── src/test/java/com/qdw/feishu/domain/
│       ├── app/                        # App unit tests
│       ├── opencode/                   # OpenCode unit tests
│       ├── card/
│       ├── model/
│       ├── service/
│       ├── history/
│       └── validation/
│
├── feishu-bot-infrastructure/          # Infrastructure layer — external integrations
│   ├── src/main/java/com/qdw/feishu/infrastructure/
│   │   ├── gateway/                    # Gateway implementations
│   │   │   ├── FeishuGatewayImpl.java
│   │   │   ├── MessageListenerGatewayImpl.java
│   │   │   ├── ImContextBindingGatewayImpl.java
│   │   │   ├── AppSessionGatewayImpl.java
│   │   │   ├── OpenCodeGatewayImpl.java
│   │   │   ├── OpenCodeEventGatewayImpl.java
│   │   │   └── CardGatewayImpl.java
│   │   ├── reply/                      # Reply strategy implementations
│   │   │   ├── DirectReplyStrategy.java
│   │   │   ├── TopicReplyStrategy.java
│   │   │   └── DefaultReplyStrategy.java
│   │   ├── parser/                     # Anti-corruption layer impl
│   │   │   └── MessageEventParserImpl.java
│   │   ├── adapter/                    # Command/Response adapter impls
│   │   │   ├── CardCommandAdapter.java
│   │   │   ├── CardResponseAdapter.java
│   │   │   ├── MessageCommandAdapter.java
│   │   │   └── MessageResponseAdapter.java
│   │   ├── config/                     # Spring configuration
│   │   │   ├── FeishuProperties.java
│   │   │   ├── OpenCodeProperties.java
│   │   │   ├── AsyncConfig.java
│   │   │   ├── DomainServiceConfig.java
│   │   │   └── OpenCodeSseConfig.java
│   │   └── session/
│   │       └── DefaultSessionIdGenerator.java
│   ├── src/main/resources/             # Infra resources
│   └── src/test/java/com/qdw/feishu/infrastructure/
│       ├── gateway/
│       ├── adapter/
│       └── handler/
│
├── feishu-bot-client/                  # Client layer — DTOs and API contracts
│   └── src/main/java/com/qdw/feishu/client/
│       └── message/
│           ├── MessageServiceI.java
│           ├── ReceiveMessageCmd.java
│           └── ReceiveMessageQry.java
│
├── pom.xml                             # Parent POM
├── AGENTS.md                           # Project conventions
├── APP_GUIDE.md                        # App development guide
├── APP_USAGE_GUIDE.md                  # App usage documentation
├── start-feishu.sh                     # Build & run script
└── .planning/                          # Analysis documents
    └── codebase/
```

## Directory Purposes

**`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/`:**
- Purpose: Concrete application implementations
- Contains: `FishuAppI` interface, BashApp, TimeApp, HelpApp, HistoryApp
- Key files: `FishuAppI.java` (contract), `BashApp.java`, `TimeApp.java`, `HelpApp.java`, `HistoryApp.java`
- **Note:** OpenCodeApp lives in `domain/opencode/` due to its complexity

**`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/`:**
- Purpose: OpenCode application subsystem — the most complex app
- Contains: App entry, command handler, session manager, task executor, streaming handler, response formatter
- Key files: `OpenCodeApp.java`, `OpenCodeCommandHandler.java`, `OpenCodeSessionManager.java`, `OpenCodeTaskExecutor.java`, `OpenCodeStreamingHandler.java`

**`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/`:**
- Purpose: Gateway interfaces defining infrastructure contracts
- Contains: All `*Gateway.java` interfaces that infrastructure implements
- Key files: `FeishuGateway.java`, `MessageListenerGateway.java`, `ImContextBindingGateway.java`, `AppSessionGateway.java`, `OpenCodeGateway.java`, `CardGateway.java`, `MessageEventParser.java`

**`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/`:**
- Purpose: Domain value objects for IM context binding
- Contains: `ImContextBinding.java`, `ImContextRef.java`, `BindingResult.java`
- Sub-package: `model/opencode/OpenCodeSessionData.java` — app-specific session data

**`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/`:**
- Purpose: Generic session model (app-agnostic)
- Contains: `AppSession.java`, `AppSessionData.java`, `AppSessionInfo.java`, `SessionState.java`, `ContextSessionState.java`, `SessionConfig.java`, `SessionIdGenerator.java`, `TypeToken.java`

**`feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/`:**
- Purpose: Concrete gateway implementations using external SDKs
- Contains: All `*Impl.java` classes implementing domain gateway interfaces
- Key files: `FeishuGatewayImpl.java` (Feishu REST API), `MessageListenerGatewayImpl.java` (WebSocket + EventDispatcher), `ImContextBindingGatewayImpl.java` (SQLite), `AppSessionGatewayImpl.java` (SQLite)

**`feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/reply/`:**
- Purpose: Reply strategy implementations
- Contains: `DirectReplyStrategy.java`, `TopicReplyStrategy.java`, `DefaultReplyStrategy.java`

**`feishu-bot-start/data/`:**
- Purpose: Runtime SQLite database files
- Contains: `.db` files for context bindings and sessions
- Generated: Yes (at runtime)
- Committed: May be committed; add `*.db` to `.gitignore` to exclude

## Key File Locations

**Entry Points:**
- `feishu-bot-start/src/main/java/com/qdw/feishu/Application.java`: Spring Boot main class
- `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/FeishuEventListener.java`: WebSocket listener startup
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java`: Message processing entry

**Configuration:**
- `feishu-bot-start/src/main/resources/application.yml`: Spring configuration
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/FeishuProperties.java`: Feishu SDK config
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/OpenCodeProperties.java`: OpenCode API config
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/DomainServiceConfig.java`: Bean wiring (ReplyStrategyFactory)
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/AsyncConfig.java`: Thread pool config
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/config/FeishuConfig.java`: Feishu mode/listener config
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/config/CardProperties.java`: Card streaming config
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/config/FeishuReplyProperties.java`: Default reply mode config

**Core Logic:**
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`: Message routing
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/message/BotMessageAppService.java`: App execution + reply orchestration
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java`: OpenCode-specific orchestration
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java`: OpenCode command routing
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java`: Session lifecycle
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java`: Async task execution

**Testing:**
- Domain tests: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/`
- App tests: `feishu-bot-app/src/test/java/com/qdw/feishu/app/`
- Infrastructure tests: `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/`
- Start tests: `feishu-bot-start/src/test/java/com/qdw/feishu/`

## Naming Conventions

**Files:**
- Java classes: PascalCase — `BotMessageService.java`, `OpenCodeApp.java`
- Interfaces suffixed with `I` or descriptive: `FishuAppI.java`, `MessageServiceI.java`
- Gateway interfaces: `XxxGateway.java` (domain), `XxxGatewayImpl.java` (infrastructure)
- Strategy classes: `XxxStrategy.java` (interface), `XxxReplyStrategy.java` (implementation)
- Test classes: `XxxTest.java` — co-located in `src/test/` mirror of `src/main/`

**Directories:**
- Maven modules: `feishu-bot-{layer}` (adapter, app, domain, infrastructure, client, start)
- Java packages: `com.qdw.feishu.{layer}.{feature}` — e.g., `com.qdw.feishu.domain.opencode`
- Feature sub-packages: lowercase, singular — `gateway/`, `reply/`, `session/`, `command/`

## Where to Add New Code

**New Application (e.g., `/weather`):**
- Implementation: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/WeatherApp.java`
- Must: implement `FishuAppI`, annotate with `@Component`
- Tests: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/WeatherAppTest.java`
- If complex (like OpenCode): create sub-package `domain/weather/` with dedicated handler, session manager, etc.

**New Gateway Interface:**
- Interface: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/NewGateway.java`
- Implementation: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/NewGatewayImpl.java`
- Tests: `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/NewGatewayImplTest.java`

**New Reply Strategy:**
- Implementation: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/reply/NewReplyStrategy.java`
- Must: implement `ReplyStrategy`, annotate with `@Component`
- Auto-registered by `ReplyStrategyFactory` via Spring DI

**New Domain Model:**
- Entity/Value Object: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/NewModel.java`
- If app-specific: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/{app}/NewModel.java`

**New App Service (orchestration):**
- Service: `feishu-bot-app/src/main/java/com/qdw/feishu/app/{feature}/NewAppService.java`
- Tests: `feishu-bot-app/src/test/java/com/qdw/feishu/app/{feature}/NewAppServiceTest.java`

**New Domain Service:**
- Service: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/NewDomainService.java`
- Tests: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/service/NewDomainServiceTest.java`

**New Command Adapter (e.g., for Slack events):**
- Interface adapter: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/SlackCommandAdapter.java`
- Response adapter: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/SlackResponseAdapter.java`

**New Configuration:**
- Config class: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/NewConfig.java`
- Properties in: `feishu-bot-start/src/main/resources/application.yml`

**New Exception:**
- Exception class: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/exception/NewException.java`

## Special Directories

**`.worktrees/`:**
- Purpose: Git worktree checkouts for parallel branch development
- Generated: Yes (by git worktree)
- Committed: No
- Contains: `architecture-refactoring/`, `card-impl/`, `im-context-binding-refined/`, `imcontext-binding-refined/`
- **Important:** These are NOT the main codebase. Always work in the root directory.

**`.planning/codebase/`:**
- Purpose: Architecture analysis documents
- Generated: Yes (by analysis tools)
- Committed: Yes

**`feishu-bot-start/data/`:**
- Purpose: SQLite database files for persistence
- Generated: Yes (at application runtime)
- Committed: Configurable (default: yes, add `*.db` to `.gitignore` to exclude)

**`docs/`:**
- Purpose: Design documents and plans
- Committed: Yes
- Contains: Plan documents for features in progress

---

*Structure analysis: 2026-04-06*

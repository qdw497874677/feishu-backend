# Technology Stack

**Analysis Date:** 2026-04-06

## Languages

**Primary:**
- Java 17 - All application code across 6 Maven modules

**Secondary:**
- YAML - Configuration (`application.yml`, `application-dev.yml`, `application-test.yml`)
- Bash - Build/deploy scripts (`start-feishu.sh`, `run-local.sh`)
- SQL (SQLite dialect) - Schema definitions inline in Java code

## Runtime

**Environment:**
- JDK 17 (OpenJDK)
- Requires `--add-opens` JVM flags for reflection access (configured in `feishu-bot-start/pom.xml`)

**Package Manager:**
- Apache Maven (multi-module POM)
- Lockfile: Not present (Maven uses version ranges in BOM imports)

**JVM Arguments:**
```
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
```

## Frameworks

**Core:**
- Spring Boot `3.2.1` - Application framework and DI container
- COLA Components `5.0.0` (Alibaba) - Clean Object-oriented Layered Architecture
  - `cola-component-domain-starter` - Domain layer support
  - `cola-component-dto` - DTO base classes
  - `cola-component-exception` - Exception framework (`SysException`, `BizException`)
  - `cola-component-extension-starter` - Extension point mechanism
  - `cola-component-catchlog-starter` - Exception catching and logging

**Testing:**
- Spring Boot Test (JUnit 5 + Mockito) - Unit and integration tests
- No additional test frameworks detected

**Build/Dev:**
- Maven Compiler Plugin `3.11.0` - Java compilation
- Spring Boot Maven Plugin - Fat JAR packaging and `spring-boot:run`
- Main class: `com.qdw.feishu.Application` (in `feishu-bot-start`)

## Module Structure

```
feishu-bot (parent POM)
├── feishu-bot-start        → Spring Boot entry point, config files
├── feishu-bot-adapter      → Event listeners, controllers
├── feishu-bot-app           → Application services, orchestrators
├── feishu-bot-client        → DTOs, API contracts
├── feishu-bot-domain        → Domain models, business logic, gateway interfaces (80 Java files)
└── feishu-bot-infrastructure → Gateway implementations, SDK integrations (21 Java files)
```

**Module Dependency Graph:**
```
start → adapter, app, domain, infrastructure
adapter → client, app, infrastructure(provided)
app → domain, client
infrastructure → domain
domain → (COLA components, Spring context, Jackson)
client → (COLA DTO, validation)
```

## Key Dependencies

**Critical:**
- `com.larksuite.oapi:oapi-sdk:2.5.2` - Feishu/Lark Open API SDK (messaging, cards, reactions, WebSocket)
  - Location: `feishu-bot-infrastructure/pom.xml`
  - Used in: `FeishuGatewayImpl`, `CardGatewayImpl`, `MessageListenerGatewayImpl`, `MessageEventParserImpl`
- `com.alibaba.cola:cola-components-bom:5.0.0` - COLA architecture framework BOM
  - Governs all `cola-component-*` versions
- `org.xerial:sqlite-jdbc:3.42.0.0` - SQLite JDBC driver for local persistence
  - Location: `feishu-bot-infrastructure/pom.xml`
  - Used in: `AppSessionGatewayImpl`, `ImContextBindingGatewayImpl`

**Infrastructure:**
- `spring-boot-starter-web` - Embedded Tomcat, REST API support
- `spring-boot-starter-webflux` - WebClient for SSE (Server-Sent Events) support with OpenCode
  - Location: `feishu-bot-infrastructure/pom.xml`
  - Used in: `OpenCodeEventGatewayImpl` (Reactor/WebFlux `Flux` for SSE streaming)
- `spring-boot-starter-data-jdbc` - JdbcTemplate for SQLite access
- `spring-boot-starter-validation` - Bean Validation (Jakarta)
- `spring-boot-starter-aop` - AOP support (COLA catchlog)
- `com.fasterxml.jackson.core:jackson-databind` - JSON serialization/deserialization
- `com.google.code.gson:gson` - Secondary JSON library (adapter layer)
- `org.projectlombok:lombok:1.18.30` - Boilerplate reduction (`@Data`, `@Slf4j`, `@NoArgsConstructor`)
- `org.slf4j:slf4j-api:2.0.9` - Logging facade

## Configuration

**Environment Variables (required for production):**
- `FEISHU_APPID` - Feishu application ID
- `FEISHU_APPSECRET` - Feishu application secret
- `FEISHU_ENCRYPT_KEY` - Event encryption key (optional)
- `FEISHU_VERIFICATION_TOKEN` - Event verification token (optional)
- `OPencode_SERVER_URL` - OpenCode server endpoint (default: `http://localhost:4098`)
- `OPencode_USERNAME` - OpenCode HTTP Basic Auth username (default: `opencode`)
- `OPencode_SERVER_PASSWORD` - OpenCode HTTP Basic Auth password
- `OPencode_PROJECT_ROOT` - Default project root directory

**Configuration Files:**
- `feishu-bot-start/src/main/resources/application.yml` - Main configuration (port 8080)
- `feishu-bot-start/src/main/resources/application-dev.yml` - Dev profile (port 17777)
- `feishu-bot-start/src/test/resources/application-test.yml` - Test profile (random port)

**Configuration Properties Classes:**
- `FeishuProperties` (`feishu-bot-infrastructure/.../config/FeishuProperties.java`) - `@ConfigurationProperties(prefix = "feishu")`
  - Implements domain interface `FeishuConfig`
- `OpenCodeProperties` (`feishu-bot-infrastructure/.../config/OpenCodeProperties.java`) - `@ConfigurationProperties(prefix = "opencode")`
- `CardProperties` (`feishu-bot-domain/.../config/CardProperties.java`) - Card streaming configuration
- `FeishuReplyProperties` (`feishu-bot-domain/.../config/FeishuReplyProperties.java`) - Reply mode configuration

**Key Config Sections in `application.yml`:**
```yaml
feishu:
  mode: listener                    # WebSocket long-connection mode (mandatory)
  reply.mode: DEFAULT               # Reply strategy: DEFAULT | TOPIC
  topic-mapping.storage-type: sqlite # Persistence: sqlite | file
  topic-mapping.sqlite.path: data/feishu-topic-mappings.db

opencode:
  server-url: http://localhost:4098
  connect-timeout: 10
  request-timeout: 60
  sync-timeout: 30
  async-timeout: 60
  sse-enabled: true
  card.enabled: true                # Card-based streaming responses
```

**Build Configuration:**
- `pom.xml` (root) - Parent POM, dependency management
- DataSource auto-configuration is **excluded**: `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})`
  - SQLite datasources are created manually in gateway implementations

## Thread Pools

**Async Executors (configured in `AsyncConfig.java`):**
- `bashExecutor` - For BashApp command execution
  - Core: 5, Max: 10, Queue: 100, Prefix: `bash-async-`
- `opencodeExecutor` - For OpenCode async operations
  - Core: 2, Max: 5, Queue: 100, Prefix: `opencode-async-`
  - Graceful shutdown: waits 60 seconds for tasks

**WebSocket Thread:**
- `feishu-ws-listener` - Dedicated thread for Feishu WebSocket connection

## Key Design Patterns

**Strategy Pattern (Reply Handling):**
- Interface: `ReplyStrategy` (`feishu-bot-domain/.../reply/ReplyStrategy.java`)
- Factory: `ReplyStrategyFactory` (`feishu-bot-domain/.../reply/ReplyStrategyFactory.java`)
- Implementations in `feishu-bot-infrastructure/.../reply/`:
  - `DirectReplyStrategy` - Direct reply, no topic creation
  - `TopicReplyStrategy` - Reply within/create topics
  - `DefaultReplyStrategy` - Default behavior

**Anti-Corruption Layer (ACL):**
- Interface: `MessageEventParser` (`feishu-bot-domain/.../gateway/MessageEventParser.java`)
- Implementation: `MessageEventParserImpl` (`feishu-bot-infrastructure/.../parser/MessageEventParserImpl.java`)
- Purpose: Isolates Feishu SDK types (`P2MessageReceiveV1`) from domain model (`Message`)

**Gateway Pattern (Dependency Inversion):**
- Interfaces defined in `feishu-bot-domain/.../gateway/`
- Implementations in `feishu-bot-infrastructure/.../gateway/`
- 7 gateway pairs:
  | Interface | Implementation |
  |-----------|---------------|
  | `FeishuGateway` | `FeishuGatewayImpl` |
  | `MessageListenerGateway` | `MessageListenerGatewayImpl` |
  | `OpenCodeGateway` | `OpenCodeGatewayImpl` |
  | `OpenCodeEventGateway` | `OpenCodeEventGatewayImpl` |
  | `CardGateway` | `CardGatewayImpl` |
  | `AppSessionGateway` | `AppSessionGatewayImpl` |
  | `ImContextBindingGateway` | `ImContextBindingGatewayImpl` |

**Command/Adapter Pattern:**
- `CommandAdapter` / `ResponseAdapter` interfaces in `feishu-bot-domain/.../adapter/`
- Implementations in `feishu-bot-infrastructure/.../adapter/`:
  - `MessageCommandAdapter`, `CardCommandAdapter`
  - `MessageResponseAdapter`, `CardResponseAdapter`

**Application Plugin System:**
- Interface: `FishuAppI` (`feishu-bot-domain/.../app/FishuAppI.java`)
- Registry: `AppRegistry` (`feishu-bot-domain/.../core/AppRegistry.java`)
- All apps auto-registered via Spring `@Component` scanning
- 5 apps: `BashApp`, `TimeApp`, `HelpApp`, `HistoryApp`, `OpenCodeApp`

## Platform Requirements

**Development:**
- JDK 17+
- Maven 3.x
- Network access to Feishu API servers (`open.feishu.cn`)
- Optional: OpenCode server running on `localhost:4098`
- Optional: SQLite (embedded, no separate install needed)

**Production:**
- JDK 17 runtime
- Outbound network access to Feishu WebSocket (`msg-frontner.feishu.cn`)
- Outbound network access to Feishu API (`open.feishu.cn`)
- Optional: OpenCode server for AI coding assistant features
- No inbound ports required (WebSocket long-connection model, NOT webhook)
- Log output: `/tmp/feishu-run.log`
- Data directory: `data/` (SQLite DB files)

**Deployment:**
- Fat JAR via `spring-boot-maven-plugin`
- Started via: `mvn spring-boot:run` (dev) or `java -jar feishu-bot-start-*.jar` (prod)
- Launch script: `start-feishu.sh` (sets env vars, kills old process, starts in background)

---

*Stack analysis: 2026-04-06*

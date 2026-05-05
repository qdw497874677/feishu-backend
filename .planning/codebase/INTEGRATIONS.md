# External Integrations

**Analysis Date:** 2026-04-06

## APIs & External Services

### Feishu/Lark Open Platform

**Purpose:** Core messaging platform - receive user commands, send bot replies, manage topics/threads, interactive cards, emoji reactions.

**SDK:** `com.larksuite.oapi:oapi-sdk:2.5.2`
- WebSocket Client: `com.lark.oapi.ws.Client` (long-connection)
- HTTP Client: `com.lark.oapi.Client` (API calls)
- Event Dispatcher: `com.lark.oapi.event.EventDispatcher`

**Auth:** OAuth2 app credentials via environment variables
- `FEISHU_APPID` - Application ID
- `FEISHU_APPSECRET` - Application secret
- `FEISHU_ENCRYPT_KEY` - Event encryption key
- `FEISHU_VERIFICATION_TOKEN` - Event verification token

**Connection Mode:** WebSocket long-connection ONLY (webhook is prohibited per AGENTS.md)
- Endpoint: `wss://msg-frontner.feishu.cn/...`
- Heartbeat: 30 seconds
- Auto-reconnect: exponential backoff, 1s → 30s max, 10 attempts

**API Operations Used:**
| Operation | SDK Method | Gateway Method | File |
|-----------|-----------|---------------|------|
| Send message to user | `im().message().create()` | `sendReply()`, `sendDirectReply()` | `FeishuGatewayImpl.java` |
| Send message to chat | `im().message().create()` | `sendMessageToChat()` | `FeishuGatewayImpl.java` |
| Reply to message | `im().message().reply()` | `sendReplyToMessage()` | `FeishuGatewayImpl.java` |
| List messages | `im().message().list()` | `listMessages()` | `FeishuGatewayImpl.java` |
| Add emoji reaction | `im().messageReaction().create()` | `addReaction()` | `FeishuGatewayImpl.java` |
| Create card entity | `cardkit().v1().card().create()` | `createCard()` | `CardGatewayImpl.java` |
| Update card content | `cardkit().v1().card().update()` | `updateCard()` | `CardGatewayImpl.java` |
| Send interactive message | `im().message().reply()` (msgType=interactive) | `sendInteractiveMessage()` | `FeishuGatewayImpl.java` |

**Event Types Handled:**
- `P2MessageReceiveV1` - User sends a message → parsed by `MessageEventParserImpl`
- `P2CardActionTrigger` - User clicks card button → converted to command in `MessageListenerGatewayImpl`

**Retry Strategy (all Feishu API calls):**
- Max retries: 3
- Exponential backoff: 1s, 2s, 4s (max 8s)
- Retries on: `UnknownHostException` (DNS failure)
- Implemented in: `FeishuGatewayImpl.executeWithRetry()`

**Implementation Files:**
- `feishu-bot-infrastructure/.../gateway/FeishuGatewayImpl.java` - Messaging API
- `feishu-bot-infrastructure/.../gateway/CardGatewayImpl.java` - Card Kit API
- `feishu-bot-infrastructure/.../gateway/MessageListenerGatewayImpl.java` - WebSocket listener
- `feishu-bot-infrastructure/.../parser/MessageEventParserImpl.java` - Event parsing (ACL)

---

### OpenCode Server (AI Coding Assistant)

**Purpose:** AI-powered coding assistant integration. Users interact with OpenCode through Feishu bot commands to create sessions, send prompts, and receive AI-generated code responses.

**Protocol:** HTTP REST API with HTTP Basic Authentication
- Base URL: configurable via `opencode.server-url` (default: `http://localhost:4098`)
- Auth: HTTP Basic with `opencode.username` / `opencode.password`

**Auth Env Vars:**
- `OPencode_SERVER_URL` - Server URL
- `OPencode_USERNAME` - Basic auth username
- `OPencode_SERVER_PASSWORD` - Basic auth password
- `OPencode_PROJECT_ROOT` - Default workspace directory

**HTTP Client:** `java.net.http.HttpClient` (JDK built-in, NOT the Feishu SDK client)
- Connect timeout: 10 seconds (configurable)
- Request timeout: 60 seconds (configurable)

**API Endpoints Used:**
| Endpoint | Method | Purpose | Gateway Method |
|----------|--------|---------|---------------|
| `/session` | POST | Create new session | `createSession()` |
| `/session` | GET | List all sessions | `listSessions()` |
| `/session/{id}` | GET | Get session details | `getSessionDetails()` |
| `/session/{id}/message` | POST | Send message to session | `sendMessageSync()` |
| `/session/{id}/prompt_async` | POST | Send async message | `sendMessageAsync()` |
| `/project` | GET | List projects | `listProjects()` |
| `/command` | GET | List slash commands | `listCommands()` |
| `/global/health` | GET | Health check | `isServerHealthy()` |
| `/event` | GET (SSE) | Server-Sent Events stream | `OpenCodeEventGatewayImpl` |

**SSE Event Streaming:**
- Uses Spring WebFlux `WebClient` for reactive SSE consumption
- Event types: `server.connected`, `server.heartbeat`, session events
- Auto-reconnect with exponential backoff (Reactor `Retry.backoff`)
- Configured via: `opencode.sse-enabled`, `opencode.sse-reconnect-interval`
- Implementation: `feishu-bot-infrastructure/.../gateway/OpenCodeEventGatewayImpl.java`
- Wired in: `feishu-bot-infrastructure/.../config/OpenCodeSseConfig.java`

**Retry Strategy (OpenCode API calls):**
- Max retries: 3
- Exponential backoff: 1s, 2s, 4s (max 8s)
- Retries on: `ConnectException`, `HttpTimeoutException`, generic exceptions
- Health check before requests (when `healthCheckEnabled=true`)
- Implemented in: `OpenCodeGatewayImpl.executeWithRetry()`

**Request/Response Format:**
```json
// Request (send message)
{"parts": [{"type": "text", "text": "user prompt"}]}

// Response (message result)  
{"parts": [{"type": "text", "text": "AI response"}, {"type": "tool_use", "toolUse": {"output": "..."}}]}

// Session creation
POST /session?directory=<encoded-path>
{"parentID": "optional"}
// Response: {"id": "session-id"}
```

**Implementation Files:**
- `feishu-bot-infrastructure/.../gateway/OpenCodeGatewayImpl.java` - REST API client (889 lines)
- `feishu-bot-infrastructure/.../gateway/OpenCodeEventGatewayImpl.java` - SSE event stream
- `feishu-bot-infrastructure/.../config/OpenCodeProperties.java` - Configuration
- `feishu-bot-infrastructure/.../config/OpenCodeSseConfig.java` - SSE auto-wiring
- `feishu-bot-domain/.../opencode/OpenCodeApp.java` - App entry point
- `feishu-bot-domain/.../opencode/OpenCodeCommandHandler.java` - Command dispatch
- `feishu-bot-domain/.../opencode/OpenCodeSessionManager.java` - Session lifecycle
- `feishu-bot-domain/.../opencode/OpenCodeStreamingHandler.java` - Streaming response handler
- `feishu-bot-domain/.../opencode/OpenCodeTaskExecutor.java` - Async task execution

---

## Data Storage

### SQLite (Embedded)

**Purpose:** Local persistence for session state, IM context bindings, and topic mappings.

**Driver:** `org.xerial:sqlite-jdbc:3.42.0.0`
**Access:** `JdbcTemplate` (Spring JDBC, no ORM)
**DataSource:** Manually created via `DataSourceBuilder` (not Spring auto-configured)

**Database File:**
- Path: `data/feishu-topic-mappings.db` (configurable via `feishu.topic-mapping.sqlite.path`)
- Git: NOT ignored by default (can be committed for version control)
- Created automatically on first startup

**Tables:**

| Table | Purpose | Gateway |
|-------|---------|---------|
| `app_session` | Application session state with optimistic locking | `AppSessionGatewayImpl` |
| `im_context_binding` | Maps IM contexts (topics/chats) to app sessions | `ImContextBindingGatewayImpl` |

**`app_session` Schema:**
```sql
CREATE TABLE IF NOT EXISTS app_session (
    app_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    state TEXT NOT NULL,         -- CREATED, ACTIVE, IDLE, TERMINATED, EXPIRED
    data TEXT,                   -- JSON serialized session data
    version INTEGER NOT NULL DEFAULT 1,  -- optimistic lock
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL,
    expires_at INTEGER,
    PRIMARY KEY (app_id, session_id)
);
-- Indexes: idx_session_app_id, idx_session_state, idx_session_created
```

**`im_context_binding` Schema:**
```sql
CREATE TABLE IF NOT EXISTS im_context_binding (
    context_key TEXT PRIMARY KEY NOT NULL,  -- e.g. "feishu:topic:ot_xxx"
    platform TEXT NOT NULL,                -- "feishu"
    context_type TEXT NOT NULL,            -- "topic" or "chat"
    context_id TEXT NOT NULL,
    app_id TEXT NOT NULL,
    session_id TEXT,                       -- nullable (two-phase binding)
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL
);
-- Indexes: idx_binding_app_session, idx_binding_platform, idx_binding_app_id
```

**Concurrency Control:**
- Optimistic locking via `version` column in `app_session`
- `OptimisticLockException` thrown on version conflicts
- State machine transitions validated: `SessionState.canTransitionTo()`

**Schema Migration:**
- Atomic create-copy-swap pattern for `im_context_binding` table
- Migrates `session_id` from NOT NULL to nullable
- Uses PRAGMA table_info for schema detection
- All steps in single transaction for atomicity

**Implementation Files:**
- `feishu-bot-infrastructure/.../gateway/AppSessionGatewayImpl.java` - Session CRUD (417 lines)
- `feishu-bot-infrastructure/.../gateway/ImContextBindingGatewayImpl.java` - Binding CRUD (436 lines)
- Conditional: `@ConditionalOnProperty(name = "feishu.topic-mapping.storage-type", havingValue = "sqlite", matchIfMissing = true)`

### File Storage

- **None** - No file uploads/downloads. Local filesystem used only for SQLite DB and logs.

### Caching

- **In-memory only** - `ConcurrentHashMap` for card sequence tracking (`CardGatewayImpl.cardSequenceMap`)
- No distributed cache (Redis, Memcached, etc.)

---

## Authentication & Identity

**Auth Provider:** Custom (no external auth provider like OAuth2/OIDC)

**Feishu SDK Authentication:**
- App-level OAuth2 (tenant access token) handled internally by Feishu SDK
- SDK auto-manages token refresh via `Client.newBuilder(appId, appSecret)`
- No user-level OAuth (bot uses app identity only)

**OpenCode Authentication:**
- HTTP Basic Authentication
- Credentials: `opencode.username` + `opencode.password`
- Base64 encoded in `Authorization` header
- Implementation: `OpenCodeGatewayImpl.getAuthHeader()`

**User Identification:**
- Feishu `openId` extracted from message events
- Used for: directing replies, user info lookup
- No local user database or user management

---

## Monitoring & Observability

**Error Tracking:**
- COLA `catchlog-starter` - Automatic exception catching and logging via AOP
- Custom exception hierarchy:
  - `MessageSysException` (system errors)
  - `MessageBizException` (business errors)
  - `MessageInvalidException` (validation errors)
  - `ConnectionLostException` (connectivity)
  - `OptimisticLockException` (concurrency)
- Global exception handler: `feishu-bot-adapter/.../exception/GlobalExceptionHandler.java`

**Logging:**
- Framework: SLF4J `2.0.9` with Logback (Spring Boot default)
- Config: `application.yml` logging section
- Log file: `/tmp/feishu-run.log` (via shell script redirect)
- Log levels:
  - `root: INFO`
  - `com.qdw.feishu: DEBUG`
  - `com.alibaba.cola: INFO`
  - `com.lark.oapi: WARN` (test profile)

**Health Checks:**
- OpenCode server health: `GET /global/health` (configurable, `opencode.health-check-enabled`)
- No Spring Actuator endpoints detected
- WebSocket connection status tracked via `AtomicReference<ConnectionStatus>`

---

## CI/CD & Deployment

**Hosting:**
- Self-hosted (Linux server at `/root/workspace/feishu-backend`)
- No cloud platform detected (no Dockerfile, Kubernetes manifests, or cloud configs)

**CI Pipeline:**
- None detected (no `.github/workflows/`, `.gitlab-ci.yml`, `Jenkinsfile`)

**Build Process:**
```bash
mvn clean package           # Build all modules
mvn spring-boot:run         # Run from source (dev)
./start-feishu.sh           # Production startup script
```

**Deployment Script (`start-feishu.sh`):**
1. Validates `FEISHU_APPID` and `FEISHU_APPSECRET` env vars
2. Sets UTF-8 locale (`LANG=zh_CN.UTF-8`)
3. Kills old processes (`pkill -9 -f "feishu"`)
4. Clears port 17777
5. Runs `mvn spring-boot:run` in background
6. Logs to `/tmp/feishu-run.log`

---

## Environment Configuration

**Required env vars (production):**
- `FEISHU_APPID` - Feishu app ID (required)
- `FEISHU_APPSECRET` - Feishu app secret (required)

**Optional env vars:**
- `FEISHU_ENCRYPT_KEY` - Event encryption
- `FEISHU_VERIFICATION_TOKEN` - Event verification
- `OPencode_SERVER_URL` - OpenCode API URL (default: `http://localhost:4098`)
- `OPencode_USERNAME` - OpenCode auth user (default: `opencode`)
- `OPencode_SERVER_PASSWORD` - OpenCode auth password
- `OPencode_PROJECT_ROOT` - Default project directory

**Secrets Management:**
- Environment variables (no vault, no encrypted config)
- `run-local.sh` contains credentials for dev (git-ignored)
- `.env` files: Not present (not used)

---

## Webhooks & Callbacks

**Incoming:**
- `MessageTestController` (`feishu-bot-adapter/.../test/MessageTestController.java`) - Test endpoint only
- No production webhook endpoints (WebSocket long-connection model used instead)
- Card action callbacks received via WebSocket `P2CardActionTrigger` events

**Outgoing:**
- None - All external communication is request/response or SSE subscription

---

## Integration Summary

| Integration | Protocol | Auth | Direction | Criticality |
|-------------|----------|------|-----------|-------------|
| Feishu IM API | HTTPS REST | OAuth2 (app token) | Outbound | **Critical** |
| Feishu WebSocket | WSS | OAuth2 (app token) | Bidirectional | **Critical** |
| Feishu Card Kit | HTTPS REST | OAuth2 (app token) | Outbound | Important |
| OpenCode Server | HTTP REST | Basic Auth | Outbound | Optional |
| OpenCode SSE | HTTP SSE | Basic Auth | Inbound (streaming) | Optional |
| SQLite | Embedded JDBC | None (local file) | Local | Important |

---

*Integration audit: 2026-04-06*

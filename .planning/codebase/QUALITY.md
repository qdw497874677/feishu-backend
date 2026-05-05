# Codebase Quality Analysis

**Analysis Date:** 2026-04-06
**Focus:** Comprehensive quality assessment — testing, code review findings, error handling, logging, configuration

---

## 1. Test Coverage Overview

### Statistics

| Metric | Count |
|--------|-------|
| Production Java files (`src/main/`) | 115 |
| Test Java files (`src/test/`) | 21 |
| Total `@Test` methods | 261 |
| Test-to-production file ratio | 18% |
| Production classes with NO test | 87 |

### Test Distribution by Module

| Module | Test Files | @Test Methods | Key Classes Tested |
|--------|-----------|---------------|--------------------|
| `feishu-bot-domain` | 12 | 163 | OpenCodeSessionManager(39), OpenCodeCommandHandler(23), OpenCodeApp(21), CommandWhitelistValidator(18), BashApp(14) |
| `feishu-bot-app` | 4 | 37 | OpenCodeMessageAppService(14), ContextSessionOrchestratorImpl(13), ReceiveMessageListenerExe(8), BotMessageAppService(2) |
| `feishu-bot-infrastructure` | 4 | 58 | ImContextBindingGatewayImpl(30), AppSessionGatewayImpl(17), CardGatewayImpl(9), FeishuGatewayImpl(2) |
| `feishu-bot-start` | 1 | 3 | HelpAppCardButtonJsonTest(3) |

### Critical Untested Classes

**Priority: HIGH**
- `feishu-bot-infrastructure/.../gateway/OpenCodeGatewayImpl.java` — 889 lines, largest file, ZERO tests. Core integration with OpenCode server.
- `feishu-bot-infrastructure/.../parser/MessageEventParserImpl.java` — Anti-corruption layer, parses ALL incoming Feishu events. ZERO tests.
- `feishu-bot-domain/.../opencode/OpenCodeTaskExecutor.java` — Async execution orchestrator. ZERO tests.
- `feishu-bot-domain/.../opencode/OpenCodeResponseFormatter.java` — Session ID extraction from output. ZERO tests.
- `feishu-bot-domain/.../service/MessageDeduplicator.java` — Event dedup. ZERO tests.

**Priority: MEDIUM**
- `feishu-bot-adapter/.../listener/FeishuEventListener.java` — WebSocket event entry point
- `feishu-bot-adapter/.../exception/GlobalExceptionHandler.java` — Exception-to-response mapping
- `feishu-bot-domain/.../app/TimeApp.java`, `HistoryApp.java` — Simple apps
- `feishu-bot-infrastructure/.../gateway/MessageListenerGatewayImpl.java` — WebSocket lifecycle

### Test Framework and Patterns

**Runner:** JUnit 5 (Jupiter) via `spring-boot-starter-test`
**Mocking:** Mockito 5 with `@ExtendWith(MockitoExtension.class)`
**Coverage tool:** None configured (no JaCoCo)

**Run all tests:**
```bash
mvn test                           # All modules
mvn test -pl feishu-bot-domain     # Domain only
mvn test -pl feishu-bot-app        # App layer only
mvn test -pl feishu-bot-infrastructure  # Infra only
```

### Test Quality Patterns (from AGENTS.md §6)

**Good patterns observed:**
- Precise assertions: `assertEquals("opencode", app.getAppId())`
- Verify calls: `verify(commandHandler).handle(eq(message), eq("projects"), any(), any())`
- `@DisplayName` in Chinese for readability
- Helper factory methods: `createTestMessage(String content, String topicId)`

**Anti-patterns to watch for:**
- Some tests use only `assertNotNull(result)` instead of precise value checks (AGENTS.md §6.1 forbids this)
- Mixed test naming conventions: `should_X_when_Y` vs `methodName_returnsExpected`
- No shared test fixtures — each test class duplicates `createTestMessage()` factories

---

## 2. Code Review Findings (C2, W1–W7)

### C2: Test Controller Unsafe Profile Exposure — CRITICAL

**File:** `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java` (line 23)

**Problem:** `@Profile({"dev", "test", "default"})` — the `"default"` profile is active when no profile is explicitly set, which is the case in production. This exposes an unauthenticated HTTP endpoint at `/test/message` that can execute arbitrary commands including `/bash`.

**Current code:**
```java
@Profile({"dev", "test", "default"})
public class MessageTestController {
    @PostMapping
    public ResponseEntity<String> sendTestMessage(@RequestParam String content, ...) {
        botMessageAppService.handleMessage(message);  // No auth check
    }
}
```

**Fix:** Remove `"default"` → `@Profile({"dev", "test"})`. Better: move to `src/test/` entirely.

### W1: Domain Layer Uses Spring Stereotypes — WARNING

**26 Spring annotations found in `feishu-bot-domain/`:**

```
@Component (20 occurrences): BashApp, HelpApp, TimeApp, HistoryApp, OpenCodeApp,
  OpenCodeCommandHandler, OpenCodeSessionManager, OpenCodeTaskExecutor,
  OpenCodeStreamingHandler, OpenCodeResponseFormatter, AppRegistry,
  MessageDeduplicator, CommandWhitelistValidator, TopicCommandValidator,
  AppRouter, UnifiedCommandRouter, CommandAdapterFactory, ResponseAdapterFactory,
  StreamingCardManager, CardProperties

@Service (3): BotMessageService, BashHistoryManager, EventProcessor, TopicCommandValidator

@ConfigurationProperties (2): FeishuReplyProperties, CardProperties
```

**Impact:** Domain module POM depends on `spring-context` and `spring-boot-autoconfigure`. Core business logic cannot be tested without Spring framework on classpath (though current tests construct objects manually).

**Fix:** Move annotations to `@Bean` factory methods in `feishu-bot-infrastructure/.../config/DomainServiceConfig.java`.

### W2: saveSession() Can Overwrite Another App's Binding — WARNING

**Files:**
- `feishu-bot-domain/.../opencode/OpenCodeSessionManager.java` (lines 156-190)
- `feishu-bot-infrastructure/.../gateway/ImContextBindingGatewayImpl.java` (line 313)

**Problem:** `saveSession()` calls `bindingGateway.bind(contextRef, "opencode", newSessionId)`. The bind() method's UPDATE path overwrites `app_id` unconditionally: `SET app_id = ?, session_id = ?`. If the context was bound to a different app, it gets silently overwritten.

**Impact:** Low probability currently (only OpenCode uses session binding), but architecturally dangerous for future apps.

**Fix:** Add app_id guard: only update if `existing.getAppId().equals(appId)` or context is unbound.

### W3: SQLite Binding Upsert Not Atomic Under Concurrency — WARNING

**File:** `feishu-bot-infrastructure/.../gateway/ImContextBindingGatewayImpl.java` (lines 284-347)

**Problem:** Read-then-write pattern (`findBinding()` → INSERT or UPDATE) is not atomic. Two concurrent messages for the same topic could both see "not found" and both attempt INSERT.

**Mitigation:** SQLite's single-writer serialization prevents actual data corruption in single-process mode. But this fails under horizontal scaling.

**Fix:** Use `INSERT INTO ... ON CONFLICT(context_key) DO UPDATE SET ...` (SQLite upsert).

### W4: Session Progression Depends on Parsing Reply Text — WARNING

**File:** `feishu-bot-app/.../opencode/OpenCodeMessageAppService.java` (lines 184-201)

**Problem:** Session ID is extracted from the bot's formatted reply text by searching for `Session ID: \`` and extracting the backtick-delimited value. This couples session state management to display formatting.

```java
private Optional<String> extractSessionId(String replyContent) {
    int startIndex = replyContent.indexOf("Session ID: `");  // Fragile!
    // ...
    return Optional.of(replyContent.substring(startIndex, endIndex));
}
```

**Impact:** If any developer changes the response text format (removes backticks, changes label, changes emoji prefix), session binding silently breaks with no error and no test to catch it.

**Fix:** Add `sessionId` field to `HandledMessageResult` and populate it in `OpenCodeTaskExecutor` rather than parsing display text.

### W5: Message Content Logged at INFO Level — WARNING

**Files (12 occurrences):**
- `feishu-bot-app/.../listener/ReceiveMessageListenerExe.java:46` — `log.info("消息内容: {}", message.getDisplayContent())`
- `feishu-bot-domain/.../app/HelpApp.java:60`, `TimeApp.java:62`, `HistoryApp.java:78` — `log.info("输入消息: {}", message.getContent())`
- `feishu-bot-domain/.../opencode/OpenCodeApp.java:174` — `log.info("OpenCodeApp.execute: content='{}'", content)`
- `feishu-bot-infrastructure/.../gateway/FeishuGatewayImpl.java:81,115,146,187,216,238` — logs full reply content on every API call

**Impact:** All user messages AND bot replies are visible in plaintext in `/tmp/feishu-run.log`. Privacy/compliance risk.

**Fix:** Change message content logging to DEBUG. Log `messageId` at INFO for traceability.

### W7: BotRoutingDecision Returns Executable App — WARNING

**File:** `feishu-bot-domain/.../message/BotRoutingDecision.java` (line 16)

**Problem:** `BotRoutingDecision` carries `FishuAppI app` — an executable reference. A routing decision should be pure metadata.

```java
@Data
@AllArgsConstructor
public class BotRoutingDecision {
    private String appId;
    private FishuAppI app;        // <-- Should not be here
    private boolean persistBinding;
}
```

**Fix:** Remove `app` field. Resolve app from `AppRegistry.getApp(decision.getAppId())` at execution time.

---

## 3. Error Handling Analysis

### Exception Hierarchy

```
com.alibaba.cola.exception.BizException
  └── MessageBizException          (business rule violations)

com.alibaba.cola.exception.SysException
  └── MessageSysException          (system failures)

RuntimeException
  ├── MessageInvalidException      (invalid message content)
  ├── ConnectionLostException      (WebSocket issues)
  └── OptimisticLockException      (concurrent update conflicts)
```

### Error Handling Patterns

**Pattern 1 — Domain throws, app layer catches and replies:**
```java
// ReceiveMessageListenerExe.java (app layer)
try {
    botMessageAppService.handleMessage(message);
} catch (MessageBizException e) {
    feishuGateway.sendMessage(message, e.getMessage(), message.getTopicId());
} catch (Exception e) {
    log.error("消息处理失败", e);  // Generic catch - no user reply
}
```

**Concern:** The generic `catch (Exception e)` logs but does NOT reply to the user. The user sees nothing — message appears to be ignored.

**Pattern 2 — Infrastructure retry with exponential backoff:**
```java
// FeishuGatewayImpl.java
executeWithRetry("sendMessage", () -> {
    // ... API call ...
});
// MAX_RETRIES = 3, backoff: 1s, 2s, 4s (capped at 8s)
// Only retries on UnknownHostException (DNS failure)
```

**Concern:** Only DNS failures are retried. Other transient errors (connection reset, timeout, 429 rate limit) are not retried.

**Pattern 3 — Optimistic locking:**
```java
// AppSessionGatewayImpl.java
if (currentVersion != version) {
    throw new OptimisticLockException(version, currentVersion);
}
```

**Concern:** `OptimisticLockException` is thrown but not caught anywhere in the call chain. It will bubble up to `ReceiveMessageListenerExe` as a generic Exception, logged but not retried.

**Pattern 4 — Silent swallow (anti-pattern):**
```java
// FeishuGatewayImpl.listMessages() — 3 empty catch blocks (lines 308, 315, 322)
try {
    if (msg.getBody() != null) { content = ...; }
} catch (Exception e) {
    // EMPTY - swallowed silently
}
```

### GlobalExceptionHandler Coverage

**File:** `feishu-bot-adapter/.../exception/GlobalExceptionHandler.java`

Only handles 2 exception types:
- `ConnectionLostException` → HTTP 503
- `MessageInvalidException` → logged, no HTTP response body

**Missing handlers:** `MessageBizException`, `MessageSysException`, `OptimisticLockException`, generic `Exception`. These would propagate as unhandled Spring errors (HTTP 500).

---

## 4. Logging Practices

### Configuration

**File:** `feishu-bot-start/src/main/resources/application.yml` (lines 74-82)

```yaml
logging:
  level:
    root: INFO
    com.qdw.feishu: DEBUG    # All project packages at DEBUG
    com.alibaba.cola: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  charset:
    console: UTF-8
```

**Concern:** `com.qdw.feishu: DEBUG` in production config means all DEBUG logs are emitted. This includes gateway lookups, binding checks, and JSON parsing details. Should be INFO in production, DEBUG in dev.

### Log Volume Analysis

**High-volume INFO logging in message processing path:**

Each incoming message generates ~15+ INFO log lines:
1. `ReceiveMessageListenerExe`: 5 lines (event ID, sender, content, message ID, separator)
2. `BotMessageService`: 1-2 lines (alias resolution, context resolution)
3. `OpenCodeApp`: 1 line (content echo)
4. `OpenCodeCommandHandler`: 2 lines (command validation, topic state)
5. `FeishuGatewayImpl`: 2-3 lines (send content, API response)
6. `ReceiveMessageListenerExe`: 2 lines (success, separator)

**At scale (100 messages/hour):** ~1500+ log lines/hour of operational noise at INFO level.

### Logging Language Mix

Logs use a mix of Chinese and English:
- Chinese: `log.info("消息内容: {}")`, `log.info("已重置话题初始化状态")`
- English: `log.info("Sending message to chatId: {}")`, `log.info("Reply success: messageId={}")`

This is consistent within individual files but inconsistent across the codebase.

---

## 5. Configuration Management

### Configuration Architecture

**Domain config interface:** `feishu-bot-domain/.../config/FeishuConfig.java` — defines `getAppId()`, `getAppSecret()`, etc.
**Infrastructure implementation:** `feishu-bot-infrastructure/.../config/FeishuProperties.java` — implements via `@ConfigurationProperties(prefix = "feishu")`

**Concern (W1):** Two config classes live in domain with Spring annotations:
- `feishu-bot-domain/.../config/FeishuReplyProperties.java` — `@Component @ConfigurationProperties(prefix = "feishu.reply")`
- `feishu-bot-domain/.../config/CardProperties.java` — `@Component @ConfigurationProperties(prefix = "opencode.card")`

These should be in infrastructure, with domain consuming them through interfaces.

### Environment Variables

**Required (from `application.yml`):**
- `FEISHU_APPID` — Feishu app ID (has hardcoded default — security risk)
- `FEISHU_APPSECRET` — Feishu app secret (has hardcoded default — security risk)
- `FEISHU_ENCRYPT_KEY` — event encryption key (default: `your_encrypt_key`)
- `FEISHU_VERIFICATION_TOKEN` — event verification token

**Optional:**
- `OPencode_SERVER_URL` — OpenCode server URL (default: `http://localhost:4098`)
- `OPencode_USERNAME` — OpenCode auth username (default: `opencode`)
- `OPencode_SERVER_PASSWORD` — OpenCode auth password (no default)
- `OPencode_PROJECT_ROOT` — default project root

**Concern:** Environment variable names use inconsistent casing: `FEISHU_APPID` (all caps, no underscore before ID) vs `OPencode_SERVER_URL` (mixed case). This is error-prone.

### Profile Management

**Profiles defined:** `default` (active by default), `dev` (in `application-dev.yml`)
**Concern:** No `application-prod.yml` exists. Production configuration relies on environment variables overriding defaults. There's no explicit production profile to disable test endpoints or set appropriate log levels.

---

## 6. Largest Files (Complexity Indicators)

| Lines | File | Concern |
|-------|------|---------|
| 889 | `feishu-bot-infrastructure/.../gateway/OpenCodeGatewayImpl.java` | 3x over 300-line limit, ZERO tests |
| 502 | `feishu-bot-infrastructure/.../gateway/FeishuGatewayImpl.java` | 1.7x over limit, only 2 tests |
| 497 | `feishu-bot-domain/.../opencode/OpenCodeCommandHandler.java` | 1.7x over limit, 23 tests |
| 436 | `feishu-bot-infrastructure/.../gateway/ImContextBindingGatewayImpl.java` | 1.5x over limit, 30 tests |
| 417 | `feishu-bot-infrastructure/.../gateway/AppSessionGatewayImpl.java` | 1.4x over limit, 17 tests |
| 346 | `feishu-bot-domain/.../app/BashApp.java` | 1.15x over limit, has duplicate code |

AGENTS.md §2 recommends max 300 lines per class. Six files exceed this.

---

## 7. Broken Points in OpenCode Flow (from live testing)

### Session ID Extraction Fragility

The `OpenCodeMessageAppService.extractSessionId()` method (line 189) searches for the literal string `Session ID: \`` in bot reply text. This is the ONLY mechanism for session progression in the `OpenCodeMessageAppService` flow.

**Known failure modes:**
1. If `OpenCodeTaskExecutor.executeAsync()` sends the reply directly via `feishuGateway.sendMessage()` (which it does), `progressSessionIfNeeded()` sees the formatted text but parsing may fail if format changes.
2. If the async path saves the session via `sessionManager.saveSession()` AND the sync path also tries to extract+save from reply text, there's a potential double-save race.

### Topic State Detection Race

`OpenCodeApp.execute()` calls `sessionManager.detectTopicState(message)` and then `commandHandler.handle()` also calls `sessionManager.detectTopicState(message)`. Between these two calls, another message could change the state (e.g., reset command in another thread). The state is detected twice independently rather than passed once.

### Streaming Handler Cleanup Gap

`OpenCodeStreamingHandler.handleSessionComplete()` calls `unregisterSession()` which removes all maps. But if `flushBuffer()` is scheduled and runs after `unregisterSession()`, it finds null maps and silently does nothing — the last text buffer content may be lost.

---

*Quality analysis: 2026-04-06*

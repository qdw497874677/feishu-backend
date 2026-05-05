# Coding Conventions

**Analysis Date:** 2026-04-06

## Naming Patterns

**Files:**
- PascalCase Java classes: `BotMessageService.java`, `OpenCodeCommandHandler.java`
- Interface suffix `-I` or `-Gateway`: `FishuAppI.java`, `FeishuGateway.java`
- Implementation suffix `-Impl`: `FeishuGatewayImpl.java`, `MessageEventParserImpl.java`
- Test suffix `Test`: `BotMessageServiceTest.java`
- Enums: PascalCase without suffix: `TopicState.java`, `ReplyMode.java`, `SessionState.java`

**Functions:**
- camelCase consistently: `routeMessage()`, `handleChatCommand()`, `extractSessionId()`
- Boolean getters: `isTopicInitialized()`, `isExplicitCommand()`, `shouldPersistBinding()`
- Handler methods: `handle()`, `handleEvent()`, `handleConnect()`
- Builder methods: `buildConnectGuide()`, `buildInitializationGuide()`
- Private helpers: `extractAppId()`, `resolveContextRef()`

**Variables:**
- camelCase: `topicId`, `sessionId`, `replyContent`, `contextRef`
- Constants: `UPPER_SNAKE_CASE`: `MAX_RETRIES`, `DEFAULT_SESSION_LIMIT`, `APP_ID`
- Package-level constants use `static final`: `EXECUTE_TIMEOUT`, `FLUSH_INTERVAL_MS`

**Types/Classes:**
- PascalCase: `BotRoutingDecision`, `ImContextBinding`, `OpenCodeSessionData`
- Enum values: `UPPER_SNAKE_CASE`: `NON_TOPIC`, `UNINITIALIZED`, `INITIALIZED`

**Packages:**
- All lowercase, domain-driven: `com.qdw.feishu.domain.opencode`, `com.qdw.feishu.infrastructure.gateway`

## Code Style

**Formatting:**
- No explicit formatter configured (no `.prettierrc`, `.editorconfig`, or formatter plugin in `pom.xml`)
- 4-space indentation (de facto standard from code examination)
- Opening braces on same line
- Max line length ~120 characters (observed)

**Linting:**
- No explicit linting tool (no Checkstyle, SpotBugs, or PMD in `pom.xml`)
- COLA framework provides some architectural guardrails via `cola-component-*`

**Lombok:**
- Used universally: `@Slf4j`, `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Domain entities: `@Data @NoArgsConstructor` (e.g., `Message.java`)
- Value objects: `@Data @AllArgsConstructor` (e.g., `BotRoutingDecision.java`)

## Import Organization

**Order (observed pattern):**
1. `com.qdw.feishu.*` (project classes)
2. `com.alibaba.cola.*` (COLA framework)
3. `com.lark.oapi.*` (Feishu SDK — infrastructure only)
4. `com.fasterxml.jackson.*` (JSON processing)
5. `org.springframework.*` (Spring framework)
6. `lombok.*`
7. `java.*` / `javax.*` / `jakarta.*` (stdlib)

**Path Aliases:**
- None. All imports are fully qualified package paths.

## Error Handling

**Domain exceptions hierarchy (all in `com.qdw.feishu.domain.exception/`):**
- `MessageBizException` extends COLA `BizException` — business rule violations
- `MessageSysException` extends COLA `SysException` — system/infrastructure failures  
- `MessageInvalidException` — invalid message content
- `ConnectionLostException` — WebSocket connection issues
- `OptimisticLockException` — concurrent session update conflicts

**Pattern — catch at app layer, reply to user:**
```java
// In ReceiveMessageListenerExe.java (app layer)
try {
    botMessageAppService.handleMessage(message);
} catch (MessageBizException e) {
    feishuGateway.sendMessage(message, e.getMessage(), message.getTopicId());
} catch (Exception e) {
    log.error("消息处理失败", e);
}
```

**Pattern — retry with exponential backoff (infrastructure layer):**
```java
// In FeishuGatewayImpl.java
private <T> T executeWithRetry(String operationName, Supplier<T> operation) {
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
        try { return operation.get(); }
        catch (SysException e) {
            if (e.getCause() instanceof UnknownHostException) {
                long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);
                Thread.sleep(delay);
            } else { throw e; }
        }
    }
}
```

**Pattern — optimistic locking (session updates):**
```java
// In AppSessionGatewayImpl.java
if (currentVersion != version) {
    throw new OptimisticLockException(version, currentVersion);
}
```

**Anti-pattern observed:** Some empty catch blocks in `FeishuGatewayImpl.listMessages()` silently swallow exceptions during message parsing (lines 308, 315, 322). Use `log.debug()` at minimum.

## Logging

**Framework:** SLF4J via Lombok `@Slf4j`

**Levels configured in `application.yml`:**
- `root`: INFO
- `com.qdw.feishu`: DEBUG
- `com.alibaba.cola`: INFO

**Patterns:**
- INFO for key business operations: message received, session created/bound, reply sent
- DEBUG for diagnostic: binding lookups, context resolution, JSON parsing fallbacks
- WARN for recoverable issues: missing config, fallback behavior, failed reactions
- ERROR for failures: exception stack traces, failed API calls

**Concern (W5): Message content logged at INFO level** — multiple files log user message content at INFO:
- `ReceiveMessageListenerExe.java:46` — `log.info("消息内容: {}", message.getDisplayContent())`
- `FeishuGatewayImpl.java:81,115,146,187,216,238` — logs reply content in full at INFO
- `OpenCodeApp.java:174` — `log.info("OpenCodeApp.execute: content='{}'", content)`
- Should be DEBUG for privacy/compliance

## Comments

**When to Comment:**
- Javadoc on public interfaces and key domain methods (e.g., `FishuAppI`, `ReplyStrategy`)
- Inline comments for complex state machine logic (e.g., `OpenCodeCommandHandler` switch statement)
- Class-level Javadoc on gateway implementations explaining schema migration strategy

**Chinese comments:** Extensively used for log messages and inline documentation. Mix of Chinese and English is the project norm. Log messages are predominantly Chinese.

**JSDoc/TSDoc:** Not applicable (Java project).

## Function Design

**Size:**
- Most methods: 5-30 lines (good)
- Largest methods: `FeishuGatewayImpl.listMessages()` ~80 lines (should be refactored)
- Largest classes: `OpenCodeGatewayImpl` (889 lines), `FeishuGatewayImpl` (502 lines), `OpenCodeCommandHandler` (497 lines) — above the 300-line guideline in AGENTS.md

**Parameters:**
- Constructor injection exclusively (no field injection, no setter injection)
- Method parameters: generally 2-4 (acceptable)
- Complex input wrapped in domain objects (`Message`, `UnifiedCommand`)

**Return Values:**
- `String` for app execution results (legacy `execute(Message)` pattern)
- `BizResult` for new `execute(UnifiedCommand)` pattern
- `Optional<T>` for nullable lookups (gateway queries, session resolution)
- `SendResult` for Feishu API operations

## Module Design

**Exports:** No explicit module system (no `module-info.java`). Dependencies managed via Maven modules.

**Barrel Files:** Not applicable (Java).

**Dependency injection pattern:**
- Constructor-based injection universally
- `@Component` / `@Service` annotations in domain layer (architectural concern W1)
- `@Bean` factory methods in `DomainServiceConfig.java` for strategy factories

## Spring Stereotypes in Domain Layer (W1)

**26 Spring annotations (`@Component`, `@Service`, `@ConfigurationProperties`) found in domain module.**
Domain layer classes should be framework-agnostic. Currently, domain depends on `spring-context` and `spring-boot-autoconfigure` in its POM.

Key offenders:
- `BotMessageService.java` — `@Service`
- All app implementations — `@Component` (BashApp, HelpApp, TimeApp, HistoryApp, OpenCodeApp)
- `OpenCodeSessionManager.java` — `@Component`
- `OpenCodeCommandHandler.java` — `@Component`
- `AppRegistry.java` — `@Component`
- Config classes — `@ConfigurationProperties`

**Recommended fix:** Move wiring to infrastructure/start modules. Domain classes should be POJOs registered via `@Bean` factories.

---

*Convention analysis: 2026-04-06*

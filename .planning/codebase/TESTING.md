# Testing Patterns

**Analysis Date:** 2026-04-06

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) — via `spring-boot-starter-test` dependency
- Config: No explicit `junit-platform.properties` or `surefire-plugin` customization in `pom.xml`

**Assertion Library:**
- JUnit Jupiter assertions: `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, `assertThrows`
- No Hamcrest or AssertJ observed

**Mocking:**
- Mockito 5 (bundled with spring-boot-starter-test)
- `@ExtendWith(MockitoExtension.class)` and `@MockitoSettings(strictness = Strictness.LENIENT)` used

**Run Commands:**
```bash
mvn test                     # Run all tests
mvn test -pl feishu-bot-domain   # Run domain tests only
mvn test -pl feishu-bot-app      # Run app layer tests only
mvn test -pl feishu-bot-infrastructure  # Run infrastructure tests only
```

## Test File Organization

**Location:**
- Co-located in module but separate `src/test/java/` directory (Maven standard)
- Mirror production package structure

**Naming:**
- `{ClassName}Test.java` (e.g., `BashAppTest.java`, `OpenCodeSessionManagerTest.java`)
- Exception: `OpenCodeExplicitInitializationTest.java` — scenario-focused name

**Structure:**
```
feishu-bot-domain/src/test/java/com/qdw/feishu/domain/
├── app/BashAppTest.java
├── card/StreamingCardManagerTest.java
├── history/BashHistoryManagerTest.java
├── model/ImContextBindingTest.java
├── opencode/
│   ├── OpenCodeAppTest.java
│   ├── OpenCodeCommandHandlerTest.java
│   ├── OpenCodeEventTest.java
│   ├── OpenCodeExplicitInitializationTest.java
│   ├── OpenCodeSessionManagerTest.java
│   └── OpenCodeStreamingHandlerTest.java
├── service/BotMessageServiceTest.java
└── validation/CommandWhitelistValidatorTest.java

feishu-bot-app/src/test/java/com/qdw/feishu/app/
├── listener/ReceiveMessageListenerExeTest.java
├── message/BotMessageAppServiceTest.java
├── opencode/OpenCodeMessageAppServiceTest.java
└── session/ContextSessionOrchestratorImplTest.java

feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/
├── AppSessionGatewayImplTest.java
├── CardGatewayImplTest.java
├── FeishuGatewayImplTest.java
└── ImContextBindingGatewayImplTest.java

feishu-bot-start/src/test/java/com/qdw/feishu/
└── HelpAppCardButtonJsonTest.java
```

## Test Suite Statistics

| Module | Test Files | @Test Methods |
|--------|-----------|---------------|
| domain | 12 | 163 |
| app | 4 | 37 |
| infrastructure | 4 | 58 |
| start | 1 | 3 |
| **Total** | **21** | **261** |

**Production files:** 115 (in `src/main/`)
**Test files:** 21 (in `src/test/`)
**Test-to-production ratio:** ~18% of production files have corresponding tests

## Test Structure

**Suite Organization Pattern:**
```java
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpenCodeApp 单元测试")
class OpenCodeAppTest {

    @Mock
    private OpenCodeGateway openCodeGateway;

    @Mock
    private OpenCodeCommandHandler commandHandler;

    private OpenCodeApp app;

    @BeforeEach
    void setUp() {
        app = new OpenCodeApp(openCodeGateway, commandHandler, sessionManager);
        // Default mock setup
        when(sessionManager.detectTopicState(any(Message.class)))
            .thenReturn(TopicState.INITIALIZED);
    }

    // ========== Section Headers ==========

    @Test
    @DisplayName("getAppId 应返回 'opencode'")
    void getAppId_returnsOpencode() {
        assertEquals("opencode", app.getAppId());
    }
}
```

**Patterns observed:**
- `@DisplayName` in Chinese for readability
- `@BeforeEach` for common mock setup
- Section comments `// ========== ... ==========` to group related tests
- Helper methods: `createTestMessage(String content, String topicId)` factory pattern

**Test method naming:**
- `should_X_when_Y` pattern (AGENTS.md standard): `should_routePlainTextToHelp_when_contextIsUnbound()`
- Verb phrase pattern: `getAppId_returnsOpencode()`, `execute_helpCommand_returnsHelp()`
- Mixed conventions across test files (inconsistent)

## Mocking

**Framework:** Mockito

**Patterns:**
```java
// Standard mock injection with constructor
@Mock private OpenCodeGateway openCodeGateway;
@Mock private OpenCodeCommandHandler commandHandler;

@BeforeEach
void setUp() {
    app = new OpenCodeApp(openCodeGateway, commandHandler, sessionManager);
}

// Verification pattern
verify(commandHandler).handle(eq(message), eq("projects"), any(), any());
verify(commandHandler, never()).handle(any(), anyString(), any(), any());

// Argument matcher pattern (must use all matchers or all values)
when(commandHandler.handle(eq(message), eq("sessions"), any(), any()))
    .thenReturn(expectedResponse);
```

**What to Mock:**
- Gateway interfaces: `FeishuGateway`, `OpenCodeGateway`, `ImContextBindingGateway`, `AppSessionGateway`
- Cross-component dependencies: `OpenCodeCommandHandler`, `OpenCodeSessionManager`
- Infrastructure services: never used directly in tests (mocked through gateways)

**What NOT to Mock:**
- Domain entities: `Message`, `Sender`, `ImContextRef`, `ImContextBinding` — always use real objects
- Value objects and enums: `TopicState`, `ReplyMode`, `CommandWhitelist`
- The class under test

**Infrastructure tests use real SQLite:**
```java
// ImContextBindingGatewayImplTest, AppSessionGatewayImplTest
// Use actual SQLite database (in-memory or temp file) for integration-like tests
```

## Fixtures and Factories

**Test Data Factory Pattern:**
```java
// Common helper in test classes
private Message createTestMessage(String content, String topicId) {
    Message message = new Message();
    message.setContent(content);
    message.setTopicId(topicId);
    message.setMessageId("msg-test-" + System.currentTimeMillis());
    message.setChatId("chat-test");
    message.setSender(new Sender("test-openid", "Test User"));
    return message;
}

// Variant for different contexts
private Message createTopicMessage(String content, String topicId) { ... }
private Message createChatMessage(String content, String chatId) { ... }
```

**Location:**
- Inline in each test class (no shared test fixtures directory)
- Each test class defines its own factory methods
- No shared `TestFixtures` or `TestDataBuilder` class

## Coverage

**Requirements:** No coverage enforcement configured (no JaCoCo plugin in `pom.xml`)

**Current coverage gaps (87 out of 115 production classes lack dedicated tests):**

**Critical untested classes:**
- `FeishuEventListener.java` — adapter layer entry point, no tests
- `MessageListenerGatewayImpl.java` — WebSocket listener, no tests  
- `OpenCodeGatewayImpl.java` — largest file (889 lines), no tests
- `OpenCodeEventGatewayImpl.java` — SSE event handling, no tests
- `MessageEventParserImpl.java` — anti-corruption layer parser, no tests
- `OpenCodeTaskExecutor.java` — async execution, no tests
- `OpenCodeResponseFormatter.java` — session ID extraction, no tests
- `TimeApp.java`, `HistoryApp.java` — simple apps, no tests
- `MessageDeduplicator.java` — dedup logic, no tests

**Adequately tested classes (by @Test count):**
- `OpenCodeSessionManagerTest.java` — 39 tests (thorough)
- `ImContextBindingGatewayImplTest.java` — 30 tests
- `OpenCodeCommandHandlerTest.java` — 23 tests
- `OpenCodeAppTest.java` — 21 tests
- `CommandWhitelistValidatorTest.java` — 18 tests
- `AppSessionGatewayImplTest.java` — 17 tests
- `BashAppTest.java` — 14 tests

## Test Types

**Unit Tests:**
- Primary testing approach
- Mock all dependencies, test single class behavior
- Cover happy path + key error paths
- Location: `*/src/test/java/`

**Integration Tests:**
- Infrastructure gateway tests use real SQLite databases
- `AppSessionGatewayImplTest`, `ImContextBindingGatewayImplTest` — test actual SQL queries
- No Spring context loaded (`@SpringBootTest` not used in any test)

**E2E Tests:**
- Not present
- `MessageTestController.java` exists in production code (`adapter/test/`) as a manual testing endpoint — NOT an automated test
- `MANUAL_TESTING_GUIDE.sh` exists for manual verification

## Common Patterns

**Async Testing:**
- Not systematically tested
- `OpenCodeTaskExecutor.executeAsync()` uses `@Async` but tests don't verify async behavior
- No `CompletableFuture` assertions or `@Timeout` annotations observed

**Error Testing:**
```java
@Test
void should_rejectOtherAppCommand_when_contextBoundToOpenCode() {
    // ... setup ...
    assertThrows(MessageBizException.class, () -> botMessageService.routeMessage(message));
    verify(bindingGateway, never()).bind(any(), anyString(), any());
}
```

**State-based Testing (OpenCode topic states):**
```java
// Three topic states require different test setup:
// NON_TOPIC:     topicId == null
// UNINITIALIZED: topicId != null, no sessionId bound
// INITIALIZED:   topicId != null, sessionId bound

// Correct setup for INITIALIZED:
String topicId = "init-topic";
when(sessionManager.getSessionId(topicId))
    .thenReturn(Optional.of("ses_123"));
when(sessionManager.isExplicitlyInitialized(topicId))
    .thenReturn(true);
```

## Known Test Quality Issues

**From AGENTS.md code review guidelines:**

1. **Assertion strength:** Some tests use only `assertNotNull(result)` instead of precise value checks. The AGENTS.md explicitly forbids this pattern.

2. **Mockito matcher mixing:** Must use all matchers or all values. Example of correct usage:
   ```java
   when(commandHandler.handle(any(Message.class), eq("projects"), any(String[].class), any(CommandWhitelist.class)))
       .thenReturn(expectedResponse);
   ```

3. **Missing verify() calls:** Some tests check return values but don't verify the correct internal methods were called.

4. **No test for session progression (W4):** `OpenCodeMessageAppService.progressSessionIfNeeded()` parses reply text to extract session IDs using string matching (`Session ID: \`...\``). This fragile pattern has no dedicated test.

---

*Testing analysis: 2026-04-06*

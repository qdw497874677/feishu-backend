# Business Exception Reply Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix critical issue C1 - ensure users receive error replies when `MessageBizException` is thrown during message processing.

**Architecture:** Inject `FeishuGateway` into `ReceiveMessageListenerExe` to send error replies directly from the listener layer. This minimizes changes and keeps the fix localized.

**Tech Stack:** Spring DI, Mockito for testing, Lombok

---

## Task 1: Write Failing Tests for Business Exception Reply

**Files:**
- Modify: `feishu-bot-app/src/test/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExeTest.java`

**Step 1: Add imports for new test dependencies**

Add to existing imports section (after line 16):

```java
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.SendResult;
```

**Step 2: Add mock for FeishuGateway**

Add after line 28 (after `MessageDeduplicator` mock):

```java
    @Mock
    private FeishuGateway feishuGateway;
```

**Step 3: Update setUp to include FeishuGateway in constructor**

Replace the existing `setUp` method (lines 32-39):

```java
    @BeforeEach
    void setUp() {
        listenerExe = new ReceiveMessageListenerExe(
                botMessageAppService,
                openCodeMessageAppService,
                messageDeduplicator,
                feishuGateway
        );
    }
```

**Step 4: Add test for BotMessageAppService throwing MessageBizException**

Add after line 74 (before `createMessage` helper):

```java
    @Test
    void should_sendBizExceptionReplyToUser_when_botMessageAppServiceThrowsBizException() {
        // given
        Message message = createMessage("/help", "evt-biz-1");
        String errorMessage = "跨应用命令被拒绝";
        when(messageDeduplicator.isProcessed("evt-biz-1")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException(errorMessage));
        when(feishuGateway.sendMessage(message, errorMessage, null)).thenReturn(SendResult.success("msg-reply-1"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(message, errorMessage, null);
    }

    @Test
    void should_sendBizExceptionReplyToTopic_when_openCodeThrowsBizException() {
        // given
        Message message = createMessage("/opencode chat test", "evt-biz-2");
        message.setTopicId("topic-123");
        String errorMessage = "会话未初始化";
        when(messageDeduplicator.isProcessed("evt-biz-2")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenThrow(new MessageBizException(errorMessage));
        when(feishuGateway.sendMessage(message, errorMessage, "topic-123")).thenReturn(SendResult.success("msg-reply-2"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(message, errorMessage, "topic-123");
    }

    @Test
    void should_sendDefaultErrorMessage_when_bizExceptionMessageIsNull() {
        // given
        Message message = createMessage("/help", "evt-biz-3");
        when(messageDeduplicator.isProcessed("evt-biz-3")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException((String) null));
        when(feishuGateway.sendMessage(message, "操作失败，请稍后重试", null)).thenReturn(SendResult.success("msg-reply-3"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(message, "操作失败，请稍后重试", null);
    }

    @Test
    void should_sendDefaultErrorMessage_when_bizExceptionMessageIsEmpty() {
        // given
        Message message = createMessage("/help", "evt-biz-4");
        when(messageDeduplicator.isProcessed("evt-biz-4")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException(""));
        when(feishuGateway.sendMessage(message, "操作失败，请稍后重试", null)).thenReturn(SendResult.success("msg-reply-4"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(message, "操作失败，请稍后重试", null);
    }

    @Test
    void should_logWarning_when_bizExceptionReplyFails() {
        // given
        Message message = createMessage("/help", "evt-biz-5");
        String errorMessage = "业务错误";
        when(messageDeduplicator.isProcessed("evt-biz-5")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException(errorMessage));
        when(feishuGateway.sendMessage(message, errorMessage, null)).thenReturn(SendResult.failure("网络错误"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(message, errorMessage, null);
        // Note: We verify the sendMessage was called; log verification is optional
    }
```

**Step 5: Run tests to verify they fail**

Run:
```bash
cd /root/workspace/feishu-backend && mvn -pl feishu-bot-app -am -Dtest=ReceiveMessageListenerExeTest test 2>&1 | tail -50
```

Expected: Compilation error (constructor signature mismatch) or test failure

---

## Task 2: Implement FeishuGateway Injection in ReceiveMessageListenerExe

**Files:**
- Modify: `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java`

**Step 1: Add imports**

Add after line 7 (after `MessageDeduplicator` import):

```java
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.SendResult;
```

**Step 2: Add FeishuGateway field**

Add after line 22 (after `messageDeduplicator` field):

```java
    private final FeishuGateway feishuGateway;
```

**Step 3: Update constructor**

Replace constructor (lines 24-30):

```java
    public ReceiveMessageListenerExe(BotMessageAppService botMessageAppService,
                                     OpenCodeMessageAppService openCodeMessageAppService,
                                     MessageDeduplicator messageDeduplicator,
                                     FeishuGateway feishuGateway) {
        this.botMessageAppService = botMessageAppService;
        this.openCodeMessageAppService = openCodeMessageAppService;
        this.messageDeduplicator = messageDeduplicator;
        this.feishuGateway = feishuGateway;
    }
```

**Step 4: Run tests to verify they still fail (now at assertion level)**

Run:
```bash
cd /root/workspace/feishu-backend && mvn -pl feishu-bot-app -am -Dtest=ReceiveMessageListenerExeTest test 2>&1 | tail -50
```

Expected: Tests compile but fail at assertions (verify() failures)

---

## Task 3: Implement Business Exception Reply Logic

**Files:**
- Modify: `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java`

**Step 1: Update the catch block for MessageBizException**

Replace the existing catch block (lines 56-57):

```java
        } catch (MessageBizException e) {
            String errorReply = e.getMessage();
            if (errorReply == null || errorReply.isEmpty()) {
                errorReply = "操作失败，请稍后重试";
            }
            SendResult result = feishuGateway.sendMessage(message, errorReply, message.getTopicId());
            if (result.isSuccess()) {
                log.info("业务异常已回复给用户: {}", errorReply);
            } else {
                log.warn("业务异常回复发送失败: {}", result.getErrorMessage());
            }
```

**Step 2: Run tests to verify they pass**

Run:
```bash
cd /root/workspace/feishu-backend && mvn -pl feishu-bot-app -am -Dtest=ReceiveMessageListenerExeTest test 2>&1 | tail -30
```

Expected: All tests pass

---

## Task 4: Run Full Test Suite

**Step 1: Run all tests**

Run:
```bash
cd /root/workspace/feishu-backend && mvn test 2>&1 | tail -50
```

Expected: All tests pass (163+ tests)

**Step 2: If tests fail, investigate and fix**

Check test output for specific failures and address them.

---

## Task 5: Commit Changes

**Step 1: Stage changes**

Run:
```bash
cd /root/workspace/feishu-backend && git add feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java feishu-bot-app/src/test/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExeTest.java
```

**Step 2: Commit with descriptive message**

Run:
```bash
cd /root/workspace/feishu-backend && git commit -m "fix(listener): send error reply to user on MessageBizException

- Inject FeishuGateway into ReceiveMessageListenerExe
- Send error reply to original message position when biz exception occurs
- Use default message when exception message is null/empty
- Log INFO on success, WARN on send failure
- Add 5 test cases for exception handling scenarios

Fixes: C1 (critical issue from code review)"
```

---

## Task 6: Manual Verification (Optional)

**Step 1: Build the application**

Run:
```bash
cd /root/workspace/feishu-backend && mvn clean package -DskipTests 2>&1 | tail -20
```

**Step 2: Start the application locally**

Run:
```bash
cd /root/workspace/feishu-backend && ./run-local.sh
```

**Step 3: Test cross-app command rejection**

Send a message in a topic bound to OpenCode:
```
/help
```

Expected: User receives error message about cross-app command rejection

---

## Summary

| Task | Description | Files Modified |
|------|-------------|----------------|
| 1 | Write failing tests | `ReceiveMessageListenerExeTest.java` |
| 2 | Add FeishuGateway injection | `ReceiveMessageListenerExe.java` |
| 3 | Implement exception reply logic | `ReceiveMessageListenerExe.java` |
| 4 | Run full test suite | - |
| 5 | Commit changes | - |
| 6 | Manual verification | - |

**Total estimated time:** 30-45 minutes

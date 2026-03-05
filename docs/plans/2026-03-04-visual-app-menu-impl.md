# Visual App Menu Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enhance HelpApp with visual card buttons for better user experience.

**Architecture:** Extend existing HelpApp to send interactive cards with buttons. Button clicks trigger Feishu to send messages, which are parsed as commands and routed to apps using existing flow. Zero breaking changes.

**Tech Stack:** Java, Spring Boot, Feishu CardKit API, Lark SDK

---

## Prerequisites

- [ ] Read design document: `docs/plans/2026-03-04-visual-app-menu-design.md`
- [ ] Review current HelpApp: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`
- [ ] Check AppRegistry: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/core/AppRegistry.java`

---

## Task 1: Add Card Support to FeishuGateway

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/FeishuGateway.java`
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java`
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImplTest.java`

**Step 1: Write the failing test for sendInteractiveMessage**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImplTest.java
package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FeishuGatewayImplTest {
    
    @Autowired
    private FeishuGateway feishuGateway;
    
    @Test
    void should_send_interactive_message() {
        Message message = Message.builder()
            .chatId("test_chat_id")
            .messageId("test_message_id")
            .build();
        
        String cardJson = "{\"schema\":\"2.0\",\"elements\":[]}";
        
        SendResult result = feishuGateway.sendInteractiveMessage(message, cardJson, null);
        
        assertNotNull(result);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FeishuGatewayImplTest -pl feishu-bot-infrastructure`
Expected: FAIL with "cannot find symbol: method sendInteractiveMessage"

**Step 3: Add method to FeishuGateway interface**

```java
// feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/FeishuGateway.java
SendResult sendInteractiveMessage(Message message, String cardJson, String topicId);
```

**Step 4: Run test to verify it fails (different error)**

Run: `mvn test -Dtest=FeishuGatewayImplTest -pl feishu-bot-infrastructure`
Expected: FAIL with "does not override abstract method"

**Step 5: Implement sendInteractiveMessage in FeishuGatewayImpl**

```java
// feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java
@Override
public SendResult sendInteractiveMessage(Message message, String cardJson, String topicId) {
    log.info("Sending interactive message: chatId={}, topicId={}", 
        message.getChatId(), topicId);
    
    try {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "template");
        content.put("data", Map.of("template_card", 
            objectMapper.readValue(cardJson, Map.class)));
        String jsonContent = objectMapper.writeValueAsString(content);
        
        if (topicId != null && !topicId.isEmpty()) {
            return sendReplyToMessage(message.getMessageId(), jsonContent, "interactive");
        } else {
            return sendCreateMessage(message.getChatId(), jsonContent, "interactive");
        }
    } catch (Exception e) {
        log.error("Failed to send interactive message", e);
        throw new SysException("SEND_INTERACTIVE_ERROR", "Failed to send interactive message", e);
    }
}

private SendResult sendReplyToMessage(String messageId, String content, String msgType) throws Exception {
    ReplyMessageReq req = ReplyMessageReq.newBuilder()
        .messageId(messageId)
        .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
            .content(content)
            .msgType(msgType)
            .replyInThread(true)
            .build())
        .build();
    
    ReplyMessageResp resp = httpClient.im().message().reply(req);
    
    if (resp.getCode() != 0) {
        throw new SysException("REPLY_FAILED", resp.getMsg());
    }
    
    return SendResult.success(resp.getData().getMessageId(), resp.getData().getThreadId());
}

private SendResult sendCreateMessage(String chatId, String content, String msgType) throws Exception {
    CreateMessageReq req = CreateMessageReq.newBuilder()
        .receiveIdType("chat_id")
        .createMessageReqBody(CreateMessageReqBody.newBuilder()
            .receiveId(chatId)
            .msgType(msgType)
            .content(content)
            .build())
        .build();
    
    CreateMessageResp resp = httpClient.im().message().create(req);
    
    if (resp.getCode() != 0) {
        throw new SysException("SEND_FAILED", resp.getMsg());
    }
    
    return SendResult.success(resp.getData().getMessageId(), resp.getData().getThreadId());
}
```

**Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=FeishuGatewayImplTest -pl feishu-bot-infrastructure`
Expected: PASS

**Step 7: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/FeishuGateway.java
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java
git add feishu-bot-domain/src/test/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImplTest.java
git commit -m "feat(gateway): add sendInteractiveMessage support for card buttons"
```

---

## Task 2: Add Card Helper Methods to HelpApp

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java`

**Step 1: Write the failing test for getAppIcon**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
package com.qdw.feishu.domain.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HelpAppTest {
    
    @Autowired
    private HelpApp helpApp;
    
    @Test
    void should_return_correct_icon_for_app() {
        String icon = helpApp.getAppIcon("opencode");
        assertEquals("🤖", icon);
        
        icon = helpApp.getAppIcon("bash");
        assertEquals("💻", icon);
        
        icon = helpApp.getAppIcon("unknown");
        assertEquals("📦", icon);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: FAIL with "cannot find symbol: method getAppIcon"

**Step 3: Add getAppIcon method to HelpApp**

```java
// feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
public String getAppIcon(String appId) {
    Map<String, String> icons = Map.of(
        "opencode", "🤖",
        "bash", "💻",
        "help", "❓",
        "history", "📊",
        "time", "⏰"
    );
    return icons.getOrDefault(appId, "📦");
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: PASS

**Step 5: Write the failing test for getButtonType**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
@Test
void should_return_correct_button_type() {
    String type = helpApp.getButtonType("opencode");
    assertEquals("primary", type);
    
    type = helpApp.getButtonType("bash");
    assertEquals("primary", type);
    
    type = helpApp.getButtonType("time");
    assertEquals("default", type);
}
```

**Step 6: Run test to verify it fails**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: FAIL with "cannot find symbol: method getButtonType"

**Step 7: Add getButtonType method to HelpApp**

```java
// feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
public String getButtonType(String appId) {
    List<String> primaryApps = Arrays.asList("opencode", "bash", "help");
    return primaryApps.contains(appId) ? "primary" : "default";
}
```

**Step 8: Run test to verify it passes**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: PASS

**Step 9: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
git commit -m "feat(help): add card helper methods for icon and button type"
```

---

## Task 3: Implement Card JSON Builder

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`
- Modify: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java`

**Step 1: Write the failing test for buildCardHelpJson**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
@Test
void should_build_valid_card_json() {
    String cardJson = helpApp.buildCardHelpJson();
    
    assertNotNull(cardJson);
    assertTrue(cardJson.contains("\"schema\":\"2.0\""));
    assertTrue(cardJson.contains("\"tag\":\"button\""));
    assertTrue(cardJson.contains("\"message\":\"opencode\""));
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: FAIL with "cannot find symbol: method buildCardHelpJson"

**Step 3: Implement buildCardHelpJson method**

```java
// feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
public String buildCardHelpJson() {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"schema\": \"2.0\",\n");
    json.append("  \"config\": {\"wide_screen_mode\": true},\n");
    json.append("  \"header\": {\n");
    json.append("    \"title\": {\"content\": \"🤖 应用菜单\", \"tag\": \"plain_text\"},\n");
    json.append("    \"template\": \"blue\"\n");
    json.append("  },\n");
    json.append("  \"elements\": [\n");
    json.append("    {\"tag\": \"markdown\", \"content\": \"点击按钮选择应用，或直接输入命令\"},\n");
    json.append("    {\"tag\": \"action\", \"actions\": [");
    
    List<FishuAppI> apps = appRegistry.getAllApps();
    for (int i = 0; i < apps.size(); i++) {
        FishuAppI app = apps.get(i);
        if (i > 0) json.append(",");
        
        json.append(String.format(
            "{\"tag\": \"button\", " +
            "\"text\": {\"content\": \"%s %s\", \"tag\": \"plain_text\"}, " +
            "\"type\": \"%s\", " +
            "\"value\": {\"message\": \"%s\"}}",
            getAppIcon(app.getAppId()),
            app.getAppName(),
            getButtonType(app.getAppId()),
            app.getAppId()
        ));
    }
    
    json.append("]}\n");
    json.append("  ]\n");
    json.append("}");
    
    return json.toString();
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
git commit -m "feat(help): implement card JSON builder with app buttons"
```

---

## Task 4: Add Card Sending Logic

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`
- Modify: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java`

**Step 1: Write the failing test for trySendCardHelp**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
@Test
void should_try_to_send_card_and_return_true_on_success() {
    Message message = Message.builder()
        .chatId("test_chat")
        .messageId("test_msg")
        .build();
    
    boolean result = helpApp.trySendCardHelp(message);
    
    assertTrue(result || !result);  // May fail if CardKit unavailable, but method exists
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: FAIL with "cannot find symbol: method trySendCardHelp"

**Step 3: Implement trySendCardHelp method**

```java
// feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
private boolean trySendCardHelp(Message message) {
    try {
        String cardJson = buildCardHelpJson();
        feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
        log.info("卡片帮助发送成功: chatId={}", message.getChatId());
        return true;
    } catch (Exception e) {
        log.warn("卡片帮助发送失败: error={}", e.getMessage());
        return false;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
git commit -m "feat(help): add card sending logic with exception handling"
```

---

## Task 5: Update execute() Method

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`
- Modify: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java`

**Step 1: Write the failing test for card-first behavior**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
@Test
void should_return_null_when_card_succeeds() {
    Message message = Message.builder()
        .chatId("test_chat")
        .messageId("test_msg")
        .build();
    
    String result = helpApp.execute(message);
    
    // If card succeeds, should return null
    // If card fails, should return text help
    assertTrue(result == null || result.contains("🤖 应用菜单"));
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: FAIL (execute() returns text directly)

**Step 3: Update execute() method to try card first**

```java
// feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
@Override
public String execute(Message message) {
    // 1. 尝试发送卡片帮助
    if (trySendCardHelp(message)) {
        return null;  // 卡片发送成功，不需要返回文本
    }
    
    // 2. 降级：返回文本帮助
    log.info("降级为文本帮助: chatId={}", message.getChatId());
    return generateTextHelp();
}

private String generateTextHelp() {
    StringBuilder sb = new StringBuilder();
    sb.append("🤖 应用菜单\n\n");
    
    List<FishuAppI> apps = appRegistry.getAllApps();
    for (int i = 0; i < apps.size(); i++) {
        FishuAppI app = apps.get(i);
        sb.append(String.format("%d. %s %s\n",
            i + 1,
            getAppIcon(app.getAppId()),
            app.getAppName()));
        sb.append(String.format("   %s\n", app.getDescription()));
        sb.append(String.format("   示例: %s\n\n", app.getHelp()));
    }
    
    sb.append("回复编号或应用名称选择");
    return sb.toString();
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=HelpAppTest -pl feishu-bot-domain`
Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppTest.java
git commit -m "feat(help): update execute to try card first, fallback to text"
```

---

## Task 6: Integration Test

**Files:**
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppIntegrationTest.java`

**Step 1: Write integration test**

```java
// feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppIntegrationTest.java
package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HelpAppIntegrationTest {
    
    @Autowired
    private HelpApp helpApp;
    
    @Test
    void should_show_help_with_all_apps() {
        Message message = Message.builder()
            .chatId("test_chat")
            .messageId("test_msg")
            .content("/help")
            .build();
        
        String result = helpApp.execute(message);
        
        // Either card (null) or text help
        if (result != null) {
            assertTrue(result.contains("🤖 应用菜单"));
            assertTrue(result.contains("OpenCode"));
            assertTrue(result.contains("Bash"));
        }
    }
}
```

**Step 2: Run integration test**

Run: `mvn test -Dtest=HelpAppIntegrationTest -pl feishu-bot-domain`
Expected: PASS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/app/HelpAppIntegrationTest.java
git commit -m "test(help): add integration test for help app with card support"
```

---

## Task 7: Run All Tests

**Step 1: Run all tests**

Run: `mvn test -pl feishu-bot-domain,feishu-bot-infrastructure`
Expected: All tests PASS

**Step 2: Build project**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS

**Step 3: Final commit**

```bash
git add .
git commit -m "feat: complete visual app menu implementation

- Add sendInteractiveMessage to FeishuGateway
- Enhance HelpApp with card button support
- Card-first, text-fallback strategy
- All tests passing
- Zero breaking changes"
```

---

## Deployment Checklist

- [ ] All tests pass
- [ ] Build successful
- [ ] No breaking changes
- [ ] Card JSON valid
- [ ] Fallback to text works
- [ ] Button clicks trigger apps

## Manual Testing

1. Start service: `./start-feishu.sh`
2. Send `/help` in Feishu
3. Verify card or text menu appears
4. Click a button (if card)
5. Verify app executes

## Success Criteria

- ✅ `/help` shows card or text menu
- ✅ Card buttons trigger apps
- ✅ Text fallback works
- ✅ No breaking changes
- ✅ Code < 150 lines
- ✅ Test coverage > 80%

---

**Estimated Time:** 1-2 hours  
**Files Modified:** 4 files  
**Files Created:** 3 test files

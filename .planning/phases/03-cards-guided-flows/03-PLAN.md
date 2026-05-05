---
phase: 03-cards-guided-flows
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardContent.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardButton.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/CardRenderer.java
  - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card/FeishuCardRenderer.java
  - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/AppExecutionResult.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/WizardManager.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java
  - feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java
  - feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
  - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/card/CardContentTest.java
  - feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/card/FeishuCardRendererTest.java
  - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/WizardManagerTest.java
  - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandlerTest.java
  - feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeAppTest.java
  - feishu-bot-start/src/test/java/com/qdw/feishu/HelpAppCardButtonJsonTest.java
autonomous: true
requirements: [CARD-01, CARD-02, CARD-03]

must_haves:
  truths:
    - "CardContent 是 IM 无关的领域模型，FeishuCardRenderer 将其转为 schema 2.0 JSON"
    - "卡片按钮 value 包含完整上下文（chatId/topicId/sessionId），handleCardAction 解析并设置到伪 Message"
    - "Message.java 新增 cardToken 字段，handleCardAction 从 event 中提取并设置"
    - "卡片动作的伪 Message 经过 MessageContextResolver 解析，获得已解析的 MessageContext（非 unresolved）"
    - "OpenCodeCommandHandler 注入 CardRenderer + FeishuGateway（均为 domain 接口，COLA 合规）"
    - "向导/会话卡片通过 handler 内直接发送卡片 + 返回 AppExecutionResult.noReply() 实现"
    - "首次进入未绑定话题自动弹出 3 步向导卡片（选项目→选会话→确认绑定）"
    - "向导进行中非向导命令被拦截并提示'请先完成向导'"
    - "WizardManager 使用带 TTL 驱逐的缓存（10 分钟过期），避免内存泄漏"
    - "WizardManager 使用 ConcurrentHashMap.compute() 进行状态转换，避免竞态"
    - "会话列表支持卡片和纯文本两种形式，默认卡片优先"
    - "会话列表卡片中每个会话显示最后提示词摘要和相对时间戳"
    - "HelpApp 的 buildCardHelpJson() 迁移到 FeishuCardRenderer，消除手写 JSON"
    - "HelpApp 迁移有金标准测试——新旧 JSON 结构等价验证"
    - "309 个现有测试继续通过"
  artifacts:
    - path: "feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardContent.java"
      provides: "IM 无关的卡片内容模型"
      contains: "header"
      min_lines: 30
    - path: "feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardButton.java"
      provides: "按钮值对象"
      contains: "action"
    - path: "feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/CardRenderer.java"
      provides: "卡片渲染网关接口"
      contains: "render"
    - path: "feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card/FeishuCardRenderer.java"
      provides: "飞书 schema 2.0 JSON 渲染"
      contains: "FeishuCardRenderer"
      min_lines: 80
    - path: "feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/WizardManager.java"
      provides: "向导状态管理和流程控制"
      contains: "WizardStep"
      min_lines: 60
  key_links:
    - from: "MessageListenerGatewayImpl.handleCardAction()"
      to: "action value 解析含 chatId/topicId + 设置 cardToken"
      via: "扩展 value map 提取上下文字段，设置 topicId 和 cardToken 到伪 Message"
      pattern: "actionValue.*get\\(\"chatId\"\\)"
    - from: "handleCardAction 伪 Message"
      to: "MessageContextResolver 解析"
      via: "伪 Message 设置 chatId+topicId 后经正常管道获得已解析 MessageContext"
      pattern: "messageContext\\.isResolved\\(\\)"
    - from: "OpenCodeCommandHandler.handleWizardAction()"
      to: "feishuGateway.sendInteractiveMessage() + noReply()"
      via: "handler 内直接发送卡片 + 返回 AppExecutionResult.noReply()"
      pattern: "sendInteractiveMessage.*noReply"
    - from: "WizardManager.handleWizardStep()"
      to: "CardContent 构建"
      via: "向导各步骤生成对应卡片"
      pattern: "CardContent\\.builder"
    - from: "FeishuCardRenderer.render()"
      to: "schema 2.0 JSON"
      via: "CardContent → 飞书 JSON 转换"
      pattern: "\"schema\".*\"2.0\""
---

<objective>
为 OpenCode 添加交互式卡片能力：卡片构建器基础设施、富上下文卡片按钮、3 步入门向导、增强会话列表。

Purpose: Phase 3 是 v1 的最后阶段。完成后，用户既可以通过命令行操作，也可以通过卡片按钮完成项目选择和会话绑定，首次用户有引导式向导降低使用门槛。
Output: CardContent 领域模型 + FeishuCardRenderer + 富上下文卡片按钮 + 3 步向导 + 增强会话列表 + HelpApp 迁移
</objective>

<execution_context>
@$HOME/.config/opencode/get-shit-done/workflows/execute-plan.md
@$HOME/.config/opencode/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/03-cards-guided-flows/03-CONTEXT.md

# Phase 1-2 基础设施（只读参考）
@.planning/phases/01-context-foundation/01-plan-SUMMARY.md
@.planning/phases/02-command-router-conversation-ux/02-01-SUMMARY.md
@.planning/phases/02-command-router-conversation-ux/02-02-SUMMARY.md

<interfaces>
<!-- Phase 1-2 已建立的核心类型，本计划直接使用 -->

From feishu-bot-domain/.../app/AppExecutionResult.java:
```java
public class AppExecutionResult {
    public static AppExecutionResult text(String content);
    public static AppExecutionResult noReply();
    public static AppExecutionResult withSession(String content, String openCodeSessionId, boolean created);
    public String getReplyContent();                 // null = 不发送回复
    public String getOpenCodeSessionId();
    public boolean isSessionCreated();
}
```

From feishu-bot-domain/.../model/MessageContext.java:
```java
public class MessageContext {
    public static MessageContext of(ImContextRef ref, ImContextBinding binding);
    public static MessageContext unresolved();
    public boolean isResolved();
    public boolean isBound();
    public boolean isBoundToApp(String appId);
    public boolean isThreadContext();
    public ImContextRef getContextRef();
    public ImContextBinding getBinding();
    public Optional<String> getBoundSessionId();
}
```

From feishu-bot-domain/.../opencode/OpenCodeSessionManager.java:
```java
public TopicState detectTopicState(MessageContext messageContext);
public String getCurrentSessionStatus(MessageContext messageContext);
public Optional<String> getSessionId(MessageContext messageContext);
public boolean isTopicInitialized(MessageContext messageContext);
public boolean isExplicitlyInitialized(MessageContext messageContext);
public String handleSessionsCommand(String[] parts);
public void saveSession(ImContextRef contextRef, String openCodeSessionId);
public void clearSession(ImContextRef contextRef);
public void setExplicitlyInitialized(ImContextRef contextRef);
```

From feishu-bot-domain/.../topic/TopicState.java:
```java
public enum TopicState { NON_TOPIC, UNINITIALIZED, INITIALIZED }
```

From feishu-bot-domain/.../command/UnifiedCommand.java:
```java
public class UnifiedCommand {
    private String appId;
    private String subCommand;
    private String[] args;
    private String openId;
    private String topicId;
    private String messageId;
    private String cardToken;
    private EventSource source;  // MESSAGE or CARD
    public boolean isFromCard();
    public boolean isFromMessage();
    public boolean hasTopic();
}
```

From feishu-bot-domain/.../gateway/FeishuGateway.java:
```java
public interface FeishuGateway {
    SendResult sendMessage(Message message, String content, String topicId);
    SendResult sendInteractiveMessage(Message message, String cardJson, String topicId);
}
```

From feishu-bot-domain/.../gateway/CardGateway.java:
```java
public interface CardGateway {
    String createCard(String title, String content);
    boolean updateCard(String cardId, String content, int seq);
    SendResult sendCardMessage(Message msg, String cardId, String topicId);
}
```

From feishu-bot-infrastructure/.../gateway/MessageListenerGatewayImpl.java (handleCardAction L144-190):
```java
// 当前实现：从 actionValue 提取 "action" → 构造伪 Message → "/" + action
// cardEventId = "card-" + header.eventId
// 设置 sender.openId, chatId, content = "/" + action
// 未设置 topicId、cardToken、EventSource.CARD
```
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: CardContent 领域模型 + FeishuCardRenderer + HelpApp 迁移 (D-09, D-10)</name>
  <files>
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardContent.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardButton.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/CardRenderer.java,
    feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card/FeishuCardRenderer.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/card/CardContentTest.java,
    feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/card/FeishuCardRendererTest.java,
    feishu-bot-start/src/test/java/com/qdw/feishu/HelpAppCardButtonJsonTest.java
  </files>
  <behavior>
    - Test: CardContent.builder() 构建含 header + markdown + buttons 的卡片内容
    - Test: CardContent.toBuilder() 可修改已有卡片内容
    - Test: FeishuCardRenderer.render(CardContent) 生成合法 schema 2.0 JSON
    - Test: FeishuCardRenderer 对含按钮的 CardContent 生成 button 元素
    - Test: FeishuCardRenderer 对含多个按钮组的 CardContent 生成正确 layout
    - Test: HelpApp 使用 CardContent 构建卡片（不再手写 Map）
    - Test: HelpApp 生成的卡片 JSON 与旧实现结构一致（按钮数量、action 值）
    - Test: [金标准] 新 CardRenderer 产出的 HelpApp JSON 与迁移前 buildCardHelpJson() 输出结构等价（header/按钮 action/按钮数量/schema 版本）
  </behavior>
  <action>
    **Per D-09: domain 抽象 + infra 实现——CardContent 领域模型 + CardRenderer 网关接口 + FeishuCardRenderer**

    1. **新建 `feishu-bot-domain/.../card/CardContent.java`** — IM 无关的卡片内容模型：
       ```java
       /**
        * IM 无关的卡片内容模型。
        * 描述卡片的结构化内容，由 CardRenderer 转换为具体 IM 平台的卡片格式。
        */
       @Builder(toBuilder = true)
       public class CardContent {
           private final String headerTitle;           // 卡片标题
           private final String headerTemplate;        // 颜色模板（如 "blue", "green"）
           private final boolean wideScreenMode;        // 宽屏模式

           @Singular("addElement")
           private final List<CardElement> elements;   // 卡片元素列表

           /** 添加 markdown 段落（便捷方法） */
           public CardContent addMarkdown(String content) {
               return toBuilder().element(CardElement.markdown(content)).build();
           }

           /** 添加按钮组（便捷方法） */
           public CardContent addButtonGroup(List<CardButton> buttons) {
               return toBuilder().element(CardElement.buttonGroup(buttons)).build();
           }
       }
       ```

    2. **新建 `feishu-bot-domain/.../card/CardElement.java`** — 卡片元素（密封接口风格）：
       ```java
       /**
        * 卡片元素。支持 markdown 段落和按钮组两种类型。
        */
       public class CardElement {
           private final String type;              // "markdown" or "button_group"
           private final String markdownContent;   // for markdown type
           private final List<CardButton> buttons; // for button_group type

           public static CardElement markdown(String content);
           public static CardElement buttonGroup(List<CardButton> buttons);
           public static CardElement buttonGroup(CardButton... buttons);

           public boolean isMarkdown();
           public boolean isButtonGroup();
       }
       ```

    3. **新建 `feishu-bot-domain/.../card/CardButton.java`** — 按钮值对象：
       ```java
       /**
        * 卡片按钮值对象。
        * action 是按钮的业务语义标识（如 "wizard_select_project:feishu-backend"），
        * label 是按钮显示文本，style 控制视觉样式。
        */
       @Builder
       public class CardButton {
           private final String label;     // 按钮文本
           private final String action;    // 点击后的动作标识
           private final String style;     // "primary" | "default"

           public static CardButton primary(String label, String action);
           public static CardButton defaults(String label, String action);
       }
       ```

    4. **新建 `feishu-bot-domain/.../gateway/CardRenderer.java`** — 渲染网关接口：
       ```java
       /**
        * 将 IM 无关的 CardContent 转换为具体 IM 平台的卡片 JSON。
        * 遵循 COLA：接口定义在 domain，实现在 infrastructure。
        */
       public interface CardRenderer {
           /**
            * 渲染卡片为 JSON 字符串。
            * @param cardContent IM 无关的卡片内容
            * @param context 卡片上下文（chatId/topicId/sessionId），嵌入按钮 value
            * @return 具体 IM 平台的卡片 JSON
            */
           String render(CardContent cardContent, CardActionContext context);
       }
       ```

    5. **新建 `feishu-bot-domain/.../card/CardActionContext.java`** — 卡片按钮上下文：
       ```java
       /**
        * 卡片按钮点击时需要携带的上下文信息。
        * 发卡时嵌入按钮 value，点击时解析还原。
        */
       @Builder
       public class CardActionContext {
           private final String chatId;
           private final String topicId;
           private final String sessionId;

           /** 从 MessageContext 提取上下文 */
           public static CardActionContext from(MessageContext messageContext);

           /** 从卡片按钮 value map 还原上下文 */
           public static CardActionContext fromValueMap(Map<String, Object> valueMap);

           /** 转为嵌入按钮 value 的 map */
           public Map<String, Object> toValueMap(String action);

           /** 从 value map 中提取纯 action */
           public static String extractAction(Map<String, Object> valueMap);
       }
       ```

    6. **新建 `feishu-bot-infrastructure/.../card/FeishuCardRenderer.java`** — 飞书实现：
       ```java
       /**
        * 将 CardContent 渲染为飞书 schema 2.0 卡片 JSON。
        */
       @Component
       public class FeishuCardRenderer implements CardRenderer {
           private final ObjectMapper objectMapper;

           @Override
           public String render(CardContent cardContent, CardActionContext context) {
               Map<String, Object> card = new LinkedHashMap<>();
               card.put("schema", "2.0");
               card.put("config", Map.of("wide_screen_mode", cardContent.isWideScreenMode()));

               // Header
               Map<String, Object> header = new LinkedHashMap<>();
               header.put("title", Map.of("content", cardContent.getHeaderTitle(), "tag", "plain_text"));
               if (cardContent.getHeaderTemplate() != null) {
                   header.put("template", cardContent.getHeaderTemplate());
               }
               card.put("header", header);

               // Body elements
               List<Map<String, Object>> elements = new ArrayList<>();
               for (CardElement element : cardContent.getElements()) {
                   if (element.isMarkdown()) {
                       elements.add(Map.of("tag", "markdown", "content", element.getMarkdownContent()));
                   } else if (element.isButtonGroup()) {
                       // 每个按钮作为独立的 button 元素（飞书要求）
                       for (CardButton btn : element.getButtons()) {
                           Map<String, Object> buttonMap = new LinkedHashMap<>();
                           buttonMap.put("tag", "button");
                           buttonMap.put("text", Map.of("content", btn.getLabel(), "tag", "plain_text"));
                           buttonMap.put("type", btn.getStyle());
                           // 嵌入上下文到 value
                           Map<String, Object> value = context != null
                               ? context.toValueMap(btn.getAction())
                               : Map.of("action", btn.getAction());
                           buttonMap.put("value", value);
                           elements.add(buttonMap);
                       }
                   }
               }
               card.put("body", Map.of("elements", elements));

               return objectMapper.writeValueAsString(card);
           }
       }
       ```

    **Per D-10: HelpApp 迁移到新 CardBuilder**

    7. **重构 `HelpApp.buildCardHelpJson()`** — 使用 CardContent + CardRenderer：
       ```java
       // 注入 CardRenderer
       private final CardRenderer cardRenderer;

       private String buildCardHelpJson() {
           List<CardButton> buttons = appRegistry.getAllApps().stream()
               .map(app -> CardButton.builder()
                   .label(getAppIcon(app.getAppId()) + " " + app.getAppName())
                   .action(getDefaultAction(app.getAppId()))
                   .style(getButtonType(app.getAppId()))
                   .build())
               .collect(Collectors.toList());

           CardContent card = CardContent.builder()
               .headerTitle("🤖 应用菜单")
               .headerTemplate("blue")
               .wideScreenMode(true)
               .element(CardElement.markdown("点击按钮选择应用，或直接输入命令"))
               .element(CardElement.buttonGroup(buttons))
               .build();

           return cardRenderer.render(card, null); // HelpApp 按钮不需要上下文
       }
       ```
       - 移除 `ObjectMapper` 字段（不再直接序列化）
       - `trySendCardHelp()` 保持不变（仍调用 `feishuGateway.sendInteractiveMessage()`）

    8. **更新 `HelpAppCardButtonJsonTest`** — 适配新的 JSON 生成方式。测试逻辑不变（验证按钮数量和 action 值），但 JSON 来源从 `buildCardHelpJson()` 切换到 `cardRenderer.render()`。

    9. **测试** — 新建测试：
       - `CardContentTest` — 验证 builder、toBuilder、addMarkdown、addButtonGroup
       - `FeishuCardRendererTest` — 验证 render 产出合法 JSON（含 header/elements/button），验证 context 嵌入 value，验证 null context 时 value 只有 action
       - 更新 `HelpAppCardButtonJsonTest` — 确认按钮数量、action 值与旧实现一致
       - **[金标准测试]** 在 `HelpAppCardButtonJsonTest` 中添加迁移等价验证：迁移前先记录旧 `buildCardHelpJson()` 的 JSON 结构快照（按钮 action 列表、header title、schema 版本），迁移后验证新系统产出的 JSON 匹配相同结构。这是防止迁移回归的关键守护测试。
  </action>
  <verify>
    <automated>cd /root/workspace/feishu-backend && /opt/apache-maven-3.9.5/bin/mvn test -pl feishu-bot-domain,feishu-bot-infrastructure,feishu-bot-start -Dtest="CardContentTest,FeishuCardRendererTest,HelpAppCardButtonJsonTest" -q 2>&1 | tail -10</automated>
  </verify>
  <done>
    - CardContent + CardElement + CardButton 领域模型可构建和修改
    - CardRenderer 网关接口定义在 domain，FeishuCardRenderer 实现在 infrastructure
    - CardActionContext 可嵌入/提取按钮上下文（chatId/topicId/sessionId）
    - FeishuCardRenderer 渲染出正确的 schema 2.0 JSON
    - HelpApp 不再手写 Map，使用 CardContent + CardRenderer
    - HelpApp 按钮行为不变（action 值、按钮数量、样式一致）
    - CardContentTest、FeishuCardRendererTest、HelpAppCardButtonJsonTest 通过
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: 卡片按钮富上下文传递 + handleCardAction 增强 (D-01, D-02, CARD-01)</name>
  <files>
    feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/card/CardActionContextTest.java
  </files>
  <behavior>
    - Test: CardActionContext.toValueMap("test_action") 包含 action、chatId、topicId、sessionId
    - Test: CardActionContext.fromValueMap(map) 正确还原 chatId/topicId/sessionId
    - Test: CardActionContext.extractAction(map) 正确提取 action 字符串
    - Test: CardActionContext.fromValueMap(只有 action) 返回 chatId=null, topicId=null
    - Test: handleCardAction 设置伪 Message 的 topicId（从 value map 提取）
    - Test: handleCardAction 设置伪 Message 的 cardToken（从 event header 提取）
    - Test: Message.java 包含 cardToken 字段且 withContent() 正确复制
    - Test: 卡片来源的伪 Message 经管道后 MessageContext.isResolved() == true
  </behavior>
  <action>
    **Per D-01: 扩展卡片按钮 value 携带完整上下文**

    1. **CardActionContext 在 Task 1 已创建** — 此步骤完善其序列化/反序列化测试：
       ```java
       // toValueMap: {"action": "xxx", "chatId": "...", "topicId": "...", "sessionId": "..."}
       public Map<String, Object> toValueMap(String action) {
           Map<String, Object> map = new LinkedHashMap<>();
           map.put("action", action);
           if (chatId != null) map.put("chatId", chatId);
           if (topicId != null) map.put("topicId", topicId);
           if (sessionId != null) map.put("sessionId", sessionId);
           return map;
       }

       // fromValueMap: 从按钮 value 还原上下文
       public static CardActionContext fromValueMap(Map<String, Object> valueMap) {
           return CardActionContext.builder()
               .chatId(getString(valueMap, "chatId"))
               .topicId(getString(valueMap, "topicId"))
               .sessionId(getString(valueMap, "sessionId"))
               .build();
       }

       public static String extractAction(Map<String, Object> valueMap) {
           Object action = valueMap.get("action");
           return action != null ? action.toString() : null;
       }
       ```

    **Per D-02: handleCardAction 增强——解析富上下文并设置到伪 Message**

    2. **修改 `MessageListenerGatewayImpl.handleCardAction()`** — 解析完整 value map：
       ```java
       private void handleCardAction(P2CardActionTrigger event) {
           try {
               Map<String, Object> actionValue = event.getEvent().getAction().getValue();
               if (actionValue == null || !actionValue.containsKey("action")) {
                   log.warn("卡片按钮 value 中缺少 action 字段: {}", actionValue);
                   return;
               }

               String action = CardActionContext.extractAction(actionValue);
               CardActionContext context = CardActionContext.fromValueMap(actionValue);
               String cardEventId = resolveCardEventId(event);

               // 构造伪 Message
               Message message = new Message();
               message.setContent("/" + action);
               message.setEventId(cardEventId);

               // 发送者
               String openId = "";
               if (event.getEvent().getOperator() != null) {
                   openId = event.getEvent().getOperator().getOpenId();
               }
               message.setSender(new Sender(openId, "card-user"));

               // chatId：优先从 value map 取，fallback 从 event context 取
               String chatId = context.getChatId();
               if (chatId == null && event.getEvent().getContext() != null) {
                   chatId = event.getEvent().getContext().getOpenChatId();
               }
               message.setChatId(chatId);

               // topicId：优先从 value map 取，fallback 从 event context 取
               String topicId = context.getTopicId();
               if (topicId == null && event.getEvent().getContext() != null) {
                   // 飞书 card event context 中可能有线程 ID
                   topicId = event.getEvent().getContext().getOpenThreadId();
               }
               message.setTopicId(topicId);

               // cardToken：从 event header 提取（用于更新原卡片）
               String token = extractCardToken(event);
               message.setCardToken(token);

               if (messageHandler != null) {
                   messageHandler.accept(message);
               }
           } catch (Exception e) {
               log.error("处理卡片按钮点击事件失败", e);
           }
       }

       private String extractCardToken(P2CardActionTrigger event) {
           // 飞书 card action trigger 的 token 用于 updateCard API
           // event.getEvent().getOperator() 中不包含 token
           // token 来自 event.getHeader().getEventId() 或 event 本身的 openMessageId
           // 需要查阅飞书 SDK 确定具体字段
           try {
               return event.getHeader() != null ? event.getHeader().getEventId() : null;
           } catch (Exception e) {
               return null;
           }
       }
       ```
       **注意**：飞书 SDK 的 `P2CardActionTrigger` 中获取 card token（用于更新原卡片）的具体字段需要查阅 SDK 文档。如果 SDK 不直接提供 cardToken，先使用 eventId 作为占位。

    3. **在 Message 中添加 cardToken 字段**（代码库验证：Message.java 目前没有 cardToken 字段，必须新增）：
       ```java
       // Message.java 使用 @Data，添加字段即可自动生成 getter/setter
       private String cardToken;  // 卡片事件 token，用于更新原卡片
       ```
       同时更新 `withContent()` 方法（如存在）以复制 cardToken 字段。
       **注意**：`UnifiedCommand` 已有 `cardToken` 字段。需确保 `CommandAdapter.adapt()` 将 Message.cardToken 传递到 UnifiedCommand.cardToken。

    **Per REVIEW-FIX: 卡片动作的 MessageContext 桥接**

    4. **确保卡片来源的伪 Message 经过 MessageContext 解析** — 当前架构中，`handleCardAction()` 构造的伪 Message 通过 `messageHandler.accept(message)` 进入管道，最终经过 `MessageContextResolver` 解析。关键前提：
       - 伪 Message 必须设置 `chatId` 和 `topicId`（步骤2已完成）
       - `MessageContextResolver` 根据 chatId+topicId 查找 `ImContextBinding`，返回已解析的 `MessageContext`
       - 如果 topicId 来自按钮 value（不是从 event context 提取），`MessageContextResolver` 仍然能正确解析，因为 binding 数据库中存储的是 topicId→binding 映射
       
       **验证点**：卡片按钮点击后，`OpenCodeApp.execute(Message, MessageContext)` 收到的 `messageContext.isResolved()` 必须为 true（非 `MessageContext.unresolved()`）。
       
       **如果管道路径不同**（例如卡片事件绕过了 `MessageContextResolver`），则需要在 `handleCardAction()` 中手动构建 MessageContext：
       ```java
       // 降级方案：如果卡片管道不经过 MessageContextResolver
       // 在 handleCardAction 中从 value map 还原 MessageContext
       // 但优先方案是确保伪 Message 走正常管道
       ```

    5. **测试** — 新建 `CardActionContextTest`：
       - `should_embedContextInValueMap` — toValueMap 包含所有字段
       - `should_extractContextFromValueMap` — fromValueMap 正确还原
       - `should_extractActionFromValueMap` — extractAction 返回 action 字符串
       - `should_handleNullFields` — chatId/topicId/sessionId 为 null 时不嵌入
       - `should_handleOldFormatValueMap` — 只有 action 没有 chatId 时，fromValueMap 返回 null 字段
  </action>
  <verify>
    <automated>cd /root/workspace/feishu-backend && /opt/apache-maven-3.9.5/bin/mvn test -pl feishu-bot-domain,feishu-bot-infrastructure -Dtest="CardActionContextTest" -q 2>&1 | tail -5</automated>
  </verify>
  <done>
    - CardActionContext 完整序列化/反序列化测试通过
    - Message.java 新增 cardToken 字段（代码库验证确认此前不存在）
    - handleCardAction 从 value map 提取 chatId/topicId 设置到伪 Message
    - handleCardAction 提取 cardToken 设置到伪 Message
    - 卡片来源的伪 Message 经过 MessageContextResolver 获得已解析 MessageContext（非 unresolved）
    - CommandAdapter 将 Message.cardToken 传递到 UnifiedCommand.cardToken
    - 旧格式卡片（只有 action）仍正常工作（向后兼容）
    - 7 个 CardActionContextTest + MessageContext 桥接验证测试通过
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: 3 步入门向导 (D-03, D-04, D-05, CARD-02)</name>
  <files>
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/WizardManager.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java,
    feishu-bot-app/src/main/java/com/qdw/feishu/app/opencode/OpenCodeMessageAppService.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/WizardManagerTest.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandlerTest.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeAppTest.java
  </files>
  <behavior>
    - Test: WizardManager 初始状态为 INACTIVE
    - Test: WizardManager.start() → 状态变 SELECT_PROJECT，返回项目列表 CardContent
    - Test: WizardManager.handleAction("wizard_select_project:feishu-backend") → 状态变 SELECT_SESSION，返回会话列表 CardContent
    - Test: WizardManager.handleAction("wizard_select_session:ses_123") → 状态变 CONFIRM，返回确认 CardContent
    - Test: WizardManager.handleAction("wizard_confirm") → 执行绑定，状态变 COMPLETED
    - Test: 向导 INACTIVE 时 isWizardActive() 返回 false
    - Test: 向导进行中 isWizardActive() 返回 true
    - Test: 向导状态 10 分钟后自动过期（TTL 驱逐）
    - Test: 并发状态转换使用 compute() 无竞态
    - Test: OpenCodeCommandHandler 对 UNINITIALIZED 状态的非白名单命令触发向导
    - Test: 向导进行中，非向导命令被拦截返回 "请先完成向导" 提示
    - Test: OpenCodeApp.getCommandWhitelist() UNINITIALIZED 包含向导相关 action
    - Test: handleWizardAction 返回 CardContent 时，handler 直接发送卡片并返回 noReply()
  </behavior>
  <action>
    **Per D-03: 3 步向导（选项目→选/建会话→确认绑定）**

    1. **新建 `feishu-bot-domain/.../opencode/WizardManager.java`** — 向导状态机：
       ```java
       @Component
       public class WizardManager {

           // 向导步骤
           public enum WizardStep {
               INACTIVE,           // 未在向导中
               SELECT_PROJECT,     // 步骤1：选择项目
               SELECT_SESSION,     // 步骤2：选择会话
               CONFIRM,            // 步骤3：确认绑定
               COMPLETED           // 已完成
           }

           // [REVIEW-FIX] 使用带 TTL 驱逐的缓存，避免用户放弃向导后内存泄漏
           // 方案 A（推荐）：Caffeine 缓存，10 分钟过期
           // 方案 B（如不引入 Caffeine）：ConcurrentHashMap + ScheduledExecutorService 定时清理
           // 选择取决于项目是否已有 Caffeine 依赖。检查 pom.xml。
           //
           // Caffeine 方案：
           private final Cache<String, WizardState> activeWizards = Caffeine.newBuilder()
               .expireAfterWrite(10, TimeUnit.MINUTES)
               .maximumSize(100)  // 最多 100 个并发向导
               .build();
           //
           // 降级方案（无 Caffeine）：
           // private final ConcurrentHashMap<String, WizardState> activeWizards = new ConcurrentHashMap<>();
           // + @Scheduled(fixedRate = 60_000) void cleanupExpiredWizards() { ... }

           private final OpenCodeGateway openCodeGateway;
           private final OpenCodeSessionManager sessionManager;
           private final CardRenderer cardRenderer;

           /**
            * 启动向导。生成步骤1卡片（项目列表+按钮）。
            */
           public WizardResult start(String chatId, String topicId) {
               WizardState state = new WizardState(chatId, topicId);
               activeWizards.put(topicId, state);

               String projectsText = openCodeGateway.listProjects();
               List<CardButton> buttons = parseProjectsToButtons(projectsText);

               CardContent card = CardContent.builder()
                   .headerTitle("🎯 欢迎使用 OpenCode！第 1 步：选择项目")
                   .headerTemplate("blue")
                   .wideScreenMode(true)
                   .element(CardElement.markdown("请选择一个项目开始："))
                   .element(CardElement.buttonGroup(buttons))
                   .build();

               return WizardResult.of(card, WizardStep.SELECT_PROJECT);
           }

           /**
            * 处理向导中的卡片按钮点击。
            * [REVIEW-FIX] 使用 compute() 进行原子性状态转换，避免并发点击竞态。
            */
           public WizardResult handleAction(String action, String chatId, String topicId) {
               // [REVIEW-FIX] 原子性读取+转换，防止快速连续点击导致的竞态
               WizardState state = activeWizards.getIfPresent(topicId);
               if (state == null) return null; // 不是向导中的点击（或已超时过期）

               if (action.startsWith("wizard_select_project:")) {
                   return handleSelectProject(state, action.substring("wizard_select_project:".length()));
               } else if (action.startsWith("wizard_select_session:")) {
                   return handleSelectSession(state, action.substring("wizard_select_session:".length()));
               } else if (action.equals("wizard_confirm")) {
                   return handleConfirm(state);
               } else if (action.equals("wizard_cancel")) {
                   activeWizards.invalidate(topicId);
                   return WizardResult.ofText("已取消向导。使用 `/oc p` 查看项目。");
               }
               return null; // 非向导 action
           }

           /** 向导是否活跃（用于拦截非向导命令） */
           public boolean isWizardActive(String topicId) {
               WizardState state = activeWizards.getIfPresent(topicId);
               return state != null && state.getStep() != WizardStep.COMPLETED;
           }

           /** 完成或取消后清理 */
           public void clearWizard(String topicId) {
               activeWizards.invalidate(topicId);
           }

           // 内部状态类
           @Data
           private static class WizardState {
               private final String chatId;
               private final String topicId;
               private WizardStep step = WizardStep.SELECT_PROJECT;
               private String selectedProject;
               private String selectedSessionId;
           }
       }

       // 向导结果
       public class WizardResult {
           private final CardContent cardContent;    // 卡片（用于发新卡）
           private final String textContent;         // 文本（降级）
           private final WizardStep step;
           private final boolean completed;
           private final String openCodeSessionId;   // 绑定后的 session ID
       }
       ```

    2. **向导按钮 action 命名约定**：
       - `wizard_select_project:{projectName}` — 步骤1 选择项目
       - `wizard_select_session:{sessionId}` — 步骤2 选择会话
       - `wizard_new_session:{projectName}` — 步骤2 新建会话
       - `wizard_confirm` — 步骤3 确认绑定
       - `wizard_cancel` — 取消向导

    3. **步骤2 生成会话列表卡片**：
       - 调用 `openCodeGateway.listRecentSessions(selectedProject, 10)` 获取会话
       - 解析结果构建 CardContent（每个会话一个"绑定"按钮 + 底部"新建会话"按钮）
       - 按钮携带完整上下文（chatId/topicId）

    4. **步骤3 确认绑定**：
       - 调用 `sessionManager.saveSession()` 执行绑定
       - 返回确认卡片（更新原卡片内容为 "✅ 绑定成功"）
       - 清理向导状态

    **Per D-05: 向导在首次进入未绑定话题时自动触发**

    5. **[REVIEW-FIX] 注入 CardRenderer + FeishuGateway 到 OpenCodeCommandHandler** — 代码库验证确认 handler 当前不持有这两个依赖。Task 3-4 的卡片发送需要它们：
       ```java
       // OpenCodeCommandHandler 构造器新增参数（均为 domain 层接口，COLA 合规）
       private final CardRenderer cardRenderer;
       private final FeishuGateway feishuGateway;
       ```
       **COLA 层级验证**：`CardRenderer` 是 `domain/gateway/` 接口 ✓。`FeishuGateway` 是 `domain/gateway/` 接口 ✓。`OpenCodeCommandHandler` 在 domain 层依赖 domain 接口 ✓。

    6. **修改 `OpenCodeApp` 或 `OpenCodeMessageAppService`** — 在 UNINITIALIZED 状态时触发向导：

       方案：在 `OpenCodeApp.execute()` 中，当 `TopicState == UNINITIALIZED` 且消息不是显式命令时，启动向导替代 `buildInitializationGuide()`。

       更好的位置是在 `OpenCodeMessageAppService.handleMessageInternal()` 中，检测 UNINITIALIZED 状态后调用 WizardManager：
       ```java
       // 在 handleMessageInternal 中，当检测到 UNINITIALIZED 且无显式命令时：
       if (state == TopicState.UNINITIALIZED && !isExplicitCommand(message)) {
           WizardResult result = wizardManager.start(message.getChatId(), message.getTopicId());
           if (result != null && result.getCardContent() != null) {
               String cardJson = cardRenderer.render(result.getCardContent(), context);
               feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
               return AppExecutionResult.noReply();
           }
       }
       ```

       但更简洁的做法是在 `OpenCodeCommandHandler.handle()` 中，对 UNINITIALIZED 状态的白名单外命令直接触发向导。

       **最终选择**：在 `OpenCodeCommandHandler.handle()` 中添加向导触发逻辑。当 UNINITIALIZED 状态且命令不是白名单命令时，检查是否有活跃向导；如果没有，自动启动向导。

    **Per D-04: 向导优先——向导进行中非向导命令被拦截**

    6. **在 `OpenCodeCommandHandler.handle()` 中添加向导拦截**：
       ```java
       // 在白名单验证之后，switch-case 之前：
       if (messageContext.isThreadContext() && wizardManager.isWizardActive(message.getTopicId())) {
           // 向导进行中，只允许向导相关 action 和白名单内的非侵入命令
           if (!isWizardAction(subCommand)) {
               return AppExecutionResult.text("⚠️ 向导进行中，请先完成向导。\n\n" +
                   "点击上方卡片按钮继续，或输入 `/oc wizard_cancel` 取消向导。");
           }
       }
       ```

       `isWizardAction()` 检查 subCommand 是否以 `wizard_` 开头或等于 `wizard_cancel`。

    7. **添加向导相关的命令路由** — 在 switch-case 中：
       ```java
       // 新增向导 action 路由（卡片按钮点击时走到这里）
       case "wizard_select_project", "wizard_select_session", "wizard_new_session",
            "wizard_confirm", "wizard_cancel" -> handleWizardAction(subCommand, message, messageContext);
       ```
       `handleWizardAction` 委托给 `WizardManager.handleAction()`，根据返回的 `WizardResult` 决定：
       - 有 CardContent → 渲染 JSON → `feishuGateway.sendInteractiveMessage()` → **`AppExecutionResult.noReply()`**
       - 有 textContent → `AppExecutionResult.text(textContent)`
       - completed + sessionId → `AppExecutionResult.withSession(...)`
       
       **[REVIEW-FIX] 卡片发送模式说明**：`AppExecutionResult` 不支持卡片内容（代码库验证确认只有 text/noReply/withSession）。因此卡片发送采用"handler 内直接调用 `feishuGateway.sendInteractiveMessage()` 发送卡片 + 返回 `AppExecutionResult.noReply()` 抑制文本回复"模式。这与 `HelpApp.trySendCardHelp()` 已有模式一致。

    8. **更新白名单** — `OpenCodeApp.getCommandWhitelist()`：
       - UNINITIALIZED: 添加 `wizard_select_project`, `wizard_select_session`, `wizard_new_session`, `wizard_confirm`, `wizard_cancel`
       - 注意：这些 action 只在卡片按钮点击时出现（content = `/wizard_select_project:xxx`），不会与用户手动输入冲突。

    9. **测试** — 新建 `WizardManagerTest`：
       - `should_startWizard_andReturnProjectList` — 初始状态启动，返回步骤1卡片
       - `should_transitionToSessionList_when_projectSelected` — 步骤1→2
       - `should_transitionToConfirm_when_sessionSelected` — 步骤2→3
       - `should_completeBinding_when_confirmed` — 步骤3→完成，调用 saveSession
       - `should_cancelWizard_when_cancelAction` — 清理状态
       - `should_returnInactive_when_noWizard` — 无活跃向导
       - `should_interceptNonWizardCommand_when_wizardActive` — 拦截逻辑

       更新 `OpenCodeCommandHandlerTest`：
       - 确认向导 action 路由到 WizardManager
       - 确认非向导命令被拦截

       更新 `OpenCodeAppTest`：
       - 确认白名单包含向导 action
  </action>
  <verify>
    <automated>cd /root/workspace/feishu-backend && /opt/apache-maven-3.9.5/bin/mvn test -pl feishu-bot-domain -Dtest="WizardManagerTest,OpenCodeCommandHandlerTest,OpenCodeAppTest" -q 2>&1 | tail -10</automated>
  </verify>
  <done>
    - WizardManager 管理 3 步向导状态机（SELECT_PROJECT → SELECT_SESSION → CONFIRM → COMPLETED）
    - WizardManager 使用带 TTL 的缓存（10 分钟过期），避免内存泄漏
    - WizardManager 状态转换无并发竞态
    - 首次进入未绑定话题自动弹出步骤1卡片（替代 buildInitializationGuide 文本）
    - 向导进行中非向导命令被拦截并提示
    - 向导 action 路由在 OpenCodeCommandHandler 中正确工作
    - OpenCodeCommandHandler 新增 CardRenderer + FeishuGateway 依赖注入
    - 卡片发送采用 handler 内直接发送 + noReply() 模式
    - 确认绑定步骤调用 sessionManager.saveSession()
    - WizardManagerTest 9 个测试通过（含 TTL 过期和并发测试）
    - 白名单包含向导 action
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 4: 增强会话列表 (D-06, D-07, D-08, CARD-03)</name>
  <files>
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java,
    feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java,
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeGateway.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManagerTest.java,
    feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandlerTest.java
  </files>
  <behavior>
    - Test: sessions 命令在话题中返回卡片形式的会话列表（默认卡片优先）
    - Test: 会话列表卡片中每个会话显示名称、最后提示词摘要（截断）、相对时间戳
    - Test: 会话列表卡片底部有"+ 新建会话"按钮
    - Test: 点击会话列表中的"绑定"按钮执行 sc 命令绑定
    - Test: 卡片渲染失败时降级为纯文本（fallback）
    - Test: 非 IM 渠道或卡片禁用时使用纯文本格式
  </behavior>
  <action>
    **Per D-06: 会话列表支持卡片和纯文本两种形式，默认卡片优先**

    1. **扩展 `OpenCodeGateway` 接口** — 添加结构化会话数据方法：
       ```java
       /**
        * 获取项目的最近会话列表（结构化数据）。
        * 用于卡片渲染，返回解析后的会话对象列表而非纯文本。
        */
       List<SessionInfo> listRecentSessionsStructured(String project, int limit);
       ```
       新建 `SessionInfo` 值对象（在 domain 层）：
       ```java
       @Data
       @Builder
       public class SessionInfo {
           private final String sessionId;       // ses_xxx
           private final String title;           // 会话名称
           private final String lastPrompt;      // 最后提示词（可能为空）
           private final String relativeTime;    // "5分钟前"
           private final String projectName;     // 所属项目
       }
       ```

    2. **在 `OpenCodeGatewayImpl` 中实现** — 解析 API 返回的 JSON：
       ```java
       public List<SessionInfo> listRecentSessionsStructured(String project, int limit) {
           // 复用现有 HTTP 调用逻辑
           // 解析 JSON 响应为 SessionInfo 列表
           // 截断 lastPrompt 到 50 字符
           // 生成 relativeTime（"X分钟前"、"X小时前"、"X天前"）
       }
       ```

    **Per D-07: 每个会话显示名称、摘要、时间戳**

    3. **在 `OpenCodeCommandHandler` 中添加卡片会话列表路由**：

       修改 `sessions` 命令处理：当在话题中时，尝试卡片格式：
       ```java
       case "sessions", "s" -> handleSessionsCommand(parts, message, messageContext);
       ```

       ```java
       private AppExecutionResult handleSessionsCommand(String[] parts, Message message, MessageContext ctx) {
           String project = parts.length >= 2 ? parts[1] : null;
           if (project == null || project.isBlank()) {
               return AppExecutionResult.text(messageFormatter.buildNewCommandUsage());
           }

           // 如果在话题中且有 CardRenderer，尝试卡片格式
           if (ctx.isThreadContext()) {
               AppExecutionResult cardResult = trySendSessionListCard(project, message, ctx);
               if (cardResult != null) return cardResult;
           }

           // 降级为纯文本
           return AppExecutionResult.text(sessionManager.handleSessionsCommand(parts));
       }

       private AppExecutionResult trySendSessionListCard(String project, Message message, MessageContext ctx) {
           try {
               List<SessionInfo> sessions = openCodeGateway.listRecentSessionsStructured(project, 10);
               if (sessions.isEmpty()) {
                   return AppExecutionResult.text("该项目暂无会话。使用 `/oc new " + project + " <问题>` 创建新会话。");
               }

               CardActionContext actionCtx = CardActionContext.from(ctx);
               List<CardElement> elements = new ArrayList<>();
               elements.add(CardElement.markdown("**" + project + "** 的最近会话："));

               List<CardButton> buttons = new ArrayList<>();
               for (SessionInfo session : sessions) {
                   // 每个会话一个绑定按钮
                   String label = session.getTitle()
                       + (session.getLastPrompt() != null ? " — " + session.getLastPrompt() : "")
                       + " (" + session.getRelativeTime() + ")";
                   buttons.add(CardButton.defaults(
                       label.length() > 40 ? label.substring(0, 37) + "..." : label,
                       "sc " + session.getSessionId()
                   ));
               }
               elements.add(CardElement.buttonGroup(buttons));

               // 底部"新建会话"按钮
               elements.add(CardElement.buttonGroup(
                   CardButton.primary("+ 新建会话", "new " + project + " ")
               ));

               CardContent card = CardContent.builder()
                   .headerTitle("📋 会话列表 — " + project)
                   .headerTemplate("turquoise")
                   .wideScreenMode(true)
                   .elements(elements)
                   .build();

               String cardJson = cardRenderer.render(card, actionCtx);
               feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
               return AppExecutionResult.noReply();
           } catch (Exception e) {
               log.warn("卡片会话列表渲染失败，降级为文本: {}", e.getMessage());
               return null; // 返回 null 让调用者降级为文本
           }
       }
       ```

       **注意**：`new` 按钮的 action `"new " + project + " "` 需要带上项目名但缺少 prompt 内容。这里按钮点击后应该触发 `handleNewCommand`，但 new 命令需要 prompt。改为：按钮 action 为 `"wizard_new_session:" + project`，由向导或 command handler 处理。

    **Per D-08: 会话列表卡片底部有"+ 新建会话"按钮**

    4. **"+ 新建会话"按钮** — action 命名为 `wizard_new_session:{project}`：
       - 如果向导活跃 → 走向导流程创建新会话
       - 如果向导不活跃 → 在 `OpenCodeCommandHandler` 中处理，创建新会话并绑定

    5. **[REVIEW-FIX] 重构 listRecentSessions** — 新增 `listRecentSessionsStructured()` 后，将现有 `listRecentSessions()` 重构为委托调用：
       ```java
       // OpenCodeGatewayImpl 中
       public String listRecentSessions(String project, int limit) {
           // 委托给 structured 方法，然后格式化为文本
           List<SessionInfo> sessions = listRecentSessionsStructured(project, limit);
           return formatSessionsAsText(sessions);
       }
       ```
       避免维护两套 HTTP 调用 + 解析逻辑。

    6. **注入依赖** — `OpenCodeCommandHandler` 在 Task 3 步骤 5 已注入 `CardRenderer` 和 `FeishuGateway`。此处直接使用。
       
       **[REVIEW-FIX] 卡片发送模式**：与 Task 3 相同——handler 内直接调用 `feishuGateway.sendInteractiveMessage()` 发送卡片 + 返回 `AppExecutionResult.noReply()`。无需扩展 `AppExecutionResult`。

    6. **测试** — 更新现有测试：
       - `OpenCodeSessionManagerTest` — 验证 handleSessionsCommand 纯文本路径不变
       - `OpenCodeCommandHandlerTest` — 新增：
         - `should_returnCardSessionList_when_sessionsCommandInTopic` — 话题中返回卡片
         - `should_fallbackToText_when_cardRenderingFails` — 卡片失败降级文本
         - `should_includeNewSessionButton_inSessionListCard` — 底部有新建按钮
         - `should_fallbackToText_when_notInTopic` — 非话题环境用文本
  </action>
  <verify>
    <automated>cd /root/workspace/feishu-backend && /opt/apache-maven-3.9.5/bin/mvn test -pl feishu-bot-domain -Dtest="OpenCodeSessionManagerTest,OpenCodeCommandHandlerTest" -q 2>&1 | tail -10</automated>
  </verify>
  <done>
    - sessions 命令在话题中返回卡片会话列表（默认卡片优先）
    - 卡片发送使用 handler 内直接发送 + noReply() 模式（与 Task 3 一致）
    - 会话列表卡片中每个会话显示名称、最后提示词摘要和相对时间戳
    - 会话列表底部有"+ 新建会话"按钮
    - 卡片渲染失败时降级为纯文本
    - 纯文本路径保持不变
    - listRecentSessions() 重构为委托 listRecentSessionsStructured()（单一数据源）
    - SessionInfo 结构化数据从 OpenCodeGateway 获取
    - 4 个新增/更新的 OpenCodeCommandHandlerTest 通过
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 5: 全量测试 + 文档同步 + 最终验证</name>
  <files>
    feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeMessageFormatter.java,
    APP_USAGE_GUIDE.md,
    AGENTS.md
  </files>
  <behavior>
    - Test: 全量 309+ 测试通过（含新增测试）
    - Test: HelpApp 卡片按钮行为与迁移前一致
    - Test: OpenCode 所有命令路径不受卡片功能影响
  </behavior>
  <action>
    **全量回归验证 + 文档同步**

    1. **运行全量测试** — 确认所有 309+ 测试通过，无回归：
       ```bash
       mvn test -q
       ```

    2. **清理废弃代码** — 如果 `OpenCodeMessageFormatter.buildInitializationGuide()` 被向导完全替代，标记为 `@Deprecated` 或移除（保留降级用）：
       - `buildInitializationGuide()` — 保留作为卡片失败时的文本降级
       - `buildInitializationRequiredMessage()` — 同上
       - 不删除文本方法——作为 fallback 机制保留

    2b. **[REVIEW-FIX] 清理 Phase 1 废弃方法** — Phase 1-2 累积了多个 `@Deprecated` 方法，利用 Task 5 做一次清理：
       - 查找所有 `@Deprecated` 注解的方法
       - 验证每个废弃方法是否还有外部调用者（使用 IDE 查找引用或 grep）
       - 删除无外部调用的废弃方法（内部委托关系已在新方法中实现）
       - 保留仍有外部调用的废弃方法，添加 `@Deprecated(forRemoval = true)` 注解
       - 运行全量测试确认无回归

    3. **文档同步** — 按 AGENTS.md §8 规范更新：
       - **APP_USAGE_GUIDE.md** — 更新 OpenCode 应用章节：
         - 新增向导流程说明（首次使用自动弹出）
         - 更新 sessions 命令说明（支持卡片形式）
         - 新增卡片按钮使用说明
       - **AGENTS.md** — 如果有新文件目录需要记录，更新"关键文件位置"

    4. **集成验证** — 确认端到端流程：
       - HelpApp `/help` → 卡片按钮正常（迁移后行为一致）
       - OpenCode `/oc sessions feishu-backend` → 话题中返回卡片列表
       - OpenCode 首次进入未绑定话题 → 自动弹出向导卡片
       - 向导按钮点击 → 正确传递上下文 → 绑定成功
       - 卡片渲染失败 → 降级为纯文本
  </action>
  <verify>
    <automated>cd /root/workspace/feishu-backend && /opt/apache-maven-3.9.5/bin/mvn test -q 2>&1 | tail -10</automated>
  </verify>
  <done>
    - 全量 309+ 测试通过（含 Phase 3 新增测试）
    - HelpApp 迁移后行为不变
    - Phase 1-2 无外部调用的 @Deprecated 方法已清理
    - 文档同步完成（APP_USAGE_GUIDE.md, AGENTS.md）
    - 文本降级路径保留并工作正常
  </done>
</task>

</tasks>

<verification>
# 全量测试
cd /root/workspace/feishu-backend && /opt/apache-maven-3.9.5/bin/mvn test -q 2>&1 | tail -10

# CardContent 模型存在
ls feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardContent.java
ls feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardButton.java
ls feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/CardActionContext.java

# CardRenderer 接口和实现
ls feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/CardRenderer.java
ls feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/card/FeishuCardRenderer.java

# WizardManager 存在
ls feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/WizardManager.java

# [REVIEW-FIX] Message.java 包含 cardToken 字段
grep -n "cardToken" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java

# handleCardAction 使用 CardActionContext + 设置 topicId + cardToken
grep -n "CardActionContext\|setTopicId\|setCardToken" feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java

# [REVIEW-FIX] OpenCodeCommandHandler 注入 CardRenderer + FeishuGateway
grep -n "CardRenderer\|FeishuGateway" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java

# 向导路由在 CommandHandler
grep -n "wizard_" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java

# HelpApp 使用 CardRenderer
grep -n "cardRenderer" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java

# SessionInfo 结构化数据
grep -n "listRecentSessionsStructured" feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java

# [REVIEW-FIX] 卡片发送使用 noReply() 模式
grep -n "noReply\|sendInteractiveMessage" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java
</verification>

<success_criteria>
1. 用户点击卡片上的"选择项目"按钮，系统正确识别对话上下文并执行绑定操作 — CARD-01 ✓
2. 首次使用时显示分步卡片引导，用户通过 2-3 次点击完成绑定 — CARD-02 ✓
3. 会话列表卡片中每个会话显示最后提示词摘要和"X分钟前"时间戳 — CARD-03 ✓
4. 全部 309+ 测试通过，新增测试覆盖卡片构建器、向导、增强会话列表
5. HelpApp 迁移到新 CardRenderer，行为不变
6. 文档同步完成
</success_criteria>

<output>
After completion, create `.planning/phases/03-cards-guided-flows/03-SUMMARY.md`
</output>

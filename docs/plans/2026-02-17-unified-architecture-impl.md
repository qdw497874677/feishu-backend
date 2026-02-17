# 统一事件架构实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将飞书消息和卡片事件统一为 Command → App → Result 的处理流程

**Architecture:** 双层适配器架构 - 输入适配器将各类事件转换为 UnifiedCommand，输出适配器将 BizResult 转换为实际响应

**Tech Stack:** Java 17, Spring Boot, COLA 架构, Lombok

---

## Phase 1: 基础模型 (domain 层)

### Task 1.1: 创建 EventSource 枚举

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/EventSource.java`

**Step 1: 创建枚举类**

```java
package com.qdw.feishu.domain.command;

public enum EventSource {
    MESSAGE,
    CARD
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/EventSource.java
git commit -m "feat(domain): 添加 EventSource 事件来源枚举"
```

---

### Task 1.2: 创建 UnifiedCommand

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/UnifiedCommand.java`

**Step 1: 创建统一命令类**

```java
package com.qdw.feishu.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedCommand {
    private String appId;
    private String subCommand;
    private String[] args;
    private String openId;
    private String topicId;
    private String messageId;
    private String cardToken;
    private EventSource source;
    
    public boolean isFromCard() {
        return source == EventSource.CARD;
    }
    
    public boolean isFromMessage() {
        return source == EventSource.MESSAGE;
    }
    
    public boolean hasTopic() {
        return topicId != null && !topicId.isEmpty();
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/UnifiedCommand.java
git commit -m "feat(domain): 添加 UnifiedCommand 统一命令模型"
```

---

### Task 1.3: 创建 BizResult

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/result/BizResult.java`

**Step 1: 创建业务结果类**

```java
package com.qdw.feishu.domain.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizResult {
    private boolean success;
    private Object data;
    private String message;
    
    public static BizResult success(Object data) {
        return BizResult.builder()
            .success(true)
            .data(data)
            .build();
    }
    
    public static BizResult success(String message, Object data) {
        return BizResult.builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    public static BizResult failure(String message) {
        return BizResult.builder()
            .success(false)
            .message(message)
            .build();
    }
    
    public static BizResult of(String message) {
        return BizResult.builder()
            .success(true)
            .message(message)
            .build();
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/result/BizResult.java
git commit -m "feat(domain): 添加 BizResult 业务结果模型"
```

---

### Task 1.4: 创建 CommandAdapter 接口

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/CommandAdapter.java`

**Step 1: 创建输入适配器接口**

```java
package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.UnifiedCommand;

public interface CommandAdapter {
    UnifiedCommand adapt(Object event);
    boolean supports(Object event);
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/CommandAdapter.java
git commit -m "feat(domain): 添加 CommandAdapter 输入适配器接口"
```

---

### Task 1.5: 创建 ResponseAdapter 接口

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/ResponseAdapter.java`

**Step 1: 创建输出适配器接口**

```java
package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.result.BizResult;

public interface ResponseAdapter {
    void respond(UnifiedCommand command, BizResult result);
    boolean supports(EventSource source, UnifiedCommand command);
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/ResponseAdapter.java
git commit -m "feat(domain): 添加 ResponseAdapter 输出适配器接口"
```

---

### Task 1.6: 创建 CommandAdapterFactory

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/CommandAdapterFactory.java`

**Step 1: 创建输入适配器工厂**

```java
package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CommandAdapterFactory {
    private final List<CommandAdapter> adapters;
    
    public CommandAdapterFactory(List<CommandAdapter> adapters) {
        this.adapters = adapters;
        log.info("CommandAdapterFactory initialized with {} adapters", adapters.size());
    }
    
    public CommandAdapter getAdapter(Object event) {
        return adapters.stream()
            .filter(a -> a.supports(event))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported event type: " + event.getClass().getSimpleName()));
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/CommandAdapterFactory.java
git commit -m "feat(domain): 添加 CommandAdapterFactory 适配器工厂"
```

---

### Task 1.7: 创建 ResponseAdapterFactory

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/ResponseAdapterFactory.java`

**Step 1: 创建输出适配器工厂**

```java
package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ResponseAdapterFactory {
    private final List<ResponseAdapter> adapters;
    
    public ResponseAdapterFactory(List<ResponseAdapter> adapters) {
        this.adapters = adapters;
        log.info("ResponseAdapterFactory initialized with {} adapters", adapters.size());
    }
    
    public ResponseAdapter getAdapter(EventSource source, UnifiedCommand command) {
        return adapters.stream()
            .filter(a -> a.supports(source, command))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported response type: " + source));
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/adapter/ResponseAdapterFactory.java
git commit -m "feat(domain): 添加 ResponseAdapterFactory 适配器工厂"
```

---

## Phase 2: 适配器实现 (infrastructure 层)

### Task 2.1: 创建 MessageCommandAdapter

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/MessageCommandAdapter.java`

**Step 1: 创建消息适配器**

```java
package com.qdw.feishu.infrastructure.adapter;

import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageCommandAdapter implements CommandAdapter {
    
    @Override
    public UnifiedCommand adapt(Object event) {
        P2MessageReceiveV1 msgEvent = (P2MessageReceiveV1) event;
        
        String content = msgEvent.getBody().getMessage().getContent();
        String topicId = extractTopicId(msgEvent);
        
        String[] parts = parseCommand(content);
        String appId = parts.length > 0 ? parts[0] : "help";
        String subCommand = parts.length > 1 ? parts[1] : null;
        String[] args = parts.length > 2 ? extractArgs(parts) : new String[0];
        
        UnifiedCommand command = UnifiedCommand.builder()
            .appId(appId)
            .subCommand(subCommand)
            .args(args)
            .openId(msgEvent.getBody().getSender().getSenderId().getOpenId())
            .topicId(topicId)
            .messageId(msgEvent.getBody().getMessage().getMessageId())
            .source(EventSource.MESSAGE)
            .build();
        
        log.debug("Adapted message to command: appId={}, subCommand={}", appId, subCommand);
        return command;
    }
    
    @Override
    public boolean supports(Object event) {
        return event instanceof P2MessageReceiveV1;
    }
    
    private String extractTopicId(P2MessageReceiveV1 event) {
        try {
            return event.getBody().getMessage().getThreadId();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String[] parseCommand(String content) {
        if (content == null || content.isEmpty()) {
            return new String[0];
        }
        content = content.trim();
        if (!content.startsWith("/")) {
            return new String[]{"help"};
        }
        content = content.substring(1);
        return content.split("\\s+");
    }
    
    private String[] extractArgs(String[] parts) {
        String[] args = new String[parts.length - 2];
        System.arraycopy(parts, 2, args, 0, args.length);
        return args;
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-infrastructure && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/MessageCommandAdapter.java
git commit -m "feat(infra): 添加 MessageCommandAdapter 消息适配器"
```

---

### Task 2.2: 创建 CardCommandAdapter

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardCommandAdapter.java`

**Step 1: 创建卡片适配器**

```java
package com.qdw.feishu.infrastructure.adapter;

import com.lark.oapi.event.model.P2CardActionTrigger;
import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CardCommandAdapter implements CommandAdapter {
    
    @Override
    public UnifiedCommand adapt(Object event) {
        P2CardActionTrigger cardEvent = (P2CardActionTrigger) event;
        
        String appId = extractAppId(cardEvent);
        String action = extractAction(cardEvent);
        String[] parts = parseAction(action);
        
        UnifiedCommand command = UnifiedCommand.builder()
            .appId(appId)
            .subCommand(parts.length > 0 ? parts[0] : null)
            .args(parts.length > 1 ? extractArgs(parts) : new String[0])
            .openId(cardEvent.getBody().getOperator().getOpenId())
            .messageId(cardEvent.getBody().getMessageId())
            .cardToken(cardEvent.getBody().getToken())
            .source(EventSource.CARD)
            .build();
        
        log.debug("Adapted card action to command: appId={}, subCommand={}", appId, parts[0]);
        return command;
    }
    
    @Override
    public boolean supports(Object event) {
        return event instanceof P2CardActionTrigger;
    }
    
    private String extractAppId(P2CardActionTrigger event) {
        try {
            Object appValue = event.getBody().getAction().getExtraMap().get("app_id");
            return appValue != null ? appValue.toString() : "opencode";
        } catch (Exception e) {
            return "opencode";
        }
    }
    
    private String extractAction(P2CardActionTrigger event) {
        try {
            return event.getBody().getAction().getValue();
        } catch (Exception e) {
            return "";
        }
    }
    
    private String[] parseAction(String action) {
        if (action == null || action.isEmpty()) {
            return new String[0];
        }
        return action.split(":");
    }
    
    private String[] extractArgs(String[] parts) {
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return args;
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-infrastructure && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardCommandAdapter.java
git commit -m "feat(infra): 添加 CardCommandAdapter 卡片适配器"
```

---

### Task 2.3: 创建 MessageResponseAdapter

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/MessageResponseAdapter.java`

**Step 1: 创建消息响应适配器**

```java
package com.qdw.feishu.infrastructure.adapter;

import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageResponseAdapter implements ResponseAdapter {
    private final FeishuGateway feishuGateway;
    private final ReplyStrategyFactory replyStrategyFactory;
    
    public MessageResponseAdapter(FeishuGateway feishuGateway,
                                  ReplyStrategyFactory replyStrategyFactory) {
        this.feishuGateway = feishuGateway;
        this.replyStrategyFactory = replyStrategyFactory;
    }
    
    @Override
    public void respond(UnifiedCommand command, BizResult result) {
        String content = formatContent(result);
        
        Message message = createMessage(command);
        ReplyStrategy strategy = replyStrategyFactory.getDefaultStrategy();
        
        SendResult sendResult = strategy.reply(message, content, command.getTopicId());
        
        if (sendResult.isSuccess()) {
            log.info("Message response sent: messageId={}", command.getMessageId());
        } else {
            log.error("Failed to send message response: {}", sendResult.getErrorMessage());
        }
    }
    
    @Override
    public boolean supports(EventSource source, UnifiedCommand command) {
        return source == EventSource.MESSAGE;
    }
    
    private String formatContent(BizResult result) {
        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            return result.getMessage();
        }
        if (result.getData() != null) {
            return result.getData().toString();
        }
        return result.isSuccess() ? "操作成功" : "操作失败";
    }
    
    private Message createMessage(UnifiedCommand command) {
        Message message = new Message();
        message.setMessageId(command.getMessageId());
        message.setOpenId(command.getOpenId());
        message.setTopicId(command.getTopicId());
        return message;
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-infrastructure && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/MessageResponseAdapter.java
git commit -m "feat(infra): 添加 MessageResponseAdapter 消息响应适配器"
```

---

### Task 2.4: 创建 CardResponseAdapter

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardResponseAdapter.java`

**Step 1: 创建卡片响应适配器**

```java
package com.qdw.feishu.infrastructure.adapter;

import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CardResponseAdapter implements ResponseAdapter {
    private final FeishuGateway feishuGateway;
    
    public CardResponseAdapter(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }
    
    @Override
    public void respond(UnifiedCommand command, BizResult result) {
        String content = formatContent(result);
        
        if (command.getCardToken() != null) {
            updateCard(command.getCardToken(), content);
        } else {
            sendMessage(command, content);
        }
    }
    
    @Override
    public boolean supports(EventSource source, UnifiedCommand command) {
        return source == EventSource.CARD;
    }
    
    private String formatContent(BizResult result) {
        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            return result.getMessage();
        }
        if (result.getData() != null) {
            return result.getData().toString();
        }
        return result.isSuccess() ? "操作成功" : "操作失败";
    }
    
    private void updateCard(String token, String content) {
        log.info("Updating card with token: {}", token);
        feishuGateway.updateCard(token, content);
    }
    
    private void sendMessage(UnifiedCommand command, String content) {
        log.info("Sending message for card event: messageId={}", command.getMessageId());
        feishuGateway.sendCardReply(command.getMessageId(), content);
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-infrastructure && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/CardResponseAdapter.java
git commit -m "feat(infra): 添加 CardResponseAdapter 卡片响应适配器"
```

---

## Phase 3: 核心组件重构

### Task 3.1: 创建 CommandRouter

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/router/CommandRouter.java`

**Step 1: 创建命令路由器**

```java
package com.qdw.feishu.domain.router;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommandRouter {
    private final AppRegistry appRegistry;
    
    public CommandRouter(AppRegistry appRegistry) {
        this.appRegistry = appRegistry;
        log.info("CommandRouter initialized");
    }
    
    public BizResult route(UnifiedCommand command) {
        log.info("Routing command: appId={}, subCommand={}", 
            command.getAppId(), command.getSubCommand());
        
        return appRegistry.getApp(command.getAppId())
            .map(app -> executeApp(app, command))
            .orElse(BizResult.failure("未知应用: " + command.getAppId() + 
                "\n\n可用应用:\n" + appRegistry.getAppHelp()));
    }
    
    private BizResult executeApp(FishuAppI app, UnifiedCommand command) {
        try {
            log.debug("Executing app: {}", app.getAppId());
            return app.execute(command);
        } catch (Exception e) {
            log.error("App execution failed: {}", app.getAppId(), e);
            return BizResult.failure("应用执行失败: " + e.getMessage());
        }
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/router/CommandRouter.java
git commit -m "feat(domain): 添加 CommandRouter 命令路由器"
```

---

### Task 3.2: 创建 EventProcessor

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/processor/EventProcessor.java`

**Step 1: 创建统一事件处理器**

```java
package com.qdw.feishu.domain.processor;

import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.adapter.CommandAdapterFactory;
import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.adapter.ResponseAdapterFactory;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.result.BizResult;
import com.qdw.feishu.domain.router.CommandRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventProcessor {
    private final CommandAdapterFactory commandAdapterFactory;
    private final CommandRouter commandRouter;
    private final ResponseAdapterFactory responseAdapterFactory;
    
    public EventProcessor(CommandAdapterFactory commandAdapterFactory,
                         CommandRouter commandRouter,
                         ResponseAdapterFactory responseAdapterFactory) {
        this.commandAdapterFactory = commandAdapterFactory;
        this.commandRouter = commandRouter;
        this.responseAdapterFactory = responseAdapterFactory;
        log.info("EventProcessor initialized");
    }
    
    public void process(Object event) {
        log.info("=== EventProcessor.process 开始 ===");
        
        try {
            CommandAdapter adapter = commandAdapterFactory.getAdapter(event);
            UnifiedCommand command = adapter.adapt(event);
            log.debug("Command adapted: appId={}, source={}", 
                command.getAppId(), command.getSource());
            
            BizResult result = commandRouter.route(command);
            log.debug("Command routed, result: success={}", result.isSuccess());
            
            ResponseAdapter responseAdapter = responseAdapterFactory.getAdapter(
                command.getSource(), command);
            responseAdapter.respond(command, result);
            
            log.info("=== EventProcessor.process 完成 ===");
            
        } catch (Exception e) {
            log.error("Event processing failed", e);
            throw e;
        }
    }
}
```

**Step 2: 编译验证**

Run: `cd feishu-bot-domain && mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/processor/EventProcessor.java
git commit -m "feat(domain): 添加 EventProcessor 统一事件处理器"
```

---

## Phase 4: 应用迁移

### Task 4.1: 修改 FishuAppI 接口

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java`

**Step 1: 添加新的 execute 方法签名**

在 `FishuAppI.java` 中添加：

```java
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.result.BizResult;

public interface FishuAppI {
    // ... 现有方法 ...
    
    /**
     * 新版执行方法：接收统一命令，返回业务结果
     */
    default BizResult execute(UnifiedCommand command) {
        return BizResult.failure("应用未实现新接口");
    }
    
    /**
     * 旧版执行方法：保持向后兼容
     * @deprecated 使用 execute(UnifiedCommand) 替代
     */
    @Deprecated
    String execute(Message message);
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java
git commit -m "feat(domain): FishuAppI 添加 execute(UnifiedCommand) 方法"
```

---

### Task 4.2: 迁移 HelpApp

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java`

**Step 1: 实现新接口**

```java
@Override
public BizResult execute(UnifiedCommand command) {
    String help = appRegistry.getAppHelp();
    return BizResult.of(help);
}

@Deprecated
@Override
public String execute(Message message) {
    return execute(UnifiedCommand.builder()
        .appId(getAppId())
        .source(EventSource.MESSAGE)
        .build()).getMessage();
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java
git commit -m "refactor(app): HelpApp 迁移到新接口"
```

---

### Task 4.3: 迁移 TimeApp

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java`

**Step 1: 实现新接口**

```java
@Override
public BizResult execute(UnifiedCommand command) {
    String time = LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    return BizResult.of("当前时间: " + time);
}

@Deprecated
@Override
public String execute(Message message) {
    return execute(UnifiedCommand.builder()
        .appId(getAppId())
        .source(EventSource.MESSAGE)
        .build()).getMessage();
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java
git commit -m "refactor(app): TimeApp 迁移到新接口"
```

---

### Task 4.4: 迁移 BashApp

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/BashApp.java`

**Step 1: 实现新接口**

```java
@Override
public BizResult execute(UnifiedCommand command) {
    String[] args = command.getArgs();
    if (args == null || args.length == 0) {
        return BizResult.failure("请提供要执行的命令\n用法: /bash <命令>");
    }
    
    String bashCmd = String.join(" ", args);
    
    if (!isSafeCommand(bashCmd)) {
        return BizResult.failure("不安全的命令: " + bashCmd);
    }
    
    try {
        String result = executeBash(bashCmd);
        return BizResult.of(result);
    } catch (Exception e) {
        return BizResult.failure("执行失败: " + e.getMessage());
    }
}

@Deprecated
@Override
public String execute(Message message) {
    return null; // 由新接口处理
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/BashApp.java
git commit -m "refactor(app): BashApp 迁移到新接口"
```

---

### Task 4.5: 迁移 HistoryApp

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HistoryApp.java`

**Step 1: 实现新接口**

```java
@Override
public BizResult execute(UnifiedCommand command) {
    String topicId = command.getTopicId();
    if (topicId == null || topicId.isEmpty()) {
        return BizResult.failure("此命令仅在话题中可用");
    }
    
    String history = getHistory(topicId);
    return BizResult.of(history);
}

@Deprecated
@Override
public String execute(Message message) {
    return null;
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HistoryApp.java
git commit -m "refactor(app): HistoryApp 迁移到新接口"
```

---

## Phase 5: 集成测试

### Task 5.1: 修改 MessageListenerGatewayImpl

**Files:**
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java`

**Step 1: 注入 EventProcessor**

```java
private final EventProcessor eventProcessor;

// 在 handle 方法中调用
@Override
public void handle(P2MessageReceiveV1 event) {
    log.info("Received message event");
    eventProcessor.process(event);
}
```

**Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java
git commit -m "refactor(infra): MessageListenerGatewayImpl 使用 EventProcessor"
```

---

### Task 5.2: 全量编译测试

**Step 1: 编译整个项目**

Run: `mvn clean compile -q`
Expected: BUILD SUCCESS

**Step 2: 运行测试**

Run: `mvn test -q`
Expected: Tests run: X, Failures: 0

**Step 3: Commit**

```bash
git add -A
git commit -m "feat: 统一事件架构实施完成

- Phase 1: 基础模型 (UnifiedCommand, BizResult, 适配器接口)
- Phase 2: 适配器实现 (Message/Card 输入输出适配器)
- Phase 3: 核心组件 (CommandRouter, EventProcessor)
- Phase 4: 应用迁移 (HelpApp, TimeApp, BashApp, HistoryApp)
- Phase 5: 集成测试"
```

---

## 验证清单

- [ ] `mvn clean compile` 成功
- [ ] `mvn test` 全部通过
- [ ] 应用启动成功
- [ ] 消息事件正常处理
- [ ] 卡片事件正常处理
- [ ] 日志输出正确

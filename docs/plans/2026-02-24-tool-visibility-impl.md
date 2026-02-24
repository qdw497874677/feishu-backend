# Tool Visibility Implementation Plan (SSE 版本)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 通过 SSE 事件订阅实现 OpenCode 工具执行可见性和流式响应

**Architecture:** 
- 使用 OpenCode 的 SSE 端点 `/event` 订阅实时事件
- 过滤目标会话的 `message.part.updated` 和 `session.status` 事件
- 实现流式文本回复和工具执行状态显示

**Tech Stack:** Java 17, Spring Boot, Spring WebClient (响应式 SSE), Jackson, Lombok

---

## 调研结论

### OpenCode SSE 事件系统

| 属性 | 值 |
|------|-----|
| **端点** | `GET /event` |
| **协议** | SSE (Server-Sent Events) |
| **认证** | Basic Auth（与 HTTP API 相同） |
| **心跳** | 每 30 秒发送 `server.heartbeat` |

### 关键事件类型

| 事件类型 | 用途 | 字段 |
|---------|------|------|
| `message.part.updated` | 流式文本增量 | `sessionID`, `part`, `delta` |
| `session.status` | 会话状态变化 | `sessionID`, `status.type` (idle/busy) |
| `server.connected` | 连接建立 | - |
| `server.heartbeat` | 心跳 | - |

### 事件 JSON 格式

```json
{
  "type": "message.part.updated",
  "properties": {
    "part": {
      "id": "prt_xxx",
      "sessionID": "ses_xxx",
      "messageID": "msg_xxx",
      "type": "text",
      "text": "完整文本..."
    },
    "delta": "新增的文本片段"
  }
}
```

---

## 前置检查

```bash
git status
mvn clean compile -q
```

---

## Task 1: 添加 Spring WebFlux 依赖

**Files:**
- Modify: `feishu-bot-infrastructure/pom.xml`

**Step 1: 添加 WebFlux 依赖**

在 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**Step 2: 验证依赖**

Run: `mvn dependency:resolve -pl feishu-bot-infrastructure -q`
Expected: 依赖解析成功

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/pom.xml
git commit -m "feat(deps): add spring-boot-starter-webflux for SSE support"
```

---

## Task 2: 创建 OpenCodeEvent 数据模型

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeEvent.java`

**Step 1: 创建事件类**

```java
package com.qdw.feishu.domain.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

/**
 * OpenCode SSE 事件
 *
 * 封装从 OpenCode SSE 端点接收到的事件
 */
@Data
@Builder
public class OpenCodeEvent {

    private String type;

    private JsonNode properties;

    public String getSessionId() {
        if (properties == null) return null;
        
        if (properties.has("sessionID")) {
            return properties.get("sessionID").asText();
        }
        if (properties.has("part") && properties.get("part").has("sessionID")) {
            return properties.get("part").get("sessionID").asText();
        }
        return null;
    }

    public String getDelta() {
        if (properties != null && properties.has("delta")) {
            return properties.get("delta").asText();
        }
        return null;
    }

    public String getText() {
        if (properties != null && properties.has("part") && properties.get("part").has("text")) {
            return properties.get("part").get("text").asText();
        }
        return null;
    }

    public String getStatus() {
        if (properties != null && properties.has("status") && properties.get("status").has("type")) {
            return properties.get("status").get("type").asText();
        }
        return null;
    }

    public boolean isSessionIdle() {
        return "idle".equals(getStatus());
    }

    public boolean isSessionBusy() {
        return "busy".equals(getStatus());
    }

    public boolean isTextUpdate() {
        return "message.part.updated".equals(type);
    }

    public boolean isStatusUpdate() {
        return "session.status".equals(type);
    }

    public static OpenCodeEvent of(String type, JsonNode properties) {
        return OpenCodeEvent.builder()
                .type(type)
                .properties(properties)
                .build();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeEvent.java
git commit -m "feat(opencode): add OpenCodeEvent data model for SSE events"
```

---

## Task 3: 创建 OpenCodeEventGateway 接口

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeEventGateway.java`

**Step 1: 创建接口**

```java
package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.opencode.OpenCodeEvent;

import java.util.function.Consumer;

/**
 * OpenCode 事件订阅网关接口
 *
 * 用于订阅 OpenCode SSE 事件流
 */
public interface OpenCodeEventGateway {

    /**
     * 启动 SSE 连接并订阅所有事件
     *
     * @param handler 事件处理器
     */
    void subscribe(Consumer<OpenCodeEvent> handler);

    /**
     * 检查 SSE 连接是否活跃
     *
     * @return true 如果连接活跃
     */
    boolean isConnected();

    /**
     * 断开 SSE 连接
     */
    void disconnect();
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeEventGateway.java
git commit -m "feat(opencode): add OpenCodeEventGateway interface"
```

---

## Task 4: 创建 SSE 配置类

**Files:**
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/OpenCodeProperties.java`

**Step 1: 添加 SSE 配置字段**

在 `OpenCodeProperties` 类中添加：

```java
/**
 * 是否启用 SSE 事件订阅
 */
private boolean sseEnabled = true;

/**
 * SSE 重连间隔（毫秒）
 */
private long sseReconnectInterval = 5000;

/**
 * SSE 心跳超时（毫秒）
 */
private long sseHeartbeatTimeout = 60000;

/**
 * 流式回复缓冲区大小（字符数）
 */
private int streamingBufferSize = 100;

/**
 * 流式回复刷新间隔（毫秒）
 */
private long streamingFlushInterval = 2000;
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-infrastructure -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/OpenCodeProperties.java
git commit -m "feat(opencode): add SSE configuration properties"
```

---

## Task 5: 实现 OpenCodeEventGatewayImpl

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeEventGatewayImpl.java`

**Step 1: 创建实现类**

```java
package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.gateway.OpenCodeEventGateway;
import com.qdw.feishu.domain.opencode.OpenCodeEvent;
import com.qdw.feishu.infrastructure.config.OpenCodeProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * OpenCode SSE 事件订阅实现
 *
 * 使用 Spring WebClient 订阅 OpenCode 的 /event SSE 端点
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "opencode", name = "sse-enabled", havingValue = "true", matchIfMissing = true)
public class OpenCodeEventGatewayImpl implements OpenCodeEventGateway {

    private final OpenCodeProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private volatile Disposable subscription;
    private volatile boolean connected = false;
    private volatile Consumer<OpenCodeEvent> eventHandler;

    public OpenCodeEventGatewayImpl(OpenCodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getServerUrl())
                .defaultHeader("Authorization", getAuthHeader())
                .build();
        log.info("OpenCode SSE Gateway 初始化完成: {}", properties.getServerUrl());
    }

    private String getAuthHeader() {
        if (properties.getPassword() == null || properties.getPassword().isEmpty()) {
            return "";
        }
        String auth = properties.getUsername() + ":" + properties.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void subscribe(Consumer<OpenCodeEvent> handler) {
        if (subscription != null && !subscription.isDisposed()) {
            log.warn("SSE 连接已存在，跳过重复订阅");
            return;
        }

        this.eventHandler = handler;
        startSubscription();
    }

    private void startSubscription() {
        log.info("开始订阅 OpenCode SSE 事件: {}/event", properties.getServerUrl());

        Flux<String> eventStream = webClient.get()
                .uri("/event")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> {
                    connected = true;
                    log.info("SSE 连接已建立");
                })
                .doOnError(e -> {
                    connected = false;
                    log.error("SSE 连接错误: {}", e.getMessage());
                })
                .doOnCancel(() -> {
                    connected = false;
                    log.info("SSE 连接已取消");
                });

        subscription = eventStream
                .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(properties.getSseReconnectInterval()))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.warn("SSE 重连中，第 {} 次尝试", signal.totalRetries() + 1)))
                .subscribe(
                        this::handleRawEvent,
                        error -> log.error("SSE 订阅异常", error)
                );
    }

    private void handleRawEvent(String rawData) {
        try {
            JsonNode json = objectMapper.readTree(rawData);
            String type = json.has("type") ? json.get("type").asText() : null;
            JsonNode properties = json.has("properties") ? json.get("properties") : null;

            if (type == null) {
                return;
            }

            if ("server.connected".equals(type)) {
                connected = true;
                log.info("收到 server.connected 事件，SSE 连接就绪");
                return;
            }

            if ("server.heartbeat".equals(type)) {
                log.debug("收到心跳事件");
                return;
            }

            OpenCodeEvent event = OpenCodeEvent.of(type, properties);
            
            if (eventHandler != null) {
                eventHandler.accept(event);
            }

        } catch (Exception e) {
            log.warn("解析 SSE 事件失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return connected && subscription != null && !subscription.isDisposed();
    }

    @Override
    public synchronized void disconnect() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            subscription = null;
            connected = false;
            log.info("SSE 连接已断开");
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-infrastructure -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeEventGatewayImpl.java
git commit -m "feat(opencode): implement SSE event subscription with WebClient"
```

---

## Task 6: 创建流式响应处理器

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeStreamingHandler.java`

**Step 1: 创建处理器**

```java
package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 流式响应处理器
 *
 * 处理 SSE 事件，累积文本增量，定期发送到飞书
 */
@Slf4j
@Component
public class OpenCodeStreamingHandler {

    private final FeishuGateway feishuGateway;
    private final OpenCodePropertiesReader config;
    private final ScheduledExecutorService scheduler;

    private final Map<String, StringBuilder> textBuffers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToTopicMap = new ConcurrentHashMap<>();
    private final Map<String, Message> sessionToMessageMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFlushTime = new ConcurrentHashMap<>();

    public OpenCodeStreamingHandler(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
        this.config = new OpenCodePropertiesReader();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void registerSession(String sessionId, Message message) {
        String topicId = message.getTopicId();
        sessionToTopicMap.put(sessionId, topicId);
        sessionToMessageMap.put(sessionId, message);
        textBuffers.put(sessionId, new StringBuilder());
        lastFlushTime.put(sessionId, System.currentTimeMillis());
        log.info("注册会话流式处理: sessionId={}, topicId={}", sessionId, topicId);
    }

    public void unregisterSession(String sessionId) {
        textBuffers.remove(sessionId);
        sessionToTopicMap.remove(sessionId);
        sessionToMessageMap.remove(sessionId);
        lastFlushTime.remove(sessionId);
        
        ScheduledFuture<?> task = flushTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        log.info("注销会话流式处理: sessionId={}", sessionId);
    }

    public void handleEvent(OpenCodeEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null || !sessionToTopicMap.containsKey(sessionId)) {
            return;
        }

        if (event.isTextUpdate()) {
            handleTextDelta(sessionId, event);
        } else if (event.isStatusUpdate() && event.isSessionIdle()) {
            handleSessionComplete(sessionId);
        }
    }

    private void handleTextDelta(String sessionId, OpenCodeEvent event) {
        String delta = event.getDelta();
        if (delta == null || delta.isEmpty()) {
            return;
        }

        StringBuilder buffer = textBuffers.get(sessionId);
        if (buffer == null) {
            return;
        }

        buffer.append(delta);
        log.debug("累积文本增量: sessionId={}, delta长度={}, buffer长度={}", 
                sessionId, delta.length(), buffer.length());

        scheduleFlush(sessionId);
    }

    private void scheduleFlush(String sessionId) {
        if (flushTasks.containsKey(sessionId)) {
            return;
        }

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            flushBuffer(sessionId);
            flushTasks.remove(sessionId);
        }, config.getStreamingFlushInterval(), TimeUnit.MILLISECONDS);

        flushTasks.put(sessionId, task);
    }

    private synchronized void flushBuffer(String sessionId) {
        StringBuilder buffer = textBuffers.get(sessionId);
        String topicId = sessionToTopicMap.get(sessionId);
        Message message = sessionToMessageMap.get(sessionId);

        if (buffer == null || topicId == null || message == null) {
            return;
        }

        String text = buffer.toString();
        if (text.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastFlush = lastFlushTime.get(sessionId);
        if (lastFlush != null && (now - lastFlush) < 1000) {
            return;
        }

        buffer.setLength(0);
        lastFlushTime.put(sessionId, now);

        String formattedText = formatStreamingText(text);
        feishuGateway.sendMessage(message, formattedText, topicId);
        log.info("发送流式更新: sessionId={}, length={}", sessionId, text.length());
    }

    private void handleSessionComplete(String sessionId) {
        flushBuffer(sessionId);
        
        StringBuilder buffer = textBuffers.get(sessionId);
        if (buffer != null && buffer.length() > 0) {
            String finalText = buffer.toString();
            Message message = sessionToMessageMap.get(sessionId);
            String topicId = sessionToTopicMap.get(sessionId);
            
            if (message != null && topicId != null) {
                feishuGateway.sendMessage(message, 
                    "✅ 完成\n\n" + finalText, topicId);
            }
        }
        
        unregisterSession(sessionId);
        log.info("会话完成: sessionId={}", sessionId);
    }

    private String formatStreamingText(String text) {
        return "⏳ 处理中...\n\n" + text;
    }

    private static class OpenCodePropertiesReader {
        long getStreamingFlushInterval() {
            return 2000;
        }
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeStreamingHandler.java
git commit -m "feat(opencode): add streaming response handler"
```

---

## Task 7: 集成到 OpenCodeTaskExecutor

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java`

**Step 1: 注入依赖并修改**

添加字段和修改方法：

```java
// 添加字段
private final OpenCodeStreamingHandler streamingHandler;

// 修改构造函数
public OpenCodeTaskExecutor(OpenCodeGateway openCodeGateway,
                            FeishuGateway feishuGateway,
                            OpenCodeResponseFormatter responseFormatter,
                            OpenCodeSessionManager sessionManager,
                            OpenCodeStreamingHandler streamingHandler) {
    this.openCodeGateway = openCodeGateway;
    this.feishuGateway = feishuGateway;
    this.responseFormatter = responseFormatter;
    this.sessionManager = sessionManager;
    this.streamingHandler = streamingHandler;
}

// 在 executeWithSpecificSession 方法中注册流式处理
public String executeWithSpecificSession(Message message, String prompt, String sessionId) {
    log.info("使用指定会话执行: sessionId={}", sessionId);
    String topicId = message.getTopicId();
    sessionManager.saveSession(topicId, sessionId);

    // 注册流式处理
    streamingHandler.registerSession(sessionId, message);

    if (prompt == null || prompt.isEmpty()) {
        return buildInitializationSuccessResponse(topicId, sessionId);
    }
    return executeTask(message, prompt, sessionId);
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-domain -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java
git commit -m "feat(opencode): integrate streaming handler in TaskExecutor"
```

---

## Task 8: 创建 SSE 初始化配置

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/OpenCodeSseConfig.java`

**Step 1: 创建配置类**

```java
package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.gateway.OpenCodeEventGateway;
import com.qdw.feishu.domain.opencode.OpenCodeEvent;
import com.qdw.feishu.domain.opencode.OpenCodeStreamingHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * OpenCode SSE 配置
 *
 * 在应用启动时自动订阅 SSE 事件
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "opencode", name = "sse-enabled", havingValue = "true", matchIfMissing = true)
public class OpenCodeSseConfig {

    private final OpenCodeEventGateway eventGateway;
    private final OpenCodeStreamingHandler streamingHandler;

    public OpenCodeSseConfig(
            @Autowired(required = false) OpenCodeEventGateway eventGateway,
            OpenCodeStreamingHandler streamingHandler) {
        this.eventGateway = eventGateway;
        this.streamingHandler = streamingHandler;
    }

    @PostConstruct
    public void init() {
        if (eventGateway == null) {
            log.warn("OpenCodeEventGateway 未启用，流式响应功能不可用");
            return;
        }

        log.info("初始化 OpenCode SSE 订阅...");
        eventGateway.subscribe(event -> {
            log.debug("收到事件: type={}, sessionId={}", event.getType(), event.getSessionId());
            streamingHandler.handleEvent(event);
        });
    }
}
```

**Step 2: 编译验证**

Run: `mvn compile -pl feishu-bot-infrastructure -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/OpenCodeSseConfig.java
git commit -m "feat(opencode): add SSE initialization config"
```

---

## Task 9: 更新 application.yml 配置

**Files:**
- Modify: `feishu-bot-start/src/main/resources/application.yml`

**Step 1: 添加 SSE 配置**

在 `opencode:` 部分添加：

```yaml
opencode:
  server-url: http://localhost:4096
  username: opencode
  password: ${OPENCODE_SERVER_PASSWORD:}
  
  # SSE 配置
  sse-enabled: true
  sse-reconnect-interval: 5000
  sse-heartbeat-timeout: 60000
  
  # 流式回复配置
  streaming-buffer-size: 100
  streaming-flush-interval: 2000
```

**Step 2: Commit**

```bash
git add feishu-bot-start/src/main/resources/application.yml
git commit -m "feat(config): add SSE configuration to application.yml"
```

---

## Task 10: 编写单元测试

**Files:**
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeEventTest.java`

**Step 1: 创建测试类**

```java
package com.qdw.feishu.domain.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenCodeEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_extractSessionId_fromProperties() throws Exception {
        String json = "{\"sessionID\": \"ses_123\"}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("session.status")
                .properties(properties)
                .build();

        assertEquals("ses_123", event.getSessionId());
    }

    @Test
    void should_extractSessionId_fromPart() throws Exception {
        String json = "{\"part\": {\"sessionID\": \"ses_456\"}}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("message.part.updated")
                .properties(properties)
                .build();

        assertEquals("ses_456", event.getSessionId());
    }

    @Test
    void should_extractDelta() throws Exception {
        String json = "{\"delta\": \"新增文本\"}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("message.part.updated")
                .properties(properties)
                .build();

        assertEquals("新增文本", event.getDelta());
    }

    @Test
    void should_extractStatus() throws Exception {
        String json = "{\"status\": {\"type\": \"idle\"}}";
        JsonNode properties = objectMapper.readTree(json);

        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("session.status")
                .properties(properties)
                .build();

        assertEquals("idle", event.getStatus());
        assertTrue(event.isSessionIdle());
    }

    @Test
    void should_detectTextUpdate() {
        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("message.part.updated")
                .build();

        assertTrue(event.isTextUpdate());
        assertFalse(event.isStatusUpdate());
    }

    @Test
    void should_detectStatusUpdate() {
        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("session.status")
                .build();

        assertTrue(event.isStatusUpdate());
        assertFalse(event.isTextUpdate());
    }

    @Test
    void should_returnNull_when_sessionIdNotPresent() {
        OpenCodeEvent event = OpenCodeEvent.builder()
                .type("server.heartbeat")
                .build();

        assertNull(event.getSessionId());
    }
}
```

**Step 2: 运行测试**

Run: `mvn test -pl feishu-bot-domain -Dtest=OpenCodeEventTest -q`
Expected: Tests run: 7, Failures: 0

**Step 3: Commit**

```bash
git add feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeEventTest.java
git commit -m "test(opencode): add unit tests for OpenCodeEvent"
```

---

## Task 11: 全量测试和构建

**Step 1: 运行所有测试**

Run: `mvn test -q`
Expected: All tests pass

**Step 2: 完整构建**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS

**Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat(opencode): complete SSE-based streaming response implementation

- Add OpenCodeEvent data model for SSE events
- Implement OpenCodeEventGateway with WebClient SSE subscription
- Add OpenCodeStreamingHandler for buffering and flushing text deltas
- Integrate streaming handler with OpenCodeTaskExecutor
- Add SSE configuration in application.yml
- Add comprehensive unit tests"
```

---

## 验证清单

- [ ] `mvn compile` 无错误
- [ ] `mvn test` 全部通过
- [ ] SSE 连接可正常建立
- [ ] 流式文本可正常累积和发送
- [ ] 会话完成时正确清理资源

---

## 测试指南

部署后测试：

1. **SSE 连接测试**
   ```bash
   # 查看日志确认 SSE 连接
   grep "SSE 连接已建立" /tmp/feishu-run.log
   ```

2. **流式响应测试**
   ```
   /opencode chat 写一个 Hello World 程序
   ```
   预期：看到 "⏳ 处理中..." 的流式更新，最后显示 "✅ 完成"

3. **多轮对话测试**
   ```
   /opencode chat 你好
   # 等待完成
   /opencode chat 再见
   ```
   预期：每次都正确处理流式响应

---

**最后更新**: 2026-02-24

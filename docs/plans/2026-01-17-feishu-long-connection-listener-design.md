# Feishu SDK 长连接消息接收设计

**日期**: 2026-01-17
**作者**: Sisyphus
**目标**: 使用飞书 SDK 长连接方式接收消息，无需公网地址

---

## 1. 需求概述

### 1.1 背景

当前飞书机器人使用 Webhook 方式接收消息，需要公网地址。为了支持内网环境部署，需要改用 SDK 长连接方式。

### 1.2 目标

- ✅ 支持内网环境部署，无需公网地址
- ✅ 完全替换 Webhook 方式，移除相关代码
- ✅ 使用 SDK 自动管理连接和重连
- ✅ 支持所有消息类型（单聊、群聊、@消息等）
- ✅ 高可用，自动重连

### 1.3 技术约束

- 保持 COLA 架构分层
- JDK 17
- Spring Boot 3.2.1
- Feishu SDK 2.4.22

---

## 2. 架构设计

### 2.1 整体架构

```
Adapter Layer (适配器层)
  └── FeishuEventListener
       ├── 初始化 SDK 长连接
       ├── 启动事件监听器
       └── 转换事件为领域对象

App Layer (应用层)
  └── ReceiveMessageListenerExe
       └── 处理接收到的消息事件
            └── 调用领域服务

Domain Layer (领域层)
  ├── Message (消息实体)
  ├── MessageListenerGateway (网关接口) ← 新增
  │    └── 定义长连接生命周期管理
  ├── BotMessageService (领域服务)
  └── ReplyExtensionPt (扩展点)

Infrastructure Layer (基础设施层)
  ├── MessageListenerGatewayImpl (网关实现) ← 新增
   │    ├── 建立 SSE 长连接
   │    ├── 心跳和重连管理
   │    └── 消息事件分发
  └── FeishuGatewayImpl (保留)
       └── 发送消息功能
```

### 2.2 架构要点

1. **接口抽象**: `MessageListenerGateway` 定义在 Domain 层，Infrastructure 层实现
2. **生命周期管理**: 连接建立、维护、断开重连由 Infrastructure 层统一处理
3. **职责分离**: 接收消息（长连接）和发送消息（HTTP API）完全分离
4. **COLA 兼容**: 保持现有 COLA 架构分层，仅替换消息接收方式

---

## 3. 组件设计

### 3.1 MessageListenerGateway (Domain 层接口)

```java
public interface MessageListenerGateway {
    /**
     * 启动长连接监听
     */
    void startListening(Consumer<Message> messageHandler);

    /**
     * 停止监听
     */
    void stopListening();

    /**
     * 获取连接状态
     */
    ConnectionStatus getConnectionStatus();

    enum ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }
}
```

### 3.2 FeishuEventListener (Adapter 层)

**职责**:
- 实现 `ApplicationRunner` 接口，应用启动时自动初始化长连接
- 注入 `MessageListenerGateway` 和 `ReceiveMessageListenerExe`
- 将消息处理逻辑委托给应用层

### 3.3 ReceiveMessageListenerExe (App 层)

**职责**:
- 处理接收到的消息事件
- 消息格式验证
- 转换为领域对象
- 调用 `BotMessageService` 处理
- 异步处理避免阻塞接收线程

### 3.4 MessageListenerGatewayImpl (Infrastructure 层)

**职责**:
- 封装 SDK 长连接实现
- 使用飞书 SDK 的 `EventDispatcher` 或 `Client.event()`
- 心跳检测和自动重连
- 连接状态管理
- 错误处理和日志记录

---

## 4. 数据流设计

### 4.1 消息接收流程

```
┌─────────────────────────────────────────────────────────────┐
│ 飞书服务器                                             │
└────────────────────┬────────────────────────────────────────┘
                     │ SSE/WebSocket 长连接
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ Infrastructure Layer                                     │
│ MessageListenerGatewayImpl                                │
│  1. 建立 SSE/WebSocket 连接                              │
│  2. 监听事件推送                                       │
│  3. 心跳检测 (30秒间隔)                                 │
│  4. 连接断开时自动重连 (指数退避: 1s→5s→10s→30s)     │
│  5. 将原始事件转换为 Message 对象                          │
└────────────────────┬────────────────────────────────────────┘
                     │ Message 对象
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ Adapter Layer                                           │
│ FeishuEventListener                                      │
│  1. 实现 ApplicationRunner                                │
│  2. 应用启动时自动初始化                                  │
│  3. 注册消息处理器                                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ App Layer                                               │
│ ReceiveMessageListenerExe                                 │
│  1. 消息验证 (内容非空、发送者有效)                         │
│  2. 提取 Sender 和 Message 内容                            │
│  3. @Async 异步处理 (不阻塞接收线程)                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ Domain Layer                                            │
│ BotMessageService                                       │
│  1. 生成原始回复                                         │
│  2. 通过扩展点增强回复 (ReplyExtensionPt)                   │
│  3. 调用 FeishuGateway 发送消息                           │
│  4. 更新消息状态                                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────────────┐
│ Infrastructure Layer                                     │
│ FeishuGatewayImpl (HTTP API)                            │
│  1. 调用飞书 SDK 发送消息 API                            │
│  2. 处理响应和错误                                       │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 数据转换

**飞书原始事件 → Message 领域对象**:

```java
// 原始事件 (SDK)
Event event = client.event().message().received();

// 转换为领域对象
Message message = Message.builder()
    .content(event.getMessage().getContent())
    .type(MessageType.from(event.getMessage().getMsgType()))
    .sender(Sender.from(event.getSender()))
    .build();
```

**扩展点增强**:

```java
String originalReply = echoReply(message);
String enhancedReply = ExtensionExecutor.getUseCaseExt(
    ReplyExtensionPt.class,
    "feishu-bot",
    "ai"  // 可配置
).enhanceReply(originalReply, message);
```

---

## 5. 错误处理

### 5.1 异常层次

```
                    Exception
                       │
           ┌───────────┴───────────┐
           │                       │
    BizException            SysException
  (业务异常)              (系统异常)
           │                       │
    ┌──────┴──────┐        ┌────┴────┐
    │             │        │         │
Connection    Message  Network  SDK
LostException  Invalid  Timeout  Error
                      Exception
```

### 5.2 连接层错误处理 (Infrastructure 层)

| 异常类型 | 场景 | 处理策略 |
|---------|------|---------|
| `ConnectionLostException` | 长连接断开 | 自动重连（指数退避） |
| `NetworkTimeoutException` | 网络超时 | 重试 3 次，间隔 1s |
| `SDKErrorException` | SDK 返回错误 | 记录日志，触发重连 |
| `AuthenticationException` | App ID/Secret 错误 | 立即停止，记录致命错误 |

**重连策略实现**:

```java
public void reconnect() {
    int attempt = 0;
    int delay = 1000; // 初始延迟 1 秒

    while (running) {
        try {
            attempt++;
            Thread.sleep(delay);
            connect();
            logger.info("Reconnect succeeded after {} attempts", attempt);
            break;
        } catch (Exception e) {
            delay = Math.min(delay * 2, 30000); // 最大 30 秒
            logger.error("Reconnect failed, next attempt in {}ms", delay);
        }
    }
}
```

### 5.3 消息处理层错误处理 (App 层)

| 异常类型 | 场景 | 处理策略 |
|---------|------|---------|
| `MessageInvalidException` | 消息格式错误 | 记录日志，忽略此消息 |
| `SenderInvalidException` | 发送者信息缺失 | 记录日志，忽略此消息 |
| `ProcessingException` | 处理逻辑错误 | 捕获并记录，不中断监听 |

### 5.4 业务层错误处理 (Domain 层)

| 异常类型 | 场景 | 处理策略 |
|---------|------|---------|
| `MessageBizException` | 回复生成失败 | 返回默认回复 |
| `SendFailedException` | 消息发送失败 | 重试 2 次，记录失败日志 |

### 5.5 全局异常处理

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConnectionLostException.class)
    public ResponseEntity<String> handleConnectionLost(ConnectionLostException e) {
        logger.warn("Connection lost, auto reconnecting...");
        return ResponseEntity.status(503).body("Service temporarily unavailable");
    }

    @ExceptionHandler(MessageInvalidException.class)
    public void handleInvalidMessage(MessageInvalidException e) {
        logger.error("Invalid message: {}", e.getMessage());
        // 不返回响应，直接忽略
    }
}
```

---

## 6. 测试策略

### 6.1 单元测试

**MessageListenerGatewayImplTest**:
- ✅ 连接成功场景
- ✅ 连接失败场景
- ✅ 消息接收场景
- ✅ 重连逻辑测试

**ReceiveMessageListenerExeTest**:
- ✅ 有效消息处理
- ✅ 空消息拒绝
- ✅ 无效发送者拒绝
- ✅ 异步处理验证

**Mock 策略**:

```java
@ExtendWith(MockitoExtension.class)
class MessageListenerGatewayImplTest {

    @Mock
    private Client mockClient;

    @Mock
    private EventDispatcher mockEventDispatcher;

    @Test
    void shouldConnectSuccessfully() {
        // Given
        when(mockClient.event()).thenReturn(mockEventDispatcher);

        // When
        gateway.startListening(mockHandler);

        // Then
        verify(mockEventDispatcher).start();
        assertEquals(ConnectionStatus.CONNECTED, gateway.getConnectionStatus());
    }
}
```

### 6.2 集成测试

**FeishuEventListenerIntegrationTest**:
- ✅ 完整的启动流程
- ✅ 消息接收端到端测试
- ✅ 使用飞书测试环境

```java
@SpringBootTest
@TestPropertySource(properties = {
    "feishu.enabled=true",
    "feishu.mode=listener"
})
class FeishuEventListenerIntegrationTest {

    @Autowired
    private FeishuEventListener listener;

    @Test
    void shouldReceiveAndProcessMessage() {
        // 发送测试消息到飞书测试群
        // 验证消息被接收和处理
    }
}
```

### 6.3 模拟测试

**使用 Mock Server 模拟飞书 SSE 端点**:

```java
@SpringBootTest(webEnvironment = MOCK_SERVER)
class ConnectionStabilityTest {

    @Test
    void shouldAutoReconnectOnConnectionLoss() {
        // 1. 启动长连接
        // 2. 关闭模拟服务器
        // 3. 验证进入 RECONNECTING 状态
        // 4. 重启模拟服务器
        // 5. 验证自动恢复到 CONNECTED
    }
}
```

### 6.4 测试覆盖率目标

- **单元测试**: > 80%
- **集成测试**: 核心流程 100%
- **关键路径**: 连接建立、消息接收、重连逻辑

---

## 7. 配置和部署

### 7.1 配置参数

```yaml
feishu:
  app-id: ${FEISHU_APPID}
  app-secret: ${FEISHU_APPSECRET}
  mode: listener  # listener 模式
  listener:
    enabled: true
    heartbeat-interval: 30  # 秒
    reconnect:
      initial-delay: 1000  # 毫秒
      max-delay: 30000     # 最大 30 秒
      max-attempts: 10     # 最大重试次数
    timeout:
      connect: 10000       # 连接超时
      read: 60000          # 读取超时
```

### 7.2 部署检查清单

**部署前准备**:
- [ ] 飞书开放平台配置事件订阅（使用内部回调地址）
- [ ] 确认 App ID 和 Secret 正确
- [ ] 确认网络可访问飞书 API 端点（`open.feishu.cn`）

**运行时验证**:
- [ ] 检查日志确认长连接建立成功
- [ ] 发送测试消息验证接收功能
- [ ] 模拟网络中断验证重连功能
- [ ] 检查监控指标（连接状态、消息数量）

### 7.3 监控指标

- `feishu.connection.status` (当前连接状态)
- `feishu.messages.received.total` (接收消息总数)
- `feishu.messages.processed.total` (处理消息总数)
- `feishu.reconnect.count` (重连次数)

---

## 8. 实施计划

### 8.1 文件变更清单

**新增文件**:
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/MessageListenerGateway.java`
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java`
- `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/listener/FeishuEventListener.java`
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java`

**删除文件**:
- `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/web/FeishuWebhookController.java`

**修改文件**:
- `feishu-bot-start/src/main/resources/application.yml`

### 8.2 实施步骤

1. **Phase 1: 接口定义**
   - 创建 `MessageListenerGateway` 接口
   - 定义连接状态枚举

2. **Phase 2: Infrastructure 实现**
   - 实现 `MessageListenerGatewayImpl`
   - 集成飞书 SDK 长连接 API
   - 实现心跳和重连逻辑

3. **Phase 3: App 层实现**
   - 创建 `ReceiveMessageListenerExe`
   - 实现消息验证和处理逻辑

4. **Phase 4: Adapter 层实现**
   - 创建 `FeishuEventListener`
   - 实现自动启动逻辑

5. **Phase 5: 测试和验证**
   - 编写单元测试
   - 集成测试
   - 部署验证

### 8.3 风险和缓解

| 风险 | 影响 | 缓解措施 |
|-----|------|---------|
| SDK 不支持长连接 | 方案不可行 | 提前验证 SDK API |
| 长连接稳定性差 | 消息丢失 | 实现健壮的重连机制 |
| 性能问题 | 消息延迟 | 异步处理，优化线程池 |

---

## 9. 总结

本设计方案使用飞书 SDK 的长连接方式替代 Webhook，使机器人能够在内网环境部署，无需公网地址。通过清晰的架构分层、完善的错误处理和全面的测试策略，确保系统的高可用性和稳定性。

关键优势：
- ✅ 无需公网地址，支持内网部署
- ✅ SDK 自动管理连接，简化维护
- ✅ 保持 COLA 架构，易于扩展
- ✅ 高可用，自动重连
- ✅ 支持所有消息类型

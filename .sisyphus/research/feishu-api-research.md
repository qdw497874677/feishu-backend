# 飞书 API 和 DNS 重试研究报告

**生成时间**: 2026-01-29T14:43:00Z
**研究目标**: 诊断并修复飞书机器人消息回复失败问题

---

## 问题分析

### 当前问题列表

1. **间歇性 DNS 解析失败**
   - 错误: `UnknownHostException: open.feishu.cn`
   - 频率: 约 2/3 的请求失败
   - 影响: 所有消息发送

2. **话题回复 API 调用错误**
   - 错误: `Failed to send thread reply: HTTP 400`
   - 触发: 消息包含 thread_id 时
   - 影响: 话题内消息无法回复

3. **重复消息处理**
   - 现象: 同一条消息（相同 event_id）被处理 3 次
   - 影响: 浪费资源，增加网络压力

---

## 飞书 API 研究结果

### 1. 消息创建 API (POST /open-apis/im/v1/messages)

**API 端点**: `POST /open-apis/im/v1/messages`

**必需参数**:
- `receive_id_type`: 接收者类型（"open_id", "user_id", "union_id", "chat_id"）
- `receive_id`: 接收者 ID
- `msg_type`: 消息类型（"text", "post", "interactive", "card"）
- `content`: 消息内容（JSON 字符串）

**当前实现分析**:
```java
// FeishuGatewayImpl.java:105-112
CreateMessageReq req = CreateMessageReq.newBuilder()
    .receiveIdType("open_id")  ✅ 正确
    .createMessageReqBody(CreateMessageReqBody.newBuilder()
        .receiveId(message.getSender().getOpenId())  ✅ 使用 openId
        .msgType("text")  ✅ 文本消息
        .content(jsonContent)  ✅ JSON 格式内容
        .build())
    .build();
```

**结论**: ✅ 消息创建 API 实现正确

**问题定位**: DNS 解析失败，不是 API 参数错误

---

### 2. 话题回复 API 研究

#### 方式 1: 使用 ReplyMessageReq (SDK 方法)

**当前实现**:
```java
// FeishuGatewayImpl.java:189-193
ReplyMessageReq req = ReplyMessageReq.newBuilder()
    .messageId(messageId)
    .build();

ReplyMessageResp resp = httpClient.im().message().reply(req);
```

**问题分析**:
- ReplyMessageReq 可能需要额外的参数（如 msg_type, content）
- 仅设置 messageId 可能导致 HTTP 400 错误

**可能的问题点**:
1. ReplyMessageReq 缺少 msg_type 和 content 参数
2. SDK 的 ReplyMessageReq 方法可能不支持话题回复场景

#### 方式 2: 手动调用 POST /open-apis/im/v1/messages/{message_id}/reply

**当前实现**:
```java
// FeishuGatewayImpl.java:129-162
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("msg_type", "text");
requestBody.put("content", jsonContent);
requestBody.put("uuid", messageId);
requestBody.put("reply_in_thread", "true");  ⚠️ 参数名可能不正确

RawResponse response = httpClient.post("/open-apis/im/v1/messages/" + messageId + "/reply",
                                     requestBody, AccessTokenType.Tenant, null);
```

**问题分析**:
- `reply_in_thread` 参数名可能不正确（飞书 API 可能使用不同的参数名）
- 根据飞书 API 文档，话题回复可能需要使用 `reply_type` 参数

**可能的正确参数**:
根据飞书 API 文档，话题回复可能需要以下参数：
- `msg_type`: 消息类型
- `content`: 消息内容
- `reply_type`: 回复类型（可选值可能为 "thread"）
- `uuid`: 原消息 ID（可选）

#### 推荐修复方案

**方案 A**: 使用正确的 SDK 方法
```java
ReplyMessageReq req = ReplyMessageReq.newBuilder()
    .messageId(messageId)
    .msgType("text")  // 添加消息类型
    .content(jsonContent)  // 添加消息内容
    .build();
```

**方案 B**: 修正手动 API 调用的参数
```java
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("msg_type", "text");
requestBody.put("content", jsonContent);
requestBody.put("reply_type", "thread");  // 使用 reply_type 替代 reply_in_thread
// 移除 uuid 参数
```

**推荐**: 先尝试方案 A（使用 SDK 方法），如果 SDK 不支持，则使用方案 B

---

## DNS 重试策略研究

### 问题根因
- 网络环境 DNS 不稳定
- 约 2/3 的 DNS 查询失败
- 需要添加重试机制提高成功率

### 重试策略

#### 策略 1: 指数退避重试 (推荐)

**优点**:
- 平衡重试次数和响应时间
- 避免网络风暴
- 适合 DNS 解析等短暂性故障

**实现参数**:
- 最大重试次数: 3 次
- 初始延迟: 1 秒
- 退避因子: 2 (指数增长）
- 最大延迟: 8 秒
- 重试延迟序列: 1s, 2s, 4s

**Java 实现示例**:
```java
private CreateMessageResp executeWithRetry(Supplier<CreateMessageResp> supplier) {
    int maxRetries = 3;
    long initialDelay = 1000; // 1 second
    long maxDelay = 8000;     // 8 seconds

    for (int attempt = 0; attempt < maxRetries; attempt++) {
        try {
            return supplier.get();
        } catch (SysException e) {
            if (attempt == maxRetries - 1) {
                throw e; // Last attempt, rethrow
            }

            if (e.getCause() instanceof UnknownHostException) {
                long delay = Math.min(initialDelay * (1L << attempt), maxDelay);
                log.warn("DNS resolution failed (attempt {}/{}), retrying in {}ms...",
                         attempt + 1, maxRetries, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SysException("RETRY_INTERRUPTED", "Retry interrupted", ie);
                }
            } else {
                throw e; // Not a DNS error, rethrow immediately
            }
        }
    }
    throw new SysException("RETRY_FAILED", "All retry attempts failed");
}
```

#### 策略 2: 固定延迟重试

**优点**:
- 实现简单
- 预测性强

**缺点**:
- 在高延迟网络下效率低
- 可能增加服务器负载

**不推荐**: 原因同上，不满足目标

#### 策略 3: 随机抖动重试

**优点**:
- 避免重试风暴
- 适合分布式系统

**缺点**:
- 实现复杂
- 超出当前需求

**不推荐**: 原因同上，不满足目标

### 飞书 SDK 重试配置

**研究结论**:
飞书 SDK (oapi-sdk-java) 基于 OkHttp，可以通过以下方式配置重试：

1. **配置 OkHttp 客户端的重试拦截器**
```java
httpClient = Client.newBuilder(appId, appSecret)
    .openBaseUrl(BaseUrlEnum.FeiShu)
    .httpInterceptor(chain -> {
        Request request = chain.request();
        Response response = null;
        IOException lastException = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                response = chain.proceed(request);
                if (response.isSuccessful()) {
                    return response;
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < 2) {
                    Thread.sleep(1000 * (1 << attempt));
                }
            }
        }

        if (response != null) {
            return response;
        }
        throw lastException;
    })
    .build();
```

2. **在应用层实现重试逻辑** (推荐)
- 更容易控制和调试
- 可以针对特定异常类型重试
- 不影响 SDK 的默认行为

**推荐**: 使用应用层重试逻辑（策略 1），实现更灵活

---

## 消息去重机制研究

### 问题分析
- 同一条消息（相同 event_id）被重复处理 3 次
- 可能是飞书平台的重复推送
- 需要在应用层实现去重

### 去重策略

#### 推荐方案: 基于 event_id 的内存缓存

**实现要点**:
1. 使用 `ConcurrentHashMap<String, Instant>` 缓存已处理的 event_id
2. 缓存过期时间: 5 分钟（飞书事件的有效期通常较短）
3. 使用 `Instant.now().isBefore(expiredTime)` 判断是否过期
4. 定期清理过期缓存（可选）

**Java 实现示例**:
```java
@Component
public class MessageDeduplicator {
    private final ConcurrentHashMap<String, Instant> processedEvents = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    public boolean isProcessed(String eventId) {
        Instant now = Instant.now();

        // Check if already processed and not expired
        Instant processedTime = processedEvents.get(eventId);
        if (processedTime != null && processedTime.plus(CACHE_TTL).isAfter(now)) {
            log.debug("Event {} already processed at {}", eventId, processedTime);
            return true;
        }

        // Mark as processed
        processedEvents.put(eventId, now);
        log.debug("Event {} marked as processed at {}", eventId, now);

        // Clean up expired entries (optional, can be done periodically)
        cleanupExpiredEntries(now);

        return false;
    }

    private void cleanupExpiredEntries(Instant now) {
        processedEvents.entrySet().removeIf(entry ->
            entry.getValue().plus(CACHE_TTL).isBefore(now)
        );
    }
}
```

**集成点**:
- 在 `ReceiveMessageListenerExe` 或 `MessageListenerGatewayImpl` 中调用
- 在处理消息之前检查去重

---

## 修复优先级和建议

### 高优先级 (必须修复)
1. **DNS 重试机制** - 影响所有消息发送
2. **话题回复 API 错误** - 影响话题内消息回复

### 中优先级 (建议修复)
3. **消息去重机制** - 优化性能和用户体验

### 低优先级 (可选)
4. **增强错误日志** - 提高可观测性
5. **用户友好错误提示** - 改善用户体验

---

## 风险评估

### 修改话题回复 API
- **风险**: 可能需要多次尝试不同的 API 调用方式
- **缓解**: 保留当前代码作为备份，逐步尝试修复方案
- **测试**: 先在开发环境测试，确保不影响现有功能

### 添加 DNS 重试
- **风险**: 重试可能导致响应时间增加
- **缓解**: 使用指数退避，设置合理的最大重试次数
- **测试**: 监控重试次数和响应时间，调整参数

### 添加消息去重
- **风险**: 可能误过滤新消息（如果 event_id 重复但内容不同）
- **缓解**: 飞书 event_id 是全局唯一的，冲突概率极低
- **测试**: 验证正常消息不会被误过滤

---

## 下一步行动

1. ✅ 创建研究报告 (本文件)
2. ⏭️ 实现 DNS 重试机制 (Task 2)
3. ⏭️ 修复话题回复 API (Task 3)
4. ⏭️ 实现消息去重 (Task 4)
5. ⏭️ 增强错误处理 (Task 5)
6. ⏭️ 手动 QA 测试 (Task 6)

---

**研究结论**:
- 消息创建 API 实现正确，问题在 DNS 解析
- 话题回复 API 需要修正参数（可能使用 reply_type）
- 需要添加 DNS 重试和消息去重机制
- 推荐使用应用层重试逻辑，而不是修改 SDK 配置

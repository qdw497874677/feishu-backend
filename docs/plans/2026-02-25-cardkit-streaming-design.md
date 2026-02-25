# CardKit 流式响应设计

> **状态**: 设计完成，待实现  
> **日期**: 2026-02-25  
> **作者**: AI Assistant

---

## 1. 背景与目标

### 1.1 问题

当前 OpenCode 流式响应使用**发送多条消息**的方式：
- 话题中出现大量消息，刷屏
- 用户体验差
- 类似 ChatGPT 的打字效果无法实现

### 1.2 目标

1. **卡片流式更新**：使用 CardKit API 实现真正的流式效果
2. **通用能力**：CardKit 作为通用能力，供所有应用使用
3. **无编辑限制**：CardKit 更新无次数限制（消息编辑有 20-30 次上限）

### 1.3 参考

- [飞书AI机器人流式输出实践](https://juejin.cn/post/7600990891206819867)
- [飞书 CardKit API](https://open.feishu.cn/document/server-docs/cardkit-v1/card/create)

---

## 2. 方案对比

| 方案 | 编辑限制 | 适用场景 |
|------|---------|---------|
| 消息编辑 API | 有上限（20-30 次） | 少量更新 |
| **卡片实体（CardKit）** | **无限制** | **流式输出** ✅ |

---

## 3. 架构设计

### 3.1 组件关系

```
┌─────────────────────────────────────────────────────────────────┐
│                         domain 层                                │
├─────────────────────────────────────────────────────────────────┤
│  CardGateway (接口)                                              │
│  ├── createCard(title, content) → cardId                        │
│  ├── updateCard(cardId, content, sequence) → boolean            │
│  └── sendCardMessage(message, cardId) → SendResult              │
│                                                                  │
│  StreamingCardManager (服务)                                     │
│  └── 管理 cardId → sequence 映射，提供流式更新能力               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     infrastructure 层                            │
├─────────────────────────────────────────────────────────────────┤
│  CardGatewayImpl                                                 │
│  └── 使用 lark-oapi-sdk 调用 CardKit API                         │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

| 组件 | 职责 |
|------|------|
| `CardGateway` | 领域层网关接口，定义卡片操作抽象 |
| `StreamingCardManager` | 流式卡片管理器，封装 sequence 递增逻辑 |
| `CardGatewayImpl` | 基础设施层实现，调用飞书 CardKit API |

---

## 4. 接口设计

### 4.1 CardGateway 接口

```java
// domain/gateway/CardGateway.java
public interface CardGateway {
    
    /**
     * 创建卡片实体（不发送消息）
     *
     * @param title 卡片标题
     * @param content 初始内容（支持 Markdown）
     * @return cardId，失败返回 null
     */
    String createCard(String title, String content);
    
    /**
     * 更新卡片内容
     *
     * @param cardId 卡片 ID
     * @param content 新内容（支持 Markdown）
     * @param sequence 序号（必须严格递增）
     * @return 是否成功
     */
    boolean updateCard(String cardId, String content, int sequence);
    
    /**
     * 发送卡片消息
     *
     * @param message 原始消息（用于获取回复上下文）
     * @param cardId 卡片 ID
     * @param topicId 话题 ID（可为 null）
     * @return 发送结果
     */
    SendResult sendCardMessage(Message message, String cardId, String topicId);
}
```

### 4.2 StreamingCardManager 服务

```java
// domain/card/StreamingCardManager.java
@Component
public class StreamingCardManager {
    
    private final CardGateway cardGateway;
    private final Map<String, Integer> cardSequences = new ConcurrentHashMap<>();
    
    /**
     * 创建卡片并发送消息
     */
    public String createAndSend(Message message, String title, String content, String topicId) {
        String cardId = cardGateway.createCard(title, content);
        if (cardId == null) {
            return null;
        }
        cardSequences.put(cardId, 1);
        cardGateway.sendCardMessage(message, cardId, topicId);
        return cardId;
    }
    
    /**
     * 更新卡片（自动管理 sequence）
     */
    public boolean update(String cardId, String content) {
        int seq = cardSequences.getOrDefault(cardId, 0) + 1;
        cardSequences.put(cardId, seq);
        return cardGateway.updateCard(cardId, content, seq);
    }
    
    /**
     * 清理卡片资源
     */
    public void cleanup(String cardId) {
        cardSequences.remove(cardId);
    }
}
```

---

## 5. OpenCode 适配

### 5.1 OpenCodeStreamingHandler 改造

```java
public class OpenCodeStreamingHandler {
    
    private final StreamingCardManager cardManager;
    private final FeishuGateway feishuGateway;
    
    // 新增：记录 sessionId → cardId 映射
    private final Map<String, String> sessionToCardMap = new ConcurrentHashMap<>();
    
    /**
     * 开始流式响应（创建卡片）
     */
    public void startStreaming(String sessionId, Message message) {
        String cardId = cardManager.create("🤖 AI 助手", "⏳ 正在思考...");
        if (cardId != null) {
            sessionToCardMap.put(sessionId, cardId);
            cardManager.sendCardMessage(message, cardId);
        } else {
            // 降级：使用普通消息
            feishuGateway.sendMessage(message, "⏳ 正在处理...", message.getTopicId());
        }
    }
    
    /**
     * 流式更新内容
     */
    public void updateContent(String sessionId, String content) {
        String cardId = sessionToCardMap.get(sessionId);
        if (cardId != null) {
            cardManager.update(cardId, content);
        }
    }
    
    /**
     * 完成流式响应
     */
    public void completeStreaming(String sessionId, String finalContent) {
        String cardId = sessionToCardMap.get(sessionId);
        if (cardId != null) {
            cardManager.update(cardId, "✅ 完成\n\n" + finalContent);
            cardManager.cleanup(cardId);
        }
        sessionToCardMap.remove(sessionId);
    }
}
```

### 5.2 用户消息格式

```
用户：帮我写一个排序算法

机器人（卡片）：
┌─────────────────────────────────┐
│ 🤖 AI 助手                        │
├─────────────────────────────────┤
│ ⏳ 正在思考...                    │  ← 初始状态
└─────────────────────────────────┘

        ↓ (1秒后，卡片更新)

┌─────────────────────────────────┐
│ 🤖 AI 助手                        │
├─────────────────────────────────┤
│ ⏳ 处理中...                      │
│                                  │
│ def bubble_sort(arr):            │  ← 逐步显示
│     n = len(arr)                 │
└─────────────────────────────────┘

        ↓ (完成时，卡片更新)

┌─────────────────────────────────┐
│ 🤖 AI 助手                        │
├─────────────────────────────────┤
│ ✅ 完成                           │
│                                  │
│ def bubble_sort(arr):            │
│     n = len(arr)                 │
│     for i in range(n):           │
│         ...                      │
└─────────────────────────────────┘
```

---

## 6. 基础设施层实现

### 6.1 CardGatewayImpl

```java
// infrastructure/gateway/CardGatewayImpl.java
@Slf4j
@Component
public class CardGatewayImpl implements CardGateway {
    
    private final Client httpClient;
    private final ObjectMapper objectMapper;
    
    @Override
    public String createCard(String title, String content) {
        try {
            String cardJson = buildCardJson(title, content);
            
            CreateCardReq req = CreateCardReq.newBuilder()
                .requestBody(CreateCardReqBody.newBuilder()
                    .type("card_json")
                    .data(cardJson)
                    .build())
                .build();
            
            CreateCardResp resp = httpClient.cardkit().v1().card().create(req);
            
            if (resp.success() && resp.getData() != null) {
                log.info("卡片创建成功: cardId={}", resp.getData().getCardId());
                return resp.getData().getCardId();
            }
            
            log.warn("卡片创建失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return null;
            
        } catch (Exception e) {
            log.error("创建卡片异常", e);
            return null;
        }
    }
    
    @Override
    public boolean updateCard(String cardId, String content, int sequence) {
        try {
            String cardJson = buildCardJson(null, content);
            
            Card card = Card.builder()
                .type("card_json")
                .data(cardJson)
                .build();
            
            UpdateCardReq req = UpdateCardReq.newBuilder()
                .cardId(cardId)
                .requestBody(UpdateCardReqBody.newBuilder()
                    .card(card)
                    .sequence(sequence)
                    .build())
                .build();
            
            UpdateCardResp resp = httpClient.cardkit().v1().card().update(req);
            
            if (resp.success()) {
                log.debug("卡片更新成功: cardId={}, seq={}", cardId, sequence);
                return true;
            }
            
            log.warn("卡片更新失败: cardId={}, code={}, msg={}", cardId, resp.getCode(), resp.getMsg());
            return false;
            
        } catch (Exception e) {
            log.error("更新卡片异常: cardId={}", cardId, e);
            return false;
        }
    }
    
    private String buildCardJson(String title, String content) throws Exception {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", Map.of("update_multi", true));
        
        if (title != null) {
            card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", title)
            ));
        }
        
        card.put("elements", List.of(
            Map.of("tag", "markdown", "content", content)
        ));
        
        return objectMapper.writeValueAsString(card);
    }
}
```

### 6.2 SDK 踩坑记录

| 坑 | 错误写法 | 正确写法 |
|----|---------|---------|
| card 参数类型 | `card(cardJsonStr)` | `card(Card.builder().data(cardJsonStr).build())` |
| sequence | 每次传 1 | 必须严格递增 |

---

## 7. 错误处理

### 7.1 降级策略

```
创建卡片失败 → 降级为普通文本消息
更新卡片失败 → 继续处理，不中断流程（已显示的内容仍在）
发送卡片失败 → 降级为普通文本消息
```

### 7.2 资源清理

```java
// 确保资源被清理
try {
    // 流式更新逻辑
} finally {
    cardManager.cleanup(cardId);
    sessionToCardMap.remove(sessionId);
}
```

---

## 8. 测试策略

### 8.1 单元测试

| 测试类 | 测试内容 |
|--------|---------|
| `CardGatewayImplTest` | 创建/更新/发送卡片 |
| `StreamingCardManagerTest` | sequence 自动递增 |
| `OpenCodeStreamingHandlerTest` | 卡片流式更新流程 |

### 8.2 集成测试场景

1. **正常流程**：创建卡片 → 发送 → 多次更新 → 完成
2. **降级流程**：创建失败 → 降级为文本消息
3. **并发更新**：多个 session 同时更新不同卡片

### 8.3 Mock 示例

```java
@Mock
CardGateway cardGateway;

@InjectMocks
StreamingCardManager manager;

@Test
void should_incrementSequence_onEachUpdate() {
    when(cardGateway.updateCard(any(), any(), anyInt())).thenReturn(true);
    
    manager.createAndSend(message, "title", "content", null);
    manager.update("card-123", "new content");
    manager.update("card-123", "newer content");
    
    verify(cardGateway).updateCard(eq("card-123"), any(), eq(2));
    verify(cardGateway).updateCard(eq("card-123"), any(), eq(3));
}
```

---

## 9. 实现步骤

| 步骤 | 任务 | 文件 |
|------|------|------|
| 1 | 创建 CardGateway 接口 | `domain/gateway/CardGateway.java` |
| 2 | 创建 StreamingCardManager | `domain/card/StreamingCardManager.java` |
| 3 | 实现 CardGatewayImpl | `infrastructure/gateway/CardGatewayImpl.java` |
| 4 | 修改 OpenCodeStreamingHandler | `domain/opencode/OpenCodeStreamingHandler.java` |
| 5 | 编写单元测试 | `*Test.java` |
| 6 | 集成测试验证 | 手动测试 |

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| CardKit API 变更 | 实现失败 | 封装在 Gateway 层，易于修改 |
| 更新频率过高 | API 限流 | 保持 2 秒间隔，节流控制 |
| 卡片内容过长 | 显示截断 | 限制内容长度，超出提示 |

---

**最后更新**: 2026-02-25

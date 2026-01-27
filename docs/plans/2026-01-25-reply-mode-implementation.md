# 应用回复模式机制实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为应用系统添加回复模式配置机制，支持应用选择默认回复或创建话题回复，并支持话题上下文持久化

**Architecture:** 全局配置优先，应用可覆盖的配置策略。话题映射使用内存存储（后续可升级为数据库）。遵循 COLA 架构分层原则，接口定义在 domain，实现在 infrastructure。

**Tech Stack:** Spring Boot, Spring ConfigurationProperties, Maven, Lombok

---

## Task 1: 创建配置类

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/FeishuReplyProperties.java`
- Modify: `feishu-bot-start/src/main/resources/application.yml`

**Step 1: 创建配置类**

```java
package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.app.ReplyMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "feishu.reply")
public class FeishuReplyProperties {

    private ReplyMode mode = ReplyMode.DEFAULT;
}
```

**Step 2: 更新配置文件**

在 `feishu-bot-start/src/main/resources/application.yml` 添加：

```yaml
feishu:
  reply:
    mode: DEFAULT  # 可选值: DEFAULT, TOPIC
```

**Step 3: 验证配置类可被扫描**

Run: `mvn clean compile -pl feishu-bot-infrastructure,feishu-bot-start`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
cd .worktrees/feature-reply-mode
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/FeishuReplyProperties.java
git add feishu-bot-start/src/main/resources/application.yml
git commit -m "feat: 添加回复模式配置类"
```

---

## Task 2: 创建话题映射领域模型

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TopicMapping.java`
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/TopicMappingGateway.java`

**Step 1: 创建 TopicMapping 实体**

```java
package com.qdw.feishu.domain.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMapping {

    private String topicId;
    private String appId;
    private Instant createdAt;
    private Instant lastActiveAt;
}
```

**Step 2: 创建 TopicMappingGateway 接口**

```java
package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.app.TopicMapping;

import java.util.Optional;

public interface TopicMappingGateway {

    void save(TopicMapping mapping);

    Optional<TopicMapping> findByTopicId(String topicId);

    void updateLastActiveTime(String topicId);

    void delete(String topicId);
}
```

**Step 3: 验证编译**

Run: `mvn clean compile -pl feishu-bot-domain`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TopicMapping.java
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/TopicMappingGateway.java
git commit -m "feat: 添加话题映射领域模型"
```

---

## Task 3: 实现话题映射网关

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/TopicMappingGatewayImpl.java`

**Step 1: 创建网关实现**

```java
package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.app.TopicMapping;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TopicMappingGatewayImpl implements TopicMappingGateway {

    private final Map<String, TopicMapping> mappings = new ConcurrentHashMap<>();

    @Override
    public void save(TopicMapping mapping) {
        mappings.put(mapping.getTopicId(), mapping);
        log.info("保存话题映射: topicId={}, appId={}", mapping.getTopicId(), mapping.getAppId());
    }

    @Override
    public Optional<TopicMapping> findByTopicId(String topicId) {
        return Optional.ofNullable(mappings.get(topicId));
    }

    @Override
    public void updateLastActiveTime(String topicId) {
        TopicMapping mapping = mappings.get(topicId);
        if (mapping != null) {
            mapping.setLastActiveTime(Instant.now());
            log.debug("更新话题活跃时间: topicId={}", topicId);
        }
    }

    @Override
    public void delete(String topicId) {
        mappings.remove(topicId);
        log.info("删除话题映射: topicId={}", topicId);
    }
}
```

**Step 2: 验证实现**

Run: `mvn clean compile -pl feishu-bot-infrastructure`
Expected: BUILD SUCCESS

**Step 3: 提交**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/TopicMappingGatewayImpl.java
git commit -m "feat: 实现话题映射网关"
```

---

## Task 4: 扩展 AppRegistry 支持全局配置

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/AppRegistry.java`

**Step 1: 读取当前 AppRegistry 实现**

Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/AppRegistry.java`

**Step 2: 添加全局配置支持**

在 `AppRegistry` 中添加依赖注入和方法：

```java
import com.qdw.feishu.domain.app.ReplyMode;
import com.qdw.feishu.infrastructure.config.FeishuReplyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AppRegistry {

    // ... 现有字段和方法

    @Autowired
    @Lazy
    private FeishuReplyProperties replyProperties;

    public ReplyMode getDefaultReplyMode() {
        return replyProperties.getMode();
    }
}
```

**Step 3: 验证编译**

Run: `mvn clean compile -pl feishu-bot-domain,feishu-bot-infrastructure`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/AppRegistry.java
git commit -m "feat: AppRegistry 支持获取全局回复模式"
```

---

## Task 5: 扩展 Message 实体支持话题ID

**Files:**
- Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java`

**Step 1: 读取当前 Message 实现**

Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java`

**Step 2: 添加 topicId 字段**

在 `Message` 类中添加：

```java
private String topicId;  // 话题ID，如果消息在话题中
```

确保添加 getter/setter 方法。

**Step 3: 验证编译**

Run: `mvn clean compile -pl feishu-bot-domain`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Message.java
git commit -m "feat: Message 支持话题ID字段"
```

---

## Task 6: 更新 FeishuGateway 支持话题回复

**Files:**
- Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/FeishuGateway.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/FeishuGateway.java`
- Read: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java`
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java`

**Step 1: 更新 FeishuGateway 接口**

在接口中添加方法：

```java
SendResult sendMessage(Message message, String content, String topicId);
```

**Step 2: 更新 FeishuGatewayImpl 实现**

```java
@Override
public SendResult sendMessage(Message message, String content, String topicId) {
    try {
        // 构建消息内容
        Message.TextContent textContent = Message.TextContent.newBuilder()
            .text(content)
            .build();

        Message.MessageContent messageContent = Message.MessageContent.newBuilder()
            .text(textContent)
            .build();

        SendMessageRequest sendMessageRequest = SendMessageRequest.newBuilder()
            .receiveIdType(receiveIdType)
            .requestBody(new SendMessageRequest.RequestBody()
                .receiveId(message.getChatId())
                .msgType(msgType)
                .content(messageContent))
            .build();

        // 如果提供了 topicId，设置话题ID
        if (topicId != null && !topicId.isEmpty()) {
            sendMessageRequest.requestBody().setRootId(topicId);
        }

        SendMessageResponse response = client.im().message().send(sendMessageRequest);

        if (response.getCode() == 0) {
            log.info("发送消息成功: chatId={}, topicId={}, messageId={}",
                message.getChatId(), topicId, response.getData().getMessageId());
            return SendResult.success(response.getData().getMessageId());
        } else {
            log.error("发送消息失败: code={}, msg={}", response.getCode(), response.getMsg());
            return SendResult.fail(response.getMsg());
        }
    } catch (Exception e) {
        log.error("发送消息异常", e);
        return SendResult.fail(e.getMessage());
    }
}
```

**Step 3: 验证编译**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/FeishuGateway.java
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java
git commit -m "feat: FeishuGateway 支持话题回复"
```

---

## Task 7: 更新 BotMessageService 支持话题路由

**Files:**
- Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`

**Step 1: 读取当前 BotMessageService 实现**

Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`

**Step 2: 添加话题路由逻辑**

在 `BotMessageService` 中添加依赖注入和更新消息处理逻辑：

```java
import com.qdw.feishu.domain.app.TopicMapping;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;

@Autowired
private TopicMappingGateway topicMappingGateway;
```

更新消息处理逻辑，添加话题检测和路由：

```java
public void processMessage(Message message) {
    log.info("开始处理消息: content={}", message.getContent());

    String topicId = message.getTopicId();
    FishuAppI app;

    // 检查是否在话题中
    if (topicId != null && !topicId.isEmpty()) {
        log.info("消息来自话题: topicId={}", topicId);
        Optional<TopicMapping> mapping = topicMappingGateway.findByTopicId(topicId);
        if (mapping.isPresent()) {
            String appId = mapping.get().getAppId();
            log.info("找到话题映射: topicId={}, appId={}", topicId, appId);
            app = appRegistry.findAppById(appId).orElse(null);
            if (app == null) {
                log.error("应用不存在: appId={}", appId);
                sendErrorReply(message, "应用不可用");
                return;
            }
            // 更新话题活跃时间
            topicMappingGateway.updateLastActiveTime(topicId);
        } else {
            log.warn("话题映射不存在: topicId={}，降级为默认处理", topicId);
            handleUnknownTopic(message);
            return;
        }
    } else {
        // 新消息，正常路由
        app = appRouter.route(message);
        if (app == null) {
            log.warn("未找到匹配的应用");
            return;
        }
    }

    // 执行应用逻辑
    String replyContent = app.execute(message);
    if (replyContent == null || replyContent.isEmpty()) {
        log.warn("应用返回空回复");
        return;
    }

    // 获取回复模式
    ReplyMode replyMode = app.getReplyMode();
    String finalTopicId = topicId;

    // 如果是话题模式且当前不在话题中，创建新话题
    if (replyMode == ReplyMode.TOPIC && (topicId == null || topicId.isEmpty())) {
        // TODO: 调用飞书 API 创建话题，获取 topicId
        // 暂时使用随机字符串模拟
        String newTopicId = "topic_" + System.currentTimeMillis();
        log.info("创建新话题: topicId={}", newTopicId);

        // 保存话题映射
        TopicMapping mapping = TopicMapping.builder()
            .topicId(newTopicId)
            .appId(app.getAppId())
            .createdAt(Instant.now())
            .lastActiveTime(Instant.now())
            .build();
        topicMappingGateway.save(mapping);

        finalTopicId = newTopicId;
    }

    // 发送回复
    SendResult result = feishuGateway.sendMessage(message, replyContent, finalTopicId);

    if (result.isSuccess()) {
        log.info("发送回复成功: topicId={}", finalTopicId);
    } else {
        log.error("发送回复失败: error={}", result.getError());
    }
}

private void handleUnknownTopic(Message message) {
    String errorReply = "话题已失效，请重新发送命令触发应用。";
    feishuGateway.sendMessage(message, errorReply, null);
}

private void sendErrorReply(Message message, String error) {
    feishuGateway.sendMessage(message, "错误: " + error, null);
}
```

**Step 3: 验证编译**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
git commit -m "feat: BotMessageService 支持话题路由"
```

---

## Task 8: 更新消息接收器解析 topicId

**Files:**
- Read: `feishu-bot-app/src/main/java/com/qdw/feishu/app/receivemessage/ReceiveMessageListenerExe.java`
- Modify: `feishu-bot-app/src/main/java/com/qdw/feishu/app/receivemessage/ReceiveMessageListenerExe.java`

**Step 1: 读取当前消息接收器**

Read: `feishu-bot-app/src/main/java/com/qdw/feishu/app/receivemessage/ReceiveMessageListenerExe.java`

**Step 2: 添加 topicId 解析逻辑**

在解析飞书消息时添加 topicId 提取：

```java
// 在解析消息内容的地方，添加 topicId 提取
String topicId = null;
try {
    // 尝试从飞书消息中获取 topicId
    topicId = event.getData().getRootId();
    log.debug("消息 topicId: {}", topicId);
} catch (Exception e) {
    log.debug("消息没有 topicId");
}

// 在创建 Message 对象时传入 topicId
Message message = Message.builder()
    .chatId(chatId)
    .userId(userId)
    .content(content)
    .topicId(topicId)
    .build();
```

**Step 3: 验证编译**

Run: `mvn clean compile`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-app/src/main/java/com/qdw/feishu/app/receivemessage/ReceiveMessageListenerExe.java
git commit -m "feat: 消息接收器解析 topicId"
```

---

## Task 9: 更新应用示例支持话题模式

**Files:**
- Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java`

**Step 1: 读取 TimeApp 实现**

Read: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java`

**Step 2: 重写 getReplyMode 方法**

```java
@Override
public ReplyMode getReplyMode() {
    return ReplyMode.TOPIC;
}
```

**Step 3: 验证编译**

Run: `mvn clean compile -pl feishu-bot-domain`
Expected: BUILD SUCCESS

**Step 4: 提交**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/TimeApp.java
git commit -m "feat: TimeApp 使用话题回复模式"
```

---

## Task 10: 更新配置文件添加示例

**Files:**
- Modify: `feishu-bot-start/src/main/resources/application.yml`

**Step 1: 添加完整配置示例**

```yaml
feishu:
  reply:
    mode: DEFAULT  # 全局默认回复模式: DEFAULT 或 TOPIC
```

**Step 2: 提交**

```bash
git add feishu-bot-start/src/main/resources/application.yml
git commit -m "docs: 更新配置文件示例"
```

---

## Task 11: 集成测试

**Files:**
- Build entire project
- Run application
- Manual testing

**Step 1: 完整构建**

Run: `mvn clean install -Dmaven.test.skip=true`
Expected: BUILD SUCCESS

**Step 2: 启动应用**

```bash
cd feishu-bot-start
LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Step 3: 测试默认回复模式**

1. 发送 `/time`
2. 验证直接回复消息

**Step 4: 测试话题模式**

1. 将 `TimeApp.getReplyMode()` 返回 `ReplyMode.TOPIC`
2. 重新构建并启动
3. 发送 `/time`
4. 验证创建话题
5. 在话题中发送消息
6. 验证自动触发 TimeApp

**Step 5: 提交最终代码**

```bash
cd .worktrees/feature-reply-mode
git status
git add .
git commit -m "test: 完成集成测试"
```

---

## Task 12: 清理和优化

**Files:**
- Review all changes
- Remove debug logs
- Update documentation

**Step 1: 代码审查**

检查所有修改的文件，移除不必要的日志和注释。

**Step 2: 更新文档**

在 `AGENTS.md` 或 `APP_GUIDE.md` 添加回复模式配置说明。

**Step 3: 最终提交**

```bash
git add docs/
git commit -m "docs: 更新文档说明回复模式配置"
```

---

## 验证清单

完成所有任务后，验证以下功能：

- [ ] 全局配置 `feishu.reply.mode` 生效
- [ ] 应用可重写 `getReplyMode()` 覆盖全局配置
- [ ] 应用使用 `DEFAULT` 模式时直接回复
- [ ] 应用使用 `TOPIC` 模式时创建话题
- [ ] 话题中的后续消息自动触发对应应用
- [ ] 话题映射正确保存和查询
- [ ] 话题映射不存在时降级处理
- [ ] 所有编译通过，无警告

---

## 注意事项

1. **TDD 原则**: 虽然项目中目前没有测试用例，但在实施过程中应始终考虑测试需求，为未来测试做好准备。

2. **COLA 架构**: 所有代码修改必须遵循 COLA 分层原则，跨层依赖必须通过接口。

3. **日志记录**: 关键操作（保存映射、查询映射、路由决策）必须记录日志。

4. **错误处理**: 所有外部调用（飞书 API）必须有异常处理和错误日志。

5. **向后兼容**: 保持现有功能不受影响，新功能通过配置可选启用。

---

**总预计时间**: 2-3小时（每个任务 10-15分钟）

**风险点**:
- 飞书话题 API 可能需要额外权限
- topicId 解析可能因 SDK 版本不同而变化
- 内存存储的话题映射会在重启后丢失（未来可升级为数据库）

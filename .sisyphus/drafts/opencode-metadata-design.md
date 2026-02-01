# OpenCode 多轮对话实现方案 v3.0（通用化设计）

## 🎯 核心设计改进

**从特定字段 → 通用 metadata 模式**

### 问题分析（v2.0 设计的缺陷）

```java
// ❌ v2.0 设计 - TopicMapping 耦合了 OpenCode 的特定字段
public class TopicMapping {
    private String topicId;
    private String appId;
    private String sessionId;  // ← 特定于 OpenCode！
    private long createdAt;
    private long lastActiveAt;
}
```

**问题**：
- ❌ `TopicMapping` 不再通用，耦合了 OpenCode 的概念
- ❌ 其他应用（BashApp、TimeApp）也有特殊需求怎么办？
- ❌ 添加新字段需要修改核心实体
- ❌ 违反开放封闭原则

---

## ✅ v3.0 设计 - 通用 metadata 模式

### 核心思想

**TopicMapping 保持通用，所有应用特定的数据都存储在 `metadata` JSON 字段中。**

```java
// ✅ v3.0 设计 - 通用 TopicMapping
public class TopicMapping {
    private String topicId;
    private String appId;
    private String metadata;  // ← JSON 字符串，存储应用特定的任意数据
    private long createdAt;
    private long lastActiveAt;
}
```

---

## 📐 架构设计

### metadata 存储格式

**JSON 结构**：
```json
{
  "opencode": {
    "sessionId": "ses_abc123",
    "lastCommand": "重构 TimeApp",
    "commandCount": 5
  },
  "bash": {
    "workspace": "/workspace/project1",
    "lastCommand": "ls -la",
    "historyEnabled": true
  },
  "time": {
    "format": "yyyy-MM-dd HH:mm:ss",
    "timezone": "Asia/Shanghai"
  }
}
```

**设计原则**：
- 🔑 **按应用 ID 分组**：每个应用拥有独立的命名空间
- 📦 **结构自由**：每个应用定义自己的数据结构
- 🔄 **向后兼容**：添加字段不影响其他应用
- 🎯 **类型安全**：提供强类型的访问工具类

---

## 💻 核心实现

### 1. 通用 TopicMapping 实体

```java
package com.qdw.feishu.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 话题映射领域实体（通用化设计）
 *
 * 使用 metadata JSON 字段存储应用特定的任意数据
 */
@Data
@NoArgsConstructor
public class TopicMapping {

    /** 话题 ID */
    private String topicId;

    /** 应用 ID */
    private String appId;

    /** 元数据（JSON 字符串） */
    private String metadata;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;

    /** 最后活跃时间（毫秒时间戳） */
    private long lastActiveAt;

    /**
     * 创建话题映射（不含元数据）
     */
    public TopicMapping(String topicId, String appId) {
        this(topicId, appId, null);
    }

    /**
     * 创建话题映射（含元数据）
     */
    public TopicMapping(String topicId, String appId, String metadata) {
        this.topicId = topicId;
        this.appId = appId;
        this.metadata = metadata;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * 激活话题映射
     */
    public void activate() {
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * 检查是否有元数据
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}
```

---

### 2. Metadata 工具类（核心）

```java
package com.qdw.feishu.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * TopicMapping Metadata 操作工具类
 *
 * 提供类型安全的 metadata 访问接口
 */
@Slf4j
public class TopicMetadata {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TopicMapping mapping;
    private JsonNode metadataNode;

    private TopicMetadata(TopicMapping mapping) {
        this.mapping = mapping;
        this.metadataNode = parseMetadata(mapping.getMetadata());
    }

    /**
     * 从 TopicMapping 创建 TopicMetadata
     */
    public static TopicMetadata of(TopicMapping mapping) {
        return new TopicMetadata(mapping);
    }

    /**
     * 解析 metadata JSON 字符串
     */
    private JsonNode parseMetadata(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return objectMapper.createObjectNode();
        }

        try {
            return objectMapper.readTree(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse metadata: {}", metadata, e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 获取当前应用的命名空间节点
     */
    private ObjectNode getAppNode() {
        String appId = mapping.getAppId();

        if (metadataNode.isObject()) {
            ObjectNode root = (ObjectNode) metadataNode;

            if (!root.has(appId)) {
                root.setObject(appId);
            }

            return (ObjectNode) root.get(appId);
        }

        return objectMapper.createObjectNode();
    }

    /**
     * 设置字符串值
     *
     * @param key 键（在当前应用命名空间下）
     * @param value 值
     */
    public TopicMetadata set(String key, String value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置整数值
     */
    public TopicMetadata set(String key, int value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置长整型值
     */
    public TopicMetadata set(String key, long value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置布尔值
     */
    public TopicMetadata set(String key, boolean value) {
        getAppNode().put(key, value);
        return this;
    }

    /**
     * 设置任意对象（序列化为 JSON）
     */
    public TopicMetadata set(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            getAppNode().set(key, objectMapper.readTree(json));
            return this;
        } catch (Exception e) {
            log.error("Failed to serialize value for key: {}", key, e);
            return this;
        }
    }

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值，如果不存在返回 Optional.empty()
     */
    public Optional<String> getString(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isTextual()) {
            return Optional.of(appNode.get(key).asText());
        }
        return Optional.empty();
    }

    /**
     * 获取整数值
     */
    public Optional<Integer> getInt(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isInt()) {
            return Optional.of(appNode.get(key).asInt());
        }
        return Optional.empty();
    }

    /**
     * 获取长整型值
     */
    public Optional<Long> getLong(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isLong()) {
            return Optional.of(appNode.get(key).asLong());
        }
        return Optional.empty();
    }

    /**
     * 获取布尔值
     */
    public Optional<Boolean> getBoolean(String key) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key) && appNode.get(key).isBoolean()) {
            return Optional.of(appNode.get(key).asBoolean());
        }
        return Optional.empty();
    }

    /**
     * 获取对象（反序列化）
     *
     * @param key 键
     * @param clazz 目标类型
     * @return 对象，如果不存在或解析失败返回 Optional.empty()
     */
    public <T> Optional<T> getObject(String key, Class<T> clazz) {
        JsonNode appNode = getAppNode();
        if (appNode.has(key)) {
            try {
                return Optional.of(objectMapper.treeToValue(appNode.get(key), clazz));
            } catch (Exception e) {
                log.error("Failed to deserialize value for key: {}", key, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 移除键
     */
    public TopicMetadata remove(String key) {
        getAppNode().remove(key);
        return this;
    }

    /**
     * 检查键是否存在
     */
    public boolean has(String key) {
        return getAppNode().has(key);
    }

    /**
     * 清空当前应用的所有 metadata
     */
    public TopicMetadata clear() {
        getAppNode().removeAll();
        return this;
    }

    /**
     * 将修改保存回 TopicMapping
     *
     * ⚠️ 重要：修改后必须调用此方法，否则不会持久化
     */
    public TopicMapping save() {
        try {
            String json = objectMapper.writeValueAsString(metadataNode);
            mapping.setMetadata(json);
            return mapping;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata", e);
            return mapping;
        }
    }

    /**
     * 获取原始 metadata JSON 字符串
     */
    public String toJson() {
        return mapping.getMetadata();
    }

    /**
     * 获取底层 JsonNode（高级用法）
     */
    public JsonNode getJsonNode() {
        return metadataNode;
    }
}
```

---

### 3. OpenCode 特定元数据模型

```java
package com.qdw.feishu.domain.model.opencode;

import lombok.Data;

/**
 * OpenCode 应用特定的元数据
 *
 * 存储在 TopicMapping.metadata 的 "opencode" 命名空间下
 */
@Data
public class OpenCodeMetadata {

    /** OpenCode 会话 ID */
    private String sessionId;

    /** 最后执行的命令 */
    private String lastCommand;

    /** 命令执行计数 */
    private int commandCount;

    /** 会话创建时间 */
    private long sessionCreatedAt;

    /** 最后活跃时间 */
    private long lastActiveAt;

    /**
     * 创建默认元数据
     */
    public static OpenCodeMetadata create() {
        OpenCodeMetadata metadata = new OpenCodeMetadata();
        metadata.setCommandCount(0);
        metadata.setSessionCreatedAt(System.currentTimeMillis());
        metadata.setLastActiveAt(System.currentTimeMillis());
        return metadata;
    }

    /**
     * 增加命令计数
     */
    public void incrementCommandCount() {
        this.commandCount++;
        this.lastActiveAt = System.currentTimeMillis();
    }
}
```

---

### 4. OpenCodeSessionGateway（基于 metadata）

```java
package com.qdw.feishu.domain.gateway;

/**
 * OpenCode 会话管理 Gateway 接口
 *
 * 基于 TopicMapping.metadata 实现
 */
public interface OpenCodeSessionGateway {

    /**
     * 保存会话 ID
     *
     * @param topicId 话题 ID
     * @param sessionId OpenCode 会话 ID
     */
    void saveSession(String topicId, String sessionId);

    /**
     * 获取会话 ID
     *
     * @param topicId 话题 ID
     * @return 会话 ID，如果不存在返回 Optional.empty()
     */
    java.util.Optional<String> getSessionId(String topicId);

    /**
     * 更新会话 ID
     *
     * @param topicId 话题 ID
     * @param sessionId 新的会话 ID
     */
    void updateSession(String topicId, String sessionId);

    /**
     * 删除会话
     *
     * @param topicId 话题 ID
     */
    void deleteSession(String topicId);

    /**
     * 清除会话（创建新会话时使用）
     *
     * @param topicId 话题 ID
     */
    void clearSession(String topicId);

    /**
     * 获取完整的元数据
     *
     * @param topicId 话题 ID
     * @return 元数据对象
     */
    java.util.Optional<com.qdw.feishu.domain.model.opencode.OpenCodeMetadata> getMetadata(String topicId);

    /**
     * 保存完整元数据
     *
     * @param topicId 话题 ID
     * @param metadata 元数据对象
     */
    void saveMetadata(String topicId, com.qdw.feishu.domain.model.opencode.OpenCodeMetadata metadata);
}
```

---

### 5. OpenCodeSessionGatewayImpl（使用 TopicMetadata 工具）

```java
package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.model.TopicMapping;
import com.qdw.feishu.domain.model.TopicMetadata;
import com.qdw.feishu.domain.model.opencode.OpenCodeMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OpenCode 会话管理实现（基于 TopicMapping.metadata）
 */
@Slf4j
@Component
public class OpenCodeSessionGatewayImpl implements OpenCodeSessionGateway {

    private final TopicMappingGateway topicMappingGateway;

    private static final String KEY_SESSION_ID = "sessionId";
    private static final String KEY_LAST_COMMAND = "lastCommand";
    private static final String KEY_COMMAND_COUNT = "commandCount";
    private static final String KEY_SESSION_CREATED = "sessionCreatedAt";
    private static final String KEY_LAST_ACTIVE = "lastActiveAt";

    public OpenCodeSessionGatewayImpl(TopicMappingGateway topicMappingGateway) {
        this.topicMappingGateway = topicMappingGateway;
    }

    @Override
    public void saveSession(String topicId, String sessionId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            log.warn("话题映射不存在，无法保存会话: topicId={}", topicId);
            return;
        }

        TopicMapping mapping = mappingOpt.get();

        // 使用 TopicMetadata 工具类修改 metadata
        TopicMetadata metadata = TopicMetadata.of(mapping);
        metadata.set(KEY_SESSION_ID, sessionId);
        metadata.set(KEY_LAST_ACTIVE, System.currentTimeMillis());

        // 保存修改
        topicMappingGateway.save(metadata.save());

        log.info("保存会话: topicId={}, sessionId={}", topicId, sessionId);
    }

    @Override
    public Optional<String> getSessionId(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return Optional.empty();
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);

        return metadata.getString(KEY_SESSION_ID);
    }

    @Override
    public void updateSession(String topicId, String sessionId) {
        saveSession(topicId, sessionId);
    }

    @Override
    public void deleteSession(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);
        metadata.remove(KEY_SESSION_ID);

        topicMappingGateway.save(metadata.save());

        log.info("删除会话: topicId={}", topicId);
    }

    @Override
    public void clearSession(String topicId) {
        deleteSession(topicId);
    }

    @Override
    public Optional<OpenCodeMetadata> getMetadata(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return Optional.empty();
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata metadata = TopicMetadata.of(mapping);

        // 从 metadata 中提取所有字段
        OpenCodeMetadata result = new OpenCodeMetadata();

        metadata.getString(KEY_SESSION_ID).ifPresent(result::setSessionId);
        metadata.getString(KEY_LAST_COMMAND).ifPresent(result::setLastCommand);
        metadata.getInt(KEY_COMMAND_COUNT).ifPresentOrElse(
            result::setCommandCount,
            () -> result.setCommandCount(0)
        );
        metadata.getLong(KEY_SESSION_CREATED).ifPresentOrElse(
            result::setSessionCreatedAt,
            () -> result.setSessionCreatedAt(System.currentTimeMillis())
        );
        metadata.getLong(KEY_LAST_ACTIVE).ifPresentOrElse(
            result::setLastActiveAt,
            () -> result.setLastActiveAt(System.currentTimeMillis())
        );

        return Optional.of(result);
    }

    @Override
    public void saveMetadata(String topicId, OpenCodeMetadata metadata) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            log.warn("话题映射不存在，无法保存元数据: topicId={}", topicId);
            return;
        }

        TopicMapping mapping = mappingOpt.get();
        TopicMetadata topicMetadata = TopicMetadata.of(mapping);

        // 保存所有字段
        topicMetadata.set(KEY_SESSION_ID, metadata.getSessionId());
        topicMetadata.set(KEY_LAST_COMMAND, metadata.getLastCommand());
        topicMetadata.set(KEY_COMMAND_COUNT, metadata.getCommandCount());
        topicMetadata.set(KEY_SESSION_CREATED, metadata.getSessionCreatedAt());
        topicMetadata.set(KEY_LAST_ACTIVE, metadata.getLastActiveAt());

        topicMappingGateway.save(topicMetadata.save());

        log.info("保存元数据: topicId={}, metadata={}", topicId, metadata);
    }
}
```

---

### 6. 其他应用示例（展示通用性）

#### BashApp 使用 metadata

```java
package com.qdw.feishu.domain.app;

@Component
public class BashApp implements FishuAppI {

    private final TopicMappingGateway topicMappingGateway;

    @Override
    public String execute(Message message) {
        String topicId = message.getTopicId();

        if (topicId != null) {
            // 保存 Bash 特定的元数据
            Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

            if (mappingOpt.isPresent()) {
                TopicMapping mapping = mappingOpt.get();
                TopicMetadata metadata = TopicMetadata.of(mapping);

                // Bash 特定的字段
                metadata.set("workspace", ".workspace");
                metadata.set("historyEnabled", true);
                metadata.set("lastCommand", command);
                metadata.set("commandCount", metadata.getInt("commandCount").orElse(0) + 1);

                topicMappingGateway.save(metadata.save());
            }
        }

        // ... 执行 bash 命令
    }
}
```

**metadata 中的数据**：
```json
{
  "bash": {
    "workspace": ".workspace",
    "historyEnabled": true,
    "lastCommand": "ls -la",
    "commandCount": 15
  }
}
```

---

## 🎨 使用示例

### OpenCodeApp 中使用

```java
@Component
public class OpenCodeApp implements FishuAppI {

    private final OpenCodeSessionGateway sessionGateway;

    @Override
    public String execute(Message message) {
        String topicId = message.getTopicId();

        // 1. 尝试获取现有会话
        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);

        if (sessionIdOpt.isPresent()) {
            String sessionId = sessionIdOpt.get();
            log.info("继续现有会话: sessionId={}", sessionId);

            // 2. 执行命令
            String result = executeWithSession(message, prompt, sessionId);

            // 3. 更新元数据
            Optional<OpenCodeMetadata> metadataOpt = sessionGateway.getMetadata(topicId);
            if (metadataOpt.isPresent()) {
                OpenCodeMetadata metadata = metadataOpt.get();
                metadata.setLastCommand(prompt);
                metadata.incrementCommandCount();
                sessionGateway.saveMetadata(topicId, metadata);
            }

            return result;
        } else {
            // 创建新会话
            log.info("创建新会话: topicId={}", topicId);
            String result = executeWithSession(message, prompt, null);

            // 提取并保存 sessionId
            String newSessionId = extractSessionId(result);
            sessionGateway.saveSession(topicId, newSessionId);

            return result;
        }
    }
}
```

---

## 📊 metadata 结构示例

### 多应用共存

```json
{
  "opencode": {
    "sessionId": "ses_abc123",
    "lastCommand": "重构 TimeApp",
    "commandCount": 5,
    "sessionCreatedAt": 1736768400000,
    "lastActiveAt": 1736772000000
  },
  "bash": {
    "workspace": "/workspace/feishu-backend",
    "lastCommand": "mvn clean install",
    "historyEnabled": true,
    "commandCount": 42
  },
  "time": {
    "format": "yyyy-MM-dd HH:mm:ss",
    "timezone": "Asia/Shanghai",
    "locale": "zh_CN"
  }
}
```

**查询隔离**：
- `TopicMetadata.of(mapping)` 只返回当前应用（`appId`）的节点
- `opencode` 应用只能看到 `opencode` 命名空间下的数据
- `bash` 应用只能看到 `bash` 命名空间下的数据

---

## ✅ 设计优势

### 1. 真正的通用性
- ✅ `TopicMapping` 不再耦合任何特定应用
- ✅ 每个应用定义自己的数据结构
- ✅ 添加新应用无需修改核心实体

### 2. 类型安全
- ✅ `TopicMetadata` 工具类提供强类型访问
- ✅ 编译时检查，避免运行时错误
- ✅ 支持复杂对象序列化/反序列化

### 3. 向后兼容
- ✅ 添加字段不影响其他应用
- ✅ 可选字段，灵活扩展
- ✅ JSON 格式，易于调试

### 4. 易于测试
- ✅ 可以轻松模拟 metadata
- ✅ 独立测试每个应用的元数据逻辑
- ✅ JSON 格式便于断言

---

## 🚀 实施步骤

### Step 1: 修改 TopicMapping（5分钟）
```java
// 移除 sessionId 字段，只保留 metadata
public class TopicMapping {
    private String topicId;
    private String appId;
    private String metadata;  // 通用 JSON 字段
    private long createdAt;
    private long lastActiveAt;
}
```

### Step 2: 创建 TopicMetadata 工具类（30分钟）
- 实现所有 getter/setter 方法
- 实现 `save()` 方法
- 添加单元测试

### Step 3: 创建 OpenCodeMetadata 模型（10分钟）
- 定义所有字段
- 添加工厂方法

### Step 4: 实现 OpenCodeSessionGatewayImpl（20分钟）
- 基于 TopicMetadata 实现
- 添加完整元数据支持

### Step 5: 更新数据库（5分钟）
```sql
-- 如果之前添加了 session_id 列，删除它
ALTER TABLE topic_mapping DROP COLUMN IF EXISTS session_id;

-- 确保有 metadata 列（TEXT 类型）
-- 如果没有，添加它
ALTER TABLE topic_mapping ADD COLUMN IF NOT EXISTS metadata TEXT;
```

### Step 6: 更新现有代码（15分钟）
- 修改所有访问 `sessionId` 的代码
- 使用 `TopicMetadata` 工具类

---

## 📚 相关模式

### 1. EAV 模式（Entity-Attribute-Value）
```
Entity: TopicMapping
Attribute: metadata.app.opencode.sessionId
Value: "ses_abc123"
```

### 2. 命名空间模式
```
opencode.*
  ├── sessionId
  ├── lastCommand
  └── commandCount

bash.*
  ├── workspace
  └── historyEnabled
```

### 3. Builder 模式（链式调用）
```java
TopicMetadata.of(mapping)
    .set("sessionId", "ses_abc123")
    .set("commandCount", 5)
    .set("lastActive", System.currentTimeMillis())
    .save();
```

---

## 🎯 最佳实践

### 1. 命名约定
```java
// ✅ 好的命名 - 清晰、简洁
metadata.set("sessionId", sessionId);
metadata.set("lastCommand", command);

// ❌ 不好的命名 - 冗余
metadata.set("opencodeSessionId", sessionId);  // 已经在 opencode 命名空间下
```

### 2. 类型选择
```java
// ✅ 使用 Optional 处理可能不存在的值
Optional<String> sessionId = metadata.getString("sessionId");
if (sessionId.isPresent()) {
    // 使用 sessionId
}

// ✅ 提供默认值
int count = metadata.getInt("commandCount").orElse(0);
```

### 3. 批量修改
```java
// ✅ 一次性设置多个值
TopicMetadata metadata = TopicMetadata.of(mapping);
metadata.set("sessionId", sessionId)
        .set("lastCommand", command)
        .set("commandCount", count)
        .set("lastActive", System.currentTimeMillis())
        .save();  // 最后一次性保存
```

---

**创建时间**: 2026-02-01
**版本**: v3.0 - 通用 metadata 设计
**状态**: 设计完成，高度推荐

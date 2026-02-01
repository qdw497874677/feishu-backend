# OpenCode 多轮对话实现方案

## 🎯 核心设计目标

**在飞书话题中实现 OpenCode 的多轮对话，自动保持会话上下文。**

---

## 📐 架构设计

### 方案选择：扩展 TopicMapping 系统

```
现有 TopicMapping:
┌─────────────┬─────────┐
│ topicId     │ appId   │
└─────────────┴─────────┘

扩展后:
┌─────────────┬─────────┬───────────┬────────────┐
│ topicId     │ appId   │ sessionId │ metadata   │
├─────────────┼─────────┼───────────┼────────────┤
│ omt_abc123  │ opencode│ ses_xyz789│ {...}      │
└─────────────┴─────────┴───────────┴────────────┘
```

**为什么选择这个方案？**
- ✅ 复用现有的基础设施
- ✅ 会话与应用绑定，符合业务逻辑
- ✅ 支持迁移到其他应用的多轮对话
- ✅ 持久化机制已经完善（SQLite）

---

## 🏗️ 文件结构

### 需要修改的文件

```
feishu-bot-domain/
├── model/
│   └── TopicMapping.java                    # ⚠️ 修改：添加 sessionId
├── gateway/
│   ├── TopicMappingGateway.java             # ⚠️ 修改：添加 sessionId 查询方法
│   └── OpenCodeSessionGateway.java          # ✨ 新增：会话管理接口
└── app/
    └── OpenCodeApp.java                     # ✨ 新增：支持多轮对话

feishu-bot-infrastructure/
├── gateway/
│   ├── TopicMappingSqliteGateway.java       # ⚠️ 修改：添加 sessionId 列
│   └── OpenCodeSessionGatewayImpl.java      # ✨ 新增：会话管理实现
└── config/
    ├── OpenCodeProperties.java              # ✨ 新增：配置
    └── AsyncConfig.java                     # ⚠️ 修改：添加 opencodeExecutor
```

---

## 💻 核心实现

### 1. 扩展 TopicMapping 实体

```java
package com.qdw.feishu.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 话题映射领域实体（扩展版）
 *
 * 支持保存会话ID，实现多轮对话
 */
@Data
@NoArgsConstructor
public class TopicMapping {

    /** 话题 ID */
    private String topicId;

    /** 应用 ID */
    private String appId;

    /** OpenCode 会话 ID（可选） */
    private String sessionId;

    /** 创建时间（毫秒时间戳） */
    private long createdAt;

    /** 最后活跃时间（毫秒时间戳） */
    private long lastActiveAt;

    /**
     * 创建话题映射（不含会话）
     */
    public TopicMapping(String topicId, String appId) {
        this(topicId, appId, null);
    }

    /**
     * 创建话题映射（含会话）
     */
    public TopicMapping(String topicId, String appId, String sessionId) {
        this.topicId = topicId;
        this.appId = appId;
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * 更新会话 ID
     */
    public void updateSessionId(String sessionId) {
        this.sessionId = sessionId;
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * 检查是否有活跃会话
     */
    public boolean hasActiveSession() {
        return sessionId != null && !sessionId.isEmpty();
    }

    /**
     * 激活话题映射
     */
    public void activate() {
        this.lastActiveAt = System.currentTimeMillis();
    }
}
```

---

### 2. OpenCodeSessionGateway 接口

```java
package com.qdw.feishu.domain.gateway;

import java.util.Optional;

/**
 * OpenCode 会话管理 Gateway 接口
 *
 * 定义会话持久化的抽象
 */
public interface OpenCodeSessionGateway {

    /**
     * 保存会话映射
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
    Optional<String> getSessionId(String topicId);

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
}
```

---

### 3. OpenCodeApp 完整实现

```java
package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.TopicMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * OpenCode 应用 - 支持多轮对话
 */
@Slf4j
@Component
public class OpenCodeApp implements FishuAppI {

    private final OpenCodeGateway openCodeGateway;
    private final FeishuGateway feishuGateway;
    private final OpenCodeSessionGateway sessionGateway;
    private final TopicMappingGateway topicMappingGateway;

    // 同步执行超时阈值（5秒）
    private static final long SYNC_TIMEOUT_MS = 5000;
    // 异步执行阈值（2秒）
    private static final long ASYNC_THRESHOLD_MS = 2000;

    public OpenCodeApp(OpenCodeGateway openCodeGateway,
                       FeishuGateway feishuGateway,
                       OpenCodeSessionGateway sessionGateway,
                       TopicMappingGateway topicMappingGateway) {
        this.openCodeGateway = openCodeGateway;
        this.feishuGateway = feishuGateway;
        this.sessionGateway = sessionGateway;
        this.topicMappingGateway = topicMappingGateway;
    }

    @Override
    public String getAppId() {
        return "opencode";
    }

    @Override
    public String getAppName() {
        return "OpenCode 助手";
    }

    @Override
    public String getDescription() {
        return "通过飞书对话控制 OpenCode，支持多轮对话";
    }

    @Override
    public String getHelp() {
        return "🤖 **OpenCode 助手** - 支持多轮对话\n\n" +
               "📝 **基本命令**：\n" +
               "  `/opencode <提示词>`          - 执行任务（自动保持会话）\n" +
               "  `/opencode new <提示词>`       - 创建新会话并执行\n\n" +
               "🔧 **会话管理**：\n" +
               "  `/opencode session status`    - 查看当前会话信息\n" +
               "  `/opencode session list`      - 查看所有会话\n" +
               "  `/opencode session continue <id>` - 继续指定会话\n\n" +
               "💡 **使用示例**：\n" +
               "  ```
  /opencode 重构 TimeApp
  /opencode 添加单元测试        # 自动继续上一会话
  /opencode new 优化 BashApp    # 创建新会话\n```" +
               "  ```";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("oc", "code");
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;  // 使用话题模式，支持多轮对话
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 3);

        log.info("OpenCodeApp.execute: content='{}'", content);

        // 空命令，返回帮助
        if (parts.length < 2) {
            return getHelp();
        }

        String subCommand = parts[1].toLowerCase();

        // 处理子命令
        switch (subCommand) {
            case "help":
                return getHelp();

            case "new":
                // 创建新会话
                if (parts.length < 3) {
                    return "❌ 用法：`/opencode new <提示词>`\n\n" +
                           "示例：`/opencode new 重构登录模块`";
                }
                String newPrompt = parts[2].trim();
                return executeWithNewSession(message, newPrompt);

            case "session":
                // 会话管理命令
                return handleSessionCommand(parts, message);

            default:
                // 默认：执行命令（自动保持会话）
                String prompt = content.substring(content.indexOf(' ') + 1).trim();
                return executeWithAutoSession(message, prompt);
        }
    }

    /**
     * 处理会话相关命令
     */
    private String handleSessionCommand(String[] parts, Message message) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode session <status|list|continue> [args]`";
        }

        String action = parts[2].toLowerCase();

        switch (action) {
            case "status":
                return getCurrentSessionStatus(message);

            case "list":
                return openCodeGateway.listSessions();

            case "continue":
                if (parts.length < 4) {
                    return "❌ 用法：`/opencode session continue <session_id>`";
                }
                String sessionId = parts[3].trim();
                return executeWithSpecificSession(message, null, sessionId);

            default:
                return "❌ 未知的 session 命令: `" + action + "`\n\n" +
                       "可用命令：`status`, `list`, `continue`";
        }
    }

    /**
     * 获取当前会话状态
     */
    private String getCurrentSessionStatus(Message message) {
        String topicId = message.getTopicId();

        if (topicId == null || topicId.isEmpty()) {
            return "❌ 当前不在话题中，无法查看会话状态";
        }

        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);

        if (sessionIdOpt.isEmpty()) {
            return "📭 当前话题还没有 OpenCode 会话\n\n" +
                   "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        String sessionId = sessionIdOpt.get();
        return "📋 **当前会话信息**\n\n" +
               "  🆔 Session ID: `" + sessionId + "`\n" +
               "  💬 话题 ID: `" + topicId + "`\n" +
               "  ✅ 状态: 活跃\n\n" +
               "💡 继续对话会自动使用此会话";
    }

    /**
     * 执行任务（自动保持会话）
     *
     * - 如果话题有活跃会话，继续使用
     * - 如果没有，创建新会话并保存
     */
    private String executeWithAutoSession(Message message, String prompt) {
        String topicId = message.getTopicId();

        // 如果不在话题中，使用新会话执行
        if (topicId == null || topicId.isEmpty()) {
            log.info("不在话题中，使用临时会话执行");
            return executeOpenCodeTask(message, prompt, null);
        }

        // 查找话题的活跃会话
        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);

        if (sessionIdOpt.isPresent()) {
            String sessionId = sessionIdOpt.get();
            log.info("找到活跃会话，继续使用: sessionId={}", sessionId);
            return executeOpenCodeTask(message, prompt, sessionId);
        } else {
            log.info("话题无活跃会话，创建新会话: topicId={}", topicId);
            return executeWithNewSession(message, prompt);
        }
    }

    /**
     * 使用新会话执行任务
     *
     * - 清除旧会话（如果有）
     * - 执行任务
     * - 保存新会话 ID
     */
    private String executeWithNewSession(Message message, String prompt) {
        String topicId = message.getTopicId();

        // 如果在话题中，清除旧会话
        if (topicId != null && !topicId.isEmpty()) {
            sessionGateway.clearSession(topicId);
            log.info("已清除旧会话: topicId={}", topicId);
        }

        // 执行任务（不指定 sessionID，让 OpenCode 创建新会话）
        String result = executeOpenCodeTask(message, prompt, null);

        // 从结果中提取 sessionID（需要 Gateway 实现）
        // 这里简化处理：假设 Gateway 返回的格式包含 sessionId
        // 实际实现中需要从 JSON 输出中解析

        return result;
    }

    /**
     * 使用指定会话执行任务
     */
    private String executeWithSpecificSession(Message message, String prompt, String sessionId) {
        log.info("使用指定会话执行: sessionId={}", sessionId);

        String topicId = message.getTopicId();

        // 更新会话映射
        if (topicId != null && !topicId.isEmpty()) {
            sessionGateway.saveSession(topicId, sessionId);
            log.info("已更新会话映射: topicId={}, sessionId={}", topicId, sessionId);
        }

        return executeOpenCodeTask(message, prompt, sessionId);
    }

    /**
     * 执行 OpenCode 任务（同步或异步）
     *
     * @param message 消息对象
     * @param prompt 提示词
     * @param sessionId 会话 ID（null 表示新会话）
     * @return 执行结果
     */
    private String executeOpenCodeTask(Message message, String prompt, String sessionId) {
        long startTime = System.nanoTime();

        try {
            // 尝试同步执行（5秒超时）
            String result = openCodeGateway.executeCommand(prompt, sessionId, 5);

            if (result == null) {
                // 执行时间超过5秒，转为异步执行
                log.info("任务执行超过5秒，转为异步执行");
                feishuGateway.sendMessage(message, "⏳ 任务正在执行中，结果将稍后返回...",
                                          message.getTopicId());
                executeOpenCodeAsync(message, prompt, sessionId);
                return null;
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // 如果执行时间超过2秒，先发送"执行中"消息
            if (durationMs > ASYNC_THRESHOLD_MS) {
                feishuGateway.sendMessage(message, "⏳ 任务执行中...",
                                          message.getTopicId());
            }

            // 提取并保存 sessionID
            String extractedSessionId = extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionGateway.saveSession(message.getTopicId(), extractedSessionId);
                log.info("保存会话ID: topicId={}, sessionId={}",
                        message.getTopicId(), extractedSessionId);
            }

            return formatOutput(result, extractedSessionId);

        } catch (Exception e) {
            log.error("OpenCode 执行失败", e);
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
     * 异步执行 OpenCode 任务
     */
    @Async("opencodeExecutor")
    public void executeOpenCodeAsync(Message message, String prompt, String sessionId) {
        try {
            String result = openCodeGateway.executeCommand(prompt, sessionId, 0);  // 0表示无超时限制

            // 提取并保存 sessionID
            String extractedSessionId = extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionGateway.saveSession(message.getTopicId(), extractedSessionId);
            }

            String formatted = formatOutput(result, extractedSessionId);
            feishuGateway.sendMessage(message, formatted, message.getTopicId());

        } catch (Exception e) {
            log.error("异步执行失败", e);
            feishuGateway.sendMessage(message, "❌ 执行失败: " + e.getMessage(),
                                      message.getTopicId());
        }
    }

    /**
     * 从 OpenCode 输出中提取 sessionID
     *
     * TODO: 实际实现需要解析 JSON 输出中的 sessionID
     */
    private String extractSessionId(String output) {
        // 简化实现：从输出中查找 "ses_" 开头的ID
        // 实际应该从 JSON 输出的 sessionID 字段提取
        if (output == null) {
            return null;
        }

        int sessionIndex = output.indexOf("ses_");
        if (sessionIndex != -1) {
            int endIndex = output.indexOfAny(new char[]{' ', '\n', '\r'}, sessionIndex);
            if (endIndex == -1) {
                endIndex = output.length();
            }
            return output.substring(sessionIndex, endIndex);
        }

        return null;
    }

    /**
     * 格式化输出结果
     */
    private String formatOutput(String rawOutput, String sessionId) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "✅ 执行完成，无输出";
        }

        // 截断过长的输出（飞书消息限制）
        int maxLength = 2000;
        String output = rawOutput;

        if (rawOutput.length() > maxLength) {
            output = rawOutput.substring(0, maxLength - 50) + "\n\n...(输出过长，已截断)";
        }

        // 如果有 sessionID，添加提示
        if (sessionId != null && !sessionId.isEmpty()) {
            return output + "\n\n💾 _会话ID: `" + sessionId + "` (已自动保存)_";
        }

        return output;
    }
}
```

---

### 4. OpenCodeSessionGatewayImpl 实现

```java
package com.qdw.feishu.infrastructure.gateway;

import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.model.TopicMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OpenCode 会话管理实现（基于 TopicMapping）
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "opencode.session.storage",
    havingValue = "topic-mapping",
    matchIfMissing = true
)
public class OpenCodeSessionGatewayImpl implements OpenCodeSessionGateway {

    private final TopicMappingGateway topicMappingGateway;

    public OpenCodeSessionGatewayImpl(TopicMappingGateway topicMappingGateway) {
        this.topicMappingGateway = topicMappingGateway;
    }

    @Override
    public void saveSession(String topicId, String sessionId) {
        Optional<TopicMapping> existingOpt = topicMappingGateway.findByTopicId(topicId);

        if (existingOpt.isPresent()) {
            TopicMapping mapping = existingOpt.get();
            mapping.updateSessionId(sessionId);
            topicMappingGateway.save(mapping);
            log.info("更新会话映射: topicId={}, sessionId={}", topicId, sessionId);
        } else {
            log.warn("话题映射不存在，无法保存会话: topicId={}", topicId);
        }
    }

    @Override
    public Optional<String> getSessionId(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isEmpty()) {
            return Optional.empty();
        }

        TopicMapping mapping = mappingOpt.get();
        if (mapping.hasActiveSession()) {
            return Optional.of(mapping.getSessionId());
        }

        return Optional.empty();
    }

    @Override
    public void updateSession(String topicId, String sessionId) {
        saveSession(topicId, sessionId);
    }

    @Override
    public void deleteSession(String topicId) {
        Optional<TopicMapping> mappingOpt = topicMappingGateway.findByTopicId(topicId);

        if (mappingOpt.isPresent()) {
            TopicMapping mapping = mappingOpt.get();
            mapping.setSessionId(null);
            topicMappingGateway.save(mapping);
            log.info("已删除会话: topicId={}", topicId);
        }
    }

    @Override
    public void clearSession(String topicId) {
        deleteSession(topicId);
    }
}
```

---

### 5. 修改 TopicMappingSqliteGateway

```sql
-- 数据库迁移：添加 session_id 列
ALTER TABLE topic_mapping ADD COLUMN session_id TEXT;

-- 创建索引（可选，提高查询性能）
CREATE INDEX idx_topic_mapping_session_id ON topic_mapping(session_id);
```

```java
// TopicMappingSqliteGateway.java 修改

@Override
public void save(TopicMapping mapping) {
    String sql = """
        INSERT INTO topic_mapping (topic_id, app_id, session_id, created_at, last_active_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(topic_id) DO UPDATE SET
            app_id = excluded.app_id,
            session_id = excluded.session_id,
            last_active_at = excluded.last_active_at
        """;

    jdbcTemplate.update(sql,
        mapping.getTopicId(),
        mapping.getAppId(),
        mapping.getSessionId(),
        mapping.getCreatedAt(),
        mapping.getLastActiveAt()
    );
}

@Override
public Optional<TopicMapping> findByTopicId(String topicId) {
    String sql = "SELECT * FROM topic_mapping WHERE topic_id = ?";

    return jdbcTemplate.query(sql,
        new Object[]{topicId},
        (rs) -> {
            if (rs.next()) {
                TopicMapping mapping = new TopicMapping();
                mapping.setTopicId(rs.getString("topic_id"));
                mapping.setAppId(rs.getString("app_id"));
                mapping.setSessionId(rs.getString("session_id"));  // 新增
                mapping.setCreatedAt(rs.getLong("created_at"));
                mapping.setLastActiveAt(rs.getLong("last_active_at"));
                return Optional.of(mapping);
            }
            return Optional.<TopicMapping>empty();
        }
    );
}
```

---

## 📊 完整执行流程

### 场景1：首次使用（创建新会话）

```
用户: /opencode 重构 TimeApp
    ↓
OpenCodeApp.executeWithAutoSession()
    ↓
查找话题会话 → 无
    ↓
executeWithNewSession()
    ↓
openCodeGateway.executeCommand(prompt, null, 5)
    ↓
OpenCode CLI 执行（创建新会话 ses_abc123）
    ↓
返回结果 + extractSessionId()
    ↓
sessionGateway.saveSession(topicId, "ses_abc123")
    ↓
返回结果给用户（显示会话ID）
```

### 场景2：继续对话（自动保持会话）

```
用户: /opencode 添加单元测试
    ↓
OpenCodeApp.executeWithAutoSession()
    ↓
查找话题会话 → 找到 ses_abc123
    ↓
executeOpenCodeTask(prompt, "ses_abc123")
    ↓
openCodeGateway.executeCommand(prompt, "ses_abc123", 5)
    ↓
OpenCode CLI 执行（继续会话 ses_abc123）
    ↓
返回结果
    ↓
返回给用户
```

### 场景3：创建新会话（显式）

```
用户: /opencode new 优化 BashApp
    ↓
OpenCodeApp.executeWithNewSession()
    ↓
sessionGateway.clearSession(topicId)  # 清除旧会话
    ↓
openCodeGateway.executeCommand(prompt, null, 5)
    ↓
OpenCode CLI 执行（创建新会话 ses_def456）
    ↓
sessionGateway.saveSession(topicId, "ses_def456")
    ↓
返回结果
```

---

## 🔄 配置文件

### application.yml

```yaml
# OpenCode 配置
opencode:
  executable-path: /usr/bin/opencode
  default-timeout: 30
  max-output-length: 2000
  async-enabled: true
  session:
    storage: topic-mapping  # 存储方式：topic-mapping（默认）| standalone
```

---

## 📝 使用示例

### 示例1：完整的开发流程

```
👤 用户: /opencode 重构 TimeApp，添加日期验证

🤖 机器人:
✅ 已完成 TimeApp 重构

主要变更：
1. 添加 DateValidator 类
2. 集成到 TimeApp.execute()
3. 添加单元测试建议

💾 会话ID: ses_abc123 (已自动保存)

---

👤 用户: /opencode 编写单元测试

🤖 机器人:
✅ 已创建单元测试 TimeAppTest.java

测试用例：
✓ testFormatTime_validDate()
✓ testFormatTime_invalidDate()
✓ testFormatTime_nullDate()

💾 会话ID: ses_abc123 (继续上一会话)

---

👤 用户: /opencode 运行测试验证

🤖 机器人:
✅ 测试通过！

Running 3 tests:
✓ testFormatTime_validDate() - PASSED
✓ testFormatTime_invalidDate() - PASSED
✓ testFormatTime_nullDate() - PASSED

All tests passed! (3/3)

💾 会话ID: ses_abc123 (继续上一会话)
```

### 示例2：查看会话状态

```
👤 用户: /opencode session status

🤖 机器人:
📋 当前会话信息

  🆔 Session ID: ses_abc123
  💬 话题 ID: omt_xyz789
  ✅ 状态: 活跃

💡 继续对话会自动使用此会话
```

### 示例3：创建新会话

```
👤 用户: /opencode new 分析 BashApp 的性能瓶颈

🤖 机器人:
⏳ 任务执行中...

✅ BashApp 性能分析完成

发现的问题：
1. 同步执行阻塞主线程（已解决）
2. 历史记录未缓存（建议优化）
3. 白名单验证可优化（可选改进）

建议优化方案：
[详细建议...]

💾 会话ID: ses_def456 (新会话)
```

---

## 🚀 实施步骤

### Step 1: 数据库迁移（5分钟）
```bash
# 连接到 SQLite 数据库
sqlite3 data/feishu-topic-mappings.db

# 添加 session_id 列
ALTER TABLE topic_mapping ADD COLUMN session_id TEXT;

# 验证
.schema topic_mapping
```

### Step 2: 修改领域层（15分钟）
- [ ] 修改 `TopicMapping.java` - 添加 `sessionId` 字段
- [ ] 创建 `OpenCodeSessionGateway.java` 接口

### Step 3: 修改基础设施层（20分钟）
- [ ] 修改 `TopicMappingSqliteGateway.java` - 支持 `sessionId`
- [ ] 创建 `OpenCodeSessionGatewayImpl.java` 实现
- [ ] 修改数据库访问代码

### Step 4: 创建应用（30分钟）
- [ ] 创建 `OpenCodeApp.java`
- [ ] 实现多轮对话逻辑
- [ ] 实现会话管理命令

### Step 5: 配置和测试（15分钟）
- [ ] 修改 `AsyncConfig.java` - 添加 `opencodeExecutor`
- [ ] 创建 `OpenCodeProperties.java`
- [ ] 更新 `application.yml`
- [ ] 测试基本功能
- [ ] 测试多轮对话

---

## ⚠️ 注意事项

### 会话ID提取

当前 `extractSessionId()` 是简化实现，实际应该从 OpenCode 的 JSON 输出中提取。

**改进方案**：
```java
private String extractSessionId(String jsonOutput) {
    // 从 JSON 输出中解析 sessionID
    String[] lines = jsonOutput.split("\n");
    for (String line : lines) {
        try {
            JsonNode node = objectMapper.readTree(line);
            if (node.has("sessionID")) {
                return node.get("sessionID").asText();
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
    }
    return null;
}
```

### 会话过期清理

建议添加定期清理过期会话的机制：

```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void cleanupExpiredSessions() {
    // 清理7天未活跃的会话
    long expirationTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
    // 实现清理逻辑
}
```

---

## 📚 参考资料

- [OpenCode CLI 文档](https://opencode.ai/docs/cli/)
- [COLA 架构规范](../../AGENTS.md)
- [SQLite 持久化](../../docs/SQLITE-PERSISTENCE.md)

---

**创建时间**: 2026-02-01
**版本**: v2.0 - 支持多轮对话
**状态**: 设计完成，待实施

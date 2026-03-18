# 会话管理抽象设计文档

**日期**: 2026-03-18  
**状态**: 已确认（经 @oracle 审查，已解决泛型问题）  
**影响范围**: domain, infrastructure

---

## 1. 概述

### 1.1 目标

设计一个通用的会话管理抽象，支持以下场景：
- **AI Agent 会话**：多轮对话上下文保持（如 OpenCode）
- **游戏会话**：游戏状态保存（如猜数字、成语接龙）
- **工作流/向导**：多步骤配置向导

### 1.2 设计原则

1. **一个应用可以开启多个会话**：同一话题下，应用可以有多个会话实例
2. **混合生命周期管理**：框架提供钩子和默认策略，应用可覆盖
3. **复用现有存储**：基于 TopicMapping.metadata，最小改动
4. **完整状态机**：支持 CREATED → ACTIVE → IDLE → EXPIRED → TERMINATED
5. **混合数据结构**：基础字段强类型 + 扩展数据 JSON
6. **类型安全**：使用 TypeToken 模式解决泛型类型擦除问题（见 4.3 节）

### 1.3 泛型问题解决方案

**问题**：`AppSessionGateway<T>` 类型擦除导致 Spring Bean 注册冲突

**解决方案**：使用 **非泛型接口 + TypeToken** 模式

```java
// 非泛型接口，避免 Spring Bean 冲突
public interface AppSessionGateway {
    
    // 使用 TypeToken 保留类型信息
    <T> String createSession(String appId, String topicId, T data, TypeToken<T> typeToken);
    <T> Optional<AppSession<T>> getSession(String appId, String topicId, String sessionId, TypeToken<T> typeToken);
}

// TypeToken 实现（参考 Gson TypeToken）
public abstract class TypeToken<T> {
    private final Type type;
    
    protected TypeToken() {
        this.type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
    
    public Type getType() { return type; }
}

// 应用使用方式
public class GameApp {
    // 定义 TypeToken
    private static final TypeToken<GameData> GAME_DATA_TYPE = new TypeToken<GameData>() {};
    
    public void example(String topicId) {
        GameData data = new GameData();
        String sessionId = sessionGateway.createSession(
            "game", topicId, data, GAME_DATA_TYPE
        );
        
        Optional<AppSession<GameData>> session = sessionGateway.getSession(
            "game", topicId, sessionId, GAME_DATA_TYPE
        );
    }
}
```

**优势**：
- 保留类型安全
- 避免 Spring Bean 冲突
- 运行时可获取泛型类型信息
- 与 Gson/Jackson 等库兼容

---

## 2. 核心模型

### 2.1 SessionContext（原 TopicMapping）

```
┌─────────────────────────────────────────────────────────┐
│                   SessionContext                        │
├─────────────────────────────────────────────────────────┤
│ topicId: String              # 飞书话题 ID              │
│ appId: String                # 当前绑定的应用           │
│ createdAt: long              # 创建时间                 │
│ lastActiveAt: long           # 最后活跃时间             │
│ metadata: String             # JSON（包含所有应用的会话）│
└─────────────────────────────────────────────────────────┘
```

### 2.2 AppSession

```
┌─────────────────────────────────────────────────────────┐
│                     AppSession<T>                       │
├─────────────────────────────────────────────────────────┤
│ sessionId: String            # 会话唯一标识             │
│ appId: String                # 所属应用                 │
│ topicId: String              # 绑定的话题               │
│ state: SessionState          # 会话状态                 │
│ createdAt: long              # 创建时间                 │
│ lastActiveAt: long           # 最后活跃时间             │
│ expiresAt: Long              # 过期时间（可选）         │
│ data: T                      # 应用特定数据（泛型）     │
└─────────────────────────────────────────────────────────┘
```

### 2.3 SessionState 状态机

```
                    ┌──────────────┐
                    │   CREATED    │  创建
                    └──────┬───────┘
                           │ activate()
                           ▼
                    ┌──────────────┐
           idle()  │              │  timeout()
        ┌─────────►│    ACTIVE    │◄─────────┐
        │          │              │          │
        │          └──────┬───────┘          │
        │                 │                  │
        │    reactivate() │                  │
        │                 ▼                  │
        │          ┌──────────────┐          │
        └──────────│     IDLE     │──────────┘
                   └──────┬───────┘
                          │
                          │ terminate()
                          ▼
                   ┌──────────────┐
                   │  TERMINATED  │  终止（不可恢复）
                   └──────────────┘
                          
        或从任意状态 ──expire()──► EXPIRED
```

**状态说明：**

| 状态 | 说明 | 可转换到 |
|------|------|----------|
| CREATED | 已创建，未激活 | ACTIVE |
| ACTIVE | 活跃中 | IDLE, TERMINATED, EXPIRED |
| IDLE | 空闲（可恢复） | ACTIVE, TERMINATED, EXPIRED |
| EXPIRED | 已过期 | TERMINATED |
| TERMINATED | 已终止（不可恢复） | - |
| CUSTOM_* | 应用自定义状态 | 由应用定义 |

---

## 3. 存储结构

### 3.1 SessionContext.metadata JSON 结构

```json
{
  "opencode": {
    "sessions": [
      {
        "sessionId": "ses_001",
        "state": "ACTIVE",
        "createdAt": 1704067200000,
        "lastActiveAt": 1704070800000,
        "expiresAt": null,
        "version": 3,
        "data": {
          "externalSessionId": "oc_abc123",
          "projectPath": "/root/workspace/feishu-backend",
          "messageCount": 5
        }
      },
      {
        "sessionId": "ses_002",
        "state": "IDLE",
        "createdAt": 1704080000000,
        "lastActiveAt": 1704085000000,
        "expiresAt": null,
        "version": 1,
        "data": {
          "externalSessionId": "oc_def456",
          "projectPath": "/root/workspace/other-project",
          "messageCount": 3
        }
      }
    ],
    "activeSessionId": "ses_001"
  },
  "game": {
    "sessions": [
      {
        "sessionId": "game_001",
        "state": "TERMINATED",
        "createdAt": 1704060000000,
        "lastActiveAt": 1704063000000,
        "expiresAt": null,
        "version": 5,
        "data": {
          "score": 85,
          "level": 5,
          "result": "win"
        }
      },
      {
        "sessionId": "game_002",
        "state": "PLAYING",
        "createdAt": 1704070000000,
        "lastActiveAt": 1704071000000,
        "expiresAt": 1704074000000,
        "version": 2,
        "data": {
          "score": 30,
          "level": 2,
          "answer": "42"
        }
      }
    ],
    "activeSessionId": "game_002"
  }
}
```

### 3.2 关键设计点

1. **sessions 数组**：存储该应用的所有会话（历史 + 当前）
2. **activeSessionId**：指向当前活跃的会话，快速访问
3. **data 字段**：应用自定义的强类型数据
4. **命名空间隔离**：每个应用在自己的 appId 命名空间下
5. **version 字段**：乐观锁版本号，每次更新递增，解决并发冲突

---

## 4. Gateway 接口设计

### 4.1 AppSessionGateway（核心接口）

> **设计说明**：使用 `TypeToken<T>` 解决泛型类型擦除问题，避免 Spring Bean 冲突。

```java
/**
 * 通用会话管理 Gateway 接口
 * 
 * 使用 TypeToken<T> 解决泛型类型擦除问题：
 * - 避免不同 T 类型的 Bean 注册冲突
 * - 运行时保留泛型类型信息
 * - 支持类型安全的序列化/反序列化
 */
public interface AppSessionGateway {

    // ========== 会话创建 ==========
    
    /**
     * 创建新会话（自动生成 sessionId）
     * @return 新会话的 sessionId
     */
    <T> String createSession(String appId, String topicId, T data, TypeToken<T> typeToken);
    
    /**
     * 使用自定义 sessionId 创建会话
     */
    <T> String createSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken);

    // ========== 会话查询 ==========
    
    /**
     * 获取当前活跃会话
     */
    <T> Optional<AppSession<T>> getActiveSession(String appId, String topicId, TypeToken<T> typeToken);
    
    /**
     * 获取指定会话
     */
    <T> Optional<AppSession<T>> getSession(String appId, String topicId, String sessionId, TypeToken<T> typeToken);
    
    /**
     * 获取应用在某话题下的所有会话（仅返回基础信息，不反序列化 data）
     */
    List<AppSessionInfo> listSessions(String appId, String topicId);
    
    /**
     * 获取应用在某话题下的活跃会话数量
     */
    int countActiveSessions(String appId, String topicId);

    // ========== 会话更新 ==========
    
    /**
     * 更新会话数据（带乐观锁）
     * @throws OptimisticLockException 当版本冲突时抛出
     */
    <T> void updateSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken, long version);
    
    /**
     * 更新会话状态（带乐观锁）
     */
    void updateState(String appId, String topicId, String sessionId, SessionState state, long version);
    
    /**
     * 设置活跃会话（切换当前会话）
     */
    void setActiveSession(String appId, String topicId, String sessionId);
    
    /**
     * 激活会话（IDLE → ACTIVE）
     */
    void activateSession(String appId, String topicId, String sessionId);
    
    /**
     * 休眠会话（ACTIVE → IDLE）
     */
    void idleSession(String appId, String topicId, String sessionId);

    // ========== 会话删除 ==========
    
    /**
     * 删除指定会话
     */
    void deleteSession(String appId, String topicId, String sessionId);
    
    /**
     * 终止会话（任意状态 → TERMINATED）
     */
    void terminateSession(String appId, String topicId, String sessionId);
    
    /**
     * 清除所有已过期/已终止的会话
     * @return 清除的会话数量
     */
    int cleanupSessions(String appId, String topicId);

    // ========== 生命周期钩子（由具体实现类覆盖）==========
    
    /**
     * 会话即将过期的回调
     */
    default void onSessionExpiring(AppSessionInfo session) {}
    
    /**
     * 会话已终止的回调
     */
    default void onSessionTerminated(AppSessionInfo session) {}
    
    /**
     * 会话状态变更的回调
     */
    default void onStateChanged(AppSessionInfo session, SessionState oldState, SessionState newState) {}
}
```

### 4.2 辅助类

```java
/**
 * 会话基础信息（不含泛型 data，用于列表查询）
 */
@Data
public class AppSessionInfo {
    private String sessionId;
    private String appId;
    private String topicId;
    private SessionState state;
    private long createdAt;
    private long lastActiveAt;
    private Long expiresAt;
    private long version;           // 乐观锁版本号
}

/**
 * 完整会话实体（含泛型 data）
 */
@Data
public class AppSession<T> extends AppSessionInfo {
    private T data;                 // 应用特定数据
}

/**
 * 类型令牌（解决泛型类型擦除）
 */
public abstract class TypeToken<T> {
    private final Type type;
    
    protected TypeToken() {
        this.type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }
    
    public Type getType() {
        return type;
    }
    
    // 使用示例
    public static final TypeToken<GameData> GAME_DATA = new TypeToken<GameData>() {};
    public static final TypeToken<OpenCodeData> OPENCODE_DATA = new TypeToken<OpenCodeData>() {};
}
```
```

### 4.2 SessionContextGateway（原 TopicMappingGateway）

```java
/**
 * 会话上下文持久化接口
 */
public interface SessionContextGateway {
    
    /**
     * 保存会话上下文
     */
    void save(SessionContext context);
    
    /**
     * 根据话题 ID 查找
     */
    Optional<SessionContext> findByTopicId(String topicId);
    
    /**
     * 删除会话上下文
     */
    void delete(String topicId);
    
    /**
     * 查找所有过期的上下文（用于清理）
     */
    List<SessionContext> findExpired(long expireBefore);
}
```

---

## 5. 生命周期配置

### 5.1 SessionConfig

```java
@Data
@Builder
public class SessionConfig {
    
    /** 会话超时时间（毫秒），从 lastActiveAt 计算 */
    @Builder.Default
    private long timeoutMs = 30 * 60 * 1000L;  // 默认 30 分钟
    
    /** 最大会话数（历史会话保留上限） */
    @Builder.Default
    private int maxSessions = 10;
    
    /** 是否自动清理过期会话 */
    @Builder.Default
    private boolean autoCleanup = true;
    
    /** 清理间隔（毫秒） */
    @Builder.Default
    private long cleanupIntervalMs = 60 * 60 * 1000L;  // 默认 1 小时
    
    /** 自定义状态（如 PLAYING, PAUSED） */
    @Builder.Default
    private Set<String> customStates = Collections.emptySet();
}
```

### 5.2 FishuAppI 扩展

```java
public interface FishuAppI {
    
    // ... 现有方法 ...
    
    /**
     * 获取会话配置（可选实现）
     * 
     * @return 会话配置，默认返回 SessionConfig.builder().build()
     */
    default SessionConfig getSessionConfig() {
        return SessionConfig.builder().build();
    }
}
```

---

## 6. 实现架构

### 6.1 包结构

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── session/                          # 会话核心模块
│   ├── AppSession.java               # 会话实体
│   ├── SessionState.java             # 状态枚举
│   ├── SessionConfig.java            # 配置类
│   └── SessionIdGenerator.java       # ID 生成器接口
│
├── gateway/
│   ├── AppSessionGateway.java        # 通用会话接口（新增）
│   └── SessionContextGateway.java    # 上下文持久化（重命名）
│
└── model/
    ├── SessionContext.java           # 上下文实体（重命名）
    └── SessionMetadata.java          # 元数据工具（重命名）

feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── gateway/
│   ├── AppSessionGatewayImpl.java    # 通用会话实现（新增）
│   └── SessionContextSqliteGateway.java  # SQLite 实现（重命名）
│
└── session/
    └── DefaultSessionIdGenerator.java    # 默认 ID 生成器
```

### 6.2 依赖关系

```
┌─────────────────────────────────────────────────────────┐
│                      应用层                             │
│   OpenCodeApp, GameApp, QuizApp...                      │
└─────────────────────┬───────────────────────────────────┘
                      │ 依赖
                      ▼
┌─────────────────────────────────────────────────────────┐
│                    Domain 层                            │
│  ┌─────────────────────┐  ┌─────────────────────────┐  │
│  │ AppSessionGateway<T>│  │ AppSession, SessionState│  │
│  └──────────┬──────────┘  └─────────────────────────┘  │
│             │                                           │
│             │ 委托                                      │
│             ▼                                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │ SessionContextGateway                            │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                Infrastructure 层                        │
│  SessionContextSqliteGateway                            │
└─────────────────────────────────────────────────────────┘
```

---

## 7. 使用示例

### 7.1 创建游戏应用

```java
@Component
public class GameApp implements FishuAppI {

    private final AppSessionGateway sessionGateway;
    
    // 1. 定义会话数据类型
    @Data
    public static class GameData {
        private int score;
        private int level;
        private String answer;
    }
    
    // 2. 定义类型令牌
    private static final TypeToken<GameData> GAME_DATA_TYPE = new TypeToken<GameData>() {};
    
    // 3. 配置会话参数
    @Override
    public SessionConfig getSessionConfig() {
        return SessionConfig.builder()
            .timeoutMs(5 * 60 * 1000L)          // 5 分钟超时
            .maxSessions(5)                      // 最多保留 5 局
            .customStates(Set.of("PLAYING", "PAUSED", "GAME_OVER"))
            .build();
    }
    
    // 4. 开始新游戏
    private String startNewGame(String topicId) {
        GameData data = new GameData();
        data.setScore(0);
        data.setLevel(1);
        data.setAnswer(generateAnswer());
        
        String sessionId = sessionGateway.createSession(
            getAppId(), topicId, data, GAME_DATA_TYPE
        );
        return "游戏开始！会话ID: " + sessionId;
    }
    
    // 5. 继续游戏（带乐观锁）
    private String makeGuess(String topicId, String guess) {
        Optional<AppSession<GameData>> optSession = 
            sessionGateway.getActiveSession(getAppId(), topicId, GAME_DATA_TYPE);
            
        if (optSession.isEmpty()) {
            return "游戏已结束，请发送 /game start 开始新游戏";
        }
        
        AppSession<GameData> session = optSession.get();
        GameData data = session.getData();
        
        // 处理猜测逻辑...
        if (guess.equals(data.getAnswer())) {
            data.setScore(data.getScore() + 100);
            sessionGateway.terminateSession(getAppId(), topicId, session.getSessionId());
            return "恭喜！答案正确！得分: " + data.getScore();
        }
        
        // 更新会话（带版本号，乐观锁）
        try {
            sessionGateway.updateSession(
                getAppId(), topicId, session.getSessionId(), 
                data, GAME_DATA_TYPE, session.getVersion()
            );
        } catch (OptimisticLockException e) {
            return "游戏状态已更新，请重试";
        }
        
        return "继续努力！当前得分: " + data.getScore();
    }
    
    // 6. 查看历史游戏
    private String showHistory(String topicId) {
        List<AppSessionInfo> sessions = 
            sessionGateway.listSessions(getAppId(), topicId);
        
        StringBuilder sb = new StringBuilder("历史游戏记录:\n");
        for (AppSessionInfo s : sessions) {
            sb.append(String.format("- %s: 状态 %s, 创建于 %s\n",
                s.getSessionId(), s.getState(), 
                new Date(s.getCreatedAt())));
        }
        return sb.toString();
    }
}
```

### 7.2 OpenCode 迁移后

```java
@Component
public class OpenCodeApp implements FishuAppI {

    private final AppSessionGateway sessionGateway;
    
    // 类型令牌常量
    private static final TypeToken<OpenCodeData> OPENCODE_DATA = 
        new TypeToken<OpenCodeData>() {};
    
    @Data
    public static class OpenCodeData {
        private String externalSessionId;  // OpenCode 系统的 session
        private String projectPath;
        private int messageCount;
    }
    
    @Override
    public SessionConfig getSessionConfig() {
        return SessionConfig.builder()
            .timeoutMs(24 * 60 * 60 * 1000L)  // 24 小时
            .maxSessions(20)
            .build();
    }
    
    // 绑定外部会话
    private void bindSession(String topicId, String externalSessionId, String projectPath) {
        OpenCodeData data = new OpenCodeData();
        data.setExternalSessionId(externalSessionId);
        data.setProjectPath(projectPath);
        data.setMessageCount(0);
        
        sessionGateway.createSession(getAppId(), topicId, data, OPENCODE_DATA);
    }
    
    // 获取当前会话
    private Optional<AppSession<OpenCodeData>> getCurrentSession(String topicId) {
        return sessionGateway.getActiveSession(getAppId(), topicId, OPENCODE_DATA);
    }
}
```

---

## 8. 迁移清单

### 8.1 文件变更

| 操作 | 旧文件 | 新文件 |
|------|--------|--------|
| 重命名 | `TopicMapping.java` | `SessionContext.java` |
| 重命名 | `TopicMappingGateway.java` | `SessionContextGateway.java` |
| 重命名 | `TopicMetadata.java` | `SessionMetadata.java` |
| 重命名 | `TopicMappingSqliteGateway.java` | `SessionContextSqliteGateway.java` |
| 重命名 | `TopicMappingGatewayImpl.java` | `SessionContextGatewayImpl.java` |
| 重构 | `OpenCodeSessionGateway.java` | `AppSessionGateway.java`（使用 TypeToken） |
| 重构 | `OpenCodeSessionGatewayImpl.java` | `AppSessionGatewayImpl.java`（通用实现） |
| 删除 | `OpenCodeMetadata.java` | 数据合并到各应用内部 |
| 新增 | - | `AppSession.java` |
| 新增 | - | `AppSessionInfo.java` |
| 新增 | - | `SessionState.java` |
| 新增 | - | `SessionConfig.java` |
| 新增 | - | `TypeToken.java` |
| 新增 | - | `OptimisticLockException.java` |

### 8.2 调用方修改

| 文件 | 修改内容 |
|------|----------|
| `BotMessageService.java` | TopicMappingGateway → SessionContextGateway |
| `OpenCodeApp.java` | 使用 AppSessionGateway<OpenCodeData> |
| `OpenCodeSessionManager.java` | 使用 AppSessionGateway |
| `OpenCodeTaskExecutor.java` | 适配新接口 |
| `FishuAppI.java` | 添加 getSessionConfig() 默认方法 |

### 8.3 数据库兼容

```sql
-- 表结构不变，只改类名
-- topic_mapping 表保持不变
CREATE TABLE topic_mapping (
    topic_id TEXT PRIMARY KEY,
    app_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL,
    metadata TEXT
);
```

### 8.4 预期改动量

| 类型 | 数量 |
|------|------|
| 新增文件 | ~8 个 |
| 重命名文件 | ~7 个 |
| 删除文件 | ~3 个 |
| 修改文件 | ~10 个 |
| 预计工作量 | 1-2 天 |

---

## 9. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 数据迁移 | 现有 OpenCode 会话数据格式变化 | 兼容旧格式，渐进迁移 |
| 接口变化 | 调用方需要修改 | 直接修改，一次性完成 |
| 状态机复杂度 | 状态转换可能出错 | 完善单元测试，覆盖所有转换路径 |
| 并发冲突 | 读-改-写竞态条件 | 乐观锁（version 字段），冲突时抛出 OptimisticLockException |
| JSON 膨胀 | metadata 字段过大 | maxSessions 限制历史会话数量，定期 cleanup |

### 9.1 并发安全设计

```java
// 更新操作必须携带版本号
public <T> void updateSession(String appId, String topicId, String sessionId, 
                               T data, TypeToken<T> typeToken, long version) {
    // 1. 读取当前会话
    AppSession<T> current = findSession(appId, topicId, sessionId);
    
    // 2. 版本检查（乐观锁）
    if (current.getVersion() != version) {
        throw new OptimisticLockException(
            "Session version conflict: expected=" + version + 
            ", actual=" + current.getVersion()
        );
    }
    
    // 3. 更新数据，版本号 +1
    current.setData(data);
    current.setVersion(version + 1);
    current.setLastActiveAt(System.currentTimeMillis());
    
    // 4. 持久化
    save(appId, topicId, current);
}
```

### 9.2 错误处理

```java
// 应用层处理并发冲突
try {
    sessionGateway.updateSession(appId, topicId, sessionId, data, typeToken, session.getVersion());
} catch (OptimisticLockException e) {
    // 方案 1: 提示用户重试
    return "会话已被修改，请重试";
    
    // 方案 2: 自动合并（如果业务允许）
    // AppSession<GameData> latest = sessionGateway.getSession(...);
    // mergedData = merge(latest.getData(), data);
    // sessionGateway.updateSession(..., mergedData, latest.getVersion());
}
```

---

## 10. 后续扩展

1. **会话事件**：添加 `SessionEventPublisher`，支持会话状态变更通知
2. **会话统计**：添加会话时长、活跃度统计
3. **分布式存储**：支持 Redis 等外部存储
4. **会话恢复**：支持从历史会话恢复上下文

---

**最后更新**: 2026-03-18

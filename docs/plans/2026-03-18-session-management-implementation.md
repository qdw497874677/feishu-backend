# Session Management Abstraction Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现通用的会话管理抽象，支持 AI Agent、游戏、工作流等多种场景

**Architecture:** 使用 TypeToken 解决泛型类型擦除，乐观锁解决并发冲突，复用 TopicMapping.metadata 存储

**Tech Stack:** Java 17, Spring Boot, Lombok, Jackson, SQLite

---

## Prerequisites

- [ ] 设计文档已确认: `docs/plans/2026-03-18-session-management-design.md`
- [ ] 当前在 main 分支工作

---

## Phase 1: Core Session Classes (Domain Layer)

### Task 1.1: Create SessionState enum

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/SessionState.java`

**Step 1: Write the enum**

```java
package com.qdw.feishu.domain.session;

/**
 * 会话状态枚举
 */
public enum SessionState {
    
    CREATED,      // 已创建，未激活
    ACTIVE,       // 活跃中
    IDLE,         // 空闲（可恢复）
    EXPIRED,      // 已过期
    TERMINATED;   // 已终止（不可恢复）
    
    public boolean canTransitionTo(SessionState target) {
        return switch (this) {
            case CREATED -> target == ACTIVE || target == TERMINATED || target == EXPIRED;
            case ACTIVE -> target == IDLE || target == TERMINATED || target == EXPIRED;
            case IDLE -> target == ACTIVE || target == TERMINATED || target == EXPIRED;
            case EXPIRED -> target == TERMINATED;
            case TERMINATED -> false;
        };
    }
    
    public boolean isActive() {
        return this == ACTIVE;
    }
    
    public boolean isRecoverable() {
        return this == IDLE || this == ACTIVE;
    }
    
    public boolean isFinished() {
        return this == TERMINATED || this == EXPIRED;
    }
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/SessionState.java
git commit -m "feat(session): add SessionState enum"
```

---

### Task 1.2: Create SessionConfig

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/SessionConfig.java`

**Step 1: Write the config class**

```java
package com.qdw.feishu.domain.session;

import lombok.Builder;
import lombok.Data;
import java.util.Collections;
import java.util.Set;

@Data
@Builder
public class SessionConfig {
    
    @Builder.Default
    private long timeoutMs = 30 * 60 * 1000L;  // 30 minutes
    
    @Builder.Default
    private int maxSessions = 10;
    
    @Builder.Default
    private boolean autoCleanup = true;
    
    @Builder.Default
    private Set<String> customStates = Collections.emptySet();
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/SessionConfig.java
git commit -m "feat(session): add SessionConfig"
```

---

### Task 1.3: Create TypeToken

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/TypeToken.java`

**Step 1: Write the TypeToken class**

```java
package com.qdw.feishu.domain.session;

import lombok.Getter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 类型令牌，解决泛型类型擦除问题
 */
@Getter
public abstract class TypeToken<T> {
    
    private final Type type;
    
    protected TypeToken() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        } else {
            throw new IllegalArgumentException("TypeToken must be created with type parameter");
        }
    }
    
    public static <T> TypeToken<T> of(Class<T> clazz) {
        return new TypeToken<T>() {};
    }
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/TypeToken.java
git commit -m "feat(session): add TypeToken for generic type retention"
```

---

### Task 1.4: Create OptimisticLockException

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/OptimisticLockException.java`

**Step 1: Write the exception**

```java
package com.qdw.feishu.domain.session;

import com.alibaba.cola.exception.BizException;

/**
 * 乐观锁冲突异常
 */
public class OptimisticLockException extends BizException {
    
    public OptimisticLockException(String message) {
        super("OPTIMISTIC_LOCK_CONFLICT", message);
    }
    
    public static OptimisticLockException versionMismatch(long expected, long actual) {
        return new OptimisticLockException(
            String.format("Version mismatch: expected=%d, actual=%d", expected, actual)
        );
    }
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/OptimisticLockException.java
git commit -m "feat(session): add OptimisticLockException"
```

---

### Task 1.5: Create AppSessionInfo

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/AppSessionInfo.java`

**Step 1: Write the class**

```java
package com.qdw.feishu.domain.session;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话基础信息（不含泛型 data，用于列表查询）
 */
@Data
@NoArgsConstructor
public class AppSessionInfo {
    
    private String sessionId;
    private String appId;
    private String topicId;
    private SessionState state;
    private long createdAt;
    private long lastActiveAt;
    private Long expiresAt;
    private long version = 1;
    
    public AppSessionInfo(String sessionId, String appId, String topicId) {
        this.sessionId = sessionId;
        this.appId = appId;
        this.topicId = topicId;
        this.state = SessionState.CREATED;
        this.createdAt = System.currentTimeMillis();
        this.lastActiveAt = System.currentTimeMillis();
    }
    
    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
    }
    
    public void incrementVersion() {
        this.version++;
    }
    
    public boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() > expiresAt;
    }
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/AppSessionInfo.java
git commit -m "feat(session): add AppSessionInfo"
```

---

### Task 1.6: Create AppSession

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/AppSession.java`

**Step 1: Write the class**

```java
package com.qdw.feishu.domain.session;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 完整会话实体（含泛型 data）
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppSession<T> extends AppSessionInfo {
    
    private T data;
    
    public AppSession(String sessionId, String appId, String topicId, T data) {
        super(sessionId, appId, topicId);
        this.data = data;
    }
    
    public static <T> AppSession<T> fromInfo(AppSessionInfo info, T data) {
        AppSession<T> session = new AppSession<>();
        session.setSessionId(info.getSessionId());
        session.setAppId(info.getAppId());
        session.setTopicId(info.getTopicId());
        session.setState(info.getState());
        session.setCreatedAt(info.getCreatedAt());
        session.setLastActiveAt(info.getLastActiveAt());
        session.setExpiresAt(info.getExpiresAt());
        session.setVersion(info.getVersion());
        session.setData(data);
        return session;
    }
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/AppSession.java
git commit -m "feat(session): add AppSession with generic data"
```

---

## Phase 2: Gateway Interface

### Task 2.1: Create AppSessionGateway interface

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/AppSessionGateway.java`

**Step 1: Write the interface**

```java
package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.session.*;

import java.util.List;
import java.util.Optional;

/**
 * 通用会话管理 Gateway 接口
 */
public interface AppSessionGateway {

    // ========== 会话创建 ==========
    
    <T> String createSession(String appId, String topicId, T data, TypeToken<T> typeToken);
    
    <T> String createSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken);

    // ========== 会话查询 ==========
    
    <T> Optional<AppSession<T>> getActiveSession(String appId, String topicId, TypeToken<T> typeToken);
    
    <T> Optional<AppSession<T>> getSession(String appId, String topicId, String sessionId, TypeToken<T> typeToken);
    
    List<AppSessionInfo> listSessions(String appId, String topicId);
    
    int countActiveSessions(String appId, String topicId);

    // ========== 会话更新 ==========
    
    <T> void updateSession(String appId, String topicId, String sessionId, T data, TypeToken<T> typeToken, long version);
    
    void updateState(String appId, String topicId, String sessionId, SessionState state, long version);
    
    void setActiveSession(String appId, String topicId, String sessionId);
    
    void activateSession(String appId, String topicId, String sessionId);
    
    void idleSession(String appId, String topicId, String sessionId);

    // ========== 会话删除 ==========
    
    void deleteSession(String appId, String topicId, String sessionId);
    
    void terminateSession(String appId, String topicId, String sessionId);
    
    int cleanupSessions(String appId, String topicId);
}
```

**Step 2: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/AppSessionGateway.java
git commit -m "feat(session): add AppSessionGateway interface"
```

---

## Phase 3: Rename Existing Classes

### Task 3.1: Rename TopicMapping → SessionContext

**Files:**
- Rename: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicMapping.java`
- To: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/SessionContext.java`

**Step 1: Use IDE or git mv to rename**

```bash
git mv feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicMapping.java \
       feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/SessionContext.java
```

**Step 2: Update class name inside file**

Change `public class TopicMapping` to `public class SessionContext`

**Step 3: Update all imports**

Find and replace all imports of `TopicMapping` to `SessionContext`

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename TopicMapping to SessionContext"
```

---

### Task 3.2: Rename TopicMappingGateway → SessionContextGateway

**Files:**
- Rename: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/TopicMappingGateway.java`
- To: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/SessionContextGateway.java`

**Step 1: Rename file**

```bash
git mv feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/TopicMappingGateway.java \
       feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/SessionContextGateway.java
```

**Step 2: Update interface name and all imports**

**Step 3: Commit**

```bash
git add -A
git commit -m "refactor: rename TopicMappingGateway to SessionContextGateway"
```

---

### Task 3.3: Rename TopicMetadata → SessionMetadata

**Files:**
- Rename: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicMetadata.java`
- To: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/SessionMetadata.java`

**Step 1: Rename file and update class**

**Step 2: Commit**

```bash
git add -A
git commit -m "refactor: rename TopicMetadata to SessionMetadata"
```

---

### Task 3.4: Rename infrastructure implementations

**Files:**
- Rename: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/TopicMappingSqliteGateway.java`
- To: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/SessionContextSqliteGateway.java`

- Rename: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/TopicMappingGatewayImpl.java`
- To: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/SessionContextGatewayImpl.java`

**Step 1: Rename files and update implementations**

**Step 2: Commit**

```bash
git add -A
git commit -m "refactor: rename infrastructure gateway implementations"
```

---

## Phase 4: Implement AppSessionGateway

### Task 4.1: Create AppSessionGatewayImpl

**Files:**
- Create: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java`

**Step 1: Write the implementation** (see design doc for full implementation)

Key points:
- Use `SessionContextGateway` for persistence
- Use Jackson `ObjectMapper` for JSON serialization
- Implement optimistic locking with version check
- Generate session IDs with UUID

**Step 2: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java
git commit -m "feat(session): implement AppSessionGatewayImpl"
```

---

## Phase 5: Update Callers

### Task 5.1: Update BotMessageService

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`

**Changes:**
- Replace `TopicMappingGateway` with `SessionContextGateway`
- Replace `TopicMapping` with `SessionContext`

**Step 1: Update imports and field**

**Step 2: Commit**

```bash
git add -A
git commit -m "refactor: update BotMessageService to use SessionContext"
```

---

### Task 5.2: Update OpenCodeApp and related classes

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeTaskExecutor.java`

**Changes:**
- Define `OpenCodeData` inner class
- Define `TypeToken<OpenCodeData>` constant
- Replace `OpenCodeSessionGateway` with `AppSessionGateway`

**Step 1: Update each file**

**Step 2: Commit**

```bash
git add -A
git commit -m "refactor(opencode): migrate to AppSessionGateway"
```

---

## Phase 6: Cleanup

### Task 6.1: Delete OpenCodeSessionGateway

**Files:**
- Delete: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/OpenCodeSessionGateway.java`
- Delete: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeSessionGatewayImpl.java`
- Delete: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/opencode/OpenCodeMetadata.java`

**Step 1: Delete files**

```bash
git rm <files>
git commit -m "refactor: remove old OpenCode session classes"
```

---

## Phase 7: Testing

### Task 7.1: Write unit tests for SessionState

**Files:**
- Create: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/session/SessionStateTest.java`

**Step 1: Write tests for state transitions**

```java
@Test
void testCreatedCanTransitionToActive() {
    assertTrue(SessionState.CREATED.canTransitionTo(SessionState.ACTIVE));
}

@Test
void testTerminatedCannotTransition() {
    assertFalse(SessionState.TERMINATED.canTransitionTo(SessionState.ACTIVE));
}
```

**Step 2: Run tests**

```bash
mvn test -Dtest=SessionStateTest
```

**Step 3: Commit**

---

### Task 7.2: Write unit tests for AppSessionGateway

**Files:**
- Create: `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImplTest.java`

**Step 1: Write tests**

**Step 2: Run tests**

**Step 3: Commit**

---

### Task 7.3: Run all tests

```bash
mvn test
```

---

## Phase 8: Final Verification

### Task 8.1: Build and verify

```bash
mvn clean package
```

### Task 8.2: Update documentation

- Update `AGENTS.md` with new class names
- Update `README.md` architecture section

### Task 8.3: Final commit

```bash
git add -A
git commit -m "docs: update documentation for session management refactoring"
```

---

## Summary

| Phase | Tasks | Estimated Time |
|-------|-------|----------------|
| Phase 1: Core Classes | 6 | 30 min |
| Phase 2: Gateway Interface | 1 | 10 min |
| Phase 3: Rename Classes | 4 | 20 min |
| Phase 4: Implementation | 1 | 30 min |
| Phase 5: Update Callers | 2 | 30 min |
| Phase 6: Cleanup | 1 | 5 min |
| Phase 7: Testing | 3 | 30 min |
| Phase 8: Verification | 3 | 15 min |
| **Total** | **21** | **~3 hours** |

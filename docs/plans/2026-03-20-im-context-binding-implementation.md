# ImContextBinding Implementation Plan

> Date: 2026-03-20
> Status: Ready for Implementation
> Design Doc: [2026-03-20-im-context-binding-design.md](./2026-03-20-im-context-binding-design.md)

---

## Overview

This plan implements the separation of:
1. **App Sessions** - application-internal session management (no IM concepts)
2. **IM Context Bindings** - mapping from external IM contexts (Feishu topics/chats) to app sessions

## Current State

```
AppSessionGateway
├── createSession(appId, topicId, data, typeToken)     ← 问题：topicId 不应在此
├── getActiveSession(appId, topicId, typeToken)        ← 问题：topicId 不应在此
├── getSession(appId, topicId, sessionId, typeToken)   ← 问题：topicId 不应在此
├── listSessions(appId, topicId)                        ← 问题：topicId 不应在此
└── ... all methods take topicId ...

Storage: SessionContext.metadata[appId].sessions[]
         SessionContext is keyed by topicId
```

## Target State

```
AppSessionGateway (refactored)
├── createSession(appId, data, typeToken) → sessionId
├── getSession(appId, sessionId, typeToken) → Optional<AppSession<T>>
├── listSessions(appId) → List<AppSessionInfo>
├── updateSession(appId, sessionId, data, typeToken, version)
├── updateState(appId, sessionId, state, version)
└── deleteSession(appId, sessionId)

ImContextBindingGateway (new)
├── bind(contextRef, appId, sessionId) → BindingResult
├── findBinding(contextRef) → Optional<ImContextBinding>
├── clearBinding(contextRef)
└── isBoundToApp(contextRef, appId) → boolean

Storage:
├── App sessions: new table/collection keyed by (appId, sessionId)
└── IM bindings: new table/collection keyed by (platform, contextType, contextId)
```

---

## Phase 1: Introduce Binding Abstraction

**Goal**: Add `ImContextRef`, `ImContextBinding`, and `ImContextBindingGateway` without breaking existing code.

### 1.1 Domain Models

Create in `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/`:

#### ImContextRef.java
```java
package com.qdw.feishu.domain.model;

import lombok.Value;

/**
 * External IM conversation context identifier
 */
@Value
public class ImContextRef {
    String platform;      // "feishu", "discord", etc.
    String contextType;   // "thread", "chat", "channel"
    String contextId;     // platform-specific id (thread_id or chat_id)
    
    public static ImContextRef feishuThread(String threadId) {
        return new ImContextRef("feishu", "thread", threadId);
    }
    
    public static ImContextRef feishuChat(String chatId) {
        return new ImContextRef("feishu", "chat", chatId);
    }
    
    /**
     * Unique key for storage
     */
    public String toStorageKey() {
        return platform + ":" + contextType + ":" + contextId;
    }
}
```

#### ImContextBinding.java
```java
package com.qdw.feishu.domain.model;

import lombok.Value;
import java.time.Instant;

/**
 * Mapping from IM context to app session
 */
@Value
public class ImContextBinding {
    ImContextRef contextRef;
    String appId;
    String sessionId;
    Instant createdAt;
    Instant lastActiveAt;
}
```

### 1.2 Gateway Interface

Create `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/ImContextBindingGateway.java`:

```java
package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import java.util.Optional;

public interface ImContextBindingGateway {
    
    /**
     * Bind IM context to app session (upsert semantics)
     * - If unbound: create new binding
     * - If bound to same session: no-op
     * - If bound to different session: replace
     */
    BindingResult bind(ImContextRef contextRef, String appId, String sessionId);
    
    /**
     * Find current binding for context
     */
    Optional<ImContextBinding> findBinding(ImContextRef contextRef);
    
    /**
     * Clear binding for context
     */
    void clearBinding(ImContextRef contextRef);
    
    /**
     * Check if context is bound to specific app
     */
    boolean isBoundToApp(ImContextRef contextRef, String appId);
}
```

#### BindingResult.java
```java
package com.qdw.feishu.domain.model;

import lombok.Value;

@Value
public class BindingResult {
    boolean created;      // new binding created
    boolean updated;      // existing binding updated
    boolean noChange;     // binding already matched
    
    public static BindingResult created() { return new BindingResult(true, false, false); }
    public static BindingResult updated() { return new BindingResult(false, true, false); }
    public static BindingResult noChange() { return new BindingResult(false, false, true); }
}
```

### 1.3 Infrastructure Implementation

Create `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java`:

- Use SQLite for persistence (similar to existing `SessionContextGatewayImpl`)
- Table schema:
```sql
CREATE TABLE im_context_binding (
    context_key TEXT PRIMARY KEY,  -- platform:contextType:contextId
    platform TEXT NOT NULL,
    context_type TEXT NOT NULL,
    context_id TEXT NOT NULL,
    app_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL
);

CREATE INDEX idx_binding_app_session ON im_context_binding(app_id, session_id);
```

### 1.4 Feishu Context Resolution Helper

Create `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/feishu/FeishuContextResolver.java`:

```java
package com.qdw.feishu.domain.feishu;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.ImContextRef;

/**
 * Resolves Feishu message to IM context reference
 */
public class FeishuContextResolver {
    
    /**
     * Resolve IM context from Feishu message
     * Rule: thread_id first, chat_id fallback
     */
    public static ImContextRef resolve(Message message) {
        String threadId = message.getThreadId();
        String chatId = message.getChatId();
        
        if (threadId != null && !threadId.isEmpty()) {
            return ImContextRef.feishuThread(threadId);
        }
        
        if (chatId != null && !chatId.isEmpty()) {
            return ImContextRef.feishuChat(chatId);
        }
        
        throw new IllegalArgumentException("Message has neither threadId nor chatId");
    }
}
```

### 1.5 Tests for Phase 1

Create `ImContextBindingGatewayImplTest.java`:
- Test bind creates new binding
- Test bind updates existing binding
- Test bind no-change when same session
- Test findBinding returns correct binding
- Test clearBinding removes binding
- Test isBoundToApp returns correct result

---

## Phase 2: Refactor AppSessionGateway

**Goal**: Remove `topicId` from `AppSessionGateway` interface and implementation.

### 2.1 New Interface (Target)

```java
public interface AppSessionGateway {
    
    // ========== 会话创建 ==========
    <T> String createSession(String appId, T data, TypeToken<T> typeToken);
    <T> String createSession(String appId, String sessionId, T data, TypeToken<T> typeToken);

    // ========== 会话查询 ==========
    <T> Optional<AppSession<T>> getSession(String appId, String sessionId, TypeToken<T> typeToken);
    List<AppSessionInfo> listSessions(String appId);
    
    // ========== 会话更新 ==========
    <T> void updateSession(String appId, String sessionId, T data, TypeToken<T> typeToken, long version);
    void updateState(String appId, String sessionId, SessionState state, long version);
    
    // ========== 会话删除 ==========
    void deleteSession(String appId, String sessionId);
}
```

### 2.2 New Storage Schema

Create new table for app sessions:
```sql
CREATE TABLE app_session (
    app_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    state TEXT NOT NULL,
    data TEXT,  -- JSON serialized
    version INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    last_active_at INTEGER NOT NULL,
    expires_at INTEGER,
    PRIMARY KEY (app_id, session_id)
);
```

### 2.3 Migration Strategy

**Approach**: Dual-write during transition

1. Create `AppSessionGatewayV2` with new interface
2. Update `OpenCodeSessionManager` to use V2 + binding
3. Migrate existing data from `SessionContext.metadata` to new tables
4. Remove V1 code

**Alternative**: One-time migration with compatibility layer

### 2.4 Implementation Steps

1. Create `AppSessionGatewayV2` interface
2. Create `AppSessionGatewayV2Impl` with SQLite storage
3. Keep old `AppSessionGateway` as-is during transition
4. Add migration script to copy existing sessions

---

## Phase 3: Refactor OpenCode to Use Binding

**Goal**: Move OpenCode to session-driven logic with IM binding.

### 3.1 Current Flow

```
OpenCodeApp.execute(message)
    ↓
OpenCodeSessionManager.getSessionId(topicId)
    ↓
AppSessionGateway.getActiveSession(appId, topicId, typeToken)
    ↓
Returns sessionId from topic-scoped storage
```

### 3.2 New Flow

```
OpenCodeApp.execute(message)
    ↓
FeishuContextResolver.resolve(message) → ImContextRef
    ↓
ImContextBindingGateway.findBinding(contextRef) → Optional<ImContextBinding>
    ↓
If binding exists: AppSessionGatewayV2.getSession(appId, sessionId, typeToken)
If no binding: return uninitialized state
```

### 3.3 OpenCodeSessionManager Refactor

**Before** (current):
```java
public Optional<String> getSessionId(String topicId) {
    return appSessionGateway.getActiveSession(APP_ID, topicId, TYPE_TOKEN)
        .map(session -> session.getData().getOpenCodeSessionId());
}
```

**After** (new):
```java
public Optional<String> getSessionId(ImContextRef contextRef) {
    return bindingGateway.findBinding(contextRef)
        .filter(b -> b.getAppId().equals(APP_ID))
        .flatMap(b -> sessionGateway.getSession(APP_ID, b.getSessionId(), TYPE_TOKEN))
        .map(session -> session.getData().getOpenCodeSessionId());
}

public void saveSession(ImContextRef contextRef, String openCodeSessionId) {
    // Check if already bound with same external session
    Optional<ImContextBinding> existing = bindingGateway.findBinding(contextRef);
    
    if (existing.isPresent()) {
        // Update existing session data
        AppSession<OpenCodeSessionData> session = sessionGateway
            .getSession(APP_ID, existing.get().getSessionId(), TYPE_TOKEN)
            .orElseThrow();
        
        OpenCodeSessionData data = session.getData();
        if (!data.getOpenCodeSessionId().equals(openCodeSessionId)) {
            data.setOpenCodeSessionId(openCodeSessionId);
            sessionGateway.updateSession(APP_ID, session.getSessionId(), 
                data, TYPE_TOKEN, session.getVersion());
        }
    } else {
        // Create new session and bind
        OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
        String sessionId = sessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
        bindingGateway.bind(contextRef, APP_ID, sessionId);
    }
}
```

### 3.4 OpenCodeApp Updates

Update `OpenCodeApp` to:
1. Resolve `ImContextRef` from message at entry point
2. Pass `ImContextRef` to `OpenCodeSessionManager` instead of `topicId`
3. Handle binding resolution errors

---

## Phase 4: Cleanup

### 4.1 Remove Obsolete Code

- Remove `topicId` parameter from all `AppSessionGateway` methods
- Remove `SessionMetadata` usage from `AppSessionGatewayImpl`
- Remove `SessionContext.appId` field (no longer needed for session namespace)
- Update `AppSessionInfo` to remove `topicId` field

### 4.2 Update Tests

- Refactor `AppSessionGatewayImplTest` for new interface
- Add `ImContextBindingGatewayImplTest`
- Update `OpenCodeAppTest` to use `ImContextRef`

### 4.3 Documentation Updates

- Update `AGENTS.md` with new architecture
- Update `APP_GUIDE.md` if needed
- Archive this implementation plan

---

## Risk Mitigation

### Risk 1: Data Migration

**Mitigation**: 
- Write migration script that copies data before deployment
- Keep old data until new system is verified
- Provide rollback path

### Risk 2: Breaking Changes

**Mitigation**:
- Use V2 interface alongside V1 during transition
- Feature flag to switch between implementations
- Incremental rollout

### Risk 3: Performance

**Mitigation**:
- Add indexes on binding and session tables
- Cache frequently accessed bindings
- Monitor query performance

---

## File Changes Summary

### New Files

| File | Layer | Description |
|------|-------|-------------|
| `ImContextRef.java` | domain | IM context identifier |
| `ImContextBinding.java` | domain | Binding entity |
| `BindingResult.java` | domain | Bind operation result |
| `ImContextBindingGateway.java` | domain | Binding gateway interface |
| `ImContextBindingGatewayImpl.java` | infrastructure | Binding gateway implementation |
| `FeishuContextResolver.java` | domain | Feishu context resolution |
| `ImContextBindingGatewayImplTest.java` | test | Binding tests |

### Modified Files

| File | Changes |
|------|---------|
| `AppSessionGateway.java` | Remove `topicId` from all methods |
| `AppSessionGatewayImpl.java` | New storage, remove topicId |
| `AppSessionInfo.java` | Remove `topicId` field |
| `OpenCodeSessionManager.java` | Use binding instead of topicId |
| `OpenCodeApp.java` | Resolve ImContextRef at entry |
| `SessionContext.java` | Remove or deprecate `appId` field |
| `SessionMetadata.java` | Deprecate or remove |

---

## Verification Checklist

### Phase 1 Complete When:
- [ ] `ImContextRef`, `ImContextBinding`, `BindingResult` classes exist
- [ ] `ImContextBindingGateway` interface defined
- [ ] `ImContextBindingGatewayImpl` implemented with SQLite
- [ ] `FeishuContextResolver` implemented
- [ ] All binding tests pass
- [ ] Existing tests still pass

### Phase 2 Complete When:
- [ ] `AppSessionGateway` refactored without `topicId`
- [ ] New SQLite storage for app sessions
- [ ] Migration script ready
- [ ] All session tests pass

### Phase 3 Complete When:
- [ ] `OpenCodeSessionManager` uses binding
- [ ] `OpenCodeApp` resolves context at entry
- [ ] OpenCode functionality preserved
- [ ] All OpenCode tests pass

### Phase 4 Complete When:
- [ ] Obsolete code removed
- [ ] Documentation updated
- [ ] No compilation warnings
- [ ] All tests pass
- [ ] Code review approved

---

## Estimated Effort

| Phase | Tasks | Complexity |
|-------|-------|------------|
| Phase 1 | Binding abstraction | Medium |
| Phase 2 | AppSessionGateway refactor | High |
| Phase 3 | OpenCode migration | Medium |
| Phase 4 | Cleanup | Low |

**Recommendation**: Start with Phase 1, verify with tests, then proceed incrementally.

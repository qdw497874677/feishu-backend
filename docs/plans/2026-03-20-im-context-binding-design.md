# ImContextBinding Design

> Date: 2026-03-20
> Status: Proposed

## Background

The current session abstraction still mixes two different concepts:

- `AppSession`: an application-internal session
- `topicId`: a Feishu-specific IM context identifier

This causes two architectural problems:

1. Application code still carries the Feishu `topic` concept, even though topic belongs to the IM platform, not the app domain.
2. The generic session abstraction is not truly generic, because session lookup and storage are still keyed by `topicId`.

The desired model is:

- apps manage their own sessions without any IM-specific concept
- IM platforms bind their external context to one app session
- one IM context can bind to exactly one app session at a time

In Feishu, the IM context should be modeled from official message fields:

- prefer `thread_id` when the message belongs to a topic/thread
- otherwise fall back to `chat_id`

In Discord it may be a `channel` or `thread`. In other platforms it may be another conversation container.

## Goals

- Remove `topicId` from the application session model
- Make `AppSession` truly application-internal
- Introduce a generic IM binding abstraction that is not Feishu-specific
- Keep the rule that one IM context binds to one app session
- Move IM context binding responsibility into the IM integration layer
- Preserve current OpenCode user experience in Feishu topics

## Non-Goals

- Support multiple active app sessions in one IM context at the same time
- Design cross-platform adapters for all IM systems now
- Introduce database schema redesign unless required by the migration
- Implement historical session-switching UI for OpenCode in this change

## Core Concepts

### AppSession

`AppSession` is the application-owned session entity.

It should only care about:

- `appId`
- `sessionId`
- `state`
- `data`
- `version`
- lifecycle timestamps

It must not include IM concepts such as:

- `topicId`
- `channelId`
- `threadId`

### ImContextRef

`ImContextRef` identifies an external IM conversation context.

Suggested fields:

- `platform`: such as `feishu`, `discord`, `slack`
- `contextType`: such as `topic`, `channel`, `thread`
- `contextId`: platform-specific unique identifier

Important note:

- `contextType` is descriptive metadata, not primary business logic input
- the true identity is the full tuple: `platform + contextType + contextId`

Examples:

- `feishu / thread / omt_xxx`
- `feishu / chat / oc_xxx`
- `discord / channel / 123456`
- `discord / thread / 789012`

### ImContextBinding

`ImContextBinding` represents the mapping from an IM context to an app session.

Suggested fields:

- `contextRef`
- `appId`
- `sessionId`
- `createdAt`
- `lastActiveAt`

Rule:

- one IM context has at most one current binding

This matches the desired Feishu behavior: one discussion context manages one app session.

## Feishu Context Resolution

Feishu has multiple identifiers on a message, but they do not all represent the same level of context.

Relevant identifiers:

- `chat_id`: stable identifier for a P2P chat or group chat
- `thread_id`: stable identifier for a topic/thread discussion context
- `message_id`: identifier of one message only
- `root_id`: root message of a reply tree, not a long-lived discussion container
- `parent_id`: direct replied-to message, not a long-lived discussion container

For Feishu integration, context resolution should be:

```java
String contextId = message.getThreadId() != null
    ? message.getThreadId()
    : message.getChatId();
```

And the corresponding `ImContextRef` should be:

```java
new ImContextRef("feishu", message.getThreadId() != null ? "thread" : "chat", contextId)
```

Design rule:

- do not use `root_id` or `parent_id` as the primary binding identifier
- use `thread_id` for topic-style discussions
- use `chat_id` as fallback for flat conversations such as P2P or normal group chat

## Architecture

The system should be split into two independent layers.

In COLA terms, ownership should be explicit.

- `domain`: generic models and gateway interfaces
- `app`: orchestration of bind / resolve / rebind use cases
- `adapter`: translate Feishu events into `ImContextRef` and invoke app services
- `infrastructure`: implement gateway persistence and external integrations

This prevents IM platform details from leaking into application session logic while still avoiding business logic in adapter code.

### 1. App Session Layer

This layer owns application sessions.

Responsibilities:

- create session
- read session
- update session data/state
- delete session
- list app sessions
- enforce optimistic locking and state transitions

This layer must not know anything about Feishu topics or any other IM platform context.

Suggested interface direction:

```java
String createSession(String appId, T data, TypeToken<T> typeToken);
<T> Optional<AppSession<T>> getSession(String appId, String sessionId, TypeToken<T> typeToken);
List<AppSessionInfo> listSessions(String appId);
<T> void updateSession(String appId, String sessionId, T data, TypeToken<T> typeToken, long version);
void updateState(String appId, String sessionId, SessionState state, long version);
void deleteSession(String appId, String sessionId);
```

### 2. IM Context Binding Layer

This layer owns the relationship between an IM context and an app session.

Responsibilities:

- bind IM context to app session
- find current binding for a context
- clear binding
- validate app/session consistency
- clean invalid bindings when the target session no longer exists

Suggested interface direction:

```java
BindingResult bind(ImContextRef contextRef, String appId, String sessionId);
Optional<ImContextBinding> findBinding(ImContextRef contextRef);
void clearBinding(ImContextRef contextRef);
boolean isBoundToApp(ImContextRef contextRef, String appId);
```

`bind(...)` should be defined as current-binding upsert, not append-only history write.

Expected behavior:

- if the context is unbound, create a new binding
- if the context is already bound to the same `appId + sessionId`, do nothing
- if the context is bound to another session, atomically replace the current binding

This makes `ImContextBinding` the source of truth for the current active session of one IM context.

## ID Semantics

The design must distinguish internal session identifiers from external integration identifiers.

### Internal App Session ID

- `AppSession.sessionId`
- owned by the generic app session layer
- used as persistence identity and update target

### External Runtime Session ID

- example: `OpenCodeSessionData.openCodeSessionId`
- owned by one specific integration or app-specific session data object
- may be displayed to users or passed to external systems

Rule:

- these two IDs are different unless a specific app deliberately chooses to align them
- implementation and docs must never refer to both simply as `sessionId` without qualification

## OpenCode Behavior

OpenCode should no longer treat `topicId` as part of its session model.

`OpenCodeSessionManager` should manage OpenCode sessions by `sessionId` only.

The Feishu flow becomes:

1. Adapter/service receives message from a Feishu topic
2. Adapter resolves `thread_id` first, otherwise `chat_id`
3. Adapter builds `ImContextRef(platform=feishu, contextType=thread|chat, contextId=...)`
4. Binding layer resolves the bound `appId + sessionId`
5. Application logic receives the resolved internal app `sessionId`
6. If a new OpenCode session is created or selected, Feishu integration updates the binding

This keeps OpenCode domain logic independent from Feishu.

## saveSession Semantics

For OpenCode, the desired behavior is upsert-like, not append-only.

Rules:

1. If the current bound OpenCode session already matches the incoming external OpenCode session id, do nothing.
2. If a current bound OpenCode session exists but maps to a different external OpenCode session id, update the current app session data instead of creating another duplicate logical binding.
3. If no current app session exists, create one.

This preserves the legacy user-facing behavior where a topic effectively tracks one current OpenCode session, while still allowing the app layer to support multiple sessions in principle.

The implementation must be explicit that:

- the current Feishu discussion context maps to one internal `AppSession.sessionId`
- that app session may contain an external OpenCode session id inside `OpenCodeSessionData`
- updating external OpenCode session identity does not necessarily imply creating a brand new internal app session

## Recommended Class Changes

### New or Updated Domain Models

- add `ImContextRef`
- add `ImContextBinding`
- keep `AppSession`, `AppSessionInfo`, `SessionState`, `SessionConfig`
- keep app-specific session data such as `OpenCodeSessionData`

### New or Updated Gateways

- refactor `AppSessionGateway` to remove `topicId`
- add `ImContextBindingGateway`

### Classes to Reduce or Remove

- remove app-session dependence on `SessionContext`
- reduce `SessionMetadata` from app-session namespace logic to implementation detail or remove it entirely if replaced cleanly
- move all topic binding logic out of `OpenCodeSessionManager`

## Lifecycle and Integrity Rules

To avoid ambiguity during implementation, the following rules should hold.

### Session Lifecycle

- an `AppSession` may exist before it is bound to any IM context
- an `AppSession` may become unbound and still continue to exist
- deleting an `AppSession` should invalidate any binding that points to it

### Binding Lifecycle

- one `ImContextRef` has at most one current binding
- one `AppSession` may be rebound across contexts over time if the business flow allows it
- current implementation only requires current binding, not historical binding records

### Integrity Constraints

- uniqueness key: `platform + contextType + contextId`
- a binding target must reference an existing `appId + sessionId`
- rebinding should be atomic from the point of view of readers

## Error Handling

Three important error paths should be explicit.

### 1. Unbound IM Context

If a Feishu topic has no binding, return a guided response appropriate for the app.

Example:

- no current OpenCode session bound to this topic
- instruct user how to initialize or bind one

### 2. Dangling Binding

If an IM context binding exists but points to a missing app session:

- log a warning
- clear the invalid binding
- return a recoverable response

This cleanup should happen in app-layer orchestration or a dedicated binding service, not in adapter-only logic.

### 3. App Mismatch

If a context is bound to one app but another app tries to use the binding:

- reject explicitly
- do not silently reuse or overwrite the binding

## Storage and Migration Strategy

The current code stores app session data inside topic-scoped `SessionContext.metadata`. The new design separates these responsibilities.

That means migration must answer two independent questions:

1. where current app sessions are stored
2. where current IM context bindings are stored

Recommended transitional strategy:

### Transitional Storage Model

- introduce a new storage path for `ImContextBinding`
- refactor `AppSessionGateway` storage so it no longer depends on `topicId`
- keep old `SessionContext`-based structures only as temporary compatibility code during migration

### Migration Approach

- prefer incremental migration over big-bang rewrite
- dual-read is acceptable during transition if needed
- avoid long-lived dual-write unless absolutely necessary
- once binding and app session storage are verified, remove topic-oriented session storage paths

### Explicit Migration Decision Needed During Implementation Plan

The implementation plan must choose one of:

- lazy migration on first access
- one-time eager migration
- temporary compatibility mode with explicit cleanup phase

The current design does not mandate one option yet, but implementation planning must.

## Testing Strategy

Testing should be split into three groups.

### 1. AppSession Tests

Verify pure session behavior without IM context:

- create/read/update/delete by `appId + sessionId`
- optimistic locking
- state transition validation
- multiple sessions under one app

### 2. ImContextBinding Tests

Verify binding behavior:

- bind context to session
- replace existing binding for same context
- clear binding
- reject or detect broken binding targets
- one context only maps to one app session at a time
- prefer `thread_id`, fall back to `chat_id` for Feishu context resolution

### 3. OpenCode Integration Tests

Verify OpenCode no longer depends on topic-oriented session APIs:

- topic message resolves binding first, then session
- repeated save/bind does not create duplicate logical OpenCode bindings
- clearing a topic binding does not corrupt unrelated app sessions
- external OpenCode session id and internal app session id are not confused

## Migration Plan

The migration should be incremental.

### Phase 1: Introduce Binding Abstraction

- add `ImContextRef`
- add `ImContextBinding` and `ImContextBindingGateway`
- add Feishu context resolution rule: `thread_id` first, `chat_id` fallback
- start resolving Feishu discussion-context-to-session through the binding layer

### Phase 2: Refactor AppSessionGateway

- remove `topicId` from app-session APIs
- update implementation to store and access sessions by app/session semantics only
- stop using `SessionContext.appId` as app-session namespace selector
- explicitly define storage layout for app sessions independent from IM bindings

### Phase 3: Move OpenCode to Session-Driven Logic

- refactor `OpenCodeSessionManager` to manage app sessions by `sessionId`
- move Feishu context binding responsibility into app orchestration plus Feishu adapter translation
- preserve current Feishu UX through the new binding layer

### Phase 4: Cleanup

- remove obsolete topic-oriented session APIs
- clean up old tests and docs
- verify layering remains COLA-compliant

## Trade-Offs Considered

### Recommended: AppSession + ImContextBinding

Pros:

- clean abstraction boundary
- reusable across Feishu, Discord, Slack, web chat, CLI
- keeps app domain independent from IM concepts
- matches current business rule cleanly

Cons:

- requires touching multiple layers
- migration is larger than a small bug fix

### Alternative: Keep Topic-Centric Session APIs

Pros:

- smaller short-term code change

Cons:

- keeps Feishu concept inside app session abstraction
- blocks future multi-platform reuse
- continues conceptual leakage

### Alternative: Model Everything as IM Session

Pros:

- simpler naming at first glance

Cons:

- easily confused with `AppSession`
- weak separation between external context and internal session

## Recommendation

Proceed with `AppSession` as the internal session model and `ImContextBinding` as the external context-to-session mapping model.

This is the cleanest abstraction, best fits the stated goal, and avoids binding the app domain to Feishu-specific topic concepts.

## Next Step

If implementation continues, create a detailed implementation plan that:

- updates the gateway interfaces first
- adds binding tests before refactor
- migrates OpenCode after the new abstraction is in place
- verifies behavior before deleting old code

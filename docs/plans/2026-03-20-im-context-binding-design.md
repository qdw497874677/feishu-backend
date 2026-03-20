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

In Feishu, the IM context is a `topic`. In Discord it may be a `channel` or `thread`. In other platforms it may be another conversation container.

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

Examples:

- `feishu / topic / oc_xxx`
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

This matches the desired Feishu behavior: one topic manages one app session.

## Architecture

The system should be split into two independent layers.

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
void bind(ImContextRef contextRef, String appId, String sessionId);
Optional<ImContextBinding> findBinding(ImContextRef contextRef);
void clearBinding(ImContextRef contextRef);
boolean isBoundToApp(ImContextRef contextRef, String appId);
```

## OpenCode Behavior

OpenCode should no longer treat `topicId` as part of its session model.

`OpenCodeSessionManager` should manage OpenCode sessions by `sessionId` only.

The Feishu flow becomes:

1. Adapter/service receives message from a Feishu topic
2. Adapter builds `ImContextRef(platform=feishu, contextType=topic, contextId=topicId)`
3. Binding layer resolves the bound `appId + sessionId`
4. Application logic receives the resolved `sessionId`
5. If a new OpenCode session is created or selected, Feishu integration updates the binding

This keeps OpenCode domain logic independent from Feishu.

## saveSession Semantics

For OpenCode, the desired behavior is upsert-like, not append-only.

Rules:

1. If the current bound OpenCode session already matches the incoming external OpenCode session id, do nothing.
2. If a current bound OpenCode session exists but maps to a different external OpenCode session id, update the current app session data instead of creating another duplicate logical binding.
3. If no current app session exists, create one.

This preserves the legacy user-facing behavior where a topic effectively tracks one current OpenCode session, while still allowing the app layer to support multiple sessions in principle.

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

### 3. App Mismatch

If a context is bound to one app but another app tries to use the binding:

- reject explicitly
- do not silently reuse or overwrite the binding

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

### 3. OpenCode Integration Tests

Verify OpenCode no longer depends on topic-oriented session APIs:

- topic message resolves binding first, then session
- repeated save/bind does not create duplicate logical OpenCode bindings
- clearing a topic binding does not corrupt unrelated app sessions

## Migration Plan

The migration should be incremental.

### Phase 1: Introduce Binding Abstraction

- add `ImContextRef`
- add `ImContextBinding` and `ImContextBindingGateway`
- start resolving topic-to-session through the binding layer

### Phase 2: Refactor AppSessionGateway

- remove `topicId` from app-session APIs
- update implementation to store and access sessions by app/session semantics only
- stop using `SessionContext.appId` as app-session namespace selector

### Phase 3: Move OpenCode to Session-Driven Logic

- refactor `OpenCodeSessionManager` to manage app sessions by `sessionId`
- move topic binding responsibility into Feishu integration/service layer
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

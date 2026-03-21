# ImContextBinding Refined Design

> Date: 2026-03-21  
> Status: Proposed

## Background

The previous design established the separation between:

- `AppSession`: application-internal session state
- `ImContextBinding`: mapping from external IM context to application session

During implementation, a second routing concept was introduced around topic-based binding. That created architectural drift and reintroduced Feishu-specific concepts into the application flow.

This refined design restores a single source of truth and clarifies how session-aware apps should work.

The key correction is:

- `ImContextBinding` becomes the only persisted context-binding model
- stateless apps do **not** persist bindings
- session-aware apps may persist app-context state even before a concrete session is selected

This design is intentionally scoped to current needs. At this stage, **only OpenCode is treated as a session-aware app**. Other apps remain stateless and do not participate in the session-aware abstraction.

## Goals

- Make `ImContextBinding` the single source of truth for IM context ownership
- Remove topic-specific routing as a separate architectural concept
- Support session-aware app flows with `sessionId = null`
- Preserve a clean separation between generic routing and app-specific session rules
- Keep stateless apps simple and non-sticky
- Introduce only lightweight reusable abstractions

## Non-Goals

- Migrate old `topic_mapping` data
- Preserve existing topic bindings across deployment
- Build a full generic framework for all future session-aware apps
- Change the command UX of stateless apps
- Support cross-app rebinding in the same IM context

## Core Model

### ImContextBinding as Single Source of Truth

`ImContextBinding` is redefined as:

> the current app-context ownership of one IM context, plus an optional active internal app session.

Suggested fields remain:

- `contextRef`
- `appId`
- `sessionId` (nullable)
- `createdAt`
- `lastActiveAt`

The meaning of `sessionId` is:

- `null`: the IM context is inside the app context, but there is no active internal session yet
- non-null: the IM context is inside the app context and bound to a concrete internal app session

This is a valid persisted state, not a temporary hack.

### Binding Rules

- one IM context has at most one current binding
- stateless apps do not persist bindings
- only session-aware apps persist bindings
- cross-app rebinding inside the same bound IM context is not allowed

At the current stage, this means:

- `help`, `time`, `bash`, `history` do not write `ImContextBinding`
- `opencode` may write `ImContextBinding`

## Feishu Context Resolution

Feishu context resolution remains unchanged:

- prefer `thread_id`
- otherwise fall back to `chat_id`

`FeishuContextResolver` remains the only supported entry for building `ImContextRef`.

Routing logic must not branch on `topicId` directly as a primary architectural concept.

## Routing Rules

### Unbound Context

If an IM context has no binding:

- explicit slash commands execute normally
- plain text silently degrades to `help`

Because old topic data will not be migrated, previously active topic/session contexts are treated as unbound after deployment.

This is an intentional product decision.

### Stateless Apps

Stateless apps are one-shot commands:

- they execute only for the current request
- they do not persist context ownership
- they do not make the IM context sticky

Examples:

- `/help`
- `/time`
- `/bash ls`
- `/history`

After execution, follow-up plain text remains unbound and falls back to `help`.

### Session-Aware Apps

Session-aware apps may persist app-context ownership in `ImContextBinding`.

At present, only `OpenCode` is session-aware.

Once a context is bound to a session-aware app, that app owns the context until the binding is cleared or the app transitions internally.

Explicit slash commands for other apps inside a bound session-aware context are rejected and must not trigger cross-app rebinding.

## OpenCode Two-Phase Model

OpenCode has two valid bound states.

### 1. Entered OpenCode, No Active Session

```text
appId = opencode
sessionId = null
```

Meaning:

- the IM context is inside OpenCode app context
- no concrete internal OpenCode app session is active yet
- only session-external OpenCode commands are allowed

Allowed behavior in this state includes:

- status checks
- project directory selection
- historical OpenCode session lookup
- choosing to create a new session
- choosing to bind an existing session

### 2. Entered OpenCode with Active Session

```text
appId = opencode
sessionId = <internalAppSessionId>
```

Meaning:

- the IM context is inside OpenCode app context
- a concrete internal app session is active
- session-internal OpenCode interaction commands are allowed

This state is reached only after the user explicitly selects or creates a concrete session.

## OpenCode Lifecycle

### Session-External Phase

When the user enters OpenCode through an allowed entry command, the system may bind:

```text
opencode + null
```

This records that the current IM context is now owned by OpenCode, but is not yet attached to a concrete session.

### Session Activation Phase

When the user explicitly chooses:

- create a new OpenCode session, or
- bind an existing OpenCode session

then two things happen:

1. the **app layer** creates or resolves the internal `AppSession`
2. the **IM binding layer** updates the binding to:

```text
opencode + <internalAppSessionId>
```

This state transition is app-internal progression, not cross-app rebinding.

## Lightweight Reusable Abstraction

The reusable part should stay intentionally small.

### What We Abstract

Extract only the common session-aware orchestration pattern:

- classify current context relative to one app
- read binding and app session together
- enter app context (`appId + null`)
- activate app session (`appId + sessionId`)
- clear or repair invalid binding state

### What We Do Not Abstract

Keep these app-specific:

- command rules
- session creation semantics
- session selection semantics
- app-specific session data
- app-specific external integration ids
- state machine text and guidance

### Proposed Lightweight Types

#### `ContextSessionState`

Recommended states:

- `UNBOUND`
- `BOUND_TO_OTHER_APP`
- `IN_APP_NO_SESSION`
- `IN_APP_WITH_SESSION`

This is more precise than a 3-state model because the bound-to-other-app case must be explicit.

#### `ContextSessionStatus<T>`

Encapsulates:

- `contextRef`
- `appId`
- `ContextSessionState`
- `ImContextBinding` (nullable)
- `AppSession<T>` (nullable)
- `danglingBinding`

This object is orchestration-shaped and is better placed in the **app layer** unless later proven to be a pure domain object.

#### `ContextSessionOrchestrator`

Responsibilities:

- load context/session status for one app
- enter app context
- activate concrete session
- clear broken state

This is a lightweight app-layer service, not a framework.

## COLA Placement

### Domain Layer

Keep core models and gateway contracts here:

- `ImContextRef`
- `ImContextBinding`
- `AppSession`
- `ContextSessionState` (if treated as core business concept)
- `ImContextBindingGateway`
- `AppSessionGateway`

### App Layer

Put orchestration here:

- `ContextSessionStatus<T>`
- `ContextSessionOrchestrator`
- OpenCode-specific app services and managers

This layer combines generic gateway operations into app-facing use cases.

### Adapter Layer

Responsibilities remain:

- translate incoming Feishu events/messages
- build `Message`
- resolve `ImContextRef`
- invoke app services

No session-aware business rules should live here.

### Infrastructure Layer

Responsibilities remain:

- persist `ImContextBinding`
- persist `AppSession`
- implement gateway contracts

## BotMessageService Responsibilities

`BotMessageService` should stay generic.

It should:

1. resolve `ImContextRef`
2. detect explicit slash command vs plain text
3. read `ImContextBinding`
4. decide which app should receive the message

It should not own OpenCode-specific session creation or session-external/session-internal rules.

Those responsibilities belong to OpenCode-specific services.

## MessageCommandAdapter Responsibilities

`MessageCommandAdapter` must align with the same routing source of truth:

- resolve Feishu context using thread-first, chat-fallback
- consult `ImContextBinding` only for session-aware context ownership
- avoid duplicating app-specific command gating rules

## Failure and Repair Semantics

### No Old Data Migration

Old topic data is intentionally not migrated.

Consequences:

- previously active topic/session contexts become logically unbound after deployment
- users must re-enter OpenCode context and re-select or re-create session state

### Dangling Session Binding

If `ImContextBinding` points to a missing internal app session for OpenCode:

- the system should clear the broken concrete session link
- degrade to `opencode + null`
- return OpenCode initialization/session-selection guidance

It should not fall back to generic `help` in this case because the context is still conceptually inside OpenCode.

## Testing Focus

The refined design requires at least these tests:

1. unbound plain text silently routes to `help`
2. stateless app commands do not create bindings
3. OpenCode entry command can create `opencode + null`
4. OpenCode explicit session creation/selection upgrades binding to `opencode + sessionId`
5. explicit `/otherApp` inside bound OpenCode context is rejected and does not rebind
6. returned Feishu `threadId` is used as the effective bound context when applicable
7. dangling OpenCode concrete session binding degrades to `opencode + null`
8. `FeishuContextResolver` uses thread-first, chat-fallback consistently in all routing entry points

## Implementation Order

Recommended sequence:

1. unify `ImContextBinding` as the only binding model and formally allow nullable `sessionId`
2. remove the separate topic-routing abstraction from the target design
3. introduce lightweight `ContextSessionState` / `ContextSessionStatus<T>` / `ContextSessionOrchestrator`
4. refactor OpenCode into explicit session-external and session-internal phases
5. converge `BotMessageService` and `MessageCommandAdapter` on `FeishuContextResolver + ImContextBinding`
6. add tests for the new routing and state semantics

## Scope Boundary

This refined design is intentionally limited.

At this stage:

- only **OpenCode** uses the session-aware binding abstraction
- other apps remain stateless
- no generic app framework is introduced
- only the minimal reusable orchestration skeleton is extracted

This keeps the design aligned with YAGNI while leaving room for future session-aware apps if they appear later.

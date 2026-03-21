# ImContextBinding Refined Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `ImContextBinding` the only binding model, support nullable `sessionId`, keep stateless apps non-persistent, and implement OpenCode’s two-phase app-context/session model without reintroducing topic-based routing concepts.

**Architecture:** `ImContextBinding` is the only persisted IM binding model. Stateless apps (`help`, `time`, `bash`, `history`) execute per-request only and never persist bindings. Only OpenCode is session-aware for now. OpenCode uses a two-phase binding model: `opencode + null` for app-context without active session, then `opencode + internalSessionId` after explicit session selection/creation. Generic routing stays generic; session-aware orchestration lives in the app layer.

**Tech Stack:** Java 17, Spring Boot 3.2.1, COLA architecture, SQLite, JUnit 5, Mockito

---

## Pre-Implementation Notes

- Work from the refined design doc: `docs/plans/2026-03-21-im-context-binding-refined-design.md`
- Ignore old topic-binding persistence as a migration target; this implementation intentionally does **not** migrate old topic data
- Current working tree contains unfinished refactor code; do not layer new behavior on top of a broken baseline
- Prefer TDD for behavior changes and regression coverage
- Add a compile/test gate after each structural task, not just at the end

---

## Responsibility Split (Must Hold During Implementation)

### Domain layer

Keep only core concepts and contracts here:

- `ImContextRef`
- `ImContextBinding`
- `AppSession`
- `ContextSessionState` (if treated as core business concept)
- `ImContextBindingGateway`
- `AppSessionGateway`

### App layer

Put session-aware orchestration here:

- `ContextSessionStatus<T>`
- `ContextSessionOrchestrator`
- `ContextSessionOrchestratorImpl`
- OpenCode-specific orchestration that combines binding state and session state

### Domain services

`BotMessageService` must remain generic:

- resolve context
- distinguish explicit slash command vs plain text
- inspect current binding
- choose target app / reject invalid routing transitions
- return a **generic routing decision/result** that the app layer can consume

It must **not** own OpenCode-specific session creation or two-phase progression rules.
It must also **not** invoke app-layer services directly.

### Adapter layer

`MessageCommandAdapter` must only translate input and align with the same context-resolution rules. No app-specific session logic belongs here.

### Infrastructure layer

Persist nullable binding state correctly and implement gateway contracts, including schema handling for existing SQLite tables.

### Explicit integration path (must remain COLA-safe)

This implementation must use the following call-chain:

1. `MessageCommandAdapter` translates Feishu input into domain `Message`
2. an **app-layer message entrypoint** receives the request and coordinates the flow
   - preferred concrete name: `BotMessageAppService` or `HandleIncomingMessageCmdExe`
3. `BotMessageService` performs generic routing only:
   - resolve `ImContextRef`
   - inspect explicit slash command vs plain text
   - inspect `ImContextBinding`
   - choose target app / fallback / rejection
   - return a routing decision/result to the app layer
4. the app-layer message entrypoint consumes that routing decision
5. for OpenCode traffic, the app-layer message entrypoint delegates to a concrete app-layer owner:
   - preferred concrete name: `OpenCodeAppService` or `OpenCodeContextUseCase`
6. that app-layer OpenCode owner uses `ContextSessionOrchestrator`
7. domain-layer OpenCode classes remain pure domain logic or become thin helpers; they must not depend on app-layer services

**Constraint:** do not make `feishu-bot-domain` depend on `feishu-bot-app`.

**Additional constraint:** no class in `feishu-bot-domain` may inject or reference any type from `feishu-bot-app`.

If the current `OpenCodeApp` class is still the main entrypoint in the domain module, either:

- move the session-aware execution entrypoint into the app layer, or
- reduce the domain class to passive/domain-only logic invoked by the app layer

Do not implement a design where `BotMessageService` or any other domain-layer class directly injects or calls `ContextSessionOrchestrator` or any app-layer OpenCode service.

---

## Task 0: Stabilize the broken intermediate refactor before feature work

**Files:**
- Inspect current working tree and touched files first
- Likely touched: `BotMessageService.java`, `MessageCommandAdapter.java`, `DomainServiceConfig.java`, `ImContextBinding.java`, `TopicAppBinding*`

**Step 1: Assess the current broken baseline**

Run:

```bash
git status --short
mvn -q -DskipTests compile
```

Expected: current working tree shows unfinished refactor changes and compile may fail.

**Step 2: Choose one stabilization direction and apply it immediately**

Because the working tree is already mid-refactor, do **one** of these before continuing:

1. **Preferred:** finish the convergence to `ImContextBinding` baseline immediately if only dead `TopicAppBinding*` remnants are preventing a stable compile
2. **Fallback:** temporarily restore deleted `TopicAppBinding*` files to get back to a coherent baseline, then remove them again in Task 3

Do not continue with Tasks 1+ until the codebase is internally coherent.

**Step 3: Add a minimal compile gate**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds or failures are now limited to the specific task you are about to work on.

**Step 4: Commit baseline stabilization if it changed code**

```bash
git add -A
git commit -m "refactor(baseline): stabilize broken context-binding migration state"
```

Only commit if code changed.

---

## Task 1: Repair `ImContextBinding` null-session semantics in the domain model

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/ImContextBinding.java`
- Test: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/model/ImContextBindingTest.java` (create)

**Step 1: Write the failing test**

Create `ImContextBindingTest.java` with tests for:

```java
@Test
void should_match_when_sameAppAndBothSessionIdsAreNull() { }

@Test
void should_match_when_sameAppAndSameSessionId() { }

@Test
void should_not_match_when_sameAppButDifferentSessionId() { }

@Test
void should_rebindWithinSameApp_when_newSessionIdProvided() { }

@Test
void should_allow_nullableSessionId_asValidPersistedState() { }
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn test -q -Dtest=ImContextBindingTest
```

Expected: compile/test failure caused by the current broken `ImContextBinding.java` or missing nullable semantics.

**Step 3: Write minimal implementation**

Fix `ImContextBinding.java`:

- restore missing/broken imports and compilation issues
- remove any duplicate trailing return / broken syntax left by intermediate edits
- make `matches(appId, sessionId)` use nullable-safe comparison (`Objects.equals(...)`)
- keep `rebind(String newSessionId)` as same-app state progression helper
- document `sessionId` as explicitly nullable and valid

**Step 4: Run test + compile gate**

Run:

```bash
mvn test -q -Dtest=ImContextBindingTest && mvn -q -DskipTests compile
```

Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/ImContextBinding.java \
        feishu-bot-domain/src/test/java/com/qdw/feishu/domain/model/ImContextBindingTest.java
git commit -m "fix(binding): repair ImContextBinding null-session semantics"
```

---

## Task 2: Update binding gateway contract and SQLite persistence for nullable `sessionId`

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/ImContextBindingGateway.java`
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java`
- Test: `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImplTest.java`

**Step 1: Write the failing test**

Add tests to `ImContextBindingGatewayImplTest.java` for:

```java
@Test
void should_bind_when_sessionIdIsNull() { }

@Test
void should_returnNoChange_when_bindingMatchesNullSession() { }

@Test
void should_update_when_bindingProgressesFromNullToConcreteSession() { }
```

**Step 2: Run test to verify it fails**

Run:

```bash
mvn test -q -Dtest=ImContextBindingGatewayImplTest
```

Expected: FAIL because current gateway rejects `sessionId == null` or existing schema still requires `NOT NULL`.

**Step 3: Implement contract + schema handling**

Modify gateway implementation to:

- allow `sessionId` to be null in domain contract and validation
- update row mapping and comparison logic to use nullable session ids
- preserve upsert semantics:
  - unbound → create
  - same app + same nullable session → no change
  - same app null → concrete session → update
  - different app/session → gateway may still upsert; business layer later disallows cross-app rebinding

**Important:** handle the existing SQLite table explicitly.

Because `CREATE TABLE IF NOT EXISTS` will not alter an existing table, choose and implement one explicit strategy:

1. **Preferred migration:** create-copy-swap for `im_context_binding`
2. **Acceptable only if confirmed disposable:** drop and recreate `im_context_binding`

Document the chosen strategy in code comments or task notes.

**Step 4: Add schema regression coverage**

Add coverage that proves an existing DB with the old `NOT NULL session_id` shape is upgraded or rebuilt correctly.

If the current test harness truly cannot simulate that safely, document why in the test file or task notes and add the closest possible regression coverage.

**Step 5: Run test + compile gate**

Run:

```bash
mvn test -q -Dtest=ImContextBindingGatewayImplTest && mvn -q -DskipTests compile
```

Expected: PASS

**Step 6: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/ImContextBindingGateway.java \
        feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java \
        feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImplTest.java
git commit -m "feat(binding): support nullable session ids in context bindings"
```

---

## Task 3: Remove `TopicAppBinding` as an architectural branch before new orchestration work

**Files:**
- Delete: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicAppBinding.java`
- Delete: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/TopicAppBindingGateway.java`
- Delete: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/TopicAppBindingSqliteGateway.java`
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/DomainServiceConfig.java`
- Modify: any remaining tests/imports referencing `TopicAppBinding*`

**Step 1: Prove remaining references**

Search first:

```bash
grep -R "TopicAppBinding" -n feishu-bot-domain feishu-bot-infrastructure feishu-bot-app feishu-bot-start
```

Expected: remaining references exist before cleanup, unless Task 0 already removed them.

**Step 2: Remove dead files and wiring**

- remove abandoned files
- update `DomainServiceConfig` to wire only `ImContextBindingGateway`
- remove stale imports from touched classes/tests
- delete or rewrite obsolete tests that assert old topic-binding states or intermediate routing concepts

**Step 3: Verify cleanup + compile gate**

Run:

```bash
grep -R "TopicAppBinding" -n feishu-bot-domain feishu-bot-infrastructure feishu-bot-app feishu-bot-start
mvn -q -DskipTests compile
```

Expected: no remaining references, compile succeeds.

**Step 4: Commit**

```bash
git add -A
git commit -m "refactor(cleanup): remove TopicAppBinding intermediate design"
```

---

## Task 4: Add lightweight app-layer session-aware orchestration types

**Files:**
- Create: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/ContextSessionState.java`
- Create: `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionStatus.java`
- Create: `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestrator.java`
- Create: `feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImpl.java`
- Test: `feishu-bot-app/src/test/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImplTest.java`

**Step 1: Write the failing test**

Create orchestrator tests covering:

```java
@Test
void should_returnUnbound_when_noBindingExists() { }

@Test
void should_returnInAppNoSession_when_bindingHasNullSessionId() { }

@Test
void should_returnInAppWithSession_when_bindingAndSessionExist() { }

@Test
void should_markDanglingBinding_when_bindingSessionMissing() { }

@Test
void should_returnBoundToOtherApp_when_contextBoundToDifferentApp() { }

@Test
void should_repairDanglingBinding_toNullSession_when_requested() { }
```

Use mocks for `ImContextBindingGateway` and `AppSessionGateway`.

**Step 2: Run test to verify it fails**

Run:

```bash
mvn test -q -Dtest=ContextSessionOrchestratorImplTest
```

Expected: FAIL because classes do not exist yet.

**Step 3: Write minimal implementation**

Implement:

- `ContextSessionState` enum with:
  - `UNBOUND`
  - `BOUND_TO_OTHER_APP`
  - `IN_APP_NO_SESSION`
  - `IN_APP_WITH_SESSION`
- `ContextSessionStatus<T>` as app-layer status object
- `ContextSessionOrchestrator` interface with:
  - `loadStatus(...)`
  - `enterAppContext(...)`
  - `activateSession(...)`
  - `repairDanglingSessionBinding(...)`
  - `clearContext(...)`

Also introduce one tiny central decision point for “session-aware app” scope if needed. Keep it minimal; do not build a generic framework.

**Step 4: Run test + compile gate**

Run:

```bash
mvn test -q -Dtest=ContextSessionOrchestratorImplTest && mvn -q -DskipTests compile
```

Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/ContextSessionState.java \
        feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionStatus.java \
        feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestrator.java \
        feishu-bot-app/src/main/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImpl.java \
        feishu-bot-app/src/test/java/com/qdw/feishu/app/session/ContextSessionOrchestratorImplTest.java
git commit -m "feat(session): add lightweight context session orchestrator"
```

---

## Task 5: Refactor OpenCode orchestration to the app layer and implement the two-phase model

**Files:**
- Create or modify a concrete app-layer OpenCode orchestration/use-case entrypoint that is called by the app-layer message entrypoint
- Modify app-layer OpenCode orchestration classes if present
- Move/refactor OpenCode session-aware orchestration out of domain-layer manager if currently misplaced
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java`
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java` only if it remains as thin domain logic, not app orchestration
- Test: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManagerTest.java`
- Test: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeAppTest.java` (create if missing)
- Add app-layer tests for the new OpenCode orchestration/use-case entrypoint

**Step 1: Write the failing tests**

Add tests for:

```java
@Test
void should_detectUninitialized_when_boundToOpenCodeWithoutSession() { }

@Test
void should_detectInitialized_when_boundToOpenCodeWithSession() { }

@Test
void should_returnOpenCodeGuidance_when_bindingSessionIsDangling() { }

@Test
void should_repairBindingToNull_when_bindingSessionIsDangling() { }

@Test
void should_upgradeBinding_when_sessionIsExplicitlyCreatedOrSelected() { }

@Test
void should_routeThroughAppLayerOrchestrator_inRealOpenCodeFlow() { }

@Test
void should_allow_statusAndProjectsCommands_when_inOpenCodeWithoutSession() { }

@Test
void should_reject_chatCommand_when_inOpenCodeWithoutSession() { }
```

**Step 2: Run tests to verify they fail**

Run:

```bash
mvn test -q -Dtest=OpenCodeSessionManagerTest,OpenCodeAppTest
```

Expected: FAIL because current code still assumes any binding means initialized or still keeps phase logic in the wrong layer.

**Step 3: Write minimal implementation**

Refactor OpenCode flow so that:

- `appId=opencode, sessionId=null` means entered OpenCode app context without active session
- `appId=opencode, sessionId=<id>` means entered OpenCode with active session
- app-layer orchestrator owns loading/repairing/activating context/session status
- app-layer message entrypoint delegates to a concrete app-layer OpenCode service/use case (`OpenCodeAppService` or `OpenCodeContextUseCase`)
- `BotMessageService` returns only a generic routing decision/result; it does not call app-layer OpenCode services
- OpenCode-specific services own command gating and session progression
- dangling concrete binding degrades to `opencode + null` and returns OpenCode guidance
- explicit create/select session upgrades binding to concrete internal session

Do **not** leave the new orchestrator unused.

Also delete or rewrite obsolete OpenCode tests that assume old initialization semantics or topic-binding intermediate states.

**Step 4: Run tests + compile gate**

Run:

```bash
mvn test -q -Dtest=OpenCodeSessionManagerTest,OpenCodeAppTest && mvn -q -DskipTests compile
```

Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-app/src/main/java/com/qdw/feishu/app/session \
        feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java \
        feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeApp.java \
        feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManagerTest.java \
        feishu-bot-domain/src/test/java/com/qdw/feishu/domain/opencode/OpenCodeAppTest.java \
        feishu-bot-app/src/test/java/com/qdw/feishu/app/session
git commit -m "refactor(opencode): implement two-phase app context and session model"
```

---

## Task 6: Slim `BotMessageService` to generic routing only

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`
- Test: `feishu-bot-domain/src/test/java/com/qdw/feishu/domain/service/BotMessageServiceTest.java` (create)

**Step 1: Write the failing test**

Create `BotMessageServiceTest.java` with tests for:

```java
@Test
void should_routePlainTextToHelp_when_contextIsUnbound() { }

@Test
void should_routePlainTextToHelp_when_bindingTargetsMissingOrInvalidApp() { }

@Test
void should_not_persistBinding_forStatelessAppCommand() { }

@Test
void should_not_persistBinding_forStatelessApp_evenWhenReplyReturnsThreadId() { }

@Test
void should_routeBoundContextToOpenCode_when_bindingExists() { }

@Test
void should_rejectOtherAppCommand_when_contextBoundToOpenCode() { }

@Test
void should_leaveBindingUnchanged_when_otherAppCommandIsRejected() { }

@Test
void should_useReturnedThreadId_when_persistingOpenCodeBinding() { }
```

Mock `FeishuGateway`, `ImContextBindingGateway`, `ReplyStrategyFactory`, app registry, and OpenCode-facing app service as appropriate.

**Step 2: Run test to verify it fails**

Run:

```bash
mvn test -q -Dtest=BotMessageServiceTest
```

Expected: FAIL because current code likely persists bindings too broadly, couples too deeply to OpenCode, or does not handle invalid binding fallback.

**Step 3: Write minimal implementation**

Refactor `BotMessageService` so that:

- unbound plain text silently routes to `help`
- invalid/missing-app binding also silently degrades to `help`
- explicit stateless commands execute without persisting `ImContextBinding`
- only OpenCode entry/session flow persists bindings
- explicit `/otherApp` inside bound OpenCode context throws business error and does not rebind
- context persistence uses actual returned thread when present
- no OpenCode-specific session state machine logic lives here
- the service returns only a generic routing decision/result for the app layer to execute
- the service must not inject or reference any app-layer type

**Step 4: Run test + compile gate**

Run:

```bash
mvn test -q -Dtest=BotMessageServiceTest && mvn -q -DskipTests compile
```

Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java \
        feishu-bot-domain/src/test/java/com/qdw/feishu/domain/service/BotMessageServiceTest.java
git commit -m "refactor(routing): keep BotMessageService generic for context binding"
```

---

## Task 7: Align `MessageCommandAdapter` with `FeishuContextResolver` and binding semantics

**Files:**
- Modify: `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/MessageCommandAdapter.java`
- Test: `feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/adapter/MessageCommandAdapterTest.java` (create)

**Step 1: Write the failing test**

Create tests for:

```java
@Test
void should_routePlainTextToHelp_when_noBindingExists() { }

@Test
void should_routePlainTextToBoundApp_when_contextBindingExists() { }

@Test
void should_useThreadFirst_thenChatFallback_when_resolvingContext() { }

@Test
void should_honorChatFallbackBinding_when_threadIdIsAbsent() { }
```

The behavior test should assert that a bound OpenCode context produces `appId = "opencode"` even for plain text.

**Step 2: Run test to verify it fails**

Run:

```bash
mvn test -q -Dtest=MessageCommandAdapterTest
```

Expected: FAIL because current adapter may still use inconsistent lookup logic.

**Step 3: Write minimal implementation**

Refactor adapter to:

- use `FeishuContextResolver` semantics consistently
- use typed `Optional<ImContextBinding>`
- route plain text to bound app only for contexts with persisted binding
- otherwise fall back to `help`
- avoid duplicating app-specific command gating rules

**Step 4: Run test + compile gate**

Run:

```bash
mvn test -q -Dtest=MessageCommandAdapterTest && mvn -q -DskipTests compile
```

Expected: PASS

**Step 5: Commit**

```bash
git add feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/adapter/MessageCommandAdapter.java \
        feishu-bot-infrastructure/src/test/java/com/qdw/feishu/infrastructure/adapter/MessageCommandAdapterTest.java
git commit -m "refactor(adapter): align command adapter with context binding rules"
```

---

## Task 8: Run focused verification and full test suite

**Files:**
- No code changes expected unless verification fails

**Step 1: Run focused tests**

Run:

```bash
mvn test -q -Dtest=ImContextBindingTest,ImContextBindingGatewayImplTest,ContextSessionOrchestratorImplTest,OpenCodeSessionManagerTest,OpenCodeAppTest,BotMessageServiceTest,MessageCommandAdapterTest
```

Expected: all targeted tests pass.

**Step 2: Run full compile + full test suite**

Run:

```bash
mvn -q -DskipTests compile
mvn test
```

Expected: `BUILD SUCCESS`

**Step 3: Commit if any verification fixes were needed**

```bash
git add -A
git commit -m "test: finalize refined ImContextBinding migration verification"
```

Only do this if code changed during verification.

---

## Task 9: Sync docs after implementation

**Files:**
- Modify: `AGENTS.md`
- Modify: `APP_USAGE_GUIDE.md` (only if user-visible OpenCode usage/help text changes)
- Modify: `docs/plans/2026-03-21-im-context-binding-refined-design.md` (set status if desired)

**Step 1: Review whether user-visible behavior changed**

Checklist:

- OpenCode session-external vs session-internal command rules changed?
- routing fallback behavior changed in a way users will notice?
- no-old-migration behavior needs explicit operator note?

**Step 2: Update docs if required**

- update architecture notes in `AGENTS.md`
- update OpenCode usage guidance in `APP_USAGE_GUIDE.md` if command flow changed for users
- update design doc status if implementation is complete

**Step 3: Verify doc consistency**

Run:

```bash
git diff -- AGENTS.md APP_USAGE_GUIDE.md docs/plans/2026-03-21-im-context-binding-refined-design.md
```

Expected: docs reflect actual behavior.

**Step 4: Commit**

```bash
git add AGENTS.md APP_USAGE_GUIDE.md docs/plans/2026-03-21-im-context-binding-refined-design.md
git commit -m "docs: sync refined context binding architecture and usage"
```

Only commit files that actually changed.

---

## Final Verification Checklist

- baseline broken refactor state was stabilized before feature work
- `ImContextBinding` compiles and supports nullable `sessionId`
- `ImContextBindingGatewayImpl` persists nullable sessions correctly
- existing SQLite table handling was explicitly implemented, not assumed
- `TopicAppBinding` is fully removed as a competing architectural path
- stateless apps do not persist bindings
- stateless apps still do not persist bindings even when reply returns/creates a thread
- OpenCode supports `opencode + null` and `opencode + sessionId`
- explicit `/otherApp` inside bound OpenCode context is rejected and binding remains unchanged
- invalid/missing-app binding silently falls back to `help`
- dangling OpenCode concrete session binding degrades to `opencode + null`
- returned Feishu thread is used for binding when applicable
- `MessageCommandAdapter` uses the same context-resolution semantics as the rest of the system
- all targeted tests pass
- full Maven test suite passes

---

Plan complete and updated in `docs/plans/2026-03-21-im-context-binding-refined-implementation.md`.

Recommended execution mode:

**Subagent-Driven (this session)** — stabilize first, then execute task-by-task with review after each structural step.

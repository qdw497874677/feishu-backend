# Phase 4: Code Cleanup and Refactoring - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Pay down accumulated technical debt after 3 feature-building phases. No new features — purely structural, safety, and maintainability improvements. Scope: security fixes, COLA architecture compliance (Spring annotation removal from domain layer), unified state model, data integrity fixes, large file decomposition, and targeted test coverage.

This is the "clean house" phase — everything built in Phases 1-3 works correctly, now we make it architecturally clean, safe, and maintainable.

</domain>

<decisions>
## Implementation Decisions

### Scope Prioritization
- **D-01:** Phase 4 covers four categories: security fixes, architecture cleanup, data integrity, and large file decomposition. All four are in scope — this is a comprehensive cleanup phase.
- **D-02:** REQUIREMENTS.md V2-03 (unified state model — merge TopicState + ContextSessionState into single enum) is included in this phase. 56 references across codebase need updating.
- **D-03:** BotRoutingDecision leaking FishuAppI app reference is fixed — remove `app` field, resolve via AppRegistry.getApp(appId) at execution time.
- **D-04:** FishuAppI @Deprecated API issue is fixed — either complete migration to execute(UnifiedCommand) or remove @Deprecated annotation. Decision on which approach is left to researcher/planner based on effort analysis.

### Security Fixes
- **D-05:** Test controller `MessageTestController` profile changed from `@Profile({"dev", "test", "default"})` to `@Profile({"dev", "test"})` — removing `"default"` so it's not active in production.
- **D-06:** Message content logging downgraded from INFO to DEBUG across all files: ReceiveMessageListenerExe, OpenCodeApp, HelpApp, TimeApp, HistoryApp, FeishuGatewayImpl. Log messageId at INFO for traceability instead.
- **D-07:** Add `application-prod.yml` profile with appropriate production settings (log levels, no test endpoints).

### COLA Spring Annotation Removal
- **D-08:** Complete removal — domain module's `pom.xml` removes dependencies on `spring-context` and `spring-boot-autoconfigure`. Domain module becomes framework-agnostic POJOs.
- **D-09:** Batch strategy — three phases of removal:
  1. Config classes first: `FeishuReplyProperties`, `CardProperties` (move to infrastructure)
  2. Service classes: `BotMessageService`, `EventProcessor`, `TopicCommandValidator` (register via `@Bean` in `DomainServiceConfig`)
  3. Application classes: All `FishuAppI` implementations, `OpenCodeSessionManager`, `OpenCodeCommandHandler`, etc. (register via `@Bean`)
- **D-10:** All 28 domain files have `@Component`/`@Service`/`@ConfigurationProperties`/`@Autowired`/`@Lazy` removed. Registration moves to infrastructure's `DomainServiceConfig.java` via `@Bean` factory methods.
- **D-11:** Verification: all 355 existing tests must pass after removal + new critical tests for DomainServiceConfig bean registration.

### Unified State Model (V2-03)
- **D-12:** Merge `TopicState` (3 values: NON_TOPIC, UNINITIALIZED, INITIALIZED) and `ContextSessionState` (4 values: UNBOUND, BOUND_TO_OTHER_APP, IN_APP_NO_SESSION, IN_APP_WITH_SESSION) into a single state enum.
- **D-13:** The unified model should be at least as expressive as the current combined set — `BOUND_TO_OTHER_APP` has no TopicState equivalent and must be preserved in the new model.

### Data Integrity
- **D-14:** Extract shared `@Bean DataSource sqliteDataSource()` in a configuration class. Both `AppSessionGatewayImpl` and `ImContextBindingGatewayImpl` inject the shared DataSource instead of creating independent instances.
- **D-15:** Fix non-atomic SQLite upsert in `ImContextBindingGatewayImpl.bind()` — use `INSERT INTO ... ON CONFLICT(context_key) DO UPDATE SET ...` instead of separate SELECT + INSERT/UPDATE.
- **D-16:** Fix optimistic lock race in `AppSessionGatewayImpl.updateSession()` — remove redundant preliminary `getVersion()` SELECT, rely on UPDATE WHERE clause + check `updated == 0`.

### Large File Decomposition
- **D-17:** `OpenCodeGatewayImpl` (1,137 lines) split by API resource: SessionApi, ProjectApi, ChatApi, HealthApi. Each sub-class handles HTTP calls for one OpenCode resource. Main class becomes a facade delegating to sub-classes.
- **D-18:** `OpenCodeCommandHandler` (661 lines) extract sub-command handlers — each sub-command (connect, session, chat, reset, status, help, etc.) becomes a dedicated handler method or small class. Main switch statement becomes a dispatcher.

### Test Coverage Strategy
- **D-19:** Test approach: "随拆分补充" — tests added as part of each refactoring task, not as a separate effort. OpenCodeGatewayImpl tests come from the split, not from testing the monolith.
- **D-20:** All major refactoring tasks trigger new tests: annotation removal (verify injection works), data layer (verify DataSource sharing), security (verify controller not active), file split (verify each sub-class).

### Agent's Discretion
- Exact naming for split API classes in OpenCodeGatewayImpl decomposition
- Whether OpenCodeCommandHandler sub-command handlers are methods or separate classes
- Exact unified state enum values and naming
- Exact production profile settings in application-prod.yml
- Order of batch execution within each removal phase
- Whether to also fix the streaming handler synchronized flush lock (V2-05) while in the area

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture reference
- `AGENTS.md` — COLA 架构规范、代码放置决策树、依赖规则
- `.planning/codebase/ARCHITECTURE.md` — COLA 四层架构、数据流图
- `.planning/codebase/CONVENTIONS.md` — 编码规范、命名模式、Spring 注解现状
- `.planning/codebase/QUALITY.md` — 代码质量分析、测试覆盖、日志规范

### Codebase concerns
- `.planning/codebase/CONCERNS.md` — 所有已知问题、安全风险、性能瓶颈、架构违规的详细记录

### Prior phase context
- `.planning/phases/01-context-foundation/01-CONTEXT.md` — AppExecutionResult DTO、MessageContext pipeline、binding propagation 基础设施决策
- `.planning/phases/03-cards-guided-flows/03-CONTEXT.md` — CardContent/CardRenderer、WizardManager、SessionInfo 等卡片系统决策

### Key files for Spring removal
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/config/DomainServiceConfig.java` — 现有的 @Bean 注册配置（需要扩展）
- `feishu-bot-domain/pom.xml` — 当前包含 spring-context 和 spring-boot-autoconfigure 依赖（需要移除）
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/config/FeishuReplyProperties.java` — 需要移到 infrastructure
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/config/CardProperties.java` — 需要移到 infrastructure

### Key files for security fixes
- `feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/test/MessageTestController.java` — 测试控制器（修改 @Profile）
- `feishu-bot-start/src/main/resources/application.yml` — 日志级别配置
- `feishu-bot-app/src/main/java/com/qdw/feishu/app/listener/ReceiveMessageListenerExe.java` — 消息日志（降级到 DEBUG）

### Key files for data integrity
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/ImContextBindingGatewayImpl.java` — 绑定持久化（upsert 修复）
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/AppSessionGatewayImpl.java` — 会话持久化（乐观锁修复、DataSource 共享）

### Key files for large file split
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java` — 1,137 行，按 API 资源拆分
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java` — 661 行，提取子命令处理器

### Key files for unified state model
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/topic/TopicState.java` — 当前 3 值状态枚举
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/session/ContextSessionState.java` — 当前 4 值状态枚举

### Requirements tracking
- `.planning/REQUIREMENTS.md` — V2-03 (统一状态模型) 从 v2 延迟项纳入本阶段

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DomainServiceConfig.java`: Already has `@Bean` factory methods for strategy factories — extend with all 28 domain class registrations
- `FeishuContextResolver`: Platform-agnostic context resolution — works without Spring, good example for POJO domain
- `AppRegistry`: Already manages app lifecycle — can be the lookup point after BotRoutingDecision refactor
- `AppExecutionResult`: Phase 1 result DTO — stable, doesn't need changes

### Established Patterns
- **Constructor injection**: Universal — all 28 domain classes already use constructor injection. Removing annotations won't change injection points, just registration.
- **Gateway pattern**: Domain defines interface, infrastructure implements — new split API classes follow this
- **Strategy pattern**: ReplyStrategyFactory with EnumMap — sub-command handlers could follow similar dispatch
- **Anti-corruption layer**: Keep domain clean of external SDKs — Spring removal continues this philosophy

### Integration Points
- `DomainServiceConfig.java` in infrastructure — primary integration point for all bean registrations
- `pom.xml` dependency changes — removing Spring deps from domain module affects compilation
- `application-prod.yml` — new file in start module resources
- Test base classes — may need `@Import(DomainServiceConfig.class)` or `@ContextConfiguration` updates if tests relied on component scanning

</code_context>

<specifics>
## Specific Ideas

- Phase 4 is a "clean house" phase — everything works, now make it architecturally correct
- The batch strategy (config → service → app) ensures each batch is independently verifiable
- Splitting OpenCodeGatewayImpl by API resource mirrors how OpenCode server itself organizes its endpoints
- Test coverage grows naturally from refactoring rather than being a separate task

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 04-code-cleanup-and-refactoring*
*Context gathered: 2026-05-04*

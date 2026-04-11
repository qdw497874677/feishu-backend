# Phase 3: Cards & Guided Flows - Context

**Gathered:** 2026-04-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Add interactive card buttons for project/session selection, step-by-step onboarding wizard for first-time users, and enhanced session list with context. After this phase, both command-line and visual entry points work.

Requirements: CARD-01, CARD-02, CARD-03

</domain>

<decisions>
## Implementation Decisions

### Card Context Passing (CARD-01)
- **D-01:** Card button的 `value` 从简单的 `{"action":"xxx"}` 扩展为 `{"action":"xxx","chatId":"...","topicId":"...","sessionId":"..."}`。发卡时将上下文嵌入 value，点击时 P2CardActionTrigger 直接解析——无需服务端状态映射。
- **D-02:** Card button 点击的响应采用混合方式——简单确认类操作（如绑定成功）就地更新原卡片（使用已有 CardKit update API）；复杂结果（如会话列表）发新卡片消息。

### Onboarding Wizard (CARD-02)
- **D-03:** 向导为 3 步流程：选项目（项目列表卡片+按钮） → 选/建会话（会话列表卡片+按钮+新建按钮） → 确认绑定（确认卡片，更新原卡片显示绑定状态）。
- **D-04:** 向导优先模式——向导进行中时，非向导命令被拦截并提示"请先完成向导"。保证流程完整性。
- **D-05:** 向导在首次进入未绑定话题时自动触发，替代当前的 `buildInitializationGuide()` 纯文本引导。无需新增命令。

### Enhanced Session List (CARD-03)
- **D-06:** 会话列表的展示形式与核心 app 机制解耦——由 IM 渠道和配置层决定。对于飞书渠道，同时支持卡片和纯文本两种形式，默认配置为卡片优先。
- **D-07:** 每个会话条目显示：会话名称、最后提示词摘要（截断）、相对时间戳（"5分钟前"）。足够让用户识别会话。
- **D-08:** 会话列表卡片底部添加"+ 新建会话"按钮，点击后创建新会话并绑定。

### Card Building Approach
- **D-09:** 采用 domain 抽象 + infrastructure 实现方案——domain 层定义卡片内容模型（CardContent: 标题/段落/按钮列表等），infrastructure 层实现 FeishuCardBuilder 将其转换为飞书 schema 2.0 JSON。符合 COLA 架构。
- **D-10:** HelpApp 现有的 `buildCardHelpJson()` 内联 JSON 统一迁移到新构建器，消除手写 JSON，保持一致性。

### Agent's Discretion
- CardContent 模型的具体字段设计（段落、分割线、图片等元素的抽象粒度）
- 向导卡片的具体视觉布局（header 颜色、按钮排列方式）
- 向导拦截的具体实现位置（app 层还是 domain 层的命令路由中）
- 会话列表卡片中摘要的截断长度
- 配置项的具体命名和默认值

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Card interaction pipeline
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/MessageListenerGatewayImpl.java` — P2CardActionTrigger 注册和 card button 点击处理（伪 Message 构建逻辑在 handleCardAction 方法）
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/gateway/CardGateway.java` — CardKit domain 接口：createCard/updateCard/sendCardMessage
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/CardGatewayImpl.java` — CardKit 实现：buildCardJson() 生成 schema 2.0 JSON，create/update 使用 httpClient.cardkit().v1()
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/card/StreamingCardManager.java` — Card 生命周期管理（cardId→sequence ConcurrentHashMap），可复用 create/update/cleanup 模式

### Existing card builder (migration target)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/HelpApp.java` — buildCardHelpJson() 是当前唯一的交互式卡片构建代码，schema 2.0 手写 Map 层级结构
- `feishu-bot-start/src/test/java/com/qdw/feishu/HelpAppCardButtonJsonTest.java` — HelpApp 卡片 JSON 结构测试

### Onboarding / guide text (replacement targets)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeMessageFormatter.java` — buildInitializationGuide()、buildConnectGuide()、buildConnectSuccessResponse() 等纯文本引导
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/NextStepSuggester.java` — 命令后的下一步文本提示

### Session management
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeSessionManager.java` — handleSessionsCommand()、handleListSessions()、detectTopicState() 等会话管理逻辑
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/OpenCodeGatewayImpl.java` — formatProjectSessionList() 当前纯文本会话列表实现；listSessions()、listRecentSessions() HTTP 调用

### Command routing (card action integration)
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/opencode/OpenCodeCommandHandler.java` — 命令路由 switch 语句，新的卡片 action 需要在此集成
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/command/UnifiedCommand.java` — 已携带 EventSource.CARD 和 cardToken 字段
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/topic/TopicState.java` — NON_TOPIC/UNINITIALIZED/INITIALIZED 三态，向导在 UNINITIALIZED 触发

### Card configuration
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/config/CardProperties.java` — opencode.card.* 配置项
- `feishu-bot-start/src/main/resources/application.yml` — 当前 card 配置（enabled、fallback-on-error、title、thinking/processing/complete text）

### Interactive message sending
- `feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/gateway/FeishuGatewayImpl.java` — sendInteractiveMessage() 发送原始卡片 JSON

### Phase 1 context (prior decisions)
- `.planning/phases/01-context-foundation/01-CONTEXT.md` — AppExecutionResult DTO、MessageContext pipeline、binding propagation 等基础设施决策

### Architecture reference
- `.planning/codebase/ARCHITECTURE.md` — COLA 四层架构、数据流图
- `.planning/codebase/CONVENTIONS.md` — 编码规范、命名模式
- `AGENTS.md` — 项目规范（COLA 架构规范、代码放置决策树）

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **CardGateway + CardGatewayImpl**: CardKit API 的 create/update/send 已封装，可直接用于向导卡片和会话列表卡片
- **StreamingCardManager**: cardId→sequence 生命周期管理模式可参考，但向导卡片不需要流式更新
- **HelpApp.buildCardHelpJson()**: 已有的 schema 2.0 卡片构建代码，可作为新 CardBuilder 的参考实现
- **P2CardActionTrigger handler**: 按钮点击→伪 Message 的管道已存在，需扩展 value 解析逻辑
- **AppExecutionResult**: Phase 1 引入的结构化返回 DTO，支持 text()/noReply()/withSession()，可扩展支持卡片结果
- **UnifiedCommand + EventSource.CARD**: 统一命令模型已区分消息来源和卡片来源

### Established Patterns
- **Gateway pattern**: domain 定义接口，infrastructure 实现——新的 CardBuilder 遵循此模式
- **Strategy pattern**: ReplyStrategyFactory + EnumMap——可能需要新增卡片回复策略
- **Constructor injection**: 全局使用，无字段注入
- **Anti-corruption layer**: MessageEventParserImpl 隔离 SDK——卡片构建器同理隔离飞书卡片格式
- **Config properties**: CardProperties 模式（@ConfigurationProperties）——新增配置项遵循此模式

### Integration Points
- **MessageListenerGatewayImpl.handleCardAction()**: 扩展 value 解析以支持富上下文
- **OpenCodeCommandHandler.handle()**: 新增向导相关的 action 路由
- **OpenCodeSessionManager**: 向导状态检测和拦截逻辑
- **OpenCodeMessageFormatter**: 纯文本引导方法需被卡片版本替代
- **BotMessageAppService.sendReply()**: 需支持卡片类型的回复（不只是纯文本）
- **AppExecutionResult**: 可能需要新增 card() 工厂方法或 cardJson 字段

</code_context>

<specifics>
## Specific Ideas

- 会话列表展示形式与核心 app 机制解耦，作为 IM 渠道配置层面的选择——未来替换 IM 平台时只需实现新的卡片构建器
- 向导优先模式保证首次用户不会迷路，但允许的操作仅限向导范围内的命令

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 03-cards-guided-flows*
*Context gathered: 2026-04-11*

# Requirements: OpenCode Interactive Flow Redesign

**Defined:** 2026-04-07
**Core Value:** A user in a bound topic can type plain text and get an AI response — no command prefix, no broken context, no ghost bubbles.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### 上下文与绑定 (Context & Binding)

- [ ] **CTX-01**: 回复创建话题时，系统自动将 IM 绑定从 chatId 传播到新的 threadId，用户在话题内继续操作无需重新绑定
- [ ] **CTX-02**: sessionId 通过方法返回值/字段传递，不再从格式化的 markdown 文本中解析提取
- [ ] **CTX-03**: 每条消息只查询一次绑定数据库，通过 MessageContext 参数在管道中传递，消除冗余查询
- [ ] **CTX-04**: IM 绑定层（ImContextBinding）和应用会话层（AppSession）保持独立存储和管理，支持未来替换 IM 平台
- [ ] **CTX-05**: 旧话题/未绑定话题访问时，静默降级为帮助引导，不报错不崩溃

### 对话体验 (Conversation UX)

- [ ] **UX-01**: 已绑定且有活跃会话的话题中，用户直接输入文字即视为聊天提示，无需 `/oc chat` 前缀
- [ ] **UX-02**: 异步任务路径返回 null 而非空字符串，不再出现空消息气泡
- [ ] **UX-03**: 机器人回复中包含当前绑定状态指示（项目名/会话ID），用户随时知道"我在哪"

### 命令与路由 (Commands & Routing)

- [ ] **CMD-01**: 重新设计命令集，匹配手动控制流（项目选择 → 会话选择/创建 → 绑定 → 对话）
- [ ] **CMD-02**: 每种状态下只显示/允许有效的命令，白名单包含所有别名
- [ ] **CMD-03**: 所有错误消息包含下一步操作建议，不只是报错原因
- [ ] **CMD-04**: 每个操作完成后，系统性地提示用户下一步该做什么

### 卡片与引导流程 (Cards & Guided Flow)

- [ ] **CARD-01**: 交互式卡片按钮可用于项目选择和会话选择，卡片事件携带完整的对话上下文（chatId/topicId）
- [ ] **CARD-02**: 首次使用时提供分步卡片向导（选项目 → 选会话 → 确认绑定），降低使用门槛
- [ ] **CARD-03**: 会话列表展示最后提示词摘要和相对时间戳，用户能通过上下文识别会话

### 兼容性 (Compatibility)

- [ ] **COMPAT-01**: 现有261个测试全部通过，无状态应用（Help/Time/Bash/History）行为不变
- [ ] **COMPAT-02**: 群聊中进行项目/会话选择，话题内进行对话（group→topic 模型）

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### 对话增强

- **V2-01**: `/oc cn <提示词>` 一步创建会话并发送提示（chatnow 完整实现）
- **V2-02**: `/oc resume` 一键重新绑定最近使用的会话（快速恢复）
- **V2-03**: 统一状态模型 — 将 TopicState 和 ContextSessionState 合并为单一枚举（如需未来简化维护）

### 性能

- **V2-04**: 会话过期清理 — 利用已有的 `expires_at` 列自动清理过期会话
- **V2-05**: 流式处理 flush 锁从实例级改为会话级，支持更高并发

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| 自动检测项目 | 启发式不可靠，违反手动控制原则 |
| 同一话题内切换项目 | 混合上下文导致会话历史混乱，打开新话题即可 |
| 迁移旧话题绑定数据 | 迁移代码复杂、边界情况多，旧话题静默降级为帮助 |
| 通用会话框架 | 只有 OpenCode 需要会话感知，YAGNI |
| Webhook 通信模式 | 项目铁律：仅 WebSocket 长连接 |
| 用户级认证 | 飞书 openId 已足够 |
| 自然语言命令解析 | 模糊不可靠，命令必须明确 |
| 自动创建会话（无需用户选择项目） | 隐藏项目选择步骤，用户不知道在哪个上下文 |
| 多用户共享会话 | 并发命令冲突，每个话题一个绑定 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CTX-01 | Phase 1 | Pending |
| CTX-02 | Phase 1 | Pending |
| CTX-03 | Phase 1 | Pending |
| CTX-04 | Phase 1 | Pending |
| CTX-05 | Phase 1 | Pending |
| UX-01 | Phase 2 | Pending |
| UX-02 | Phase 2 | Pending |
| UX-03 | Phase 2 | Pending |
| CMD-01 | Phase 2 | Pending |
| CMD-02 | Phase 2 | Pending |
| CMD-03 | Phase 2 | Pending |
| CMD-04 | Phase 2 | Pending |
| CARD-01 | Phase 3 | Pending |
| CARD-02 | Phase 3 | Pending |
| CARD-03 | Phase 3 | Pending |
| COMPAT-01 | Phase 1 | Pending |
| COMPAT-02 | Phase 2 | Pending |

**Coverage:**
- v1 requirements: 17 total
- Mapped to phases: 17
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 after roadmap creation*

# Phase 3: Cards & Guided Flows - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-11
**Phase:** 03-cards-guided-flows
**Areas discussed:** 卡片上下文传递, 入门向导流程, 增强会话列表, 卡片构建方式

---

## 卡片上下文传递

### Q1: 卡片按钮点击如何携带上下文？

| Option | Description | Selected |
|--------|-------------|----------|
| 扩展 action value（推荐） | 按钮的 value 从简单的 {"action":"xxx"} 扩展为 {"action":"xxx","chatId":"...","topicId":"...","sessionId":"..."} —— 发卡时就把上下文嵌入进去，点击时直接解析 | ✓ |
| 维持现有机制 | 继续用伪 Message 的 chatId/topicId（取自事件本身），不额外嵌入数据。如果卡片是在正确上下文发的，点击也会在正确上下文 | |
| 服务端状态映射 | 发卡时服务端记录 cardId → 上下文映射，点击时通过 cardId 反查上下文。更安全但增加存储复杂度 | |

**User's choice:** 扩展 action value（推荐）
**Notes:** 无额外说明

### Q2: 卡片按钮点击的响应方式？

| Option | Description | Selected |
|--------|-------------|----------|
| 更新原卡片（推荐） | 点击后就地更新当前卡片内容（已有 CardKit update API）—— 无新消息气泡，体验更干净 | |
| 发新卡片消息 | 点击后发一张新卡片作为回复，原卡片保持不变。更简单但聊天记录会有多张卡片 | |
| 混合方式 | 简单确认类更新原卡片，复杂结果（如会话列表）发新卡片 | ✓ |

**User's choice:** 混合方式
**Notes:** 无额外说明

---

## 入门向导流程

### Q3: 入门向导应该有几个步骤？

| Option | Description | Selected |
|--------|-------------|----------|
| 2步：选项目→选/建会话（推荐） | 简洁——项目卡片（带按钮）→ 会话卡片（带按钮）→ 自动绑定到话题。绑定成功时显示确认状态（更新原卡片） | |
| 3步：选项目→选/建会话→确认绑定 | 更明确——最后一步让用户确认项目+会话组合再绑定，避免误点 | ✓ |
| 1步：一张卡片搞定 | 单卡片显示项目列表+会话列表，点一次就绑定。信息密度高但步骤最少 | |

**User's choice:** 3步：选项目→选/建会话→确认绑定
**Notes:** 无额外说明

### Q4: 向导过程中用户中途打字输入命令怎么处理？

| Option | Description | Selected |
|--------|-------------|----------|
| 向导可中断（推荐） | 用户打字输入命令时正常执行，向导状态自然失效。不限制用户——熟练用户可以跳过向导直接打命令 | |
| 向导优先 | 向导进行中时，非向导命令被拦截并提示"请先完成向导"。保证流程完整性但可能让用户不耐烦 | ✓ |
| 向导可跳过 | 卡片上加一个"跳过向导"按钮，点击后显示命令列表让用户自行操作 | |

**User's choice:** 向导优先
**Notes:** 无额外说明

### Q5: 向导的触发时机？

| Option | Description | Selected |
|--------|-------------|----------|
| 首次进入未绑定话题自动触发（推荐） | 用户在未初始化的话题中发消息时，自动弹出向导卡片。当前 buildInitializationGuide() 的卡片版替代 | ✓ |
| /oc start 命令触发 | 新增一个 start 命令专门启动向导，不自动弹出 | |
| 两种都支持 | 未绑定时自动弹出 + 也可以 /oc start 手动触发重新开始 | |

**User's choice:** 首次进入未绑定话题自动触发（推荐）
**Notes:** 无额外说明

---

## 增强会话列表

### Q6: 会话列表应该用卡片还是纯文本？

| Option | Description | Selected |
|--------|-------------|----------|
| 卡片+按钮（推荐） | 每个会话是卡片上的一行，带"绑定"按钮。点击即绑定，无需复制粘贴 sessionId | |
| 纯文本保持现状 | 继续用文本列表，只增加最后提示词摘要和时间戳。简单但用户仍需手动打命令绑定 | |
| 混合 | 向导流程中用卡片+按钮，/oc sessions 命令继续用文本。满足两种场景 | |

**User's choice:** 自定义回答——两者应该都支持，具体的形式和核心 app 机制无关，和 IM 渠道和配置相关，对于飞书，两者都支持，且先配置为卡片优先
**Notes:** 会话列表展示形式与核心 app 机制解耦，由 IM 渠道和配置层决定

### Q7: 每个会话条目应该显示哪些信息？

| Option | Description | Selected |
|--------|-------------|----------|
| 摘要+时间（推荐） | 会话名称、最后提示词摘要（截断）、相对时间戳（"5分钟前"）。足够让用户识别会话 | ✓ |
| 摘要+时间+项目名 | 额外显示项目名称，适合多项目用户。信息更完整但占用更多空间 | |
| 简洁模式 | 只显示会话名称和相对时间。最简洁，但可能不够区分多个会话 | |

**User's choice:** 摘要+时间（推荐）
**Notes:** 无额外说明

### Q8: 会话列表是否支持"新建会话"按钮？

| Option | Description | Selected |
|--------|-------------|----------|
| 是，底部加按钮（推荐） | 会话列表卡片底部添加"+ 新建会话"按钮，点击后创建新会话并绑定 | ✓ |
| 不需要 | 新建会话继续用 /oc new 命令，卡片只负责展示和选择已有会话 | |

**User's choice:** 是，底部加按钮（推荐）
**Notes:** 无额外说明

---

## 卡片构建方式

### Q9: 是否创建可复用的卡片构建器？

| Option | Description | Selected |
|--------|-------------|----------|
| 创建域层 CardBuilder（推荐） | 在 domain/card/ 下创建流式 API 的卡片构建器，封装 header、body、button 等元素。所有卡片统一使用，消除重复 JSON 手写 | |
| 模板方式 | 创建 JSON 模板文件，运行时填充变量。更灵活但编译期无法检查类型 | |
| 继续手写 | 每张卡片继续内联手写 JSON。简单直接，但代码重复多 | |

**User's choice:** 自定义回答——帮我分析下放在哪里合适，它是和具体的 IM 相关的
**Notes:** 用户指出卡片构建器与 IM 平台耦合，需要考虑架构分层

### Q10: 卡片构建器的架构分层方案？

| Option | Description | Selected |
|--------|-------------|----------|
| domain抽象+infra实现（推荐） | domain 层定义卡片内容模型（CardContent: 标题/段落/按钮列表），infrastructure 层实现 FeishuCardBuilder 把它转为 schema 2.0 JSON。符合 COLA 架构 | ✓ |
| 纯 infra 层 | 整个构建器都在 infrastructure，domain 层用 Gateway 接口发卡片时直接传文本/按钮参数。更简单但 domain 层无法描述卡片结构 | |
| 你决定 | 让下游 agent 根据实际情况决定分层方案 | |

**User's choice:** domain抽象+infra实现（推荐）
**Notes:** 无额外说明

### Q11: 现有 HelpApp 的内联卡片 JSON 是否迁移到新构建器？

| Option | Description | Selected |
|--------|-------------|----------|
| 是，统一迁移（推荐） | HelpApp 现有的 buildCardHelpJson() 迁移到新构建器，消除手写 JSON。保持一致性 | ✓ |
| 不迁移，仅新卡片用 | 现有代码不动，只有 Phase 3 新建的卡片用新构建器。降低风险但不一致 | |
| 你决定 | 让下游 agent 根据工作量决定是否迁移 | |

**User's choice:** 是，统一迁移（推荐）
**Notes:** 无额外说明

---

## Agent's Discretion

- CardContent 模型的具体字段设计
- 向导卡片的具体视觉布局
- 向导拦截的具体实现位置
- 会话列表摘要截断长度
- 配置项命名和默认值

## Deferred Ideas

None — discussion stayed within phase scope.

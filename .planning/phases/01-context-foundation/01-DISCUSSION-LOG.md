# Phase 1: Context Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 01-context-foundation
**Areas discussed:** Binding propagation strategy, Request-scoped caching approach, Graceful degradation behavior, Structured sessionId passing shape

---

## Binding Propagation Strategy

### Q1: 回复创建新话题时，chatId 绑定如何迁移到 threadId？

| Option | Description | Selected |
|--------|-------------|----------|
| 完整复制 | 回复收到 threadId 后，立即将 chatId 上的完整绑定（appId + sessionId）复制到 threadId。简单直接，一次完成。 | ✓ |
| 双查找回退 | 查 threadId 绑定找不到时，自动回退查 chatId。不需要复制，但每次复杂查询。 | |
| 复制+保留原始 | 复制到 threadId 同时保留 chatId 上的绑定。后续可从群聊主聊查到绑定状态。 | |

**User's choice:** 完整复制
**Notes:** 无

### Q2: 完整复制发生在哪个环节？

| Option | Description | Selected |
|--------|-------------|----------|
| 回复后立即复制 | SendResult 返回 threadId 后，在 BotMessageAppService.sendReply() 中立即复制。同步完成，下条消息就能找到绑定。 | ✓ |
| 延迟复制 | 下条消息进来时，发现 threadId 无绑定再触发复制。容错性更高但复杂。 | |

**User's choice:** 回复后立即复制
**Notes:** 无

---

## Request-Scoped Caching Approach

### Q3: 如何确保每条消息只查一次绑定数据库？

| Option | Description | Selected |
|--------|-------------|----------|
| MessageContext 参数传递 | 管道入口查一次，封装为 MessageContext 对象，通过方法参数向下传递。显式、可测试、无魔法。 | ✓ |
| ThreadLocal 缓存 | 入口查一次存 ThreadLocal，后续调用方自动读取。不用改方法签名，但隐式耦合、异步危险。 | |
| 你来决定 | 只要能达到"每条消息只查一次"的效果就行 | |

**User's choice:** MessageContext 参数传递
**Notes:** 无

### Q4: MessageContext 应该包含哪些内容？

| Option | Description | Selected |
|--------|-------------|----------|
| 绑定+会话 | 包含 ImContextRef、ImContextBinding（可为 null）、AppSession（可为 null）。一次查询覆盖所有下游需求。 | ✓ |
| 仅绑定 | 只包含 ImContextRef + ImContextBinding。AppSession 由各用户自行查。 | |
| 你来决定 | 字段设计交给研究和规划阶段 | |

**User's choice:** 绑定+会话
**Notes:** 无

---

## Graceful Degradation Behavior

### Q5: 旧话题/未绑定话题的降级展示方式？

| Option | Description | Selected |
|--------|-------------|----------|
| 简短文字提示 | 一行文字"该话题未绑定会话，请在群聊中使用 /oc projects 开始"。简洁不干扰。 | ✓ |
| 带按钮的引导卡片 | 卡片显示"开始使用"按钮 + 简要说明。更友好但复杂度高。 | |
| 完全静默 | 不回复任何内容。用户可能困惑但零干扰。 | |

**User's choice:** 简短文字提示
**Notes:** 无

### Q6: 降级时是否记录日志？

| Option | Description | Selected |
|--------|-------------|----------|
| DEBUG 级别记录 | 记录 threadId + 降级原因，方便排查但不刷屏。 | ✓ |
| 不记录 | 旧话题复访是正常行为，无需记录。 | |
| 你来决定 | 日志级别交给实现阶段 | |

**User's choice:** DEBUG 级别记录
**Notes:** 无

---

## Structured sessionId Passing Shape

### Q7: 用什么替代从文本解析 sessionId？

| Option | Description | Selected |
|--------|-------------|----------|
| 结果 DTO | 创建 OpenCodeExecutionResult（或类似），包含 replyContent + sessionId + sessionCreated 等字段。execute() 返回此对象而非 String。 | ✓ |
| 回调/事件 | 会话创建后发布事件，监听方处理绑定。解耦但复杂度高。 | |
| 你来决定 | 只要不再从文本解析就行 | |

**User's choice:** 结果 DTO
**Notes:** 无

### Q8: 结果 DTO 的影响范围？

| Option | Description | Selected |
|--------|-------------|----------|
| 所有 App 统一 | 所有 FishuAppI.execute() 都返回结果对象而非 String。统一但改动大。 | ✓ |
| 仅 OpenCode | 只改 OpenCode ，其他 App 保持 String。最小改动。 | |

**User's choice:** 所有 App 统一
**Notes:** 确认了这是破坏性变更，影响 FishuAppI 接口和所有 5 个 App 实现，以及相关测试。用户明确选择一次到位。

### Q9: 确认统一所有 App 的范围？

| Option | Description | Selected |
|--------|-------------|----------|
| 确认，统一所有 | 一次到位，所有 App 统一返回结果对象。测试跟着改。 | ✓ |
| 改主意，仅 OpenCode | 只改 OpenCode，其他 App 保持 String。减少风险。 | |

**User's choice:** 确认，统一所有
**Notes:** 无

---

## Agent's Discretion

- MessageContext 字段命名和类位置
- AppExecutionResult 字段集（replyContent + sessionId 之外的字段）
- 简单 App 的便捷工厂方法（如 `AppExecutionResult.text("Hello")`）
- 降级提示的具体文案
- 是否顺带修复 AppSessionGatewayImpl 的冗余 getVersion() 检查

## Deferred Ideas

无 — 讨论保持在 phase 范围内。

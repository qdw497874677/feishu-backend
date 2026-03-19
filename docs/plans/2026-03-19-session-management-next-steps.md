# 会话管理抽象 - 下一步计划

> 日期：2026-03-19
> 状态：进行中

---

## 已完成 ✅

### Phase 1-3: 核心实现
- [x] 核心会话类（`SessionState`, `SessionConfig`, `TypeToken`, `AppSession`, `AppSessionInfo`）
- [x] `AppSessionGateway` 接口定义
- [x] `AppSessionGatewayImpl` 实现
- [x] `TopicMapping` → `SessionContext` 重命名
- [x] 修复编译错误（移除 `CardActionTriggerHandler`）
- [x] 修复测试（`OpenCodeAppTest`, `OpenCodeStreamingHandlerTest`）

---

## 待完成 🔄

### 任务组 1：完善会话管理系统（合并）

**目标**：将 OpenCode 迁移到新的 `AppSessionGateway`，并清理遗留代码

**包含子任务**：
- [ ] 定义 `OpenCodeSessionData` 类（存储 sessionId, projectId 等）
- [ ] 修改 `OpenCodeSessionManager` 使用 `AppSessionGateway`
- [ ] 更新 `OpenCodeCommandHandler` 适配新接口
- [ ] 删除 `OpenCodeMetadata.java`（已被新系统替代）
- [ ] 运行测试验证

**收益**：
- 支持每个 App 多个会话
- 支持乐观锁并发控制
- 支持泛型类型安全的会话数据
- 代码库更整洁

**优先级**：低（当前实现可正常工作）

---

### 任务组 2：修复卡片测试（独立）

**问题**：`HelpAppCardButtonJsonTest` 3个测试失败

```
HelpAppCardButtonJsonTest.should_generateCorrectNumberOfButtons
HelpAppCardButtonJsonTest.should_printCardJson_forDebugging
HelpAppCardButtonJsonTest.should_useStringValue_forButtonValue
```

**错误**：`Failed to build card JSON`

**步骤**：
- [ ] 调查失败原因（可能是 SDK API 变更）
- [ ] 修复或删除这些测试

**优先级**：中（非阻塞，但影响 CI）

---

## 建议下一步

| 选项 | 任务组 | 预计耗时 | 优先级 |
|------|--------|----------|--------|
| A | 完善会话管理系统 | 2-3小时 | 低 |
| B | 修复卡片测试 | 30分钟 | 中 |

---

## 当前架构状态

```
┌─────────────────────────────────────────────────────┐
│                    App Layer                         │
│  OpenCodeApp → OpenCodeSessionManager                │
│                    ↓                                 │
│           SessionContextGateway ←── 当前使用         │
│                    ↓                                 │
│           AppSessionGateway ←── 新接口（未使用）      │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│               Infrastructure Layer                   │
│  SessionContextSqliteGateway                         │
│  AppSessionGatewayImpl (基于 SessionContextGateway)  │
└─────────────────────────────────────────────────────┘
```

**结论**：新架构已就绪，OpenCode 暂用旧接口，可随时迁移。

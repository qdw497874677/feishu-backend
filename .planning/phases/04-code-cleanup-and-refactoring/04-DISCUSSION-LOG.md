# Phase 4: Code Cleanup and Refactoring - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-04
**Phase:** 04-code-cleanup-and-refactoring
**Areas discussed:** 范围优先级, COLA Spring 注解移除, 大文件拆分策略, 测试覆盖策略

---

## 范围优先级

| Option | Description | Selected |
|--------|-------------|----------|
| 安全 + 架构（推荐） | 先修安全漏洞，再清理架构违规。数据完整性和代码质量作为附带修复 | |
| 全部类别 | 安全 + 架构 + 数据完整性 + 代码质量 + 测试 | |
| 仅安全 + 快速修复 | 只修测试控制器 profile 和日志级别 | |
| 架构为主 | 专注 COLA Spring 注解移除和双状态模型统一 | |

**User's choice:** 安全 + 架构（推荐）— 但随后将数据完整性、BotRoutingDecision、@Deprecated API 也纳入

### V2-03 统一状态模型

| Option | Description | Selected |
|--------|-------------|----------|
| 纳入阶段 4（推荐） | TopicState 和 ContextSessionState 合并。56 处引用要改 | ✓ |
| 继续延迟 | 保留双状态模型，未来再处理 | |

**User's choice:** 纳入阶段 4

### 数据完整性

| Option | Description | Selected |
|--------|-------------|----------|
| 不纳入（推荐） | 数据完整性修复工作量大 | |
| 仅修双 DataSource | 双 DataSource 是实际风险，修起来简单 | |
| 全部纳入 | 全面修数据层问题 | ✓ |

**User's choice:** 全部纳入

### 安全修复范围

| Option | Description | Selected |
|--------|-------------|----------|
| 全部修复（推荐） | 移除 default profile、日志降级、添加 prod 配置 | ✓ |
| 仅修控制器 | 只修最危险的测试控制器 profile 问题 | |
| 安全为主 | 安全修复快速做，日志清理随带处理 | |

**User's choice:** 全部修复

### BotRoutingDecision

| Option | Description | Selected |
|--------|-------------|----------|
| 纳入（推荐） | 移除 app 引用，改用 appId + AppRegistry 查找 | ✓ |
| 不纳入 | 不影响功能，延后处理 | |

**User's choice:** 纳入

### FishuAppI @Deprecated API

| Option | Description | Selected |
|--------|-------------|----------|
| 纳入 | 完成迁移或移除标注 | ✓ |
| 不纳入（推荐） | 不影响功能，延后处理 | |
| 移除标注 | 接受 Message 作为主 API | |

**User's choice:** 纳入

---

## COLA Spring 注解移除

### 移除程度

| Option | Description | Selected |
|--------|-------------|----------|
| 完全移除（推荐） | domain 模块零 Spring 依赖 | ✓ |
| 部分移除 | 只移除 @ConfigurationProperties 和 @Async | |
| 注册统一化 | 保留注解但通过 DomainServiceConfig 统一注册 | |

**User's choice:** 完全移除

### 移除策略

| Option | Description | Selected |
|--------|-------------|----------|
| 一次性完成 | 28 个文件同时移除 | |
| 分批进行（推荐） | 先配置类，再服务类，最后应用类 | ✓ |
| 最小范围 | 只修最容易出问题的 | |

**User's choice:** 分批进行

### 验证策略

| Option | Description | Selected |
|--------|-------------|----------|
| 仅验证现有测试通过 | 底线 | |
| 现有测试 + 新增关键测试（推荐） | 补充 DomainServiceConfig 注册逻辑等关键测试 | ✓ |
| 全面新增测试 | 为每个移除的注解写测试 | |

**User's choice:** 现有测试 + 新增关键测试

---

## 大文件拆分策略

### 拆分范围

| Option | Description | Selected |
|--------|-------------|----------|
| 拆分 OpenCodeGatewayImpl（推荐） | 按 API 资源拆分 | ✓ |
| 拆分 OpenCodeGatewayImpl + CommandHandler | 两个都拆 | |
| 不拆分 | 专注注解移除和安全修复 | |

**User's choice:** 拆分 OpenCodeGatewayImpl

### OpenCodeGatewayImpl 拆分方式

| Option | Description | Selected |
|--------|-------------|----------|
| 按资源/API 拆分（推荐） | SessionApi、ProjectApi、ChatApi、HealthApi | ✓ |
| 提取工具类 | 拆出工具方法，保持主文件不变 | |
| 按读写操作拆分 | 按读/写/管理拆分 | |

**User's choice:** 按资源/API 拆分

### OpenCodeCommandHandler 拆分方式

| Option | Description | Selected |
|--------|-------------|----------|
| 提取子命令处理器（推荐） | 每个子命令抽为独立处理方法或策略类 | ✓ |
| 提取通用逻辑 | 只提取重复代码 | |
| 策略模式重构 | 每个子命令一个类，实现 CommandHandler 接口 | |

**User's choice:** 提取子命令处理器

---

## 测试覆盖策略

### 拆分后测试补充

| Option | Description | Selected |
|--------|-------------|----------|
| 随拆分补充（推荐） | 拆分后每个 API 资源类写测试 | ✓ |
| 全面补充 | 为所有未测文件写完整测试 | |
| 最小范围 | 只确保现有 355 个测试通过 | |

**User's choice:** 随拆分补充

### 触发新测试的重构

| Option | Description | Selected |
|--------|-------------|----------|
| 注解移除触发测试 | 验证依赖注入仍然工作 | |
| 数据层重构触发测试 | 验证 DataSource 共享 | |
| 安全修复触发测试 | 验证测试控制器不再激活 | |
| 全部（推荐） | 上述全部 | ✓ |

**User's choice:** 全部

---

## Agent's Discretion

- Exact naming for split API classes in OpenCodeGatewayImpl decomposition
- Whether OpenCodeCommandHandler sub-command handlers are methods or separate classes
- Exact unified state enum values and naming
- Exact production profile settings in application-prod.yml
- Order of batch execution within each removal phase
- Whether to also fix V2-05 (per-session flush locks) while in the area

## Deferred Ideas

None — discussion stayed within phase scope.

# OpenCode 应用优化计划

## TL;DR

> **Quick Summary**: 基于 68 个单元测试的安全网，优化 OpenCode 应用的代码质量和可维护性
> 
> **主要目标**:
> - 修复 3 个关键代码问题（extractChatContent、输入验证、日志缺失）
> - 提升代码可维护性（常量提取、异常细化）
> - 完成单元测试修复（目标 100% 通过率）
> 
> **Estimated Effort**: Medium
> **Parallel Execution**: NO - 顺序执行
> **Critical Path**: 修复测试 → 快速优化 → 验证测试 → 架构优化

---

## Context

### Original Request

在完成 OpenCode 应用的代码审查和单元测试添加后，对发现的代码质量问题进行系统性优化，提升代码健壮性和可维护性。

### Current State

**代码审查发现**:
- 🔴 OpenCodeCommandHandler 过于庞大（424行）
- 🔴 硬编码字符串过多
- 🔴 extractChatContent 逻辑脆弱
- ⚠️ 缺少关键日志
- ⚠️ 异常处理不够细致
- ⚠️ 缺少输入验证

**测试覆盖情况**:
- ✅ 已添加 3 个测试文件（1130+ 行测试代码）
- ✅ 68 个测试用例
- ⚠️ 79.4% 通过率（54/68 通过，10 失败，4 错误）
- ✅ 核心功能已有测试保护

### Files Involved

**Domain Layer**:
- `OpenCodeApp.java` (155 行)
- `OpenCodeCommandHandler.java` (423 行)
- `OpenCodeSessionManager.java` (147 行)
- `OpenCodeTaskExecutor.java` (202 行)
- `OpenCodeResponseFormatter.java` (130 行)

**Infrastructure Layer**:
- `OpenCodeGatewayImpl.java` (930 行)

**Test Files**:
- `OpenCodeSessionManagerTest.java`
- `OpenCodeAppTest.java`
- `OpenCodeCommandHandlerTest.java`
- `OpenCodeExplicitInitializationTest.java`

---

## Work Objectives

### Core Objective

基于单元测试的安全网，系统性地优化 OpenCode 应用的代码质量、健壮性和可维护性，确保所有优化都通过现有测试验证。

### Concrete Deliverables

1. 修复 3 个关键代码问题
2. 添加关键日志追踪
3. 提取常量类
4. 细化异常处理
5. 修复所有失败的单元测试（目标 100% 通过率）
6. 创建优化总结报告

### Definition of Done

- [ ] 所有高优先级任务完成
- [ ] 单元测试通过率达到 100%（68/68）
- [ ] 关键方法添加日志追踪
- [ ] 输入验证添加完成
- [ ] 代码审查中的严重问题全部解决

### Must Have

- 保持现有功能不变
- 所有测试通过
- 不改变公共 API
- 日志输出不影响性能

### Must NOT Have (Guardrails)

- 不修改接口定义（保持向后兼容）
- 不改变命令语义
- 不降低性能
- 不引入新的依赖

---

## Execution Strategy

### Phase 1: Quick Fixes (本周完成，3-4 小时)

**目标**: 修复严重且容易出问题的代码缺陷

#### Task 1.1: 修复 extractChatContent 方法
**File**: `OpenCodeCommandHandler.java:319-327`
**Time**: 30 分钟

**Current Problem**:
```java
// 使用 indexOf 和魔法数字，逻辑脆弱
String chatPrompt = content.substring(content.indexOf(' ') + 1).trim();
if (chatPrompt.toLowerCase().startsWith("chat ")) {
    chatPrompt = chatPrompt.substring(5).trim();  // 魔法数字 5
}
```

**Solution**:
```java
private String extractChatContent(String[] parts, Message message) {
    // 方案1: 优先使用 parts 数组（更简单可靠）
    if (parts.length >= 3) {
        return String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
    }
    
    // 方案2: 降级到字符串处理
    String content = message.getContent().trim();
    int firstSpace = content.indexOf(' ');
    if (firstSpace < 0) return "";
    
    String remaining = content.substring(firstSpace + 1).trim();
    // 移除 "chat" 子命令
    if (remaining.toLowerCase().startsWith("chat ")) {
        remaining = remaining.substring(5).trim();
    }
    return remaining;
}
```

**Verification**:
- 运行测试: `mvn test -Dtest=OpenCodeCommandHandlerTest#handleChat_success`
- 验证 chat 命令能正确提取内容

---

#### Task 1.2: 添加输入验证
**File**: `OpenCodeSessionManager.java:79-104`
**Time**: 30 分钟

**Current Problem**:
```java
String project = parts[2].trim();  // 没有验证是否为空
```

**Solution**:
```java
public String handleSessionsCommand(String[] parts) {
    if (parts.length < 3) {
        return "❌ 用法：`/opencode sessions <项目名称>`\n\n" +
               "示例：`/opencode sessions my-project`\n\n" +
               "💡 提示：\n" +
               " - 使用 `/opencode projects` 查看所有项目\n" +
               " - 项目名称支持部分匹配（不区分大小写）";
    }

    String project = parts[2].trim();
    
    // 输入验证
    if (project.isEmpty()) {
        return "❌ 项目名称不能为空\n\n" +
               "用法：`/opencode sessions <项目名称>`";
    }
    
    // 验证项目名称长度
    if (project.length() > 100) {
        return "❌ 项目名称过长（最多100个字符）";
    }
    
    int limit = DEFAULT_SESSION_LIMIT;  // 使用常量
    
    if (parts.length >= 4) {
        try {
            limit = Integer.parseInt(parts[3].trim());
            if (limit < MIN_SESSION_LIMIT || limit > MAX_SESSION_LIMIT) {
                return "❌ 数量必须在 " + MIN_SESSION_LIMIT + "-" + MAX_SESSION_LIMIT + " 之间";
            }
        } catch (NumberFormatException e) {
            log.warn("无效的数量参数，使用默认值: {}", parts[3]);
            // 使用默认值
        }
    }
    
    log.info("查询项目会话: project={}, limit={}", project, limit);
    return openCodeGateway.listRecentSessions(project, limit);
}
```

**Verification**:
- 运行测试: `mvn test -Dtest=OpenCodeSessionManagerTest`
- 验证空项目名称被正确处理

---

#### Task 1.3: 添加关键日志
**Files**: 
- `OpenCodeGatewayImpl.java` (会话创建、消息发送)
- `OpenCodeTaskExecutor.java` (任务执行)

**Time**: 1 小时

**Add Logs**:
```java
// OpenCodeGatewayImpl.executeInNewSession
log.info("创建新会话: prompt='{}', timeout={}s", prompt, timeoutSeconds);
log.info("新会话创建成功: sessionId={}, 开始执行命令", sessionId);

// OpenCodeGatewayImpl.executeInExistingSession
log.info("在会话 {} 中执行命令: prompt='{}', timeout={}s", sessionId, prompt, timeoutSeconds);

// OpenCodeGatewayImpl.createSession
log.debug("调用 API 创建会话: parentID={}", parentID);
log.info("会话创建成功: sessionId={}", sessionId);

// OpenCodeTaskExecutor.executeWithAutoSession
log.info("自动选择会话执行: topicId={}, prompt='{}'", topicId, prompt);
```

**Verification**:
- 运行应用并执行命令
- 检查日志包含关键信息

---

### Phase 2: Medium Improvements (下周完成，5-6 小时)

#### Task 2.1: 细化异常处理
**Files**: 
- `OpenCodeGatewayImpl.java`
- `OpenCodeCommandHandler.java`

**Time**: 2 小时

**Current Problem**:
```java
} catch (Exception e) {
    // 捕获所有异常，无法区分错误类型
    return "❌ 失败: " + e.getMessage();
}
```

**Solution**:
```java
// OpenCodeGatewayImpl.executeWithRetry
try {
    return operation.get();
} catch (java.net.ConnectException e) {
    if (attempt == MAX_RETRIES - 1) {
        log.error("连接失败: 无法连接到 OpenCode 服务");
        return "❌ 无法连接到 OpenCode 服务，请检查服务是否启动";
    }
    log.warn("连接失败，重试 {}/{}", attempt + 1, MAX_RETRIES);
} catch (java.net.http.HttpTimeoutException e) {
    if (attempt == MAX_RETRIES - 1) {
        log.error("请求超时: OpenCode 服务响应超时");
        return "❌ OpenCode 服务响应超时，请稍后重试";
    }
} catch (Exception e) {
    log.error("未知错误: operation={}, error={}", operationName, e.getMessage(), e);
    throw new RuntimeException(operationName + " 失败", e);
}
```

**Verification**:
- 运行测试确保异常处理正确
- 检查日志输出清晰的错误信息

---

#### Task 2.2: 提取常量类
**New File**: `OpenCodeConstants.java`

**Time**: 1 小时

**Content**:
```java
package com.qdw.feishu.domain.opencode;

/**
 * OpenCode 应用常量定义
 */
public final class OpenCodeConstants {
    
    private OpenCodeConstants() {
        // 防止实例化
    }

    /**
     * 会话查询限制
     */
    public static final int DEFAULT_SESSION_LIMIT = 5;
    public static final int MIN_SESSION_LIMIT = 1;
    public static final int MAX_SESSION_LIMIT = 20;

    /**
     * 超时设置（秒）
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;
    public static final int MAX_TIMEOUT_SECONDS = 600;

    /**
     * 命令解析
     */
    public static final String COMMAND_SEPARATOR = "\\s+";
    public static final int MAX_COMMAND_PARTS = 3;

    /**
     * 字符串长度限制
     */
    public static final int MAX_PROJECT_NAME_LENGTH = 100;
    public static final int MAX_PROMPT_LENGTH = 5000;

    /**
     * 错误消息模板
     */
    public static final String ERROR_PROJECT_NAME_EMPTY = "❌ 项目名称不能为空";
    public static final String ERROR_LIMIT_OUT_OF_RANGE = "❌ 数量必须在 " + MIN_SESSION_LIMIT + "-" + MAX_SESSION_LIMIT + " 之间";
    public static final String ERROR_PROJECT_NAME_TOO_LONG = "❌ 项目名称过长（最多" + MAX_PROJECT_NAME_LENGTH + "个字符）";
}
```

**Apply to**: 在相关类中替换魔法数字

---

#### Task 2.3: 修复失败的单元测试
**Time**: 2 小时

**Issues to Fix**:
1. Mockito 使用问题（`any()` 和具体值混用）
2. 断言调整（检查实际返回值）
3. NullPointer 问题（缺少 mock）

**Action Plan**:
```bash
# 运行测试查看详细错误
mvn test -Dtest=OpenCodeSessionManagerTest,OpenCodeAppTest,OpenCodeCommandHandlerTest

# 逐个修复失败和错误的测试
# 重点修复：
# - OpenCodeCommandHandlerTest: 10 failures
# - OpenCodeAppTest: 2 errors
# - OpenCodeSessionManagerTest: 3 errors
```

---

### Phase 3: Architecture Refactoring (可选，6-8 小时)

#### Task 3.1: 拆分 OpenCodeCommandHandler
**Target**: 将 424 行的类拆分为 3 个职责单一的类
**Time**: 4-6 小时

**Design**:
```
OpenCodeCommandHandler (150行) - 纯路由逻辑
  ↓ 依赖
CommandValidator (100行) - 命令验证逻辑
  ↓ 依赖
OpenCodeResponseBuilder (200行) - 响应构建逻辑
```

**Benefits**:
- 单一职责
- 易于测试
- 符合 SOLID 原则

---

#### Task 3.2: 引入命令模式（可选）
**Target**: 用命令模式替代 switch 语句
**Time**: 2-3 小时

**Design**:
```java
public interface Command {
    boolean canHandle(String subCommand);
    String execute(Message message, String[] parts);
}

@Component
public class SessionsCommand implements Command {
    public boolean canHandle(String subCommand) {
        return "sessions".equals(subCommand) || "s".equals(subCommand);
    }
    
    public String execute(Message message, String[] parts) {
        return sessionManager.handleSessionsCommand(parts);
    }
}
```

**Benefits**:
- 开闭原则
- 添加新命令无需修改核心逻辑
- 每个命令独立测试

---

## Execution Strategy

### Phase 1: Quick Fixes (3-4 小时)

**Tasks**:
1. 修复 extractChatContent 方法
2. 添加输入验证
3. 添加关键日志

**Order**:
```
Task 1.1 (extractChatContent) → 测试验证
  ↓
Task 1.2 (输入验证) → 测试验证
  ↓
Task 1.3 (关键日志) → 手动验证
```

**Dependencies**: 无依赖，可独立完成

### Phase 2: Medium Improvements (5-6 小时)

**Tasks**:
1. 细化异常处理
2. 提取常量类
3. 修复单元测试

**Order**:
```
Task 2.1 (异常处理) → 测试验证
  ↓
Task 2.2 (常量类) → 测试验证
  ↓
Task 2.3 (修复测试) → 全量测试验证
```

**Dependencies**:
- Task 2.2 可以在 Task 2.1 完成后开始
- Task 2.3 需要等待前两个任务完成

### Phase 3: Architecture Refactoring (6-8 小时)

**Tasks**:
1. 拆分 OpenCodeCommandHandler
2. 引入命令模式（可选）

**Dependencies**: 依赖于 Phase 1 和 Phase 2 完成

---

## TODOs

### Phase 1: Quick Fixes

- [x] 1.1. 修复 extractChatContent 方法
  **What to do**:
  - [x] 重写方法逻辑，优先使用 parts 数组
  - [x] 添加边界检查（indexOf 返回 -1）
  - [x] 移除魔法数字
 
  **Must NOT do**:
  - [x] 不改变方法签名
  - [x] 不改变现有行为
 
  **Verification**:
  - [x] 运行测试: `mvn test -Dtest=OpenCodeCommandHandlerTest#handleChat_success`
  - [x] 手动测试: `/opencode chat 测试消息`
  - [x] 确认 chat 内容被正确提取
 
  **Estimated Time**: 30 minutes

- [x] 1.2. 添加输入验证
  **What to do**:
  - [x] 在 handleSessionsCommand 开头添加项目名称验证
  - [x] 检查空字符串
  - [x] 检查长度限制（100字符）
  - [x] 添加常量: DEFAULT_SESSION_LIMIT, MIN_SESSION_LIMIT, MAX_SESSION_LIMIT
 
  **Must NOT do**:
  - [x] 不修改方法签名
  - [x] 不改变错误消息格式（仅增强）
 
  **Verification**:
  - [x] 运行测试: `mvn test -Dtest=OpenCodeSessionManagerTest#handleSessionsCommand_*`
  - [x] 测试空项目名称
  - [x] 测试超长项目名称
  - [x] 测试无效数量参数
 
  **Estimated Time**: 30 minutes

- [x] 1.3. 添加关键日志
  **What to do**:
  - [x] OpenCodeGatewayImpl.executeInNewSession: 添加创建会话日志
  - [x] OpenCodeGatewayImpl.executeInExistingSession: 添加执行命令日志
  - [x] OpenCodeTaskExecutor.executeWithAutoSession: 添加自动选择会话日志
  - [x] 使用合适的日志级别（INFO for 关键操作, DEBUG for 详细信息）
 
  **Must NOT do**:
  - [x] 不记录敏感信息
  - [x] 不在循环中记录日志（避免日志爆炸）
 
  **Verification**:
  - [x] 运行应用，执行命令
  - [x] 检查 `/tmp/feishu-run.log` 包含新日志
  - [x] 确认日志不包含敏感数据
 
  **Estimated Time**: 1 hour

### Phase 2: Medium Improvements

- [x] 2.1. 细化异常处理
  **What to do**:
  - [x] 修改 OpenCodeGatewayImpl.executeWithRetry
  - [x] 区分 ConnectException、TimeoutException、其他异常
  - [x] 为每种异常提供具体的错误消息
  - [x] 保留重试逻辑
 
  **Must NOT do**:
  - [x] 不改变重试次数和延迟逻辑
  - [x] 不降低错误处理的质量
 
  **Verification**:
  - [x] 运行测试确保异常处理正确
  - [x] 模拟连接失败，检查错误消息
  - [x] 模拟超时，检查错误消息
 
  **Estimated Time**: 2 hours
 
- [x] 2.2. 提取常量类
  **What to do**:
  - [ ] 创建新文件: `OpenCodeConstants.java`
  - [ ] 定义所有常量（会话限制、超时、字符串长度等）
  - [ ] 在相关类中替换魔法数字
  - [ ] 添加注释说明常量用途

  **Must NOT do**:
  - [ ] 不修改常量的值（除非是错误的）

  **Verification**:
  - [ ] 编译成功
  - [ ] 所有测试通过
  - [ ] 验证魔法数字已消除

  **Estimated Time**: 1 hour

- [ ] 2.3. 修复失败的单元测试
  **What to do**:
  - [x] 修复 Mockito 使用问题（any() + eq() 混用）
  - [x] 修复断言问题（检查实际返回值）
  - [ ] 添加缺失的 mock 设置
  - [ ] 目标: 100% 测试通过率（68/68）
 
  **Must NOT do**:
  - [x] 不修改测试的意图（只修复实现问题）
  - [ ] 不降低测试覆盖率
 
  **Verification**:
  - [ ] 运行: `mvn test -Dtest=OpenCode*Test`
  - [ ] 查看结果: "Tests run: 68, Failures: 0, Errors: 0"
  - [ ] 所有测试用例全部通过
 
  **Estimated Time**: 2 hours

### Phase 3: Architecture Refactoring (Optional)

- [ ] 3.1. 拆分 OpenCodeCommandHandler
  **What to do**:
  - [ ] 创建 CommandValidator 类
  - [ ] 创建 OpenCodeResponseBuilder 类
  - [ ] 重构 OpenCodeCommandHandler（只保留路由逻辑）
  - [ ] 移动字符串构建逻辑到 ResponseBuilder
  - [ ] 更新测试以适应新结构

  **Must NOT do**:
  - [ ] 不改变命令行为
  - [ ] 不破坏现有功能

  **Estimated Time**: 4-6 hours

- [ ] 3.2. 引入命令模式（可选）
  **What to do**:
  - [ ] 定义 Command 接口
  - [ ] 为每个命令创建实现类
  - [ ] 创建 CommandRegistry 管理命令
  - [ ] 重构 OpenCodeCommandHandler 使用命令模式
  - [ ] 更新测试

  **Estimated Time**: 2-3 hours

---

## Success Criteria

### Verification Commands
```bash
# Phase 1 验证
mvn test -Dtest=OpenCode*Test 2>&1 | grep "Tests run:"

# Phase 2 验证
mvn test -Dtest=OpenCode*Test 2>&1 | grep -E "(Tests run:|BUILD)"

# 最终验证
mvn test 2>&1 | grep -E "Tests run:"
mvn package -DskipTests
```

### Final Checklist
- [ ] 所有高优先级任务完成（Phase 1）
- [ ] 单元测试 100% 通过（68/68）
- [ ] 关键方法包含日志
- [ ] 输入验证添加完成
- [ ] 代码审查中的严重问题全部解决
- [ ] 无新增编译警告
- [ ] 服务启动正常
- [ ] 现有功能全部正常工作

---

## Commit Strategy

| Phase | Tasks | Message | Files |
|-------|-------|---------|-------|
| 1.1 | 修复 extractChatContent | fix(opencode): 优化 extractChatContent 方法逻辑 | OpenCodeCommandHandler.java |
| 1.2 | 添加输入验证 | fix(opencode): 添加项目名称输入验证 | OpenCodeSessionManager.java, OpenCodeConstants.java |
| 1.3 | 添加关键日志 | fix(opencode): 添加会话创建和执行的关键日志 | OpenCodeGatewayImpl.java, OpenCodeTaskExecutor.java |
| 2.1 | 细化异常处理 | refactor(opencode): 细化异常处理，区分错误类型 | OpenCodeGatewayImpl.java |
| 2.2 | 提取常量类 | refactor(opencode): 提取常量类消除魔法数字 | OpenCodeConstants.java, multiple files |
| 2.3 | 修复单元测试 | test(opencode): 修复所有失败的单元测试 | multiple test files |

---

## Additional Notes

### 优化原则

1. **测试驱动**: 所有修改都应有测试保护
2. **小步快跑**: 每个任务独立提交，便于回滚
3. **向后兼容**: 不改变公共 API
4. **性能优先**: 不降低运行性能
5. **可读性**: 代码应自解释，减少注释依赖

### 回滚策略

如果优化后出现问题：
```bash
# 回滚到上一个稳定版本
git revert HEAD~1

# 或使用 git reset
git reset --hard HEAD~1

# 重新打包
mvn clean package -DskipTests
```

### 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|-----------|
| 修改破坏现有功能 | 低 | 高 | 测试保护，小步提交 |
| 性能下降 | 低 | 中 | 日志级别控制，避免过度日志 |
| 兼容性问题 | 低 | 中 | 不修改接口，保持行为一致 |
| 测试覆盖率不足 | 中 | 中 | 每个修改都运行测试验证 |

---

**Created**: 2026-02-06
**Priority**: HIGH
**Estimated Total Time**: 8-10 hours (Phase 1-2), 14-18 hours (all phases)
**Parallel Execution**: NO - 顺序执行
**Critical Path**: 测试修复 → 快速优化 → 验证测试 → 架构优化

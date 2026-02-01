# OpenCode 非话题模式限制优化

## TL;DR

> **Quick Summary**: 优化 OpenCode 飞书应用，实现渐进式引导流程：非话题 → 话题未初始化 → 话题已初始化。**同时抽象公共设计，让其他应用可以复用话题限制逻辑。**
>
> **Deliverables**:
> - **TopicCommandValidator** - 公共的话题命令验证器（可复用）
> - **TopicState 枚举** - 话题状态定义（NON_TOPIC、UNINITIALIZED、INITIALIZED）
> - **FishuAppI 接口扩展** - 添加命令白名单和初始化检测方法
> - OpenCodeApp.java - 使用公共验证器实现分层限制
> - connect 子命令 - 返回健康信息、帮助摘要、项目列表
> - 用户友好的引导提示
>
> **Estimated Effort**: Medium
> **Parallel Execution**: NO - sequential changes
> **Critical Path**: 创建公共验证器 → 修改 FishuAppI → OpenCodeApp 使用 → 测试验证

---

## Context

### Original Request

优化 opencode 飞书应用，实现渐进式引导：非话题只能用 connect，进入话题后引导绑定 session，绑定后才能使用 chat。

### Interview Summary

**Key Discussions**:
- **渐进式流程设计**:
  1. 非话题：只有 connect、help、projects 可用 → 引导进入话题
  2. 话题（未初始化）：chat/new 禁用 → 引导初始化（绑定 session）
  3. 话题（已初始化）：所有命令可用
- **connect 命令行为**: 返回三部分信息（健康信息 + 帮助 + 项目列表），作为非话题用户的"一站式"入口
- **初始化检测**: 使用 `OpenCodeSessionGateway.getSessionId()` 判断话题是否已初始化（已绑定 session）
- **架构抽象需求**: 将话题状态检测和命令白名单验证抽象为公共设计，方便其他应用复用
- **命名优化**: 使用"初始化"（UNINITIALIZED/INITIALIZED）代替"绑定"（UNBOUND/BOUND），更通用
- **状态命名优化**: 使用"初始化"（INITIALIZED/UNINITIALIZED）代替"绑定"（BOUND/UNBOUND），更加通用

**Research Findings**:
- OpenCodeApp 位于 `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/OpenCodeApp.java`
- 当前支持子命令：help、new、chat、session、projects、commands
- 使用 `message.getTopicId()` 判断是否在话题中（null 或空字符串表示非话题）
- OpenCodeApp 使用 ReplyMode.TOPIC，会创建话题

---

## Work Objectives

### Core Objective

在 OpenCodeApp 中实现**分层命令限制**，根据话题状态和初始化状态控制可用的子命令，引导用户完成：非话题 → 进入话题 → 初始化（如绑定 session）→ 开始对话。

### Concrete Deliverables

1. **公共组件 - TopicCommandValidator**:
   - 创建 `TopicState` 枚举（NON_TOPIC、UNINITIALIZED、INITIALIZED）
   - 创建 `TopicCommandValidator` 服务类
   - 提供通用的命令验证方法
   - 提供默认的友好错误提示

2. **FishuAppI 接口扩展**:
   - 添加 `getCommandWhitelist(TopicState)` 方法（可选实现）
   - 添加 `isTopicInitialized(Message)` 方法（可选实现，用于检测话题是否已初始化）
   - 添加默认实现（允许所有命令，未初始化的话题视为已初始化）

3. **OpenCodeApp.java 修改**:
   - 注入 `TopicCommandValidator`
   - 实现 `getCommandWhitelist()` 定义白名单
   - 在 `execute()` 中调用验证器
   - 实现 `connect` 子命令

4. **connect 子命令功能**:
   - 调用 OpenCode API 获取健康信息
   - 返回帮助命令摘要
   - 返回近期项目列表

5. **分层引导提示**:
   - 使用验证器提供的默认提示
   - OpenCodeApp 可定制特殊提示

### Definition of Done

- [ ] 非话题中只有 connect、help、projects 可用
- [ ] 话题未初始化时，chat/new 返回提示，引导初始化（绑定 session）
- [ ] 话题已初始化时，所有命令正常工作
- [ ] connect 返回健康信息 + 帮助 + 项目列表
- [ ] 构建成功，无编译错误

### Must Have

- 非话题白名单验证逻辑
- connect 子命令实现（健康 + 帮助 + 项目）
- 用户友好的错误提示

### Must NOT Have (Guardrails)

- 不要修改 OpenCodeGateway（使用现有方法）
- 不要修改 TopicMapping 机制
- 不要影响话题中已初始化的现有功能
- 不要添加单独的 health/status 命令（已明确不需要）
- 不要在非话题中允许任何会话相关命令（chat、new、session）
- 不要让其他现有应用（BashApp、TimeApp 等）的行为发生变化（向后兼容）
- 不要将"初始化"概念硬编码为 session 绑定（保持通用性）

---

## Verification Strategy (MANDATORY)

### Test Decision

- **Infrastructure exists**: YES (Spring Boot + Maven)
- **User wants tests**: Manual verification (项目未配置测试框架)
- **Framework**: None
- **QA approach**: Manual verification with build + restart

### Manual Verification Procedures

#### 测试用例 1: 非话题模式

```bash
# 1. 启动应用
./start-feishu.sh

# 2. 在飞书中发送（非话题）
# /opencode connect
# 预期: 返回健康信息 + 帮助摘要 + 项目列表

# 3. 在飞书中发送（非话题）
# /opencode help
# 预期: 返回帮助信息

# 4. 在飞书中发送（非话题）
# /opencode projects
# 预期: 返回项目列表

# 5. 在飞书中发送（非话题）
# /opencode chat 测试
# 预期: 返回提示："此命令只能在话题中使用，请使用 /opencode connect 进入话题"

# 6. 在飞书中发送（非话题）
# /opencode new 测试
# 预期: 返回提示："此命令只能在话题中使用"

# 7. 在飞书中发送（非话题）
# /opencode session status
# 预期: 返回提示："此命令只能在话题中使用"
```

#### 测试用例 2: 话题模式（未初始化）

```bash
# 1. 在飞书话题中发送（首次，未初始化）
# /opencode connect
# 预期: 返回信息，话题创建但未初始化

# 2. 在同一话题中发送
# /opencode chat 测试
# 预期: 返回提示："话题未初始化，请使用 /opencode session continue <id> 绑定 session"

# 3. 在同一话题中发送
# /opencode new 测试
# 预期: 返回提示："话题未初始化，请使用 /opencode session continue <id> 绑定 session"

# 4. 在同一话题中发送
# /opencode session list
# 预期: 正常返回 session 列表

# 5. 在同一话题中发送
# /opencode session continue ses_xxx
# 预期: 初始化（绑定 session），返回成功提示
```

#### 测试用例 3: 话题模式（已初始化）

```bash
# 1. 初始化后，在话题中发送
# /opencode chat 测试
# 预期: 正常执行对话

# 2. 在话题中发送
# /opencode new 测试
# 预期: 正常创建新会话并初始化

# 3. 在话题中发送
# /opencode session status
# 预期: 返回当前 session 状态

# 4. 在话题中发送
# 测试（无前缀）
# 预期: 自动添加 /opencode 前缀，正常执行对话
```

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately):
├── Task 1: 创建公共组件（TopicCommandValidator + TopicState）
└── Task 2: 扩展 FishuAppI 接口

Wave 2 (After Wave 1):
└── Task 3: 修改 OpenCodeApp 使用公共组件

Critical Path: Task 1 → Task 2 → Task 3 → Manual Verification
```

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|---------------------|
| 1 | None | 2, 3 | None |
| 2 | 1 | 3 | None |
| 3 | 1, 2 | Manual Verification | None |

---

## TODOs

- [ ] 1. 创建公共组件 TopicCommandValidator

  **What to do**:

  1. **创建 TopicState 枚举**:
     - 位置：`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicState.java`
     - 值：`NON_TOPIC`（非话题）、`UNINITIALIZED`（未初始化）、`INITIALIZED`（已初始化）
     - 添加 `getDescription()` 方法返回中文描述
     - 添加枚举值的说明注释，解释各状态的含义
     - 语义说明：
       - `NON_TOPIC`: 非话题（独立消息）
       - `UNINITIALIZED`: 话题未初始化（应用特定的初始化状态，如未绑定 session）
       - `INITIALIZED`: 话题已初始化（应用就绪，可以执行所有操作）

   2. **创建 CommandWhitelist 配置类**:
      - 位置：`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/CommandWhitelist.java`
      - 包含三个 `Set<String>`：nonTopicAllowed、uninitializedAllowed、initializedAllowed
      - 提供便捷的构造方法（如 `all()`、`none()`、`except(String...)`）

  3. **创建 TopicCommandValidator 服务**:
     - 位置：`feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/TopicCommandValidator.java`
     - 注入 `AppRegistry`（用于访问应用的初始化检测方法）
     - 方法：
       ```java
       public TopicState detectState(Message message, FishuAppI app)
       public ValidationResult validateCommand(String subCommand, TopicState state, CommandWhitelist whitelist)
       public String getRestrictedCommandMessage(TopicState state, String appId, String command)
       ```
     - `detectState()` 逻辑：
       - 检测 `topicId` 是否为空 → NON_TOPIC
       - 调用 `app.isTopicInitialized(message)` → INITIALIZED/UNINITIALIZED
     - 添加 `@Service` 注解
     - **设计说明**: 使用 `Function<String, Optional<String>>` 而不是直接注入 OpenCodeSessionGateway，保持通用性
       - 输入：topicId
       - 输出：Optional<sessionId>（有值表示已初始化，空值表示未初始化）

  4. **创建 ValidationResult 类**:
     - 包含 `allowed`（boolean）和 `message`（String，如果被限制）
     - 提供静态工厂方法：`allowed()`、`restricted(String message)`

  **Must NOT do**:
  - 不要依赖具体的应用逻辑（保持通用性）
  - 不要硬编码应用名称或命令名称

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 创建新的公共组件，逻辑清晰，快速实现
  - **Skills**: [`git-master`]
    - `git-master`: 查看现有组件的实现模式

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 2 (FishuAppI 扩展)
  - **Blocked By**: None

  **References**:
  - **Pattern References**:
    - `BotMessageService.java` - 参考服务类的结构和依赖注入
    - `ReplyMode.java` - 参考枚举的设计模式
    - `TopicMapping.java` - 参考领域模型的设计

  - **API/Type References**:
    - `Message.java:getTopicId()` - 获取话题ID
    - `OpenCodeSessionGateway:getSessionId(String)` - 参考初始化检测方法签名

  - **Documentation References**:
    - `/root/workspace/feishu-backend/feishu-bot-domain/AGENTS.md` - 领域层规范

  **Acceptance Criteria**:
  - [ ] TopicState 枚举创建，包含三个值和 getDescription()
  - [ ] CommandWhitelist 类创建，提供便捷构造方法
  - [ ] TopicCommandValidator 服务创建，添加 @Service 注解
  - [ ] detectState() 方法实现（检测话题状态）
  - [ ] validateCommand() 方法实现（验证命令是否允许）
  - [ ] getRestrictedCommandMessage() 方法实现（返回友好提示）
  - [ ] 代码编译通过: `mvn clean compile`

  **Evidence to Capture**:
  - [ ] 编译成功日志
  - [ ] 新创建的文件列表

  **Commit**: NO (等待所有任务完成后一起提交)

---

- [ ] 2. 扩展 FishuAppI 接口

  **What to do**:

  1. **添加命令白名单方法到 FishuAppI**:
     ```java
     /**
      * 获取命令白名单（可选实现）
      *
      * @param state 话题状态
      * @return 允许的命令集合，null 表示允许所有命令
      */
     default CommandWhitelist getCommandWhitelist(TopicState state) {
         return null;  // 默认允许所有命令（向后兼容）
     }
     ```

   2. **添加初始化检测方法到 FishuAppI**:
      ```java
      /**
       * 检测话题是否已初始化（可选实现）
       *
       * 每个应用可以定义自己的"初始化"含义：
       * - OpenCode：已绑定 session（通过 sessionGateway.getSessionId(topicId) 检测）
       * - 其他应用：可能完成配置向导、设置参数、创建上下文等
       *
       * @param message 消息对象
       * @return true 表示已初始化，false 表示未初始化
       */
      default boolean isTopicInitialized(Message message) {
          return true;  // 默认视为已初始化（向后兼容）
      }
      ```

   3. **添加使用示例到 Javadoc**:
      - 在接口顶部添加使用示例注释
      - 说明如何实现 `getCommandWhitelist()` 和 `isTopicInitialized()`
      - 提供 OpenCodeApp 的示例实现

  **Must NOT do**:
  - 不要修改现有的方法签名（向后兼容）
  - 不要强制现有应用实现新方法（使用 default 实现）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 接口扩展，添加 default 方法，快速实现
  - **Skills**: [`git-master`]
    - `git-master`: 查看接口的 Git 历史

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Task 3 (OpenCodeApp 实现)
  - **Blocked By**: Task 1 (TopicCommandValidator)

  **References**:
  - **Pattern References**:
    - `FishuAppI.java` - 现有接口结构
    - `ReplyMode.java` - default 方法实现参考

  - **API/Type References**:
    - `TopicState` (Task 1 创建)
    - `CommandWhitelist` (Task 1 创建)

  **Acceptance Criteria**:
  - [ ] FishuAppI 添加 getCommandWhitelist() 方法
  - [ ] 方法使用 default 实现，返回 null（允许所有）
  - [ ] 添加完整的 Javadoc 注释
  - [ ] 现有应用（BashApp、TimeApp 等）无需修改即可编译
  - [ ] 代码编译通过: `mvn clean compile`

  **Evidence to Capture**:
  - [ ] 编译成功日志
  - [ ] 现有应用无需修改的验证

  **Commit**: NO (等待所有任务完成后一起提交)

---

- [ ] 3. 修改 OpenCodeApp 使用公共验证器

  **What to do**:

  1. **注入 TopicCommandValidator**:
     ```java
     private final TopicCommandValidator commandValidator;

     public OpenCodeApp(...,
                        TopicCommandValidator commandValidator,
                        ...) {
         // ...
         this.commandValidator = commandValidator;
     }
     ```

   2. **实现 getCommandWhitelist() 方法**:
      ```java
      @Override
      public CommandWhitelist getCommandWhitelist(TopicState state) {
          switch (state) {
              case NON_TOPIC:
                  return CommandWhitelist.builder()
                      .add("connect", "help", "projects")
                      .build();
              case UNINITIALIZED:
                  return CommandWhitelist.allExcept("chat", "new");
              case INITIALIZED:
                  return CommandWhitelist.all();
              default:
                  return CommandWhitelist.all();
          }
      }
      ```

   3. **实现 isTopicInitialized() 方法**:
      ```java
      @Override
      public boolean isTopicInitialized(Message message) {
          String topicId = message.getTopicId();
          if (topicId == null || topicId.isEmpty()) {
              return false;  // 非话题，视为未初始化
          }

          // 检测是否已绑定 session
          Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);
          return sessionIdOpt.isPresent();
      }
      ```

   4. **修改 execute() 方法，使用验证器**:
      ```java
      public String execute(Message message) {
          String content = message.getContent().trim();
          String[] parts = content.split("\\s+", 3);

          // 检测话题状态（传入 this，让验证器调用 isTopicInitialized）
          TopicState state = commandValidator.detectState(message, this);

          // 提取子命令
          String subCommand = parts.length < 2 ? "" : parts[1].toLowerCase();

          // 验证命令是否允许
          CommandWhitelist whitelist = getCommandWhitelist(state);
          if (whitelist != null) {
              ValidationResult result = commandValidator.validateCommand(
                  subCommand,
                  state,
                  whitelist
              );
              if (!result.isAllowed()) {
                  return result.getMessage();  // 返回限制提示
              }
          }

          // 继续原有的 switch 逻辑...
      }
      ```

   5. **实现 connect 子命令**:

  4. **实现 connect 子命令**:
     - 调用 `openCodeGateway.getServerStatus()` 获取健康信息
     - 调用 `openCodeGateway.listCommands()` 获取命令列表
     - 调用 `openCodeGateway.listProjects()` 获取项目列表
     - 组合三部分信息并格式化输出
     - 使用简化的帮助摘要（不超过 3 行）

  **Must NOT do**:
  - 不要重复实现验证逻辑（使用验证器）
  - 不要硬编码错误提示（使用验证器提供的方法）

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: 单文件修改，使用公共组件，逻辑清晰
  - **Skills**: [`git-master`]
    - `git-master`: 查看文件的 Git 历史

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Sequential
  - **Blocks**: Manual Verification
  - **Blocked By**: Task 1, Task 2

  **References**:
  - **Pattern References**:
    - `OpenCodeApp.java:98-187` - execute() 方法结构
    - `BashApp.java` - 其他应用的实现参考

  - **API/Type References**:
    - `TopicCommandValidator` (Task 1 创建)
    - `CommandWhitelist` (Task 1 创建)
    - `FishuAppI.java:getCommandWhitelist()` (Task 2 添加)

   **Acceptance Criteria**:
   - [ ] 注入 TopicCommandValidator
   - [ ] 实现 getCommandWhitelist() 方法
   - [ ] 实现 isTopicInitialized() 方法
   - [ ] 修改 execute() 方法使用验证器
   - [ ] 实现 connect 子命令
   - [ ] 删除旧的硬编码验证逻辑（如果有）
   - [ ] 代码编译通过: `mvn clean compile`

  **功能验证** (在飞书中测试):

  **非话题模式**:
  - [ ] `/opencode connect` 返回健康信息 + 帮助 + 项目列表
  - [ ] `/opencode help` 返回帮助信息
  - [ ] `/opencode projects` 返回项目列表
  - [ ] `/opencode chat 测试` 返回"只能在话题中使用"提示
  - [ ] `/opencode new 测试` 返回"只能在话题中使用"提示
  - [ ] `/opencode session status` 返回"只能在话题中使用"提示
  - [ ] `/opencode commands` 返回"只能在话题中使用"提示

  **话题模式（未绑定 session）**:
  - [ ] `/opencode connect` 返回信息
  - [ ] `/opencode chat 测试` 返回"需要绑定 session"提示
  - [ ] `/opencode new 测试` 返回"需要绑定 session"提示
  - [ ] `/opencode session list` 正常返回 session 列表
  - [ ] `/opencode session continue ses_xxx` 成功绑定并返回提示
  - [ ] `/opencode projects` 正常返回项目列表

  **话题模式（已绑定 session）**:
  - [ ] `/opencode chat 测试` 正常执行对话
  - [ ] `/opencode new 测试` 正常创建新会话
  - [ ] `/opencode session status` 正常返回状态
  - [ ] `/opencode projects` 正常返回项目列表
  - [ ] `/opencode commands` 正常返回命令列表
  - [ ] 直接输入 `测试`（无前缀）自动添加 /opencode 前缀并执行

  **向后兼容验证**:
  - [ ] BashApp 所有命令正常工作
  - [ ] TimeApp 所有命令正常工作
  - [ ] HelpApp 所有命令正常工作
  - [ ] HistoryApp 所有命令正常工作

  **Evidence to Capture**:
  - [ ] 编译成功日志
  - [ ] 飞书对话截图（所有测试用例）
  - [ ] 其他应用的功能验证日志

  **Commit**: YES
  - Message: `feat(opencode): 添加渐进式引导流程和话题命令验证器`
  - Files:
    - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicState.java`
    - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/CommandWhitelist.java`
    - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/TopicCommandValidator.java`
    - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java`
    - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/OpenCodeApp.java`
  - Pre-commit: `mvn clean compile`

  **Must NOT do**:
  - 不要修改 OpenCodeGateway 接口或实现
  - 不要添加单独的 health/status 命令
  - 不要影响话题中的现有功能

   **Acceptance Criteria**:
   - [ ] 注入 TopicCommandValidator
   - [ ] 实现 getCommandWhitelist() 方法
   - [ ] 实现 isTopicInitialized() 方法
   - [ ] 修改 execute() 方法使用验证器
   - [ ] 实现 connect 子命令
   - [ ] 删除旧的硬编码验证逻辑（如果有）
   - [ ] 代码编译通过: `mvn clean compile`

   **功能验证** (在飞书中测试):

   **非话题模式**:
   - [ ] `/opencode connect` 返回健康信息 + 帮助 + 项目列表
   - [ ] `/opencode help` 返回帮助信息
   - [ ] `/opencode projects` 返回项目列表
   - [ ] `/opencode chat 测试` 返回"只能在话题中使用"提示
   - [ ] `/opencode new 测试` 返回"只能在话题中使用"提示
   - [ ] `/opencode session status` 返回"只能在话题中使用"提示
   - [ ] `/opencode commands` 返回"只能在话题中使用"提示

   **话题模式（未初始化）**:
   - [ ] `/opencode connect` 返回信息
   - [ ] `/opencode chat 测试` 返回"话题未初始化，需要绑定 session"提示
   - [ ] `/opencode new 测试` 返回"话题未初始化，需要绑定 session"提示
   - [ ] `/opencode session list` 正常返回 session 列表
   - [ ] `/opencode session continue ses_xxx` 成功初始化（绑定）并返回提示
   - [ ] `/opencode projects` 正常返回项目列表

   **话题模式（已初始化）**:
   - [ ] `/opencode chat 测试` 正常执行对话
   - [ ] `/opencode new 测试` 正常创建新会话并初始化
   - [ ] `/opencode session status` 正常返回状态
   - [ ] `/opencode projects` 正常返回项目列表
   - [ ] `/opencode commands` 正常返回命令列表
   - [ ] 直接输入 `测试`（无前缀）自动添加 /opencode 前缀并执行

   **向后兼容验证**:
   - [ ] BashApp 所有命令正常工作
   - [ ] TimeApp 所有命令正常工作
   - [ ] HelpApp 所有命令正常工作
   - [ ] HistoryApp 所有命令正常工作

   **Evidence to Capture**:
   - [ ] 编译成功日志
   - [ ] 飞书对话截图（所有测试用例）
   - [ ] 其他应用的功能验证日志

   **Commit**: YES
   - Message: `feat(opencode): 添加渐进式引导流程和话题命令验证器`
   - Files:
     - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/TopicState.java`
     - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/model/CommandWhitelist.java`
     - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/TopicCommandValidator.java`
     - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java`
     - `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/OpenCodeApp.java`
   - Pre-commit: `mvn clean compile`
  - Message.java:getTopicId() - 用于判断是否在非话题
  - OpenCodeGateway.java - 需要查看是否有获取健康信息的方法，如果没有可能需要确认实现方式

  **Acceptance Criteria** (MANUAL VERIFICATION):

  **代码变更验证**:
  - [ ] 添加 `TopicState` 枚举（NON_TOPIC、UNBOUND、BOUND）
  - [ ] 添加 `getTopicState()` 方法
  - [ ] 添加 `isCommandAllowed()` 方法
  - [ ] 修改 `execute()` 方法添加分层白名单验证
  - [ ] 实现 `connect` 子命令
  - [ ] 添加分层错误提示（非话题、话题未绑定）
  - [ ] 代码编译通过: `mvn clean compile`

  **功能验证** (在飞书中测试):

  **非话题模式**:
  - [ ] `/opencode connect` 返回健康信息 + 帮助 + 项目列表
  - [ ] `/opencode help` 返回帮助信息
  - [ ] `/opencode projects` 返回项目列表
  - [ ] `/opencode chat 测试` 返回"只能在话题中使用"提示
  - [ ] `/opencode new 测试` 返回"只能在话题中使用"提示
  - [ ] `/opencode session status` 返回"只能在话题中使用"提示
  - [ ] `/opencode commands` 返回"只能在话题中使用"提示

  **话题模式（未绑定 session）**:
  - [ ] `/opencode connect` 返回信息
  - [ ] `/opencode chat 测试` 返回"需要绑定 session"提示
  - [ ] `/opencode new 测试` 返回"需要绑定 session"提示
  - [ ] `/opencode session list` 正常返回 session 列表
  - [ ] `/opencode session continue ses_xxx` 成功绑定并返回提示
   - [ ] `/opencode projects` 正常返回项目列表

   **向后兼容验证**:
   - [ ] BashApp 所有命令正常工作
   - [ ] TimeApp 所有命令正常工作
   - [ ] HelpApp 所有命令正常工作
   - [ ] HistoryApp 所有命令正常工作

   **Evidence to Capture**:
   - [ ] 编译成功日志
   - [ ] 飞书对话截图（所有测试用例）
   - [ ] 其他应用的功能验证日志

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1, 2, 3 | `feat(opencode): 添加渐进式引导流程和话题命令验证器` | TopicState.java, CommandWhitelist.java, TopicCommandValidator.java, FishuAppI.java, OpenCodeApp.java | mvn clean compile + manual testing |

**说明**：所有三个任务完成后一起提交，因为它们是一个完整的功能单元。

---

## Success Criteria

### Verification Commands

```bash
# 编译验证
mvn clean compile

# 启动应用
./start-feishu.sh

# 查看日志
tail -f /tmp/feishu-run.log | grep -i opencode
```

### Final Checklist

**公共组件**:
- [ ] TopicState 枚举创建并可用（NON_TOPIC、UNINITIALIZED、INITIALIZED）
- [ ] CommandWhitelist 类创建并可用
- [ ] TopicCommandValidator 服务创建并可用（使用函数式接口保持通用性）
- [ ] FishuAppI 接口扩展完成，向后兼容

**OpenCode 功能**:
- [ ] 非话题中只有 connect、help、projects 可用
- [ ] 非话题中使用受限命令返回"只能在话题中使用"提示
- [ ] 话题未初始化时，chat/new 返回"话题未初始化"提示
- [ ] 话题已初始化时，所有命令正常工作
- [ ] connect 返回健康信息 + 帮助 + 项目列表

**代码质量**:
- [ ] 代码编译通过，无错误
- [ ] 所有现有应用（BashApp、TimeApp 等）正常工作（向后兼容）
- [ ] 飞书对话测试通过（三种状态均测试）

**可复用性**:
- [ ] 其他应用可以轻松使用 TopicCommandValidator
- [ ] 通过实现 getCommandWhitelist() 即可启用限制
- [ ] 验证逻辑完全解耦，不依赖具体应用
- [ ] "初始化"概念足够通用，适用于不同应用场景

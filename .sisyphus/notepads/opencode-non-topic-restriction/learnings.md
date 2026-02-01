## [2026-02-01] Task 1 & 2 & 3 完成 - 公共组件和 OpenCodeApp 优化

### Files Created/Modified

**New Files (Task 1)**:
1. `TopicState.java` - 枚举（NON_TOPIC, UNINITIALIZED, INITIALIZED）
2. `CommandWhitelist.java` - 白名单配置类，Builder 模式
3. `ValidationResult.java` - 验证结果（allowed/restricted）
4. `TopicCommandValidator.java` - 验证服务

**Modified Files (Task 2)**:
5. `FishuAppI.java` - 添加 getCommandWhitelist() 和 isTopicInitialized() 方法

**Modified Files (Task 3)**:
6. `OpenCodeApp.java` - 注入验证器，实现接口方法，添加验证逻辑，实现 connect 子命令

### Key Implementation Details

**CommandWhitelist 设计**:
- Builder 模式：`CommandWhitelist.builder().add("a", "b").build()`
- 便捷方法：`all()`, `none()`, `allExcept("chat", "new")`
- 状态分离：每个状态（NON_TOPIC, UNINITIALIZED, INITIALIZED）独立的命令集合
- 空集合 = 允许所有命令

**TopicCommandValidator**:
- `detectState(message, app)` - 调用 app.isTopicInitialized()
- `validateCommand(subCommand, state, whitelist)` - 检查命令是否允许
- `getRestrictedCommandMessage()` - 生成友好的中文提示

**OpenCodeApp 集成**:
- 注入 `TopicCommandValidator`
- 实现 `getCommandWhitelist()` - 定义三层白名单
- 实现 `isTopicInitialized()` - 检测 session 绑定
- `execute()` 开始时验证命令
- 新增 `connect` 子命令 - 组合三个 API 的结果

### OpenCode 命令白名单

```java
NON_TOPIC:     {connect, help, projects}
UNINITIALIZED:  {all except "chat", "new"}
INITIALIZED:    {all}
```

### connect 子命令输出格式

```
🔗 **OpenCode 连接状态**

**健康信息**：[server status]

**快速开始**：
  /opencode chat <内容> - 发送对话
  /opencode new <内容> - 创建新会话
  /opencode session list - 查看所有会话

**近期项目**：[projects list]
```

### Verification

- ✅ mvn clean compile SUCCESS
- ✅ 所有模块编译通过
- ✅ 代码符合项目规范
- ✅ 向后兼容（default 方法）

### Next Steps

需要手动测试（飞书对话）：
1. 非话题中测试受限命令
2. 话题中测试未初始化状态
3. 话题中测试已初始化状态
4. 验证其他应用（BashApp, TimeApp）正常工作

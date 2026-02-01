# 计划目录

本目录包含飞书机器人后端的开发计划。

## 📁 目录结构

```
plans/
├── opencode-non-topic-restriction.md    # 🚀 当前活跃计划
└── completed/                           # ✅ 已完成的计划
    ├── opencode-multiturn-implementation.md
    └── topic-context-aware-commands.md
```

## 🚀 当前活跃计划

### OpenCode 非话题模式限制优化

- **文件**: `opencode-non-topic-restriction.md`
- **状态**: 📝 计划中
- **目标**: 实现渐进式引导流程（非话题 → 未初始化 → 已初始化）+ 公共话题命令验证器
- **下一步**: 运行 `/start-work` 开始执行

## ✅ 已完成计划

已完成的计划存放在 `completed/` 目录中：

### 1. OpenCode 多轮对话实现
- **文件**: `completed/opencode-multiturn-implementation.md`
- **完成日期**: 2026-02-01
- **功能**: 支持 OpenCode 在话题中的多轮对话、会话管理、异步执行

### 2. Topic-Aware Command Execution
- **文件**: `completed/topic-context-aware-commands.md`
- **完成日期**: 2026-02-01
- **功能**: 用户在话题中可以省略命令前缀（如输入 `pwd` 而非 `/bash pwd`）

## 📝 如何开始新计划

1. 在 `plans/` 目录创建新的 `.md` 文件
2. 使用 Prometheus (Plan Builder) 创建详细的工作计划
3. 运行 `/start-work` 开始执行
4. 完成后移动到 `completed/` 目录并标记状态

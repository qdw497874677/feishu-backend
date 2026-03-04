# 可视化应用菜单设计方案

**创建时间**: 2026-03-04  
**作者**: OpenCode  
**状态**: 设计完成，待实施

---

## 📋 概述

### 核心目标

增强现有的 HelpApp，添加可视化卡片按钮交互，提升用户体验。

### 设计原则

- ✅ **最小改动**：只修改 HelpApp，无需新增应用
- ✅ **零破坏性**：保持原有文本格式作为降级方案
- ✅ **渐进增强**：卡片优先，文本降级
- ✅ **简单直接**：用户点击按钮 → 飞书发送消息 → 触发应用

### 核心理念

```
用户输入: /help 或 /?
    ↓
HelpApp.execute()
    ↓
尝试发送卡片（带按钮）
    ├─ 成功 → 显示卡片菜单
    │         用户点击按钮 → 飞书发送消息 → 触发应用
    │
    └─ 失败 → 显示文本菜单（原有逻辑）
```

**关键点**：
- 卡片按钮点击后，飞书自动发送消息（如 "opencode"）
- 后端接收消息并路由到对应应用
- 完全复用现有流程，零破坏性修改

---

## 🏗️ 架构设计

### 系统架构（无变化）

```
┌─────────────────────────────────┐
│  用户输入: /help                 │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  HelpApp（增强）                 │
│  - 尝试发送卡片（新增）          │
│  - 失败降级为文本（原有）        │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  显示应用菜单                   │
│  - 卡片格式（优先）              │
│  - 文本格式（降级）              │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  用户点击按钮: [OpenCode]       │
│  → 飞书发送: "opencode"         │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│  现有流程（完全不变）            │
│  - 解析命令                     │
│  - 路由到 OpenCodeApp           │
│  - 执行并回复                   │
└─────────────────────────────────┘
```

### 核心组件

#### HelpApp 扩展

**位置**: `domain/app/HelpApp.java`

**扩展逻辑**:

```java
@Component
@Slf4j
public class HelpApp implements FishuAppI {
    
    @Autowired
    private AppRegistry appRegistry;
    
    @Autowired
    private FeishuGateway feishuGateway;
    
    @Override
    public String execute(Message message) {
        // 1. 尝试发送卡片帮助
        if (trySendCardHelp(message)) {
            return null;  // 卡片发送成功，不需要返回文本
        }
        
        // 2. 降级：返回文本帮助
        return generateTextHelp();
    }
    
    private boolean trySendCardHelp(Message message) {
        try {
            String cardJson = buildCardHelpJson();
            feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
            return true;
        } catch (Exception e) {
            log.warn("卡片帮助发送失败: error={}", e.getMessage());
            return false;
        }
    }
    
    private String buildCardHelpJson() {
        // 构建带按钮的卡片
        // 每个应用一个按钮，value.message = appId
    }
    
    private String generateTextHelp() {
        // 原有的文本格式帮助信息
        return appRegistry.getAppHelp();
    }
}
```

---

## 🔄 交互流程

### 完整交互流程

```
1. 用户输入: /help
   ↓
2. HelpApp.execute()
   ↓
3. 尝试发送卡片
   ├─ 成功 → 显示卡片菜单
   └─ 失败 → 显示文本菜单
   ↓
4. 用户点击按钮: [🤖 OpenCode 助手]
   ↓
5. 飞书自动发送消息: "opencode"
   ↓
6. 后端接收消息并路由
   ↓
7. OpenCodeApp.execute()
```

### 按钮点击 → 消息转换

**关键机制**：飞书卡片按钮的 `value.message` 字段

```json
{
  "tag": "button",
  "text": {
    "content": "🤖 OpenCode 助手",
    "tag": "plain_text"
  },
  "type": "primary",
  "value": {
    "message": "opencode"  // ← 点击后，飞书自动发送这条消息
  }
}
```

---

## 🎨 菜单设计

### 卡片格式（优先）

```json
{
  "schema": "2.0",
  "header": {
    "title": {"content": "🤖 应用菜单", "tag": "plain_text"},
    "template": "blue"
  },
  "elements": [
    {
      "tag": "action",
      "actions": [
        {
          "tag": "button",
          "text": {"content": "🤖 OpenCode 助手", "tag": "plain_text"},
          "type": "primary",
          "value": {"message": "opencode"}
        },
        {
          "tag": "button",
          "text": {"content": "💻 Bash 命令", "tag": "plain_text"},
          "type": "default",
          "value": {"message": "bash"}
        }
      ]
    }
  ]
}
```

### 文本格式（降级）

```
🤖 应用菜单

1. 🤖 OpenCode 助手
   AI编程助手
   示例: /opencode chat 帮我写代码

2. 💻 Bash 命令
   执行安全的bash命令
   示例: /bash ls -la

回复编号或应用名称选择
```

---

## 🚀 实施计划

### 唯一修改：HelpApp.java

**改动点**：
1. 添加 `trySendCardHelp()` 方法
2. 添加 `buildCardHelpJson()` 方法
3. 修改 `execute()` 方法（卡片优先，文本降级）

**预计代码量**：~100 行

### 测试验证

- [ ] 卡片发送成功
- [ ] 卡片失败降级
- [ ] 按钮点击触发应用

---

## 🎯 成功标准

- ✅ 用户能通过 `/help` 看到卡片菜单
- ✅ 点击按钮能正确触发应用
- ✅ 文本菜单降级体验良好
- ✅ 不影响现有功能
- ✅ 代码改动 < 100 行

---

**预计实施时间**: 1 小时  
**预计上线时间**: 2026-03-04

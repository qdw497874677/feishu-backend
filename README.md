# 飞书机器人后端项目

基于 COLA 架构的飞书机器人后端，使用长连接模式接收和回复消息。

---

## 📄 文档索引

| 文档 | 用途 | 适合人群 |
|------|------|----------|
| [AGENTS.md](./AGENTS.md) | 项目核心规范、COLA 架构、启动命令 | **所有开发者** |
| [APP_GUIDE.md](./APP_GUIDE.md) | 应用开发快速开始、创建应用 | **应用开发者** |

---

## 🎯 快速开始

### 启动项目

```bash
cd feishu-bot-start

LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
FEISHU_MODE=listener \
FEISHU_LISTENER_ENABLED=true \
mvn spring-boot:run
```

### 创建新应用

查看 [APP_GUIDE.md](./APP_GUIDE.md)，3 步完成应用开发：

1. 创建类 → 添加 `@Component` → 实现 `FishuAppI`
2. 构建项目
3. 重启应用（自动注册）

---

## 📁 项目结构

```
feishu-bot/                         # 父 POM
├── feishu-bot-client/              # DTO 层
├── feishu-bot-domain/             # 领域层（应用在这里）
├── feishu-bot-app/                # 应用层
├── feishu-bot-infrastructure/      # 基础设施层
├── feishu-bot-adapter/           # 适配层
└── feishu-bot-start/              # 启动模块
```

---

## ⚠️ 核心规范

### 严禁使用 WebHook

本项目**只允许使用长连接模式**，严格禁止使用 WebHook 模式。

- ❌ WebHook：需要公网 IP 和域名，部署复杂，不稳定
- ✅ 长连接：WebSocket 实时推送，无需回调端点，稳定可靠

**重要说明**：
- ✅ 所有新代码必须基于长连接模式
- ❌ 禁止添加任何 WebHook 相关的新代码
- ✅ 消息接收和发送统一使用 `MessageListenerGateway` 和 `FeishuGateway`

### COLA 架构

遵循 [COLA (Clean Object-oriented and Layered Architecture)](https://github.com/alibaba/COLA) 架构。

**依赖关系**：
```
start → adapter → app → domain + client → infrastructure
```

**分层职责**：
- `feishu-bot-domain`：领域模型、业务逻辑、领域服务、网关接口、应用实现
- `feishu-bot-app`：应用服务、用例编排、命令/查询
- `feishu-bot-infrastructure`：基础设施实现、外部系统集成
- `feishu-bot-adapter`：适配层、外部接口、事件监听
- `feishu-bot-client`：DTO 对象、对外接口定义
- `feishu-bot-start`：启动模块、配置

---

## 🎨 已实现功能

### 应用系统

| 应用ID | 应用名称 | 触发命令 | 状态 |
|---------|---------|-----------|------|
| `time` | 时间查询 | `/time` | ✅ 已实现 |

### 核心功能

- ✅ WebSocket 长连接接收消息
- ✅ 自动应用注册和路由
- ✅ 消息发送和回复
- ✅ UTF-8 中文编码支持
- ✅ 异常处理和日志记录

---

## 🚀 技术栈

- **JDK**: 17+
- **Spring Boot**: 3.2.1
- **飞书 SDK**: 2.5.2
- **架构**: COLA
- **构建工具**: Maven

---

## 📊 项目状态

| 模块 | 状态 | 说明 |
|------|------|------|
| 核心规范 | ✅ 已定义 | AGENTS.md |
| 应用系统 | ✅ 已实现 | AppRegistry, AppRouter, TimeApp |
| 长连接 | ✅ 正常运行 | WebSocket 监听器 |
| 废弃代码 | ✅ 已清理 | 删除 CommandRouter, WebHook, 扩展点 |
| 编码支持 | ✅ 已修复 | UTF-8 中英文显示正常 |

---

## 🔍 常见问题

### Q: 如何创建新应用？

**A:** 查看 [APP_GUIDE.md](./APP_GUIDE.md)，3 步完成：
1. 创建类（添加 `@Component` 和实现 `FishuAppI`）
2. 构建项目
3. 重启应用（自动注册）

### Q: 应用没有生效？

**A:** 检查：
1. 类添加了 `@Component` 注解
2. 实现了 `FishuAppI` 接口
3. `appId` 是唯一的（如 `time`, `weather`）
4. 启动日志显示应用已注册

### Q: 如何查看日志？

**A:**
```bash
tail -f /tmp/feishu-start.log
```

### Q: 如何停止应用？

**A:**
```bash
ps aux | grep "spring-boot:run" | grep -v grep | awk '{print $2}' | xargs kill -9
```

---

## 🔄 消息处理流程

### 表情反馈 vs 卡片降级

**两者是独立的**：

| 机制 | 作用 | 触发时机 |
|------|------|---------|
| **表情** | 对用户消息的即时反馈 | 始终执行 |
| **降级** | 卡片失败时的内容展示策略 | 仅卡片创建失败时 |

### 完整流程

```
用户消息: "帮我写代码"
    │
    ├─→ 添加 ❤️ 表情（始终执行，告诉用户"收到"）
    │
    ├─→ 尝试创建卡片
    │   ├─ 成功 → 卡片流式更新
    │   └─ 失败 → 降级模式（不发中间消息，静默等待）
    │
    ├─→ 执行命令（异步）
    │
    ├─→ 添加 👏 表情（始终执行，告诉用户"完成"）
    │
    └─→ 发送最终结果
```

### 表情的意义

| 表情 | 时机 | 含义 |
|------|------|------|
| ❤️ HEART | 任务开始 | "收到，正在处理" |
| 👏 CLAP | 任务完成 | "处理完成" |

### 卡片配置

```yaml
opencode:
  card:
    enabled: true              # 是否启用卡片流式输出
    fallback-on-error: true    # 卡片失败时是否降级
    title: "🤖 AI 助手"
    thinking-text: "⏳ 正在思考..."
    processing-text: "⏳ 处理中..."
    complete-text: "✅ 完成"
```

---

## 📚 参考资料

- [应用开发规范](./APP_GUIDE.md) - 详细的应用开发指南
- [项目规范](./AGENTS.md) - 核心规范、架构、启动命令
- [飞书 IM SDK 文档](https://open.feishu.cn/document/serverSdk/im sdk)
- [飞书 WebSocket 文档](https://open.feishu.cn/document/serverSdk/event-sdk)
- [COLA 架构](https://github.com/alibaba/COLA)
- [飞书 SDK GitHub](https://github.com/larksuite/oapi-sdk-java)

---

## 📝 开发日志

### 2026-01-25

- ✅ 重构应用系统：引入 `FishuAppI`、`AppRegistry`、`AppRouter`
- ✅ 创建 `TimeApp` 作为示例应用
- ✅ 删除废弃代码：`CommandRouter`、`WebHook`、扩展点
- ✅ 优化项目文档：精简并明确规范
- ✅ 长连接正常工作，消息接收和回复正常

---

**最后更新**: 2026-01-25

# 飞书机器人后端项目

基于 COLA 架构的飞书机器人后端，使用长连接模式接收和回复消息。

---

## 📄 文档索引

| 文档 | 用途 | 适合人群 |
|------|------|----------|
| [AGENTS.md](./AGENTS.md) | 项目核心规范、COLA 架构、启动命令 | **所有开发者** |
| [APP_GUIDE.md](./APP_GUIDE.md) | 应用开发快速开始、创建应用 | **应用开发者** |
| [APP_USAGE_GUIDE.md](./APP_USAGE_GUIDE.md) | 所有应用的使用指南和命令格式 | **用户** |

---

## 🏗️ 架构概览

### COLA 分层架构

```
┌─────────────────────────────────────┐
│         feishu-bot-start          │  ← 启动入口
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│        feishu-bot-adapter         │  ← 适配层（事件监听）
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         feishu-bot-app            │  ← 应用层（用例编排）
└──────────────┬──────────────────────┘
               │
        ┌──────┴───────┐
        │              │
┌──────▼──────┐ ┌─────▼─────┐
│  feishu-bot- │ │feishu-bot-│
│   domain     │ │  client   │  ← 领域层 + DTO 层
└──────┬───────┘ └───────────┘
       │
┌──────▼──────────────────────────┐
│  feishu-bot-infrastructure     │  ← 基础设施层
└─────────────────────────────────┘
```

### 模块职责

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **domain** | 领域模型、业务逻辑、网关接口、应用实现 | `FishuAppI`, `Message`, `BotMessageService` |
| **app** | 应用服务、用例编排 | `Cmd`, `Qry`, `CmdExe` |
| **infrastructure** | 基础设施、外部集成、Gateway实现 | `FeishuGatewayImpl`, `TopicMappingSqliteGateway` |
| **adapter** | 适配层、事件监听 | `FeishuEventListener` |
| **client** | DTO 对象 | `@DTO`, `@Request`, `@Response` |
| **start** | 启动配置 | `Application.java`, `application.yml` |

---

## 🎯 核心组件

### 1. 应用系统（App System）

所有应用实现 `FishuAppI` 接口：

```java
public interface FishuAppI {
    String getAppId();                      // 应用ID
    String getAppName();                    // 应用名称
    String getDescription();                // 描述
    String execute(Message message);        // 执行逻辑
    ReplyMode getReplyMode();               // 回复模式
    List<String> getAppAliases();           // 命令别名
}
```

### 2. 策略模式（Reply Strategy）

处理不同回复场景：

| 策略 | 场景 | 实现 |
|------|------|------|
| `DirectReplyStrategy` | 直接回复（无话题） | `infrastructure/reply/` |
| `TopicReplyStrategy` | 话题回复 | `infrastructure/reply/` |
| `DefaultReplyStrategy` | 默认策略 | `infrastructure/reply/` |

### 3. 网关接口（Gateway Pattern）

领域层定义接口，基础设施层实现：

| 网关接口 | 实现 | 职责 |
|----------|------|------|
| `FeishuGateway` | `FeishuGatewayImpl` | 飞书 API 调用 |
| `MessageListenerGateway` | `MessageListenerGatewayImpl` | WebSocket 长连接 |
| `TopicMappingGateway` | `TopicMappingSqliteGateway` | 话题映射持久化 |
| `OpenCodeGateway` | `OpenCodeGatewayImpl` | OpenCode 集成 |
| `CardGateway` | `CardGatewayImpl` | 卡片消息 |

### 4. 防腐层（Anti-Corruption Layer）

隔离飞书 SDK 变化：
- `MessageEventParser` - 解析飞书事件为领域模型
- 领域层不依赖飞书 SDK 具体类

---

## 🔄 消息处理流程

```
用户消息 (飞书)
    │
    ▼
MessageListenerGateway (WebSocket 接收)
    │
    ▼
MessageEventParser (防腐层解析)
    │
    ▼
BotMessageService.handleMessage() (编排)
    │
    ├─→ 解析命令 / 查找应用
    │       └─→ AppRouter / AppRegistry
    │
    ├─→ 预处理内容（话题模式）
    │
    ├─→ 添加表情反馈
    │
    ├─→ 执行应用逻辑
    │       └─→ FishuAppI.execute()
    │
    ├─→ 发送回复
    │       └─→ ReplyStrategyFactory → ReplyStrategy
    │
    └─→ 保存话题映射
            └─→ TopicMappingGateway
```

---

## 📱 已实现应用

| 应用ID | 名称 | 触发命令 | 别名 | 功能 |
|--------|------|---------|------|------|
| `help` | 帮助 | `/help` | `h`, `?`, `man` | 显示所有命令 |
| `time` | 时间 | `/time` | `t`, `now`, `date` | 查询系统时间 |
| `bash` | 命令 | `/bash <cmd>` | `cmd`, `shell`, `exec` | 执行安全命令 |
| `history` | 历史 | `/history` | - | 查询对话历史 |
| `opencode` | AI助手 | `/opencode <sub>` | `oc`, `code` | OpenCode 集成 |

**详细使用指南**: [APP_USAGE_GUIDE.md](./APP_USAGE_GUIDE.md)

---

## 🚀 快速开始

### 1. 环境准备

```bash
# JDK 17+
java -version

# 克隆项目
git clone <repo-url>
cd feishu-backend
```

### 2. 配置凭证

```bash
# 创建本地启动脚本
cp start-feishu.sh run-local.sh

# 编辑并添加飞书凭证
vim run-local.sh
# 添加：
# export FEISHU_APPID='your-app-id'
# export FEISHU_APPSECRET='your-app-secret'
```

### 3. 启动服务

```bash
./run-local.sh

# 查看日志
tail -f /tmp/feishu-run.log
```

### 4. 验证启动

```bash
# 检查 WebSocket 连接
grep "connected to wss://" /tmp/feishu-run.log

# 检查应用注册
grep "已注册" /tmp/feishu-run.log
```

---

## 🔧 创建新应用

3 步完成：

### 1. 创建应用类

```java
@Component
public class MyApp implements FishuAppI {
    
    @Override
    public String getAppId() {
        return "myapp";
    }

    @Override
    public String execute(Message message) {
        return "Hello from MyApp!";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("ma", "my");
    }
}
```

### 2. 放置到正确目录

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/MyApp.java
```

### 3. 构建并重启

```bash
mvn clean package
./run-local.sh
```

**详细指南**: [APP_GUIDE.md](./APP_GUIDE.md)

---

## 📁 项目结构

```
feishu-backend/
├── feishu-bot-client/              # DTO 层
├── feishu-bot-domain/              # 领域层 ⭐
│   └── src/main/java/.../domain/
│       ├── app/                    # 应用实现
│       ├── core/                   # 核心接口
│       ├── gateway/                # 网关接口
│       ├── message/                # 消息模型
│       ├── reply/                  # 回复策略
│       ├── router/                 # 应用路由
│       └── service/                # 领域服务
├── feishu-bot-app/                 # 应用层
├── feishu-bot-infrastructure/      # 基础设施层
│   └── src/main/java/.../infrastructure/
│       ├── gateway/                # 网关实现
│       ├── reply/                  # 策略实现
│       └── config/                 # 配置
├── feishu-bot-adapter/             # 适配层
└── feishu-bot-start/               # 启动模块
```

---

## ⚠️ 核心规范

### 通信模式（铁律）

| 模式 | 状态 | 说明 |
|------|------|------|
| 长连接 | ✅ **唯一允许** | WebSocket 实时推送 |
| WebHook | ❌ **严禁使用** | 需要公网 IP，部署复杂 |

### COLA 依赖规则

1. **上层依赖下层**: `start → adapter → app → domain → infrastructure`
2. **下层定义接口**: `domain` 定义，`infrastructure` 实现
3. **横向隔离**: 同层模块不能直接依赖

### 代码规范

- 类名：PascalCase (`BotMessageService`)
- 方法名：camelCase (`handleMessage`)
- 常量：UPPER_SNAKE_CASE (`MAX_RETRIES`)
- 禁止单字母变量（循环除外）
- 禁止吞掉异常

---

## 🛠️ 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| JDK | OpenJDK | 17+ |
| Framework | Spring Boot | 3.2.1 |
| Architecture | COLA | - |
| Feishu SDK | larksuite-oapi | 2.5.2 |
| Database | SQLite | - |
| Build | Maven | 3.x |

---

## 📊 项目状态

| 模块 | 状态 | 说明 |
|------|------|------|
| 核心规范 | ✅ | AGENTS.md |
| 应用系统 | ✅ | 5 个应用 |
| 长连接 | ✅ | WebSocket 正常 |
| 话题管理 | ✅ | SQLite 持久化 |
| OpenCode 集成 | ✅ | 多轮对话 |
| 测试覆盖 | ✅ | 单元测试 |

---

## 🔍 常见问题

### Q: 应用没有生效？

检查：
1. 类添加了 `@Component` 注解
2. 实现了 `FishuAppI` 接口
3. `appId` 是唯一的
4. 启动日志显示应用已注册

### Q: 如何查看日志？

```bash
tail -f /tmp/feishu-run.log
```

### Q: 如何停止服务？

```bash
pkill -f "feishu-bot-start"
```

### Q: 消息处理失败？

检查日志中的异常信息：
```bash
grep -i "error\|exception" /tmp/feishu-run.log
```

---

## 📚 参考资料

- [应用开发指南](./APP_GUIDE.md)
- [应用使用指南](./APP_USAGE_GUIDE.md)
- [项目规范](./AGENTS.md)
- [COLA 架构](https://github.com/alibaba/COLA)
- [飞书开放平台](https://open.feishu.cn/)
- [飞书 SDK GitHub](https://github.com/larksuite/oapi-sdk-java)

---

## 📝 开发日志

### 2026-03-18
- ✅ 架构重构完成
- ✅ 5 个应用全部迁移
- ✅ 文档更新

### 2026-01-25
- ✅ 引入 `FishuAppI`、`AppRegistry`、`AppRouter`
- ✅ 创建 `TimeApp` 示例应用
- ✅ 删除废弃代码
- ✅ 长连接正常工作

---

**最后更新**: 2026-03-18

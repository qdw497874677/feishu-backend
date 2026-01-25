# 飞书机器人 - 项目规范

---

## ⚠️ 核心规范（必须遵守）

### 🔴 严禁使用 WebHook 模式

| 模式 | 状态 | 原因 |
|------|------|------|
| WebHook | ❌ **严禁使用** | 需要公网 IP 和域名，部署复杂，不稳定 |
| 长连接 | ✅ **唯一允许** | WebSocket 实时推送，无需回调端点，稳定可靠 |

**重要说明**：
- ✅ 所有新代码必须基于长连接模式
- ❌ 禁止添加任何 WebHook 相关的新代码
- ✅ 消息接收和发送统一使用 `MessageListenerGateway` 和 `FeishuGateway`

---

### 🏗️ COLA 架构规范

本项目严格遵循 [COLA (Clean Object-oriented and Layered Architecture)](https://github.com/alibaba/COLA) 架构。

#### 新建代码放置规则

| 模块 | 职责 | 新建代码类型 | 示例 |
|------|------|-------------|------|
| **feishu-bot-domain** | 领域模型、业务逻辑、领域服务、网关接口、应用实现 | `@Entity`, `@ValueObject`, `DomainService`, `Gateway Interface`, `FishuAppI` | `Message.java`, `BotMessageService.java`, `FeishuGateway.java`, `TimeApp.java` |
| **feishu-bot-app** | 应用服务、用例编排、命令/查询 | `@AppService`, `Cmd`, `Qry`, `CmdExe`, `QryExe` | `ReceiveMessageCmd.java`, `ReceiveMessageCmdExe.java` |
| **feishu-bot-infrastructure** | 基础设施实现、外部系统集成 | Gateway 实现、Config、Repository 实现 | `FeishuGatewayImpl.java`, `FeishuProperties.java` |
| **feishu-bot-adapter** | 适配层、外部接口、事件监听 | Controller、Listener、Event Handler | `FeishuEventListener.java`, `GlobalExceptionHandler.java` |
| **feishu-bot-client** | DTO 对象、对外接口定义 | `@DTO`, `@Request`, `@Response` | `ReceiveMessageCmd.java` |
| **feishu-bot-start** | 启动模块、配置 | `Application.java`, `application.yml`, `pom.xml` (父) | - |

#### COLA 分层依赖原则

```
┌─────────────────────────────────────┐
│         feishu-bot-start          │  ← 启动入口
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│        feishu-bot-adapter         │  ← 适配层
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         feishu-bot-app            │  ← 应用层
└──────────────┬──────────────────────┘
               │
        ┌───────┴───────┐
        │               │
┌──────▼──────┐  ┌─────▼─────┐
│  feishu-bot-  │  │feishu-bot-│
│   domain     │  │  client   │  ← 领域层 + DTO 层
└──────┬───────┘  └───────────┘
       │
┌──────▼──────────────────────────┐
│  feishu-bot-infrastructure     │  ← 基础设施层
└─────────────────────────────────┘
```

**依赖规则**：
- 上层可以依赖下层
- 下层不能依赖上层（反转依赖：domain 定义接口，infrastructure 实现）
- 横向模块之间不能直接依赖

#### 代码放置决策树

```
需要添加什么代码？
│
├─ 实体/值对象/领域服务/领域事件/应用实现
│  └─ → feishu-bot-domain
│
├─ 命令/查询/用例执行器
│  └─ → feishu-bot-app
│
├─ 数据库/外部 API 实现/配置类
│  └─ → feishu-bot-infrastructure
│
├─ Controller/EventListener/事件处理
│  └─ → feishu-bot-adapter
│
├─ DTO/请求响应对象
│  └─ → feishu-bot-client
│
└─ 启动配置/主类
   └─ → feishu-bot-start
```

#### ⚠️ 注意事项

- **禁止跨层依赖**：下层不能依赖上层
- **接口定义在 domain**：domain 定义接口，infrastructure 实现
- **横向隔离**：同层模块之间不能直接依赖
- **长连接相关**：
  - `MessageListenerGateway` 接口定义在 `domain`
  - `MessageListenerGatewayImpl` 实现在 `infrastructure`
  - `FeishuEventListener` 启动监听器在 `adapter`

---

## 🚀 启动命令

### 长连接模式（唯一允许模式）

```bash
cd /root/workspace/feishu-backend/feishu-bot-start

LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
FEISHU_APPID="your_app_id" \
FEISHU_APPSECRET="your_app_secret" \
FEISHU_MODE="listener" \
FEISHU_LISTENER_ENABLED=true \
mvn spring-boot:run
```

**⚠️ 注意：本项目不支持 WebHook 模式启动，只能使用长连接模式！**

---

## 📁 关键文件位置

**注意：以下文件位置严格遵循 COLA 架构规范**

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── app/                              # 应用系统
│   ├── FishuAppI.java               # 应用接口
│   ├── AppRegistry.java               # 应用注册中心
│   └── TimeApp.java                  # 时间应用（示例）
├── exception/                        # 异常定义
├── gateway/                         # 网关接口
│   ├── FeishuGateway.java
│   └── MessageListenerGateway.java
├── message/                          # 消息领域模型
│   ├── Message.java
│   ├── MessageType.java
│   ├── Sender.java
│   ├── MessageStatus.java
│   └── SendResult.java
├── router/                          # 路由器
│   └── AppRouter.java                # 应用路由器
└── service/                          # 领域服务
    └── BotMessageService.java

feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── config/
│   └── FeishuProperties.java       # 飞书配置属性
└── gateway/
    ├── FeishuGatewayImpl.java      # 飞书网关实现
    └── MessageListenerGatewayImpl.java # 长连接实现

feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/
├── exception/
│   └── GlobalExceptionHandler.java
└── listener/
    └── FeishuEventListener.java      # 长连接监听器
```

---

## 🐛 常见错误

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `NoSuchMethodError: Sender: method 'void <init>()' not found` | 缺少无参构造函数 | 添加 `@NoArgsConstructor` 注解 |
| `content is not a string in json format` | 消息内容格式错误 | 使用 `MessageText.newBuilder().text().build()` |
| `app_id is invalid` | 凭证配置错误 | 检查 `FEISHU_APPID` 和 `FEISHU_APPSECRET` |
| `No qualifying bean of type 'BotMessageService'` | 未注册为 Bean | 添加 `@Service` 注解 |
| 中文显示为 `?` | 编码配置不正确 | 配置系统 locale、JVM 参数和日志编码 |

### ⚠️ 架构规范违规

| 违规行为 | 后果 | 正确做法 |
|---------|------|----------|
| 在 `domain` 中引用 `infrastructure` | 违反 COLA 原则，代码无法编译 | `domain` 定义接口，`infrastructure` 实现接口 |
| 在 `app` 中直接使用 SDK | 耦合外部依赖，难以测试 | 通过 `Gateway` 接口调用 |
| 使用 WebHook 模式 | 不符合项目规范，代码将被拒绝 | 必须使用长连接模式 |
| 跨模块直接依赖 | 违反分层原则 | 通过 DTO 或网关接口交互 |

---

## 📚 参考资料

- [应用开发规范](./APP_GUIDE.md) - 快速创建新应用
- [飞书 IM SDK 文档](https://open.feishu.cn/document/serverSdk/im sdk)
- [飞书 WebSocket 文档](https://open.feishu.cn/document/serverSdk/event-sdk)
- [COLA 框架](https://github.com/alibaba/COLA)
- [飞书 SDK GitHub](https://github.com/larksuite/oapi-sdk-java)

---

## 🔍 调试命令

```bash
# 实时查看日志
tail -f /tmp/feishu-run.log

# 查看消息相关日志
tail -f /tmp/feishu-run.log | grep -E "(Received|Processing|Sending|Error)"

# 验证 WebSocket 连接
grep "connected to wss://" /tmp/feishu-run.log
```

---

**最后更新**: 2026-01-25

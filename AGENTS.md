# 飞书机器人长连接实现 - 开发日志

## 📋 项目规范

### ⚠️ 重要：仅使用长连接模式

**本项目严格禁止使用 WebHook 模式，必须使用飞书长连接（WebSocket）模式。**

- ❌ **禁止**：WebHook 模式（需要公网 IP，不稳定）
- ✅ **强制**：长连接模式（WebSocket，稳定可靠）

**原因**：
- WebHook 需要服务器具备公网 IP 和域名，部署复杂
- 长连接通过 WebSocket 实时推送消息，无需回调端点
- 长连接在消息延迟和稳定性上优于 WebHook

---

### 🏗️ COLA 架构规范

本项目采用 [COLA (Clean Object-oriented and Layered Architecture)](https://github.com/alibaba/COLA) 架构。

#### 模块职责

**新建代码时，请严格按照以下规则选择模块：**

| 模块 | 职责 | 新建代码类型 | 示例 |
|------|------|-------------|------|
| **feishu-bot-domain** | 领域模型、业务逻辑、领域服务、网关接口 | `@Entity`, `@ValueObject`, `DomainService`, `Gateway Interface` | `Message.java`, `BotMessageService.java`, `FeishuGateway.java` |
| **feishu-bot-app** | 应用服务、用例编排、命令/查询 | `@AppService`, `Cmd`, `Qry`, `CmdExe`, `QryExe` | `ReceiveMessageCmd.java`, `ReceiveMessageCmdExe.java` |
| **feishu-bot-infrastructure** | 基础设施实现、外部系统集成 | Gateway 实现、Config、Repository 实现 | `FeishuGatewayImpl.java`, `FeishuProperties.java`, `MessageListenerGatewayImpl.java` |
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
├─ 实体/值对象/领域服务/领域事件
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

---

## ✅ 最终状态（2026-01-24）

**长连接机器人已成功上线并正常工作！**

- ✅ WebSocket 成功连接到飞书服务器
- ✅ 消息接收正常
- ✅ 消息回复正常
- ✅ 中文编码正常（UTF-8）
- ✅ 回显模式工作正常

---

## 📝 关键问题修复

### 最新修复（2026-01-24）

**问题**: `NoSuchMethodError: Sender: method 'void <init>()' not found`

**原因**: `Sender` 类使用 `@Data` 但缺少 `@NoArgsConstructor` 注解

**修复**:
1. 给 `Sender.java` 添加 `@NoArgsConstructor` 注解
2. 修复 `BotMessageService.java` 的导入问题（添加 `UserId` 和 `MessageSysException`）

**位置**:
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/message/Sender.java`
- `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`

### 之前修复的问题汇总

1. **SDK 依赖**: 使用 `com.larksuite.oapi:oapi-sdk:2.5.2`（包名 `com.lark.oapi`）
2. **方法签名**: 统一 `sendReply` 为 `String receiveOpenId`
3. **消息格式**: 解析 JSON content，提取 `text` 字段
4. **消息发送**: 使用 `MessageText.newBuilder().text().build()`，避免 `.message()` 和 `.content()` 冲突
5. **Bean 注册**: 领域服务添加 `@Service` 注解
6. **编码问题**: 配置系统 locale、JVM 参数和日志编码

---

## 💡 核心经验

### 飞书 SDK 使用要点

```java
// 消息接收 - content 是 JSON 字符串
String textContent = content;
if (content != null && content.startsWith("{")) {
    JsonObject json = gson.fromJson(content, JsonObject.class);
    if (json.has("text")) {
        textContent = json.get("text").getAsString();
    }
}

// 消息发送
MessageText messageText = MessageText.newBuilder().text("内容").build();
client.message().sendMessage()
    .receiveIdType("open_id")
    .receiveId(openId)
    .msgType("text")
    .content(messageText)
    .build();
```

**注意事项**:
- SDK 2.5.2 包名是 `com.lark.oapi`（不是 `com.larksuite.oapi`）
- 不要同时使用 `.message()` 和 `.content()`

### Spring Boot + COLA 架构

```java
// 领域服务必须注册为 Spring Bean
@Service
public class BotMessageService {
    // ...
}

// 配置属性
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {
    // ...
}
```

### 字符编码配置

**系统层面**:
```bash
LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8
```

**JVM 层面** (pom.xml):
```xml
<arguments>
    <argument>-Dfile.encoding=UTF-8</argument>
    <argument>-Dconsole.encoding=UTF-8</argument>
    <argument>-Dsun.jnu.encoding=UTF-8</argument>
</arguments>
```

**日志框架** (application.yml):
```yaml
logging:
  charset:
    console: UTF-8
```

---

## 🚀 启动命令

### 长连接模式（唯一推荐模式）

```bash
cd /root/workspace/feishu-backend/feishu-bot-start

LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
FEISHU_APPID="your_app_id" \
FEISHU_APPSECRET="your_app_secret" \
FEISHU_MODE="listener" \
FEISHU_LISTENER_ENABLED=true \
mvn spring-boot:run
```

---

## 📁 关键文件位置

```
feishu-bot-domain/src/main/java/com/qdw/feishu/domain/
├── gateway/
│   ├── FeishuGateway.java           # 飞书网关接口
│   └── MessageListenerGateway.java  # 长连接网关接口
├── message/
│   ├── Message.java                 # 消息实体
│   ├── Sender.java                  # 发送者（需要 @NoArgsConstructor）
│   └── SendResult.java              # 发送结果
└── service/
    └── BotMessageService.java       # 消息处理服务（需要 @Service）

feishu-bot-infrastructure/src/main/java/com/qdw/feishu/infrastructure/
├── config/
│   └── FeishuProperties.java       # 飞书配置属性
└── gateway/
    ├── FeishuGatewayImpl.java      # 飞书网关实现
    └── MessageListenerGatewayImpl.java # 长连接实现

feishu-bot-adapter/src/main/java/com/qdw/feishu/adapter/
├── listener/
│   └── FeishuEventListener.java    # 飞书事件监听器（启动长连接）
└── FeishuWebhookController.java    # WebHook 控制器（已弃用）
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

---

## 📚 参考资料

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

**🎉 项目状态**: 100% 完成，所有功能正常运行

# Feishu Bot Backend - COLA Architecture

基于 COLA (Clean Object-Oriented and Layered Architecture) 框架的飞书机器人后端项目。

## 架构设计

```
Adapter Layer (适配器层)
  └── FeishuWebhookController
       └── 处理飞书 Webhook 事件

App Layer (应用层)
  └── ReceiveMessageCmdExe
       └── 编排业务逻辑，参数校验

Domain Layer (领域层)
  ├── Message (消息实体)
  ├── BotMessageSender (领域服务)
  ├── FeishuGateway (网关接口)
  └── ReplyExtensionPt (扩展点)
       └── 支持插件化的回复策略

Infrastructure Layer (基础设施层)
  ├── FeishuGatewayImpl
  │    └── 封装飞书 SDK 调用
  └── FeishuProperties
       └── 配置管理
```

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.6+

### 配置环境变量

```bash
export FEISHU_APPID=your_app_id
export FEISHU_APPSECRET=your_app_secret
export FEISHU_VERIFICATION_TOKEN=your_verification_token
export FEISHU_ENCRYPT_KEY=your_encrypt_key
```

### 编译和运行

```bash
# 编译项目
mvn clean install -DskipTests

# 运行应用
cd feishu-bot-start
mvn spring-boot:run
```

### 测试接口

```bash
# 健康检查
curl http://localhost:8080/webhook/health

# 模拟飞书 Webhook
curl -X POST http://localhost:8080/webhook/feishu \
  -H "Content-Type: application/json" \
  -d '{"type":"im.message.receive_v1","event":{"message":{"content":"Hello Feishu Bot"}}}'
```

## 项目模块

| 模块 | 说明 | 主要类 |
|--------|------|--------|
| **feishu-bot-adapter** | 适配器层 | FeishuWebhookController |
| **feishu-bot-client** | 客户端层 | MessageServiceI, ReceiveMessageCmd |
| **feishu-bot-app** | 应用层 | ReceiveMessageCmdExe |
| **feishu-bot-domain** | 领域层 | Message, FeishuGateway, ReplyExtensionPt |
| **feishu-bot-infrastructure** | 基础设施层 | FeishuGatewayImpl, FeishuProperties |
| **feishu-bot-start** | 启动模块 | Application, application.yml |

## COLA 架构优势

✅ **职责清晰**: 每层有明确的单一职责
✅ **依赖倒置**: 领域层定义接口，基础设施层实现
✅ **高内聚**: 相关的类组织在同一个包内
✅ **低耦合**: 层之间通过 DTO 交互，不直接依赖实现
✅ **易扩展**: 扩展点机制，支持新功能无需修改核心
✅ **可测试**: 依赖抽象，便于 Mock 和测试

## 扩展点示例

实现不同的回复策略：

```java
@Extension(bizId = "feishu-bot", useCase = "ai")
@Component
public class AiReplyExtension implements ReplyExtensionPt {
    @Override
    public String enhanceReply(String originalReply, Message message) {
        // 调用 AI 生成回复
        return aiGateway.generateReply(message.getContent());
    }
}

@Extension(bizId = "feishu-bot", useCase = "keyword")
@Component
public class KeywordReplyExtension implements ReplyExtensionPt {
    @Override
    public String enhanceReply(String originalReply, Message message) {
        String content = message.getContent().toLowerCase();
        if (content.contains("你好") {
            return "你好！我是飞书机器人 🤖";
        }
        return originalReply;
    }
}
```

## 技术栈

- **JDK**: 17
- **Spring Boot**: 3.2.1
- **COLA**: 5.0.0
- **Feishu SDK**: larksuite-oapi 2.4.22
- **Lombok**: 1.18.30
- **SLF4J**: 2.0.9
- **Maven**: 3.9.x

## License

Copyright © 2026

# 飞书机器人长连接实现经验沉淀

这个 Skill 包含飞书机器人长连接实现过程中遇到的问题、解决方案和最佳实践。

## 适用场景

当你在使用 Spring Boot + COLA 架构开发飞书机器人，并需要实现长连接（WebSocket）功能时，这个 Skill 会帮助你避免常见的坑点。

---

## 核心经验

### 1. SDK 依赖和版本问题

#### 问题
- 错误的 Maven 坐标：`larksuite-oapi:2.4.22`（Maven Central 不存在）
- SDK 版本变化导致的包名更改

#### 解决方案
**使用正确的 Maven 依赖**：
```xml
<dependency>
    <groupId>com.larksuite.oapi</groupId>
    <artifactId>oapi-sdk</artifactId>
    <version>2.5.2</version>
</dependency>
```

**注意包名**：
- SDK 2.5.2 的包名是 `com.lark.oapi`（不是 `com.larksuite.oapi`）
- 导入时使用：`import com.lark.oapi.*`

#### 验证方法
```bash
# 检查 SDK 版本
mvn dependency:tree | grep larksuite

# 清理本地仓库缓存
rm -rf ~/.m2/repository/com/larksuite.oapi/
```

---

### 2. 消息格式处理

#### 问题
长连接和 WebHook 接收到的 `content` 字段都是 JSON 格式：
```json
{"text":"消息内容"}
```

如果直接传递给 SDK，会导致格式错误：`content is not a string in json format`

#### 解决方案
**接收端解析 JSON**：
```java
private Message convertToMessage(P2MessageReceiveV1 event) {
    P2MessageReceiveV1Data data = event.getEvent();
    String content = data.getMessage().getContent();

    // 解析 JSON 格式的消息内容
    String textContent = content;
    if (content != null && content.startsWith("{")) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            if (json.has("text")) {
                textContent = json.get("text").getAsString();
            }
        } catch (Exception e) {
            log.warn("Failed to parse message content, using original: {}", content, e);
        }
    }

    return new Message(data.getMessage().getMessageId(), textContent, sender);
}
```

**发送端使用 SDK 构建器**：
```java
CreateMessageReq req = CreateMessageReq.newBuilder()
    .receiveIdType("open_id")
    .createMessageReqBody(CreateMessageReqBody.newBuilder()
        .receiveId(receiveOpenId)
        .msgType("text")
        .content(MessageText.newBuilder().text(content).build())
        .build())
    .build();
```

#### 重要提醒
- ❌ 不要同时使用 `.message()` 和 `.content()`，会冲突
- ✅ 只使用 `.content()` 传递 `MessageText.newBuilder().text("...").build()`

---

### 3. Spring Bean 注册

#### 问题
领域服务（如 `BotMessageService`）未注册为 Spring Bean 时，会导致依赖注入失败：
```
No qualifying bean of type 'BotMessageService' available
```

#### 解决方案
**方式1：使用 `@Service` 注解（推荐）**
```java
package com.qdw.feishu.domain.service;

import org.springframework.stereotype.Service;

@Service
public class BotMessageService {
    // ...
}
```

**方式2：创建配置类显式注册**
```java
package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.service.BotMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public BotMessageService botMessageService(FeishuGateway feishuGateway) {
        return new BotMessageService(feishuGateway);
    }
}
```

#### COLA 架构最佳实践
- 领域服务应该在 domain 层，使用 `@Service` 注解
- 如果需要在 infrastructure 层注册，使用配置类

---

### 4. 配置属性管理

#### 问题
配置类缺少 `@Component` 注解，导致属性绑定失败。

#### 解决方案
**完整的配置类**：
```java
package com.qdw.feishu.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    private String appid;
    private String appsecret;
    private String encryptKey;
    private String verificationToken;
    private String mode = "webhook";
    private Listener listener = new Listener();

    @Data
    public static class Listener {
        private boolean enabled = false;
    }
}
```

#### 配置文件
```yaml
feishu:
  appid: ${FEISHU_APPID:your_app_id}
  appsecret: ${FEISHU_APPSECRET:your_app_secret}
  encryptKey: ${FEISHU_ENCRYPT_KEY:your_encrypt_key}
  verificationToken: ${FEISHU_VERIFICATION_TOKEN:your_verification_token}
  mode: ${FEISHU_MODE:webhook}
  listener:
    enabled: ${FEISHU_LISTENER_ENABLED:false}
```

#### 重要提醒
- 嵌套配置类（如 `Listener`）必须是静态内部类
- 使用 `@Component` 确保被 Spring 扫描到
- 支持环境变量覆盖：`${VAR_NAME:default_value}`

---

### 5. 条件化 Bean

#### 场景
只在特定模式下启用某个组件（如长连接监听器）。

#### 解决方案
```java
@Component
@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")
public class FeishuEventListener implements ApplicationRunner {

    @Value("${feishu.listener.enabled}")
    private boolean listenerEnabled;

    @Override
    public void run(String... args) {
        if (!listenerEnabled) {
            log.info("Feishu listener is disabled, skipping...");
            return;
        }

        log.info("Starting WebSocket connection to Feishu...");
        // 启动长连接逻辑
    }
}
```

#### 常用条件
- `@ConditionalOnProperty(name = "xxx", havingValue = "yyy")` - 匹配特定值
- `@ConditionalOnProperty(name = "xxx", matchIfMissing = true)` - 缺失时启用
- `@ConditionalOnMissingBean(BeanType.class)` - Bean 不存在时启用

---

### 6. 字符编码处理

#### 问题
日志中中文显示为 `?`，影响可读性。

#### 根本原因
系统 locale 不支持 UTF-8（如 `POSIX`）。

#### 解决方案

**方案1：系统环境变量（Docker 推荐）**
```dockerfile
ENV LANG=zh_CN.UTF-8
ENV LC_ALL=zh_CN.UTF-8
```

**方案2：JVM 启动参数**
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <mainClass>com.qdw.feishu.Application</mainClass>
                <arguments>
                    <argument>-Dfile.encoding=UTF-8</argument>
                    <argument>-Dconsole.encoding=UTF-8</argument>
                    <argument>-Dsun.jnu.encoding=UTF-8</argument>
                </arguments>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**方案3：日志框架配置**
```yaml
logging:
  level:
    root: INFO
    com.qdw.feishu: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  charset:
    console: UTF-8
```

**方案4：运行时设置环境变量**
```bash
LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
FEISHU_APPID="xxx" \
FEISHU_APPSECRET="xxx" \
mvn spring-boot:run
```

#### 验证方法
```bash
# 检查系统 locale
locale

# 查看日志中中文是否正常
tail -f /tmp/feishu.log | grep "你"
```

---

### 7. 接口签名匹配

#### 问题
接口定义和实现的方法签名不一致，导致编译错误。

#### 解决方案
**接口定义**：
```java
public interface FeishuGateway {
    SendResult sendReply(String receiveOpenId, String content);
}
```

**实现类**：
```java
@Component
public class FeishuGatewayImpl implements FeishuGateway {

    @Override
    public SendResult sendReply(String receiveOpenId, String content) {
        // 实现逻辑
    }
}
```

#### 常见错误
- 参数类型不匹配（`String` vs `Object`）
- 参数数量不匹配
- 返回类型不匹配

---

### 8. 长连接 WebSocket 集成

#### 关键代码
```java
@Component
@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")
public class MessageListenerGatewayImpl implements MessageListenerGateway {

    private final FeishuProperties properties;
    private final EventDispatcher eventDispatcher;
    private Client wsClient;

    @Override
    public void startListening(Consumer<Message> messageHandler) {
        String appId = properties.getAppid();
        String appSecret = properties.getAppsecret();

        wsClient = com.lark.oapi.ws.Client.newBuilder(appId, appSecret)
            .eventDispatcher(new EventDispatcher()
                .onP2MessageReceiveV1(this::handleMessage)
                .build())
            .build();

        wsClient.start();
    }

    private void handleMessage(P2MessageReceiveV1 event) {
        Message msg = convertToMessage(event);
        messageHandler.accept(msg);
    }
}
```

#### 启动逻辑
使用 `ApplicationRunner` 在应用启动时自动启动：
```java
@Component
@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")
public class FeishuEventListener implements ApplicationRunner {

    @Autowired
    private MessageListenerGateway messageListenerGateway;

    @Autowired
    private ReceiveMessageListenerExe receiveMessageListenerExe;

    @Value("${feishu.listener.enabled}")
    private boolean listenerEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!listenerEnabled) {
            return;
        }

        messageListenerGateway.startListening(message -> {
            receiveMessageListenerExe.execute(message);
        });
    }
}
```

---

## 常见错误及解决方案

### 错误1: "content is not a string in json format"
- **原因**：消息内容格式错误
- **解决**：确保使用 `MessageText.newBuilder().text("内容").build()`
- **排查**：检查日志中的 content 是否为纯文本

### 错误2: "app_id is invalid"
- **原因**：app_id 或 app_secret 配置错误
- **解决**：检查环境变量 `FEISHU_APPID` 和 `FEISHU_APPSECRET`
- **排查**：确认在飞书开发者后台获取的凭证正确

### 错误3: "No qualifying bean of type 'BotMessageService'"
- **原因**：领域服务未注册为 Spring Bean
- **解决**：添加 `@Service` 注解或创建配置类

### 错误4: 中文显示为 `?`
- **原因**：编码配置不正确
- **解决**：同时配置系统 locale、JVM 参数和日志编码

### 错误5: 端口被占用
- **原因**：应用重启时端口未释放
- **解决**：停止占用端口的进程
- **排查**：
  ```bash
  ps aux | grep java | grep -v grep | awk '{print $2}' | xargs kill -9
  ```

---

## 调试技巧

### 查看消息处理流程
```bash
# 实时查看日志
tail -f /tmp/feishu.log | grep -E "(Received|Processing|Sending)"
```

### 验证 WebSocket 连接
```bash
# 查找连接成功日志
grep "connected to wss://" /tmp/feishu.log
```

### 测试 API 端点
```bash
# 健康检查
curl -X POST http://localhost:8080/webhook/health

# Webhook 端点
curl -X POST http://localhost:8080/webhook/feishu \
  -H "Content-Type: application/json" \
  -d '{"type":"im.message.receive_v1","event":{...}}'
```

### 检查依赖树
```bash
mvn dependency:tree | grep larksuite
```

---

## 项目结构参考

### COLA 架构模块
```
feishu-bot/
├── feishu-bot-client/         # 客户端 DTO
├── feishu-bot-domain/         # 领域层（核心业务逻辑）
├── feishu-bot-app/           # 应用层（用例编排）
├── feishu-bot-infrastructure/  # 基础设施层（SDK 集成）
├── feishu-bot-adapter/       # 适配层（Webhook、事件监听）
└── feishu-bot-start/         # 启动模块
```

### 关键类位置
```
feishu-bot-domain/
├── gateway/
│   ├── FeishuGateway.java           # 飞书网关接口
│   └── MessageListenerGateway.java  # 长连接网关接口
├── message/
│   ├── Message.java                 # 消息实体
│   └── Sender.java                  # 发送者
└── service/
    └── BotMessageService.java       # 消息处理服务

feishu-bot-infrastructure/
├── config/
│   └── FeishuProperties.java       # 飞书配置属性
└── gateway/
    └── FeishuGatewayImpl.java      # 飞书网关实现（SDK调用）

feishu-bot-adapter/
├── listener/
│   └── FeishuEventListener.java    # 飞书事件监听器（启动长连接）
└── FeishuWebhookController.java    # WebHook 控制器
```

---

## 参考资源

### 官方文档
- 飞书 IM SDK 文档：https://open.feishu.cn/document/serverSdk/im sdk
- 飞书 WebSocket 文档：https://open.feishu.cn/document/serverSdk/event-sdk
- 飞书机器人文档：https://open.feishu.cn/document/chatbot/chatbot-overview

### 开源项目
- 飞书 SDK GitHub：https://github.com/larksuite/oapi-sdk-java
- COLA 框架：https://github.com/alibaba/COLA

---

## 总结

这个 Skill 涵盖了飞书机器人长连接实现过程中遇到的所有关键问题和解决方案。通过遵循这些经验，你可以：

1. ✅ 正确配置飞书 SDK 依赖
2. ✅ 正确处理 JSON 格式的消息内容
3. ✅ 正确注册和管理 Spring Bean
4. ✅ 正确配置和管理应用属性
5. ✅ 正确实现条件化组件
6. ✅ 正确处理字符编码问题
7. ✅ 正确集成 WebSocket 长连接
8. ✅ 快速诊断和解决常见错误

**遵循这些最佳实践，可以避免 90% 的常见坑点！**

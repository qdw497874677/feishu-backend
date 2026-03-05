# 消息处理钩子系统 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为飞书应用添加后处理钩子机制，默认对用户消息添加表情回应

**Architecture:** 在 FishuAppI 接口添加 afterExecute 默认方法，BotMessageService 在应用执行后调用钩子，默认行为是添加 👍 表情回应

**Tech Stack:** Java 17, Spring Boot, Lombok, Feishu SDK

---

## Task 1: 添加 afterExecute 钩子到 FishuAppI 接口

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java`

**Step 1: 在 FishuAppI 接口添加 afterExecute 方法**

在 `isTopicInitialized` 方法后添加：

```java
    /**
     * 后处理钩子 - 应用执行完成后调用
     *
     * 默认行为：对用户消息添加 👍 表情回应
     * 子类可覆盖此方法定制行为（如使用不同表情、记录日志等）
     *
     * @param message 原始消息对象
     * @param result 执行结果
     * @param feishuGateway 飞书网关（用于调用飞书 API）
     */
    default void afterExecute(Message message, String result, com.qdw.feishu.domain.gateway.FeishuGateway feishuGateway) {
        feishuGateway.addReaction(message.getMessageId(), "THUMBSUP");
    }
```

**Step 2: 验证编译通过**

Run: `cd /root/workspace/feishu-backend && mvn compile -Dmaven.test.skip=true -q`
Expected: BUILD SUCCESS

---

## Task 2: 修改 BotMessageService 调用钩子

**Files:**
- Modify: `feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java`

**Step 1: 移除当前的硬编码表情回应**

找到并删除这行代码（约在 194 行）：
```java
            // 对用户消息添加表情回应（默认能力）
            feishuGateway.addReaction(message.getMessageId(), "THUMBSUP");
```

**Step 2: 在应用执行后调用钩子**

在 `String replyContent = app.execute(message);` 之后，`if (replyContent == null ...)` 之前添加：

```java
            String replyContent = app.execute(message);
            
            // 调用应用的后处理钩子（默认添加表情回应）
            app.afterExecute(message, replyContent, feishuGateway);
            
            if (replyContent == null || replyContent.isEmpty()) {
```

**Step 3: 验证编译通过**

Run: `cd /root/workspace/feishu-backend && mvn compile -Dmaven.test.skip=true -q`
Expected: BUILD SUCCESS

---

## Task 3: 构建并测试

**Files:**
- 无新增文件

**Step 1: 完整构建**

Run: `cd /root/workspace/feishu-backend && mvn clean package -Dmaven.test.skip=true -q`
Expected: BUILD SUCCESS

**Step 2: 重启服务测试**

Run: 
```bash
pkill -f "feishu-bot-start" 2>/dev/null; sleep 1
cd /root/workspace/feishu-backend/feishu-bot-start
export FEISHU_APPID='cli_a8f66e3df8fb100d'
export FEISHU_APPSECRET='CFVrKX1w00ypHEqT1vInwdeKznwmYWpn'
java -jar target/feishu-bot-start-1.0.0-SNAPSHOT.jar > /tmp/feishu-run.log 2>&1 &
```
Expected: 服务启动成功

**Step 3: 在飞书测试**

发送 `/help` 或 `chat 你好`，验证：
- 用户消息上出现 👍 表情回应
- 机器人正常回复

---

## Task 4: 提交代码

**Step 1: 查看变更**

Run: `cd /root/workspace/feishu-backend && git status`
Expected: 显示修改的文件

**Step 2: 提交**

Run: 
```bash
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/app/FishuAppI.java
git add feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
git commit -m "feat(hooks): 添加消息处理钩子系统

- FishuAppI 接口添加 afterExecute 默认方法
- 默认行为是对用户消息添加 👍 表情回应
- BotMessageService 在应用执行后调用钩子
- 子类可覆盖钩子定制行为"
```

---

## 扩展示例（可选）

如果某个应用需要定制钩子行为，可以这样覆盖：

```java
@Component
public class OpenCodeApp implements FishuAppI {
    
    // ... 其他代码 ...
    
    @Override
    public void afterExecute(Message message, String result, FeishuGateway feishuGateway) {
        // 根据执行结果使用不同表情
        if (result != null && result.startsWith("❌")) {
            feishuGateway.addReaction(message.getMessageId(), "CONFUSED");
        } else {
            feishuGateway.addReaction(message.getMessageId(), "ROCKET");
        }
    }
}
```

---

## 设计文档

本计划的设计讨论记录在：`docs/plans/2026-02-22-message-hooks-design.md`

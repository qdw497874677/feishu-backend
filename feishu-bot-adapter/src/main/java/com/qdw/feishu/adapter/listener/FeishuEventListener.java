package com.qdw.feishu.adapter.listener;

import com.qdw.feishu.app.listener.ReceiveMessageListenerExe;
import com.qdw.feishu.domain.config.FeishuConfig;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Slf4j
@Component
@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")
public class FeishuEventListener implements ApplicationRunner {

    private final MessageListenerGateway messageListenerGateway;
    private final ReceiveMessageListenerExe receiveMessageListenerExe;
    private final FeishuConfig config;

    @Autowired
    public FeishuEventListener(
            MessageListenerGateway messageListenerGateway,
            ReceiveMessageListenerExe receiveMessageListenerExe,
            FeishuConfig config) {
        this.messageListenerGateway = messageListenerGateway;
        this.receiveMessageListenerExe = receiveMessageListenerExe;
        this.config = config;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== FeishuEventListener 启动开始 ===");
        log.info("模式: {}", config.getMode());
        log.info("监听器启用状态: {}", config.isListenerEnabled());

        if (!config.getListener().isEnabled()) {
            log.warn("Feishu listener is disabled, skipping initialization");
            return;
        }

        log.info("开始初始化飞书事件监听器...");

        Consumer<Message> messageHandler = this.receiveMessageListenerExe::execute;

        try {
            messageListenerGateway.startListening(messageHandler);
            log.info("✅ 飞书事件监听器启动成功");
            log.info("=== FeishuEventListener 启动完成 ===\n");
        } catch (Exception e) {
            log.error("❌ 飞书事件监听器启动失败", e);
            throw new RuntimeException("Failed to start Feishu event listener", e);
        }
    }

    public void destroy() {
        log.info("Shutting down Feishu event listener...");
        messageListenerGateway.stopListening();
    }
}
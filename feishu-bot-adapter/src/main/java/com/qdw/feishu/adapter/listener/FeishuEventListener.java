package com.qdw.feishu.adapter.listener;

import com.qdw.feishu.app.listener.ReceiveMessageListenerExe;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 飞书事件监听器
 * 应用启动时自动初始化长连接
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "feishu.mode", havingValue = "listener")
public class FeishuEventListener implements ApplicationRunner {

    private final MessageListenerGateway messageListenerGateway;
    private final ReceiveMessageListenerExe receiveMessageListenerExe;
    private final FeishuProperties properties;

    @Autowired
    public FeishuEventListener(
            MessageListenerGateway messageListenerGateway,
            ReceiveMessageListenerExe receiveMessageListenerExe,
            FeishuProperties properties) {
        this.messageListenerGateway = messageListenerGateway;
        this.receiveMessageListenerExe = receiveMessageListenerExe;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getListener().isEnabled()) {
            log.info("Feishu listener is disabled, skipping initialization");
            return;
        }

        log.info("Initializing Feishu event listener...");

        Consumer<Message> messageHandler = this.receiveMessageListenerExe::execute;

        try {
            messageListenerGateway.startListening(messageHandler);
            log.info("Feishu event listener started successfully");
        } catch (Exception e) {
            log.error("Failed to start Feishu event listener", e);
            throw new RuntimeException("Failed to start Feishu event listener", e);
        }
    }

    public void destroy() {
        log.info("Shutting down Feishu event listener...");
        messageListenerGateway.stopListening();
    }
}
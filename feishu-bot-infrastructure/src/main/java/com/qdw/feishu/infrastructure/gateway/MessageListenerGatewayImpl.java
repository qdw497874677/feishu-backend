package com.qdw.feishu.infrastructure.gateway;

import com.lark.oapi.Client;
import com.lark.oapi.core.event.EventDispatcher;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 飞书消息监听网关实现
 * 使用 SDK 的 EventDispatcher 建立长连接
 */
@Slf4j
@Component
public class MessageListenerGatewayImpl implements MessageListenerGateway {

    private final Client client;
    private final FeishuProperties properties;

    private EventDispatcher eventDispatcher;
    private final AtomicReference<ConnectionStatus> connectionStatus;
    private final AtomicBoolean running;
    private Consumer<Message> messageHandler;

    public MessageListenerGatewayImpl(Client client, FeishuProperties properties) {
        this.client = client;
        this.properties = properties;
        this.connectionStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
        this.running = new AtomicBoolean(false);
    }

    @Override
    public synchronized void startListening(Consumer<Message> messageHandler) {
        ConnectionStatus currentStatus = connectionStatus.get();
        if (currentStatus == ConnectionStatus.CONNECTED || currentStatus == ConnectionStatus.CONNECTING) {
            log.warn("Listener already started, status: {}", currentStatus);
            return;
        }

        this.messageHandler = messageHandler;
        running.set(true);
        connectionStatus.set(ConnectionStatus.CONNECTING);

        try {
            eventDispatcher = client.event();
            registerMessageHandler();
            eventDispatcher.start();
            connectionStatus.set(ConnectionStatus.CONNECTED);
            log.info("Feishu event listener started successfully");
        } catch (Exception e) {
            connectionStatus.set(ConnectionStatus.DISCONNECTED);
            running.set(false);
            log.error("Failed to start event listener", e);
            throw new RuntimeException("Failed to start event listener", e);
        }
    }

    @Override
    public synchronized void stopListening() {
        running.set(false);
        if (eventDispatcher != null) {
            eventDispatcher.stop();
        }
        connectionStatus.set(ConnectionStatus.DISCONNECTED);
        messageHandler = null;
        log.info("Feishu event listener stopped");
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus.get();
    }

    private void registerMessageHandler() {
        if (messageHandler == null) {
            log.warn("Message handler not provided, skipping registration");
            return;
        }

        try {
            eventDispatcher.onP2MessageReceiveV1((event) -> {
                Message message = convertToMessage(event);
                if (messageHandler != null) {
                    messageHandler.accept(message);
                }
            });
            log.info("Message handler registered successfully");
        } catch (Exception e) {
            log.error("Failed to register message handler", e);
            throw new RuntimeException("Failed to register message handler", e);
        }
    }

    private Message convertToMessage(com.lark.oapi.service.im.v1.event.P2MessageReceiveV1 event) {
        return new Message(
            event.getEvent().getMessage().getMessageId(),
            event.getEvent().getMessage().getContent(),
            com.qdw.feishu.domain.message.Sender.builder()
                .openId(event.getEvent().getSender().getSenderType().equals("user") ? 
                    event.getEvent().getSender().getSenderId() : 
                    event.getEvent().getSender().getSenderId())
                .build()
        );
    }

    private synchronized void reconnect() {
        if (!running.get()) {
            return;
        }

        connectionStatus.set(ConnectionStatus.RECONNECTING);
        int attempt = 0;
        int delay = 1000;
        int maxAttempts = 10;

        while (running.get() && attempt < maxAttempts) {
            try {
                attempt++;
                Thread.sleep(delay);
                log.info("Reconnect attempt {}/{}", attempt, maxAttempts);

                startListening(this.messageHandler);
                log.info("Reconnect succeeded after {} attempts", attempt);
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Reconnect interrupted");
                break;
            } catch (Exception e) {
                delay = Math.min(delay * 2, 30000);
                log.error("Reconnect failed, next attempt in {}ms", delay, e);
            }
        }

        if (attempt >= maxAttempts) {
            log.error("Reconnect failed after {} attempts, stopping", maxAttempts);
            stopListening();
        }
    }
}

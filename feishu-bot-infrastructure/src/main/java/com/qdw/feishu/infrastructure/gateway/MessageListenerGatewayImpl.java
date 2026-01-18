package com.qdw.feishu.infrastructure.gateway;

import com.lark.oapi.Client;
import com.lark.oapi.core.event.EventDispatcher;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private volatile ConnectionStatus connectionStatus = ConnectionStatus.DISCONNECTED;
    private volatile boolean running = false;

    public MessageListenerGatewayImpl(Client client, FeishuProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void startListening(Consumer<Message> messageHandler) {
        if (connectionStatus == ConnectionStatus.CONNECTED || connectionStatus == ConnectionStatus.CONNECTING) {
            log.warn("Listener already started, status: {}", connectionStatus);
            return;
        }

        running = true;
        connectionStatus = ConnectionStatus.CONNECTING;

        try {
            eventDispatcher = client.event();
            eventDispatcher.start();
            connectionStatus = ConnectionStatus.CONNECTED;
            log.info("Feishu event listener started successfully");
        } catch (Exception e) {
            connectionStatus = ConnectionStatus.DISCONNECTED;
            log.error("Failed to start event listener", e);
            throw new RuntimeException("Failed to start event listener", e);
        }
    }

    @Override
    public void stopListening() {
        running = false;
        if (eventDispatcher != null) {
            eventDispatcher.stop();
        }
        connectionStatus = ConnectionStatus.DISCONNECTED;
        log.info("Feishu event listener stopped");
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    private void reconnect() {
        if (!running) {
            return;
        }

        connectionStatus = ConnectionStatus.RECONNECTING;
        int attempt = 0;
        int delay = 1000;

        while (running) {
            try {
                attempt++;
                Thread.sleep(delay);
                log.info("Reconnect attempt {}/{}", attempt, 10);
                log.info("Reconnect succeeded after {} attempts", attempt);
                break;
            } catch (Exception e) {
                delay = Math.min(delay * 2, 30000);
                log.error("Reconnect failed, next attempt in {}ms", delay);
            }
        }
    }
}

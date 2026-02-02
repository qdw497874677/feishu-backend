package com.qdw.feishu.infrastructure.gateway;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import com.qdw.feishu.domain.gateway.MessageEventParser;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class MessageListenerGatewayImpl implements MessageListenerGateway {

    private final FeishuProperties properties;
    private final EventDispatcher eventDispatcher;
    private final MessageEventParser messageEventParser;
    private Client wsClient;

    private final AtomicReference<ConnectionStatus> connectionStatus;
    private final AtomicBoolean running;
    private Consumer<Message> messageHandler;

    public MessageListenerGatewayImpl(FeishuProperties properties, 
                                     MessageEventParser messageEventParser) {
        this.properties = properties;
        this.messageEventParser = messageEventParser;
        this.connectionStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
        this.running = new AtomicBoolean(false);

        this.eventDispatcher = EventDispatcher.newBuilder(
            properties.getVerificationToken(),
            properties.getEncryptKey()
        ).onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
            @Override
            public void handle(P2MessageReceiveV1 event) throws Exception {
                log.info("Received message event");

                if (messageHandler != null) {
                    // 使用防腐层解析事件
                    Message message = messageEventParser.parse(event);
                    messageHandler.accept(message);
                }
            }
        }).build();
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
            wsClient = new Client.Builder(
                properties.getAppId(),
                properties.getAppSecret()
            ).eventHandler(eventDispatcher)
             .build();

            log.info("Starting WebSocket connection to Feishu...");

            new Thread(() -> {
                try {
                    connectionStatus.set(ConnectionStatus.CONNECTED);
                    wsClient.start();
                } catch (Exception e) {
                    log.error("WebSocket connection failed", e);
                    connectionStatus.set(ConnectionStatus.DISCONNECTED);
                    running.set(false);
                }
            }, "feishu-ws-listener").start();

            log.info("Feishu WebSocket listener started successfully");

        } catch (Exception e) {
            connectionStatus.set(ConnectionStatus.DISCONNECTED);
            running.set(false);
            log.error("Failed to start WebSocket listener", e);
            throw new RuntimeException("Failed to start WebSocket listener", e);
        }
    }

    @Override
    public synchronized void stopListening() {
        running.set(false);
        connectionStatus.set(ConnectionStatus.DISCONNECTED);
        messageHandler = null;

        wsClient = null;
        log.info("Feishu WebSocket listener stopped");
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        return connectionStatus.get();
    }
}

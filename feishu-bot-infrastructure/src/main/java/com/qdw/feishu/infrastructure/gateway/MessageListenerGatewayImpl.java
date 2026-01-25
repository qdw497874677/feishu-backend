package com.qdw.feishu.infrastructure.gateway;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lark.oapi.core.utils.Jsons;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.ws.Client;
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
    private Client wsClient;
    private final Gson gson = new Gson();

    private final AtomicReference<ConnectionStatus> connectionStatus;
    private final AtomicBoolean running;
    private Consumer<Message> messageHandler;

    public MessageListenerGatewayImpl(FeishuProperties properties) {
        this.properties = properties;
        this.connectionStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
        this.running = new AtomicBoolean(false);

        this.eventDispatcher = EventDispatcher.newBuilder(
            properties.getVerificationToken(),
            properties.getEncryptKey()
        ).onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
            @Override
            public void handle(P2MessageReceiveV1 event) throws Exception {
                log.info("Received message event: {}", Jsons.DEFAULT.toJson(event));

                if (messageHandler != null) {
                    Message message = convertToMessage(event);
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
                properties.getAppid(),
                properties.getAppsecret()
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

    private Message convertToMessage(P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data data = event.getEvent();

        String content = data.getMessage().getContent();

        // 解析 JSON 格式的消息内容
        String textContent = content;
        if (content != null && content.startsWith("{")) {
            try {
                com.google.gson.JsonObject json = gson.fromJson(content, com.google.gson.JsonObject.class);
                if (json.has("text")) {
                    textContent = json.get("text").getAsString();
                }
            } catch (Exception e) {
                log.warn("Failed to parse message content, using original: {}", content, e);
            }
        }

        com.qdw.feishu.domain.message.Sender sender = new com.qdw.feishu.domain.message.Sender();
        sender.setUserId(data.getSender().getSenderId());

        // 提取 topicId（如果消息来自话题）
        // 由于 SDK v2.5.2 可能不直接支持 getRootId()，从原始 JSON 中提取
        // TODO: 性能优化 - 当前先序列化再反序列化，可考虑直接访问 SDK 内部数据或升级 SDK
        String topicId = null;
        try {
            // 尝试从事件的原始数据中获取 rootId
            String eventJson = Jsons.DEFAULT.toJson(data);
            com.google.gson.JsonObject eventObj = gson.fromJson(eventJson, com.google.gson.JsonObject.class);
            if (eventObj.has("root_id") && !eventObj.get("root_id").isJsonNull()) {
                topicId = eventObj.get("root_id").getAsString();
                log.debug("消息 topicId: {}", topicId);
            } else {
                log.debug("消息没有 topicId (root_id)");
            }
        } catch (Exception e) {
            log.debug("解析 topicId 失败: {}", e.getMessage());
        }

        Message message = new Message(
            data.getMessage().getMessageId(),
            textContent,
            sender
        );

        // 设置 topicId
        message.setTopicId(topicId);

        return message;
    }
}

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

        // 提取 eventId（用于去重）
        String eventId = null;
        try {
            eventId = event.getHeader().getEventId();
            log.debug("Extracted eventId: {}", eventId);
        } catch (Exception e) {
            log.error("Failed to extract eventId", e);
        }

        // 提取 topicId 和 rootId（如果消息来自话题）
        // 使用正则表达式从原始 JSON 中直接提取
        String topicId = null;
        String rootId = null;
        try {
            // 将事件转换为 JSON 字符串
            String eventJson = Jsons.DEFAULT.toJson(event);

            // 提取 thread_id
            java.util.regex.Pattern threadIdPattern = java.util.regex.Pattern.compile("\"thread_id\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher threadMatcher = threadIdPattern.matcher(eventJson);

            if (threadMatcher.find()) {
                topicId = threadMatcher.group(1);
                log.info("Successfully extracted threadId: {}", topicId);
            }

            // 提取 root_id（话题根消息ID，用于回复到话题）
            java.util.regex.Pattern rootIdPattern = java.util.regex.Pattern.compile("\"root_id\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher rootMatcher = rootIdPattern.matcher(eventJson);

            if (rootMatcher.find()) {
                rootId = rootMatcher.group(1);
                log.info("Successfully extracted rootId: {}", rootId);
            }

            if (topicId == null && rootId == null) {
                log.info("No thread_id or root_id found, treating as normal message");
            }

        } catch (Exception e) {
            log.error("Failed to extract topicId/rootId", e);
        }

        Message message = new Message(
            data.getMessage().getMessageId(),
            textContent,
            sender
        );

        // 设置 eventId
        message.setEventId(eventId);

        // 设置 topicId 和 rootId
        message.setTopicId(topicId);
        message.setRootId(rootId);

        // 设置 chatId
        message.setChatId(data.getMessage().getChatId());

        return message;
    }
}

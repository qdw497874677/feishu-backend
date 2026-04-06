package com.qdw.feishu.infrastructure.gateway;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import com.qdw.feishu.domain.gateway.MessageEventParser;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.processor.EventProcessor;
import com.qdw.feishu.infrastructure.config.FeishuProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class MessageListenerGatewayImpl implements MessageListenerGateway {

    private final FeishuProperties properties;
    private final EventDispatcher eventDispatcher;
    private final MessageEventParser messageEventParser;
    private final EventProcessor eventProcessor;
    private Client wsClient;

    private final AtomicReference<ConnectionStatus> connectionStatus;
    private final AtomicBoolean running;
    private Consumer<Message> messageHandler;

    public MessageListenerGatewayImpl(FeishuProperties properties,
                                      MessageEventParser messageEventParser,
                                      EventProcessor eventProcessor) {
        this.properties = properties;
        this.messageEventParser = messageEventParser;
        this.eventProcessor = eventProcessor;
        this.connectionStatus = new AtomicReference<>(ConnectionStatus.DISCONNECTED);
        this.running = new AtomicBoolean(false);

        this.eventDispatcher = EventDispatcher.newBuilder(
            properties.getVerificationToken(),
            properties.getEncryptKey()
        )
        .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
            @Override
            public void handle(P2MessageReceiveV1 event) throws Exception {
                handleEvent(event);
            }
        })
        .onP2CardActionTrigger(new P2CardActionTriggerHandler() {
            @Override
            public P2CardActionTriggerResponse handle(P2CardActionTrigger event) throws Exception {
                handleCardAction(event);
                return new P2CardActionTriggerResponse();
            }
        })
        .build();
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

    @SuppressWarnings("unchecked")
    private void handleEvent(P2MessageReceiveV1 event) {
        try {
            log.debug("Received message event: {}", event.getRequestId());
            Message message = messageEventParser.parse(event);
            
            if (messageHandler != null) {
                messageHandler.accept(message);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle message event", e);
        }
    }

    /**
     * 处理卡片按钮点击事件
     * 将按钮 action 值（如 "time"）转换为 "/time" 命令，通过现有消息处理流程执行
     */
    private void handleCardAction(P2CardActionTrigger event) {
        try {
            log.info("收到卡片按钮点击事件");

            if (event.getEvent() == null || event.getEvent().getAction() == null) {
                log.warn("卡片事件缺少 action 数据");
                return;
            }

            Map<String, Object> actionValue = event.getEvent().getAction().getValue();
            if (actionValue == null || !actionValue.containsKey("action")) {
                log.warn("卡片按钮 value 中缺少 action 字段: {}", actionValue);
                return;
            }

            String action = actionValue.get("action").toString();
            log.info("卡片按钮点击: action={}", action);

            // 获取平台事件 ID（用于去重）
            String cardEventId = resolveCardEventId(event);
            log.info("卡片事件 ID: {}", cardEventId);

            // 构造伪 Message，将按钮点击转换为文字命令
            Message message = new Message();
            message.setContent("/" + action);
            message.setEventId(cardEventId);

            // 设置发送者
            String openId = "";
            if (event.getEvent().getOperator() != null) {
                openId = event.getEvent().getOperator().getOpenId();
            }
            message.setSender(new Sender(openId, "card-user"));

            // 从 context 获取 chatId
            if (event.getEvent().getContext() != null) {
                message.setChatId(event.getEvent().getContext().getOpenChatId());
            }

            if (messageHandler != null) {
                messageHandler.accept(message);
            }

        } catch (Exception e) {
            log.error("处理卡片按钮点击事件失败", e);
        }
    }

    /**
     * 从卡片事件中提取唯一事件 ID，用于去重
     * 优先使用飞书平台的 eventId，兜底使用 UUID
     */
    private String resolveCardEventId(P2CardActionTrigger event) {
        if (event.getHeader() != null && event.getHeader().getEventId() != null
                && !event.getHeader().getEventId().isEmpty()) {
            return "card-" + event.getHeader().getEventId();
        }
        return "card-" + UUID.randomUUID().toString();
    }
}

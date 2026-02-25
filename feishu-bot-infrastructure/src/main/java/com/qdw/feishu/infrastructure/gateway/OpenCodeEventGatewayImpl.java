package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.gateway.OpenCodeEventGateway;
import com.qdw.feishu.domain.opencode.OpenCodeEvent;
import com.qdw.feishu.infrastructure.config.OpenCodeProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Consumer;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "opencode", name = "sse-enabled", havingValue = "true", matchIfMissing = true)
public class OpenCodeEventGatewayImpl implements OpenCodeEventGateway {

    private final OpenCodeProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private volatile Disposable subscription;
    private volatile boolean connected = false;
    private volatile Consumer<OpenCodeEvent> eventHandler;

    public OpenCodeEventGatewayImpl(OpenCodeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getServerUrl())
                .defaultHeader("Authorization", getAuthHeader())
                .build();
        log.info("OpenCode SSE Gateway 初始化完成: {}", properties.getServerUrl());
    }

    private String getAuthHeader() {
        if (properties.getPassword() == null || properties.getPassword().isEmpty()) {
            return "";
        }
        String auth = properties.getUsername() + ":" + properties.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void subscribe(Consumer<OpenCodeEvent> handler) {
        if (subscription != null && !subscription.isDisposed()) {
            log.warn("SSE 连接已存在，跳过重复订阅");
            return;
        }

        this.eventHandler = handler;
        startSubscription();
    }

    private void startSubscription() {
        log.info("开始订阅 OpenCode SSE 事件: {}/event", properties.getServerUrl());

        Flux<String> eventStream = webClient.get()
                .uri("/event")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnSubscribe(s -> {
                    connected = true;
                    log.info("SSE 连接已建立");
                })
                .doOnError(e -> {
                    connected = false;
                    log.error("SSE 连接错误: {}", e.getMessage());
                })
                .doOnCancel(() -> {
                    connected = false;
                    log.info("SSE 连接已取消");
                });

        subscription = eventStream
                .retryWhen(reactor.util.retry.Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(properties.getSseReconnectInterval()))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.warn("SSE 重连中，第 {} 次尝试", signal.totalRetries() + 1)))
                .subscribe(
                        this::handleRawEvent,
                        error -> log.error("SSE 订阅异常", error)
                );
    }

    private void handleRawEvent(String rawData) {
        try {
            JsonNode json = objectMapper.readTree(rawData);
            String type = json.has("type") ? json.get("type").asText() : null;
            JsonNode props = json.has("properties") ? json.get("properties") : null;

            if (type == null) {
                return;
            }

            if ("server.connected".equals(type)) {
                connected = true;
                log.info("收到 server.connected 事件，SSE 连接就绪");
                return;
            }

            if ("server.heartbeat".equals(type)) {
                log.debug("收到心跳事件");
                return;
            }

            OpenCodeEvent event = OpenCodeEvent.of(type, props);
            
            if (eventHandler != null) {
                eventHandler.accept(event);
            }

        } catch (Exception e) {
            log.warn("解析 SSE 事件失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isConnected() {
        return connected && subscription != null && !subscription.isDisposed();
    }

    @Override
    public synchronized void disconnect() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            subscription = null;
            connected = false;
            log.info("SSE 连接已断开");
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }
}

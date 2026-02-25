package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.gateway.OpenCodeEventGateway;
import com.qdw.feishu.domain.opencode.OpenCodeStreamingHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "opencode", name = "sse-enabled", havingValue = "true", matchIfMissing = true)
public class OpenCodeSseConfig {

    private final OpenCodeEventGateway eventGateway;
    private final OpenCodeStreamingHandler streamingHandler;

    public OpenCodeSseConfig(
            @Autowired(required = false) OpenCodeEventGateway eventGateway,
            OpenCodeStreamingHandler streamingHandler) {
        this.eventGateway = eventGateway;
        this.streamingHandler = streamingHandler;
    }

    @PostConstruct
    public void init() {
        if (eventGateway == null) {
            log.warn("OpenCodeEventGateway 未启用，流式响应功能不可用");
            return;
        }

        log.info("初始化 OpenCode SSE 订阅...");
        eventGateway.subscribe(event -> {
            log.debug("收到事件: type={}, sessionId={}", event.getType(), event.getSessionId());
            streamingHandler.handleEvent(event);
        });
    }
}

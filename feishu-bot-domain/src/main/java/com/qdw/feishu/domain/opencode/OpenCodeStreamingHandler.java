package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.card.StreamingCardManager;
import com.qdw.feishu.domain.config.CardProperties;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 流式响应处理器
 *
 * 支持两种模式：
 * 1. 卡片流式更新：使用 CardKit API 实现单卡片动态更新
 * 2. 降级模式：卡片失败时使用普通消息
 *
 * 配置项：
 * - opencode.card.enabled: 是否启用卡片流式
 * - opencode.card.fallback-on-error: 是否降级
 */
@Slf4j
@Component
public class OpenCodeStreamingHandler {

    private final FeishuGateway feishuGateway;
    private final StreamingCardManager cardManager;
    private final CardProperties cardProperties;
    private final ScheduledExecutorService scheduler;

    private final Map<String, StringBuilder> textBuffers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToTopicMap = new ConcurrentHashMap<>();
    private final Map<String, Message> sessionToMessageMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFlushTime = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToCardMap = new ConcurrentHashMap<>();
    private final Set<String> fallbackSessions = ConcurrentHashMap.newKeySet();

    private static final long FLUSH_INTERVAL_MS = 2000;
    private static final long MIN_FLUSH_INTERVAL_MS = 1000;

    public OpenCodeStreamingHandler(FeishuGateway feishuGateway, 
                                    StreamingCardManager cardManager,
                                    CardProperties cardProperties) {
        this.feishuGateway = feishuGateway;
        this.cardManager = cardManager;
        this.cardProperties = cardProperties;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void registerSession(String sessionId, Message message) {
        String topicId = message.getTopicId();
        sessionToTopicMap.put(sessionId, topicId);
        sessionToMessageMap.put(sessionId, message);
        textBuffers.put(sessionId, new StringBuilder());
        lastFlushTime.put(sessionId, System.currentTimeMillis());
        
        if (cardManager.isEnabled()) {
            String cardId = cardManager.createAndSend(message, cardProperties.getThinkingText(), topicId);
            if (cardId != null) {
                sessionToCardMap.put(sessionId, cardId);
                log.info("注册会话流式处理（卡片模式）: sessionId={}, topicId={}, cardId={}", sessionId, topicId, cardId);
            } else {
                fallbackSessions.add(sessionId);
                log.warn("注册会话流式处理（降级模式）: sessionId={}, topicId={}, 卡片创建失败，等待最终结果", sessionId, topicId);
            }
        } else {
            fallbackSessions.add(sessionId);
            log.info("卡片模式已禁用，使用降级模式: sessionId={}", sessionId);
        }
    }

    public void unregisterSession(String sessionId) {
        textBuffers.remove(sessionId);
        sessionToTopicMap.remove(sessionId);
        sessionToMessageMap.remove(sessionId);
        lastFlushTime.remove(sessionId);
        sessionToCardMap.remove(sessionId);
        fallbackSessions.remove(sessionId);
        
        ScheduledFuture<?> task = flushTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        log.info("注销会话流式处理: sessionId={}", sessionId);
    }

    public void handleEvent(OpenCodeEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null || !sessionToTopicMap.containsKey(sessionId)) {
            return;
        }

        if (event.isTextUpdate()) {
            handleTextDelta(sessionId, event);
        } else if (event.isStatusUpdate() && event.isSessionIdle()) {
            handleSessionComplete(sessionId);
        }
    }

    private void handleTextDelta(String sessionId, OpenCodeEvent event) {
        String delta = event.getDelta();
        if (delta == null || delta.isEmpty()) {
            return;
        }

        StringBuilder buffer = textBuffers.get(sessionId);
        if (buffer == null) {
            return;
        }

        buffer.append(delta);
        log.debug("累积文本增量: sessionId={}, delta长度={}, buffer长度={}", 
                sessionId, delta.length(), buffer.length());

        scheduleFlush(sessionId);
    }

    private void scheduleFlush(String sessionId) {
        if (flushTasks.containsKey(sessionId)) {
            return;
        }

        ScheduledFuture<?> task = scheduler.schedule(() -> {
            flushBuffer(sessionId);
            flushTasks.remove(sessionId);
        }, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);

        flushTasks.put(sessionId, task);
    }

    private synchronized void flushBuffer(String sessionId) {
        StringBuilder buffer = textBuffers.get(sessionId);
        String topicId = sessionToTopicMap.get(sessionId);
        Message message = sessionToMessageMap.get(sessionId);

        if (buffer == null || topicId == null || message == null) {
            return;
        }

        String text = buffer.toString();
        if (text.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastFlush = lastFlushTime.get(sessionId);
        if (lastFlush != null && (now - lastFlush) < MIN_FLUSH_INTERVAL_MS) {
            return;
        }

        buffer.setLength(0);
        lastFlushTime.put(sessionId, now);

        String formattedText = formatStreamingText(text);
        
        String cardId = sessionToCardMap.get(sessionId);
        if (cardId != null && !fallbackSessions.contains(sessionId)) {
            boolean success = cardManager.update(cardId, formattedText);
            if (success) {
                log.info("更新卡片: sessionId={}, cardId={}, length={}", sessionId, cardId, text.length());
            } else {
                log.error("更新卡片失败，降级为普通消息: sessionId={}, cardId={}", sessionId, cardId);
                fallbackSessions.add(sessionId);
                feishuGateway.sendMessage(message, formattedText, topicId);
            }
        } else {
            feishuGateway.sendMessage(message, formattedText, topicId);
            log.info("发送流式更新（降级模式）: sessionId={}, length={}", sessionId, text.length());
        }
    }

    private void handleSessionComplete(String sessionId) {
        flushBuffer(sessionId);
        
        StringBuilder buffer = textBuffers.get(sessionId);
        if (buffer != null && buffer.length() > 0) {
            String finalText = buffer.toString();
            Message message = sessionToMessageMap.get(sessionId);
            String topicId = sessionToTopicMap.get(sessionId);
            String cardId = sessionToCardMap.get(sessionId);
            
            if (message != null && topicId != null) {
                String completeText = cardProperties.getCompleteText() + "\n\n" + finalText;
                
                if (cardId != null && !fallbackSessions.contains(sessionId)) {
                    boolean success = cardManager.update(cardId, completeText);
                    if (success) {
                        log.info("会话完成（卡片模式）: sessionId={}, cardId={}", sessionId, cardId);
                    } else {
                        log.error("更新完成状态失败，降级为普通消息: sessionId={}, cardId={}", sessionId, cardId);
                        feishuGateway.sendMessage(message, completeText, topicId);
                    }
                    cardManager.cleanup(cardId);
                } else {
                    feishuGateway.sendMessage(message, completeText, topicId);
                    log.info("会话完成（降级模式）: sessionId={}", sessionId);
                }
            }
        } else {
            String cardId = sessionToCardMap.get(sessionId);
            if (cardId != null) {
                cardManager.cleanup(cardId);
            }
        }
        
        unregisterSession(sessionId);
        log.info("会话完成: sessionId={}", sessionId);
    }

    private String formatStreamingText(String text) {
        return cardProperties.getProcessingText() + "\n\n" + text;
    }
}

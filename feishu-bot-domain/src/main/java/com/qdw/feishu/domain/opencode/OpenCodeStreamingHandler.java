package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 流式响应处理器
 *
 * 处理 SSE 事件，累积文本增量，定期发送到飞书
 */
@Slf4j
@Component
public class OpenCodeStreamingHandler {

    private final FeishuGateway feishuGateway;
    private final ScheduledExecutorService scheduler;

    private final Map<String, StringBuilder> textBuffers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToTopicMap = new ConcurrentHashMap<>();
    private final Map<String, Message> sessionToMessageMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> flushTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFlushTime = new ConcurrentHashMap<>();

    private static final long FLUSH_INTERVAL_MS = 2000;
    private static final long MIN_FLUSH_INTERVAL_MS = 1000;

    public OpenCodeStreamingHandler(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void registerSession(String sessionId, Message message) {
        String topicId = message.getTopicId();
        sessionToTopicMap.put(sessionId, topicId);
        sessionToMessageMap.put(sessionId, message);
        textBuffers.put(sessionId, new StringBuilder());
        lastFlushTime.put(sessionId, System.currentTimeMillis());
        log.info("注册会话流式处理: sessionId={}, topicId={}", sessionId, topicId);
    }

    public void unregisterSession(String sessionId) {
        textBuffers.remove(sessionId);
        sessionToTopicMap.remove(sessionId);
        sessionToMessageMap.remove(sessionId);
        lastFlushTime.remove(sessionId);
        
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
        feishuGateway.sendMessage(message, formattedText, topicId);
        log.info("发送流式更新: sessionId={}, length={}", sessionId, text.length());
    }

    private void handleSessionComplete(String sessionId) {
        flushBuffer(sessionId);
        
        StringBuilder buffer = textBuffers.get(sessionId);
        if (buffer != null && buffer.length() > 0) {
            String finalText = buffer.toString();
            Message message = sessionToMessageMap.get(sessionId);
            String topicId = sessionToTopicMap.get(sessionId);
            
            if (message != null && topicId != null) {
                feishuGateway.sendMessage(message, 
                    "✅ 完成\n\n" + finalText, topicId);
            }
        }
        
        unregisterSession(sessionId);
        log.info("会话完成: sessionId={}", sessionId);
    }

    private String formatStreamingText(String text) {
        return "⏳ 处理中...\n\n" + text;
    }
}

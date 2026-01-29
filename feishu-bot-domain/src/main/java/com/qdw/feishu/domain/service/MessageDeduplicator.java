package com.qdw.feishu.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息去重器
 * 基于 event_id 判断消息是否已处理
 * 使用内存缓存，设置过期时间为 5 分钟
 */
@Slf4j
@Component
public class MessageDeduplicator {

    private final ConcurrentHashMap<String, Instant> processedEvents = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    /**
     * 检查消息是否已处理
     * 如果已处理且未过期，返回 true
     * 如果未处理或已过期，标记为已处理并返回 false
     */
    public boolean isProcessed(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            log.warn("EventId is null or empty, treating as not processed");
            return false;
        }

        Instant now = Instant.now();

        Instant processedTime = processedEvents.get(eventId);
        if (processedTime != null && processedTime.plus(CACHE_TTL).isAfter(now)) {
            log.debug("Event {} already processed at {}", eventId, processedTime);
            return true;
        }

        processedEvents.put(eventId, now);
        log.debug("Event {} marked as processed at {}", eventId, now);

        cleanupExpiredEntries(now);

        return false;
    }

    /**
     * 清理过期的缓存条目
     */
    private void cleanupExpiredEntries(Instant now) {
        int removedCount = 0;
        for (String key : processedEvents.keySet()) {
            Instant processedTime = processedEvents.get(key);
            if (processedTime == null || processedTime.plus(CACHE_TTL).isBefore(now)) {
                processedEvents.remove(key);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.debug("Cleaned up {} expired event entries", removedCount);
        }
    }

    /**
     * 获取缓存大小（用于监控）
     */
    public int getCacheSize() {
        return processedEvents.size();
    }

    /**
     * 手动清理所有缓存（用于测试或特殊情况）
     */
    public void clearAll() {
        int size = processedEvents.size();
        processedEvents.clear();
        log.info("Cleared all {} cached events", size);
    }
}

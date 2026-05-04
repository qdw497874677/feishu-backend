package com.qdw.feishu.domain.card;

import com.qdw.feishu.domain.config.CardProperties;
import com.qdw.feishu.domain.gateway.CardGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式卡片管理器服务
 * 
 * 封装卡片的创建、更新和资源清理逻辑
 * 自动管理卡片更新的 sequence 递增
 * 
 * 设计原则：
 * - 可配置：通过 CardProperties 控制卡片行为
 * - 可降级：卡片失败时可降级为普通消息
 * - 抽象分层：依赖 CardGateway 接口，便于替换实现
 */
@Slf4j
public class StreamingCardManager {

    private final CardGateway cardGateway;
    private final CardProperties properties;
    private final Map<String, Integer> cardSequences = new ConcurrentHashMap<>();

    public StreamingCardManager(CardGateway cardGateway, CardProperties properties) {
        this.cardGateway = cardGateway;
        this.properties = properties;
    }

    /**
     * 是否启用卡片流式输出
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 创建卡片并发送消息
     *
     * @param message 原始消息（用于获取回复上下文）
     * @param content 初始内容（支持 Markdown）
     * @param topicId 话题 ID（可为 null）
     * @return cardId，失败返回 null
     */
    public String createAndSend(Message message, String content, String topicId) {
        if (!properties.isEnabled()) {
            log.debug("卡片流式输出已禁用");
            return null;
        }
        
        String title = properties.getTitle();
        log.info("创建流式卡片: title={}, topicId={}", title, topicId);
        
        String cardId = cardGateway.createCard(title, content);
        if (cardId == null) {
            log.error("创建卡片失败: title={}", title);
            return null;
        }
        
        cardSequences.put(cardId, 1);
        log.debug("卡片创建成功: cardId={}, sequence=1", cardId);
        
        SendResult result = cardGateway.sendCardMessage(message, cardId, topicId);
        if (!result.isSuccess()) {
            log.error("发送卡片消息失败: cardId={}, error={}", cardId, result.getErrorMessage());
            cardSequences.remove(cardId);
            return null;
        }
        
        log.info("卡片消息发送成功: cardId={}, messageId={}", cardId, result.getMessageId());
        return cardId;
    }

    /**
     * 更新卡片（自动管理 sequence）
     *
     * @param cardId 卡片 ID
     * @param content 新内容（支持 Markdown）
     * @return 是否成功
     */
    public boolean update(String cardId, String content) {
        int seq = cardSequences.getOrDefault(cardId, 0) + 1;
        
        if (seq == 1) {
            log.warn("卡片未初始化，sequence 从 1 开始: cardId={}", cardId);
        }
        
        boolean success = cardGateway.updateCard(cardId, content, seq);
        
        if (success) {
            cardSequences.put(cardId, seq);
            log.debug("卡片更新成功: cardId={}, sequence={}", cardId, seq);
        } else {
            log.error("卡片更新失败: cardId={}, sequence={}", cardId, seq);
        }
        
        return success;
    }

    /**
     * 清理卡片资源
     *
     * @param cardId 卡片 ID
     */
    public void cleanup(String cardId) {
        Integer removedSeq = cardSequences.remove(cardId);
        if (removedSeq != null) {
            log.info("清理卡片资源: cardId={}, lastSequence={}", cardId, removedSeq);
        } else {
            log.debug("卡片资源已清理或不存在: cardId={}", cardId);
        }
    }

    /**
     * 获取卡片当前的 sequence（用于测试和调试）
     *
     * @param cardId 卡片 ID
     * @return 当前 sequence，不存在返回 0
     */
    public int getSequence(String cardId) {
        return cardSequences.getOrDefault(cardId, 0);
    }

    /**
     * 检查卡片是否存在（用于测试和调试）
     *
     * @param cardId 卡片 ID
     * @return 是否存在
     */
    public boolean exists(String cardId) {
        return cardSequences.containsKey(cardId);
    }

    /**
     * 获取配置属性（供外部使用）
     */
    public CardProperties getProperties() {
        return properties;
    }
}

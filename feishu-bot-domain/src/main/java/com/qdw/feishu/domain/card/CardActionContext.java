package com.qdw.feishu.domain.card;

import com.qdw.feishu.domain.model.MessageContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 卡片按钮点击时需要携带的上下文信息。
 *
 * 发卡时嵌入按钮 value，点击时解析还原。
 */
public class CardActionContext {

    private final String chatId;
    private final String topicId;
    private final String sessionId;

    private CardActionContext(String chatId, String topicId, String sessionId) {
        this.chatId = chatId;
        this.topicId = topicId;
        this.sessionId = sessionId;
    }

    /** 从 MessageContext 提取上下文 */
    public static CardActionContext from(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved()) {
            return new CardActionContext(null, null, null);
        }
        String chatId = null;
        String topicId = null;
        if (messageContext.getContextRef() != null) {
            if (messageContext.getContextRef().isThread()) {
                topicId = messageContext.getContextRef().getContextId();
            } else if (messageContext.getContextRef().isChat()) {
                chatId = messageContext.getContextRef().getContextId();
            }
        }
        String sessionId = messageContext.getBoundSessionId().orElse(null);
        return new CardActionContext(chatId, topicId, sessionId);
    }

    /** 从卡片按钮 value map 还原上下文 */
    public static CardActionContext fromValueMap(Map<String, Object> valueMap) {
        if (valueMap == null) {
            return new CardActionContext(null, null, null);
        }
        return new CardActionContext(
            getString(valueMap, "chatId"),
            getString(valueMap, "topicId"),
            getString(valueMap, "sessionId")
        );
    }

    /** 从 value map 中提取纯 action */
    public static String extractAction(Map<String, Object> valueMap) {
        if (valueMap == null) return null;
        Object action = valueMap.get("action");
        return action != null ? action.toString() : null;
    }

    /** 转为嵌入按钮 value 的 map */
    public Map<String, Object> toValueMap(String action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", action);
        if (chatId != null) map.put("chatId", chatId);
        if (topicId != null) map.put("topicId", topicId);
        if (sessionId != null) map.put("sessionId", sessionId);
        return map;
    }

    public String getChatId() {
        return chatId;
    }

    public String getTopicId() {
        return topicId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    public static class Builder {
        private String chatId;
        private String topicId;
        private String sessionId;

        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder topicId(String topicId) {
            this.topicId = topicId;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public CardActionContext build() {
            return new CardActionContext(chatId, topicId, sessionId);
        }
    }
}

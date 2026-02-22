package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.message.ChatHistory;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;

/**
 * 飞书 Gateway 接口
 */
public interface FeishuGateway {

    SendResult sendReply(String receiveOpenId, String content);

    SendResult sendMessage(Message message, String content, String topicId);

    SendResult sendDirectReply(Message message, String content);

    UserInfo getUserInfo(String openId);

    ChatHistory listMessages(String chatId, String threadId, Integer pageSize, String pageToken);

    void updateCard(String token, String content);

    void sendCardReply(String messageId, String content);

    /**
     * 对消息添加表情回应
     *
     * @param messageId 消息 ID
     * @param emojiType 表情类型（如 "THUMBSUP", "HEART", "ROCKET", "HOURGLASS", "CHECK", "CROSS" 等）
     */
    void addReaction(String messageId, String emojiType);
}

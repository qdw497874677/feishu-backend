package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.message.ChatHistory;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.ReactionEmoji;
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
     * @param emoji 表情类型
     * @return true 表示成功，false 表示失败
     */
    boolean addReaction(String messageId, ReactionEmoji emoji);

    /**
     * 发送交互式卡片消息
     *
     * @param message 消息对象
     * @param cardJson 卡片JSON内容
     * @param topicId 话题ID（可选）
     * @return 发送结果
     */
    SendResult sendInteractiveMessage(Message message, String cardJson, String topicId);

    /**
     * 发送交互式卡片作为新话题（不回复任何消息）。
     * 在话题群中，新消息自动成为独立话题。
     *
     * @param chatId 群聊ID
     * @param cardJson 卡片JSON内容
     * @return 发送结果（含 threadId）
     */
    SendResult sendCardAsNewTopic(String chatId, String cardJson);
}

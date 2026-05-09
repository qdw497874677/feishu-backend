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
     * 发送交互式卡片并尽可能创建新话题。
     *
     * <p>parentMessageId 为空时使用 CreateMessage 发送根消息；在话题群中会创建独立话题并返回 threadId。
     * parentMessageId 非空时使用 ReplyMessage(replyInThread=true)，会在父消息下创建/回复话题。
     *
     * @param chatId 群聊ID
     * @param cardJson 卡片JSON内容
     * @param parentMessageId 父消息ID；为空表示发送独立根消息
     * @return 发送结果（尽可能包含 threadId）
     */
    SendResult sendCardAsNewTopic(String chatId, String cardJson, String parentMessageId);
}

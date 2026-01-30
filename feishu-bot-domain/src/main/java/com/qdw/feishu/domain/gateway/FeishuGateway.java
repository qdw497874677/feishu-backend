package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.message.ChatHistory;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;

/**
 * 飞书 Gateway 接口
 *
 * 定义与飞书平台交互的抽象
 * 符合 COLA 的 Gateway 模式，将外部依赖隔离在领域层
 */
public interface FeishuGateway {

    /**
     * 发送消息回复
     *
     * @param receiveOpenId 接收者 Open ID
     * @param content 回复内容
     * @return 发送结果
     */
    SendResult sendReply(String receiveOpenId, String content);

    /**
     * 发送消息（支持话题回复）
     *
     * @param message 消息对象
     * @param content 回复内容
     * @param topicId 话题 ID（可选，如果提供则回复到话题；如果为 null 则创建话题）
     * @return 发送结果
     */
    SendResult sendMessage(Message message, String content, String topicId);

    /**
     * 直接回复消息（不创建话题）
     *
     * @param message 消息对象
     * @param content 回复内容
     * @return 发送结果
     */
    SendResult sendDirectReply(Message message, String content);

    /**
     * 获取用户信息
     *
     * @param openId 用户 Open ID
     * @return 用户信息
     */
    UserInfo getUserInfo(String openId);

    /**
     * 查询历史消息
     *
     * @param chatId 会话 ID
     * @param threadId 话题 ID（可选，如果提供则只查询该话题的消息）
     * @param pageSize 每页消息数量
     * @param pageToken 分页令牌（可选）
     * @return 历史消息列表
     */
    ChatHistory listMessages(String chatId, String threadId, Integer pageSize, String pageToken);
}

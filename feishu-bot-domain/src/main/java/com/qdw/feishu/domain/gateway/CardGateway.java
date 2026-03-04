package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;

/**
 * 飞书卡片网关接口
 *
 * 提供卡片实体的创建、更新和发送能力
 * 用于实现流式响应等需要高频更新内容的场景
 */
public interface CardGateway {

    /**
     * 创建卡片实体（不发送消息）
     *
     * @param title 卡片标题
     * @param content 初始内容（支持 Markdown）
     * @return cardId，失败返回 null
     */
    String createCard(String title, String content);

    /**
     * 更新卡片内容
     *
     * @param cardId 卡片 ID
     * @param content 新内容（支持 Markdown）
     * @param sequence 序号（必须严格递增）
     * @return 是否成功
     */
    boolean updateCard(String cardId, String content, int sequence);

    /**
     * 发送卡片消息
     *
     * @param message 原始消息（用于获取回复上下文）
     * @param cardId 卡片 ID
     * @param topicId 话题 ID（可为 null）
     * @return 发送结果
     */
    SendResult sendCardMessage(Message message, String cardId, String topicId);
}

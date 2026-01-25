package com.qdw.feishu.domain.gateway;

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
     * @param receiveOpenId 接收者 ID
     * @param content 消息内容
     * @param topicId 话题 ID（可选，如果提供则回复到话题）
     * @return 发送结果
     */
    SendResult sendMessage(String receiveOpenId, String content, String topicId);

    /**
     * 获取用户信息
     *
     * @param openId 用户 Open ID
     * @return 用户信息
     */
    UserInfo getUserInfo(String openId);
}

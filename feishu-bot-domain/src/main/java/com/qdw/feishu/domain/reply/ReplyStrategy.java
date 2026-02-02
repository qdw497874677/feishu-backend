package com.qdw.feishu.domain.reply;

import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;

/**
 * 消息回复策略接口
 * 
 * 使用策略模式封装不同的回复行为，符合开放封闭原则：
 * - 新增回复模式只需实现此接口，无需修改现有代码
 * - 各策略之间相互独立，职责单一
 * 
 * @see ReplyMode
 */
public interface ReplyStrategy {

    /**
     * 获取该策略支持的回复模式
     * 
     * @return 回复模式枚举
     */
    ReplyMode getReplyMode();

    /**
     * 执行消息回复
     * 
     * 根据策略定义的规则发送回复消息：
     * - DIRECT: 直接回复，不创建话题
     * - TOPIC: 回复到话题（创建新话题或回复到现有话题）
     * - DEFAULT: 默认行为，通常等同于 TOPIC
     * 
     * @param message 原始消息对象
     * @param replyContent 回复内容
     * @param topicId 话题ID（可选，某些模式下使用）
     * @return 发送结果
     */
    SendResult reply(Message message, String replyContent, String topicId);

    /**
     * 检查该策略是否支持指定的回复模式
     * 
     * @param mode 回复模式
     * @return true 表示支持
     */
    default boolean supports(ReplyMode mode) {
        return this.getReplyMode() == mode;
    }
}

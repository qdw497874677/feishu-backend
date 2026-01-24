package com.qdw.feishu.domain.message;

import com.alibaba.cola.exception.BizException;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息领域实体
 * 
 * 封装飞书消息的领域概念和行为
 */
@Data
@NoArgsConstructor
public class Message {

    /** 消息ID */
    private String messageId;

    /** 消息内容 */
    private String content;

    /** 发送者信息 */
    private Sender sender;

    /** 消息类型 */
    private MessageType type;

    /** 接收时间 */
    private LocalDateTime receiveTime;

    /** 状态 */
    private MessageStatus status;

    public Message(String messageId, String content, Sender sender) {
        this.messageId = messageId;
        this.content = content;
        this.sender = sender;
        this.receiveTime = LocalDateTime.now();
        this.status = MessageStatus.RECEIVED;
        this.validate();
    }

    /**
     * 领域业务逻辑：验证消息
     */
    public void validate() {
        if (content == null || content.trim().isEmpty()) {
            throw new BizException("MESSAGE_CONTENT_EMPTY", "消息内容不能为空");
        }
        if (content.length() > 5000) {
            throw new BizException("MESSAGE_TOO_LONG", "消息内容不能超过5000字");
        }
    }

    /**
     * 领域业务逻辑：标记已处理
     */
    public void markProcessed() {
        this.status = MessageStatus.PROCESSED;
    }

    /**
     * 领域业务逻辑：生成回复内容
     */
    public String generateReply() {
        // 领域逻辑可以根据消息类型生成不同的回复
        // 返回原消息的回显
        return this.content;
    }
}

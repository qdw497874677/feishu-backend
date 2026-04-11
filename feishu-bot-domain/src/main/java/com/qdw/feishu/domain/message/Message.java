package com.qdw.feishu.domain.message;

import com.alibaba.cola.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 消息领域实体
 * 
 * 封装飞书消息的领域概念和行为
 */
@Slf4j
@Data
@NoArgsConstructor
public class Message {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 事件ID（用于去重）*/
    private String eventId;

    /** 消息ID */
    private String messageId;

    /** 消息内容 */
    private String content;

    /** 话题ID，如果消息在话题中 */
    private String topicId;

    /** 话题根消息ID（root_id），用于回复到话题 */
    private String rootId;

    /** 会话ID（群聊或私聊的 ID） */
    private String chatId;

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

    /**
     * 创建一条内容不同的消息副本。
     * 保留原消息的所有元数据（eventId, messageId, chatId, topicId, sender 等），
     * 仅替换 content 字段。
     *
     * <p>用于命令合成场景（如将纯文本合成为 /opencode chat 命令）。
     *
     * @param newContent 新的消息内容（不能为空）
     * @return 内容不同的消息副本
     */
    public Message withContent(String newContent) {
        Message copy = new Message();
        copy.eventId = this.eventId;
        copy.messageId = this.messageId;
        copy.content = newContent;
        copy.topicId = this.topicId;
        copy.rootId = this.rootId;
        copy.chatId = this.chatId;
        copy.sender = this.sender;
        copy.type = this.type;
        copy.receiveTime = this.receiveTime;
        copy.status = this.status;
        return copy;
    }

    /**
     * 获取用于日志显示的消息内容
     * 如果内容是 JSON 格式，自动解析并提取文本
     */
    public String getDisplayContent() {
        if (content == null) {
            return "";
        }

        if (content.trim().startsWith("{")) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(content);
                if (node.has("text")) {
                    return node.get("text").asText();
                }
            } catch (JsonProcessingException e) {
                log.debug("JSON解析失败，返回原内容: {}", e.getMessage());
            }
        }

        return content;
    }
}

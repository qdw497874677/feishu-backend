package com.qdw.feishu.domain.message;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天历史领域模型
 *
 * 封装历史消息查询结果，包括消息列表和分页信息
 */
@Data
@AllArgsConstructor
public class ChatHistory {

    /** 消息列表 */
    private List<HistoryMessage> messages;

    /** 是否还有更多消息 */
    private boolean hasMore;

    /** 下一页令牌（用于分页） */
    private String pageToken;

    /**
     * 历史消息领域模型
     */
    @Data
    @AllArgsConstructor
    public static class HistoryMessage {
        /** 消息 ID */
        private String messageId;

        /** 消息内容 */
        private String content;

        /** 发送者 */
        private String sender;

        /** 发送时间 */
        private LocalDateTime sendTime;

        /** 消息类型 */
        private String messageType;

        /** 话题 ID（如果消息在话题中） */
        private String threadId;
    }
}

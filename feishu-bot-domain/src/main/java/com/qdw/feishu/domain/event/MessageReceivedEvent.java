package com.qdw.feishu.domain.event;

import com.qdw.feishu.domain.message.Sender;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 消息接收事件
 * 
 * 当收到飞书消息时发布此事件，用于异步处理机制
 */
@Data
@AllArgsConstructor
public class MessageReceivedEvent {

    private String messageId;
    private String content;
    private Sender sender;
    private Map<String, String> metadata;
}

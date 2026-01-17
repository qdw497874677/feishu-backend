package com.qdw.feishu.domain.event;

import com.qdw.feishu.domain.message.Sender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * 消息接收事件
 * 
 * 当收到飞书消息时发布此事件，用于异步处理机制
 */
@Value
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class MessageReceivedEvent {

    @NonNull
    private String messageId;
    @NonNull
    private String content;
    @NonNull
    private Sender sender;
    private Map<String, String> metadata;
    @Builder.Default
    private Instant timestamp = Instant.now();
}

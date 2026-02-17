package com.qdw.feishu.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedCommand {
    private String appId;
    private String subCommand;
    private String[] args;
    private String openId;
    private String topicId;
    private String messageId;
    private String cardToken;
    private EventSource source;
    
    public boolean isFromCard() {
        return source == EventSource.CARD;
    }
    
    public boolean isFromMessage() {
        return source == EventSource.MESSAGE;
    }
    
    public boolean hasTopic() {
        return topicId != null && !topicId.isEmpty();
    }
}

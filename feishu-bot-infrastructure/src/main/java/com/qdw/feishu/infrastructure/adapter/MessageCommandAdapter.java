package com.qdw.feishu.infrastructure.adapter;

import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageCommandAdapter implements CommandAdapter {
    
    @Override
    public UnifiedCommand adapt(Object event) {
        P2MessageReceiveV1 msgEvent = (P2MessageReceiveV1) event;
        P2MessageReceiveV1Data data = msgEvent.getEvent();
        
        String content = data.getMessage().getContent();
        String topicId = extractTopicId(msgEvent);
        
        String[] parts = parseCommand(content);
        String appId = parts.length > 0 ? parts[0] : "help";
        String subCommand = parts.length > 1 ? parts[1] : null;
        String[] args = parts.length > 2 ? extractArgs(parts) : new String[0];
        
        UnifiedCommand command = UnifiedCommand.builder()
            .appId(appId)
            .subCommand(subCommand)
            .args(args)
            .openId(data.getSender().getSenderId().getOpenId())
            .topicId(topicId)
            .messageId(data.getMessage().getMessageId())
            .source(EventSource.MESSAGE)
            .build();
        
        log.debug("Adapted message to command: appId={}, subCommand={}", appId, subCommand);
        return command;
    }
    
    @Override
    public boolean supports(Object event) {
        return event instanceof P2MessageReceiveV1;
    }
    
    private String extractTopicId(P2MessageReceiveV1 event) {
        try {
            return event.getEvent().getMessage().getThreadId();
        } catch (Exception e) {
            return null;
        }
    }
    
    private String[] parseCommand(String content) {
        if (content == null || content.isEmpty()) {
            return new String[0];
        }
        content = content.trim();
        if (!content.startsWith("/")) {
            return new String[]{"help"};
        }
        content = content.substring(1);
        return content.split("\\s+");
    }
    
    private String[] extractArgs(String[] parts) {
        String[] args = new String[parts.length - 2];
        System.arraycopy(parts, 2, args, 0, args.length);
        return args;
    }
}

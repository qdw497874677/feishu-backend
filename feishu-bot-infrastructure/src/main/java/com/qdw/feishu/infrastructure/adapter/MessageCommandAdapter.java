package com.qdw.feishu.infrastructure.adapter;

import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.model.ImContextRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class MessageCommandAdapter implements CommandAdapter {
    
    private final ImContextBindingGateway bindingGateway;
    
    public MessageCommandAdapter(ImContextBindingGateway bindingGateway) {
        this.bindingGateway = bindingGateway;
    }
    
    @Override
    public UnifiedCommand adapt(Object event) {
        P2MessageReceiveV1 msgEvent = (P2MessageReceiveV1) event;
        P2MessageReceiveV1Data data = msgEvent.getEvent();
        
        String content = data.getMessage().getContent();
        String topicId = extractTopicId(msgEvent);
        String chatId = data.getMessage().getChatId();
        
        String[] parts = parseCommand(content);
        String appId;
        String subCommand;
        String[] args;
        
        if (content.trim().startsWith("/")) {
            // 有命令前缀，直接解析
            appId = parts.length > 0 ? parts[0] : "help";
            subCommand = parts.length > 1 ? parts[1] : null;
            args = parts.length > 2 ? extractArgs(parts) : new String[0];
        } else if (topicId != null && !topicId.isEmpty()) {
            // 话题中的消息，使用 ImContextBinding 查找绑定的应用
            ImContextRef contextRef = ImContextRef.feishuThread(topicId);
            Optional<?> binding = bindingGateway.findBinding(contextRef);
            if (binding.isPresent()) {
                appId = ((com.qdw.feishu.domain.model.ImContextBinding) binding.get()).getAppId();
                subCommand = parts.length > 0 ? parts[0] : null;
                args = parts.length > 1 ? extractArgsFromContent(parts) : new String[0];
                log.debug("话题消息映射到应用: topicId={}, appId={}", topicId, appId);
            } else {
                // 没有绑定，使用 help
                appId = "help";
                subCommand = null;
                args = new String[0];
            }
        } else {
            // 非话题且无命令前缀
            appId = "help";
            subCommand = null;
            args = new String[0];
        }
        
        UnifiedCommand command = UnifiedCommand.builder()
            .appId(appId)
            .subCommand(subCommand)
            .args(args)
            .openId(data.getSender().getSenderId().getOpenId())
            .topicId(topicId)
            .messageId(data.getMessage().getMessageId())
            .source(EventSource.MESSAGE)
            .build();
        
        log.debug("Adapted message to command: appId={}, subCommand={}, chatId={}", appId, subCommand, chatId);
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
            return content.split("\\s+");
        }
        content = content.substring(1);
        return content.split("\\s+");
    }
    
    private String[] extractArgs(String[] parts) {
        if (parts.length <= 2) {
            return new String[0];
        }
        String[] args = new String[parts.length - 2];
        System.arraycopy(parts, 2, args, 0, args.length);
        return args;
    }
    
    private String[] extractArgsFromContent(String[] parts) {
        if (parts.length <= 1) {
            return new String[0];
        }
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        return args;
    }
}

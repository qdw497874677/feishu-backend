package com.qdw.feishu.infrastructure.adapter;

import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CardResponseAdapter implements ResponseAdapter {
    private final FeishuGateway feishuGateway;
    
    public CardResponseAdapter(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }
    
    @Override
    public void respond(UnifiedCommand command, BizResult result) {
        String content = formatContent(result);
        
        if (command.getCardToken() != null && !command.getCardToken().isEmpty()) {
            updateCard(command.getCardToken(), content);
        } else {
            sendMessage(command, content);
        }
    }
    
    @Override
    public boolean supports(EventSource source, UnifiedCommand command) {
        return source == EventSource.CARD;
    }
    
    private String formatContent(BizResult result) {
        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            return result.getMessage();
        }
        if (result.getData() != null) {
            return result.getData().toString();
        }
        return result.isSuccess() ? "操作成功" : "操作失败";
    }
    
    private void updateCard(String token, String content) {
        log.info("Updating card with token: {}", token);
        feishuGateway.updateCard(token, content);
    }
    
    private void sendMessage(UnifiedCommand command, String content) {
        log.info("Sending message for card event: messageId={}", command.getMessageId());
        feishuGateway.sendCardReply(command.getMessageId(), content);
    }
}

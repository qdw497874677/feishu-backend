package com.qdw.feishu.infrastructure.handler;

import com.lark.oapi.Client;
import com.lark.oapi.event.cardcallback.P2CardActionTriggerHandler;
import com.lark.oapi.event.cardcallback.model.P2CardActionTrigger;
import com.lark.oapi.event.cardcallback.model.P2CardActionTriggerResponse;
import com.lark.oapi.service.im.v1.model.GetMessageReq;
import com.lark.oapi.service.im.v1.model.GetMessageResp;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.BotMessageService;
import com.qdw.feishu.infrastructure.config.FeishuProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 卡片按钮点击事件处理器
 * 
 * 处理飞书卡片的 button 点击回调事件，通过 BotMessageService 完整流程执行
 */
@Slf4j
@Component
public class CardActionTriggerHandler extends P2CardActionTriggerHandler {

    private final BotMessageService botMessageService;
    private final Client httpClient;

    public CardActionTriggerHandler(BotMessageService botMessageService, FeishuProperties properties) {
        this.botMessageService = botMessageService;
        this.httpClient = Client.newBuilder(properties.getAppId(), properties.getAppSecret())
            .appId(properties.getAppId())
            .build();
        log.info("CardActionTriggerHandler initialized with BotMessageService");
    }

    @Override
    public P2CardActionTriggerResponse handle(P2CardActionTrigger event) throws Exception {
        try {
            log.info("=== 收到卡片按钮点击事件 ===");
            log.info("Event ID: {}", event.getRequestId());
            
            // 提取关键信息
            String openId = event.getEvent().getOperator().getOpenId();
            String openMessageId = event.getEvent().getContext().getOpenMessageId();
            String openChatId = event.getEvent().getContext().getOpenChatId();
            String token = event.getEvent().getToken();
            
            // 获取消息的 threadId
            String threadId = getMessageThreadId(openMessageId);
            log.info("Thread ID from message: {}", threadId);
            
            log.info("Card action: openId={}, messageId={}, chatId={}, threadId={}, token={}", 
                openId, openMessageId, openChatId, threadId, token);
            
            // 获取 action value
            String actionValue = null;
            var callBackAction = event.getEvent().getAction();
            if (callBackAction != null) {
                log.info("Action type: {}", callBackAction.getClass());
                
                Map<String, Object> valueMap = callBackAction.getValue();
                log.info("Action value map: {}", valueMap);
                
                if (valueMap != null) {
                    Object actionObj = valueMap.get("action");
                    actionValue = actionObj != null ? String.valueOf(actionObj) : null;
                    log.info("Extracted action value: {}", actionValue);
                }
            }
            
            if (actionValue == null || actionValue.isEmpty()) {
                log.warn("Card action value is empty");
                return new P2CardActionTriggerResponse();
            }
            
            log.info("✅ Card button clicked: value={}", actionValue);
            
            // 创建消息对象
            Message message = new Message();
            message.setChatId(openChatId);
            message.setMessageId(openMessageId);
            message.setSender(new Sender(openId, "User"));
            message.setContent("/" + actionValue);
            message.setTopicId(threadId);  // 设置话题ID，用于话题命令验证
            
            log.info("Message created: content={}, topicId={}", message.getContent(), message.getTopicId());
            
            // 使用 BotMessageService 完整流程（包括创建话题、保存映射）
            var result = botMessageService.handleMessage(message);
            
            if (result.isSuccess()) {
                log.info("Card action handled successfully: messageId={}, threadId={}", 
                    result.getMessageId(), result.getThreadId());
            } else {
                log.error("Card action failed: {}", result.getErrorMessage());
            }
            
            return new P2CardActionTriggerResponse();
            
        } catch (Exception e) {
            log.error("Failed to handle card.action.trigger event", e);
            return new P2CardActionTriggerResponse();
        }
    }
    
    /**
     * 获取消息的 threadId
     */
    private String getMessageThreadId(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return null;
        }
        
        try {
            GetMessageReq req = GetMessageReq.newBuilder()
                .messageId(messageId)
                .build();
            
            GetMessageResp resp = httpClient.im().message().get(req);
            
            if (resp.getCode() == 0 && resp.getData() != null) {
                return resp.getData().getThreadId();
            } else {
                log.warn("Failed to get message threadId: code={}, msg={}", resp.getCode(), resp.getMsg());
                return null;
            }
        } catch (Exception e) {
            log.error("Exception getting message threadId", e);
            return null;
        }
    }
}

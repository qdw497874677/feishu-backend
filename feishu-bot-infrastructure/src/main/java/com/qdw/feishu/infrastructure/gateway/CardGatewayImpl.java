package com.qdw.feishu.infrastructure.gateway;

import com.alibaba.cola.exception.SysException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.service.cardkit.v1.model.*;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import com.qdw.feishu.domain.config.FeishuConfig;
import com.qdw.feishu.domain.gateway.CardGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CardGatewayImpl implements CardGateway {

    private final Client httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Integer> cardSequenceMap = new ConcurrentHashMap<>();

    public CardGatewayImpl(FeishuConfig config) {
        this.httpClient = Client.newBuilder(config.getAppId(), config.getAppSecret())
            .openBaseUrl(BaseUrlEnum.FeiShu)
            .build();
        this.objectMapper = new ObjectMapper();
        log.info("CardGateway initialized with appId: {}", config.getAppId());
    }

    protected CardGatewayImpl(Client httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        log.info("CardGateway initialized with injected client");
    }

    @Override
    public String createCard(String title, String content) {
        try {
            String cardJson = buildCardJson(title, content);
            
            CreateCardReqBody reqBody = new CreateCardReqBody();
            reqBody.setType("card_json");
            reqBody.setData(cardJson);
            
            CreateCardReq req = new CreateCardReq();
            req.setCreateCardReqBody(reqBody);
            
            CreateCardResp resp = httpClient.cardkit().v1().card().create(req);
            
            if (resp.success() && resp.getData() != null) {
                String cardId = resp.getData().getCardId();
                log.info("卡片创建成功: cardId={}", cardId);
                cardSequenceMap.put(cardId, 0);
                return cardId;
            }
            
            log.warn("卡片创建失败: code={}, msg={}", resp.getCode(), resp.getMsg());
            return null;
            
        } catch (Exception e) {
            log.error("创建卡片异常", e);
            return null;
        }
    }

    @Override
    public boolean updateCard(String cardId, String content, int sequence) {
        try {
            String cardJson = buildCardJson(null, content);
            
            Card card = new Card();
            card.setType("card_json");
            card.setData(cardJson);
            
            UpdateCardReqBody reqBody = new UpdateCardReqBody();
            reqBody.setCard(card);
            reqBody.setSequence(sequence);
            
            UpdateCardReq req = new UpdateCardReq();
            req.setCardId(cardId);
            req.setUpdateCardReqBody(reqBody);
            
            UpdateCardResp resp = httpClient.cardkit().v1().card().update(req);
            
            if (resp.success()) {
                log.debug("卡片更新成功: cardId={}, seq={}", cardId, sequence);
                cardSequenceMap.put(cardId, sequence);
                return true;
            }
            
            log.warn("卡片更新失败: cardId={}, code={}, msg={}", cardId, resp.getCode(), resp.getMsg());
            return false;
            
        } catch (Exception e) {
            log.error("更新卡片异常: cardId={}", cardId, e);
            return false;
        }
    }

    @Override
    public SendResult sendCardMessage(Message message, String cardId, String topicId) {
        log.info("发送卡片消息: chatId={}, cardId={}, topicId={}", message.getChatId(), cardId, topicId);

        try {
            if (topicId != null && !topicId.isEmpty()) {
                log.info("回复到现有话题: topicId={}", topicId);
                String rootId = message.getRootId();
                if (rootId != null && !rootId.isEmpty()) {
                    log.info("使用 rootId 回复话题: rootId={}", rootId);
                    return sendCardReplyToMessage(rootId, cardId);
                } else {
                    log.warn("消息中没有 rootId，无法正确回复话题");
                    return sendCardReplyToChat(message.getChatId(), cardId);
                }
            } else {
                log.info("回复原始消息创建新话题: messageId={}", message.getMessageId());
                return sendCardReplyToMessage(message.getMessageId(), cardId);
            }
        } catch (Exception e) {
            log.error("发送卡片消息异常", e);
            throw new SysException("SEND_CARD_ERROR", "Failed to send card message", e);
        }
    }

    private SendResult sendCardReplyToMessage(String messageId, String cardId) throws Exception {
        log.info("回复消息: messageId={}, cardId={}", messageId, cardId);

        try {
            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("type", "card");
            cardContent.put("card_id", cardId);
            String jsonContent = objectMapper.writeValueAsString(cardContent);

            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                .messageId(messageId)
                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                    .content(jsonContent)
                    .msgType("interactive")
                    .replyInThread(true)
                    .build())
                .build();

            ReplyMessageResp resp = httpClient.im().message().reply(req);

            if (resp.getCode() != 0) {
                log.error("回复卡片消息失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                throw new SysException("SEND_FAILED", resp.getMsg());
            }

            String returnedMessageId = resp.getData().getMessageId();
            String returnedThreadId = resp.getData().getThreadId();
            log.info("卡片消息发送成功: messageId={}, threadId={}", returnedMessageId, returnedThreadId);
            return SendResult.success(returnedMessageId, returnedThreadId);

        } catch (Exception e) {
            log.error("回复卡片消息异常", e);
            throw new SysException("SEND_ERROR", "Failed to send card reply", e);
        }
    }

    private SendResult sendCardReplyToChat(String chatId, String cardId) throws Exception {
        log.info("发送卡片消息到群聊: chatId={}, cardId={}", chatId, cardId);

        try {
            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("type", "card");
            cardContent.put("card_id", cardId);
            String jsonContent = objectMapper.writeValueAsString(cardContent);

            CreateMessageReq req = CreateMessageReq.newBuilder()
                .receiveIdType("chat_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                    .receiveId(chatId)
                    .msgType("interactive")
                    .content(jsonContent)
                    .build())
                .build();

            CreateMessageResp resp = httpClient.im().message().create(req);

            if (resp.getCode() != 0) {
                log.error("发送卡片消息到群聊失败: code={}, msg={}", resp.getCode(), resp.getMsg());
                throw new SysException("SEND_FAILED", resp.getMsg());
            }

            String messageId = resp.getData().getMessageId();
            String threadId = resp.getData().getThreadId();
            log.info("卡片消息发送成功: messageId={}, threadId={}", messageId, threadId);
            return SendResult.success(messageId, threadId);

        } catch (Exception e) {
            log.error("发送卡片消息到群聊异常", e);
            throw new SysException("SEND_ERROR", "Failed to send card message to chat", e);
        }
    }

    private String buildCardJson(String title, String content) throws Exception {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", Map.of("update_multi", true));
        
        if (title != null) {
            card.put("header", Map.of(
                "title", Map.of("tag", "plain_text", "content", title)
            ));
        }
        
        card.put("elements", List.of(
            Map.of("tag", "markdown", "content", content)
        ));
        
        return objectMapper.writeValueAsString(card);
    }
}

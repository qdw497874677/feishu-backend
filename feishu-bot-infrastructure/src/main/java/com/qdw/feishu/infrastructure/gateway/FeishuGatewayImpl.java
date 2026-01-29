package com.qdw.feishu.infrastructure.gateway;

import com.alibaba.cola.exception.SysException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.core.enums.BaseUrlEnum;
import com.lark.oapi.core.response.RawResponse;
import com.lark.oapi.core.token.AccessTokenType;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.ListMessageReq;
import com.lark.oapi.service.im.v1.model.ListMessageResp;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.UserInfo;
import com.qdw.feishu.domain.message.ChatHistory;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.net.UnknownHostException;

@Slf4j
@Component
public class FeishuGatewayImpl implements FeishuGateway {

    private final com.lark.oapi.Client httpClient;
    private final ObjectMapper objectMapper;

    // DNS 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second
    private static final long MAX_RETRY_DELAY_MS = 8000; // 8 seconds

    public FeishuGatewayImpl(@Value("${feishu.appid}") String appId,
                            @Value("${feishu.appsecret}") String appSecret) {
        this.httpClient = com.lark.oapi.Client.newBuilder(appId, appSecret)
            .openBaseUrl(BaseUrlEnum.FeiShu)
            .build();
        this.objectMapper = new ObjectMapper();
        log.info("Feishu SDK Client initialized with appId: {}", appId);
    }

    /**
     * 使用指数退避策略执行带重试的操作
     * 仅对 UnknownHostException 进行重试
     */
    private <T> T executeWithRetry(String operationName, Supplier<T> operation) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (SysException e) {
                if (attempt == MAX_RETRIES - 1) {
                    throw e;
                }

                if (e.getCause() instanceof UnknownHostException) {
                    long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);
                    log.warn("DNS resolution failed for {} (attempt {}/{}), retrying in {}ms...",
                             operationName, attempt + 1, MAX_RETRIES, delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SysException("RETRY_INTERRUPTED", "Retry interrupted", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new SysException("RETRY_FAILED", "All retry attempts failed for: " + operationName);
    }

    @Override
    public SendResult sendReply(String receiveOpenId, String content) {
        log.info("Sending reply to: {}, content: {}", receiveOpenId, content);

        return executeWithRetry("sendReply", () -> {
            try {
                Map<String, String> textContent = new HashMap<>();
                textContent.put("text", content);
                String jsonContent = objectMapper.writeValueAsString(textContent);

                CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id")
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(receiveOpenId)
                        .msgType("text")
                        .content(jsonContent)
                        .build())
                    .build();

                CreateMessageResp resp = httpClient.im().message().create(req);

                if (resp.getCode() != 0) {
                    log.error("Failed to send message: code={}, msg={}", resp.getCode(), resp.getMsg());
                    throw new SysException("SEND_FAILED", resp.getMsg());
                }

                return SendResult.success(resp.getData().getMessageId());

            } catch (Exception e) {
                log.error("Exception sending reply", e);
                throw new SysException("SEND_ERROR", "send message failed", e);
            }
        });
    }

    @Override
    public SendResult sendMessage(Message message, String content, String topicId) {
        log.info("Sending message to chatId: {}, content: {}, topicId: {}", message.getChatId(), content, topicId);

        return executeWithRetry("sendMessage", () -> {
            try {
                if (topicId != null && !topicId.isEmpty()) {
                    log.info("Replying to existing thread: topicId={}", topicId);
                    return sendReplyToTopic(topicId, content);
                } else {
                    log.info("Sending message (no topic): messageId={}", message.getMessageId());
                    return sendMessageToChat(message.getChatId(), content);
                }
            } catch (Exception e) {
                log.error("Exception sending message", e);
                throw new SysException("SEND_ERROR", "send message failed", e);
            }
        });
    }

    @Override
    public SendResult sendDirectReply(Message message, String content) {
        log.info("Sending direct reply to chatId: {}, content: {}", message.getChatId(), content);

        return executeWithRetry("sendDirectReply", () -> {
            try {
                Map<String, String> textContent = new HashMap<>();
                textContent.put("text", content);
                String jsonContent = objectMapper.writeValueAsString(textContent);

                CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id")
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(message.getSender().getOpenId())
                        .msgType("text")
                        .content(jsonContent)
                        .build())
                    .build();

                CreateMessageResp resp = httpClient.im().message().create(req);

                if (resp.getCode() != 0) {
                    log.error("Failed to send direct reply: code={}, msg={}", resp.getCode(), resp.getMsg());
                    throw new SysException("SEND_FAILED", resp.getMsg());
                }

                return SendResult.success(resp.getData().getMessageId());

            } catch (Exception e) {
                log.error("Exception sending direct reply", e);
                throw new SysException("SEND_ERROR", "send message failed", e);
            }
        });
    }

    /**
     * 创建新话题（发送消息到会话）
     */
    private SendResult sendMessageToChat(String chatId, String content) throws Exception {
        Map<String, String> textContent = new HashMap<>();
        textContent.put("text", content);
        String jsonContent = objectMapper.writeValueAsString(textContent);

        log.info("Sending message to chat: chatId={}, content={}", chatId, content);

        return executeWithRetry("sendMessageToChat", () -> {
            try {
                CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("chat_id")
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(chatId)
                        .msgType("text")
                        .content(jsonContent)
                        .build())
                    .build();

                CreateMessageResp resp = httpClient.im().message().create(req);

                if (resp.getCode() != 0) {
                    log.error("Failed to send message: code={}, msg={}", resp.getCode(), resp.getMsg());
                    throw new SysException("SEND_FAILED", resp.getMsg());
                }

                return SendResult.success(resp.getData().getMessageId());
            } catch (Exception e) {
                log.error("Exception sending message", e);
                throw new SysException("SEND_ERROR", "send message failed", e);
            }
        });
    }

    private SendResult sendReplyToTopic(String threadId, String content) throws Exception {
        log.info("Replying to thread: threadId={}, content={}", threadId, content);

        ChatHistory history = listMessages(null, threadId, 1, null);

        if (history.getMessages() == null || history.getMessages().isEmpty()) {
            log.error("Failed to find thread messages for threadId: {}", threadId);
            throw new SysException("SEND_FAILED", "Thread not found");
        }

        ChatHistory.HistoryMessage rootMessage = history.getMessages().get(0);
        String rootMessageId = rootMessage.getMessageId();

        log.info("Found thread root message: messageId={}", rootMessageId);

        return sendReplyToMessage(rootMessageId, content);
    }

    private SendResult sendReplyToMessage(String messageId, String content) throws Exception {
        Map<String, String> textContent = new HashMap<>();
        textContent.put("text", content);
        String jsonContent = objectMapper.writeValueAsString(textContent);

        log.info("Replying to message: {} with content: {}", messageId, content);

        return executeWithRetry("sendReplyToMessage", () -> {
            try {
                ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(messageId)
                    .replyMessageReqBody(com.lark.oapi.service.im.v1.model.ReplyMessageReqBody.newBuilder()
                        .content(jsonContent)
                        .msgType("text")
                        .replyInThread(true)
                        .build())
                    .build();

                ReplyMessageResp resp = httpClient.im().message().reply(req);

                if (resp.getCode() != 0) {
                    log.error("Failed to reply to message: code={}, msg={}", resp.getCode(), resp.getMsg());
                    throw new SysException("SEND_FAILED", resp.getMsg());
                }

                String returnedMessageId = resp.getData().getMessageId();
                String returnedThreadId = resp.getData().getThreadId();
                log.info("Reply success: messageId={}, threadId={}", returnedMessageId, returnedThreadId);
                return SendResult.success(returnedMessageId, returnedThreadId);
            } catch (Exception e) {
                log.error("Exception replying to message", e);
                throw new SysException("SEND_ERROR", "send reply failed", e);
            }
        });
    }

    @Override
    public ChatHistory listMessages(String chatId, String threadId, Integer pageSize, String pageToken) {
        log.info("Querying chat history: chatId={}, threadId={}, pageSize={}, pageToken={}", chatId, threadId, pageSize, pageToken);

        try {
            String containerId = chatId;
            String containerIdType = "chat_id";

            if (threadId != null && !threadId.isEmpty()) {
                containerId = threadId;
                containerIdType = "thread_id";
            }

            ListMessageReq req = ListMessageReq.newBuilder()
                .containerIdType(containerIdType)
                .containerId(containerId)
                .pageSize(pageSize != null ? pageSize : 20)
                .pageToken(pageToken)
                .build();

            ListMessageResp resp = httpClient.im().message().list(req);

            if (resp.getCode() != 0) {
                log.error("Failed to list messages: {}", resp.getMsg());
                throw new SysException("LIST_FAILED", resp.getMsg());
            }

            com.lark.oapi.service.im.v1.model.Message[] messages = resp.getData().getItems();
            java.util.List<ChatHistory.HistoryMessage> historyMessages = new java.util.ArrayList<>();

            for (com.lark.oapi.service.im.v1.model.Message msg : messages) {
                String content = "unknown";
                String sender = "unknown";
                java.time.LocalDateTime sendTime = java.time.LocalDateTime.now();

                try {
                    if (msg.getBody() != null) {
                        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(objectMapper.writeValueAsString(msg.getBody()));
                        content = node.has("content") ? node.get("content").asText() : "unknown";
                    }
                } catch (Exception e) {
                }

                try {
                    if (msg.getSender() != null) {
                        sender = msg.getSender() != null ? msg.getSender().toString() : "unknown";
                    }
                } catch (Exception e) {
                }

                try {
                    if (msg.getCreateTime() != null) {
                        sendTime = java.time.LocalDateTime.parse(msg.getCreateTime().replace("T", " ").substring(0, 19));
                    }
                } catch (Exception e) {
                }

                ChatHistory.HistoryMessage historyMsg = new com.qdw.feishu.domain.message.ChatHistory.HistoryMessage(
                    msg.getMessageId(),
                    content,
                    sender,
                    sendTime,
                    msg.getMsgType(),
                    msg.getThreadId()
                );
                historyMessages.add(historyMsg);
            }

            return new ChatHistory(
                historyMessages,
                resp.getData().getHasMore() != null ? resp.getData().getHasMore() : false,
                resp.getData().getPageToken()
            );

        } catch (Exception e) {
            log.error("Exception listing messages", e);
            throw new SysException("LIST_ERROR", "Failed to list messages", e);
        }
    }

    private String extractTextContent(String jsonContent) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(jsonContent);
            return node.has("text") ? node.get("text").asText() : jsonContent;
        } catch (Exception e) {
            return jsonContent;
        }
    }

    @Override
    public UserInfo getUserInfo(String openId) {
        log.info("Getting user info for: {}", openId);
        return new UserInfo(openId, "unknown", "Unknown User");
    }
}

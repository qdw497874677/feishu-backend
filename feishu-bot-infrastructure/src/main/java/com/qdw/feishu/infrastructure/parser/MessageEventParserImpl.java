package com.qdw.feishu.infrastructure.parser;

import com.google.gson.Gson;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.qdw.feishu.domain.gateway.MessageEventParser;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 飞书消息事件解析器实现（防腐层实现）
 * 
 * 职责：
 * - 将飞书 SDK 的 P2MessageReceiveV1 事件转换为领域 Message 对象
 * - 封装 SDK 特定的解析逻辑
 * - 统一处理 content JSON 格式提取
 * 
 * 特点：
 * - 使用正则表达式提取 thread_id 和 root_id（飞书 SDK 未直接提供这些字段）
 * - 自动解析 JSON 格式的文本内容
 */
@Slf4j
@Component
public class MessageEventParserImpl implements MessageEventParser {

    private static final String MESSAGE_RECEIVE_V1_CLASS_NAME = "P2MessageReceiveV1";
    private final Gson gson = new Gson();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Message parse(T rawEvent) {
        if (rawEvent instanceof P2MessageReceiveV1) {
            return parseMessageReceiveEvent((P2MessageReceiveV1) rawEvent);
        }
        throw new IllegalArgumentException("Unsupported event type: " + rawEvent.getClass().getSimpleName());
    }

    @Override
    public boolean supports(Class<?> eventClass) {
        return P2MessageReceiveV1.class.getSimpleName().equals(eventClass.getSimpleName())
            || eventClass.getName().contains("P2MessageReceiveV1");
    }

    /**
     * 解析 P2MessageReceiveV1 事件
     */
    private Message parseMessageReceiveEvent(P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data data = event.getEvent();

        // 提取消息内容
        String content = extractContent(data.getMessage().getContent());

        // 创建发送者
        Sender sender = new Sender(
            data.getSender().getSenderId().getOpenId(),
            data.getSender().getSenderType()
        );

        // 创建消息对象
        Message message = new Message(
            data.getMessage().getMessageId(),
            content,
            sender
        );

        // 提取 eventId（用于去重）
        extractEventId(event, message);

        // 提取话题信息
        extractTopicInfo(event, message);

        // 设置 chatId
        message.setChatId(data.getMessage().getChatId());

        return message;
    }

    /**
     * 提取消息文本内容
     * 飞书返回的 content 是 JSON 格式，需要提取 text 字段
     */
    private String extractContent(String content) {
        if (content == null) {
            return "";
        }

        if (content.startsWith("{")) {
            try {
                var json = gson.fromJson(content, com.google.gson.JsonObject.class);
                if (json.has("text")) {
                    return json.get("text").getAsString();
                }
            } catch (Exception e) {
                log.warn("Failed to parse message content JSON, using original: {}", content, e);
            }
        }

        return content;
    }

    /**
     * 提取 eventId
     */
    private void extractEventId(P2MessageReceiveV1 event, Message message) {
        try {
            String eventId = event.getHeader().getEventId();
            message.setEventId(eventId);
            log.trace("Extracted eventId: {}", eventId);
        } catch (Exception e) {
            log.warn("Failed to extract eventId", e);
        }
    }

    /**
     * 从飞书事件中提取话题信息
     * 使用正则表达式解析 JSON，因为飞书 SDK 2.5.2 未直接提供这些字段
     */
    private void extractTopicInfo(P2MessageReceiveV1 event, Message message) {
        try {
            String eventJson = gson.toJson(event);

            // 提取 thread_id
            java.util.regex.Pattern threadIdPattern = 
                java.util.regex.Pattern.compile("\"thread_id\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher threadMatcher = threadIdPattern.matcher(eventJson);

            if (threadMatcher.find()) {
                String threadId = threadMatcher.group(1);
                message.setTopicId(threadId);
                log.debug("Extracted threadId: {}", threadId);
            }

            // 提取 root_id
            java.util.regex.Pattern rootIdPattern = 
                java.util.regex.Pattern.compile("\"root_id\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher rootMatcher = rootIdPattern.matcher(eventJson);

            if (rootMatcher.find()) {
                String rootId = rootMatcher.group(1);
                message.setRootId(rootId);
                log.debug("Extracted rootId: {}", rootId);
            }

            if (message.getTopicId() == null && message.getRootId() == null) {
                log.trace("No thread_id or root_id found, treating as normal message");
            }

        } catch (Exception e) {
            log.warn("Failed to extract topic/root info", e);
        }
    }
}

package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeMessageFormatter;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.opencode.OpenCodeTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Optional;

/**
 * 处理 chat / chatnow / cn 子命令。
 *
 * <ul>
 *   <li>{@code chat <prompt>} — 在当前会话中发送对话</li>
 *   <li>{@code chatnow / cn} — 创建新会话并绑定到话题</li>
 * </ul>
 */
@Slf4j
public class ChatHandler implements SubCommandHandler {

    private final OpenCodeSessionManager sessionManager;
    private final OpenCodeTaskExecutor taskExecutor;
    private final OpenCodeMessageFormatter messageFormatter;

    public ChatHandler(OpenCodeSessionManager sessionManager,
                       OpenCodeTaskExecutor taskExecutor,
                       OpenCodeMessageFormatter messageFormatter) {
        this.sessionManager = sessionManager;
        this.taskExecutor = taskExecutor;
        this.messageFormatter = messageFormatter;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        String topicId = message.getTopicId();
        boolean inTopic = topicId != null && !topicId.isEmpty();
        String subCommand = parts.length > 1 ? parts[1].toLowerCase() : "chat";

        boolean isChatNow = "chatnow".equals(subCommand) || "cn".equals(subCommand);

        if (isChatNow) {
            return handleChatNow(message, messageContext);
        }

        if (parts.length < 3) {
            if (inTopic) {
                return AppExecutionResult.text(
                    sessionManager.getSessionId(messageContext)
                        .map(sessionId -> messageFormatter.buildChatStatusWithSession(topicId, sessionId))
                        .orElse(messageFormatter.buildChatQuickStart())
                );
            }
            return AppExecutionResult.text(messageFormatter.buildChatQuickStart());
        }

        String prompt = extractChatContent(parts, message);

        if (inTopic && !sessionManager.isTopicInitialized(messageContext)) {
            log.info("话题未初始化，自动创建新会话");
            return taskExecutor.executeWithNewSession(message, prompt, null);
        }

        return taskExecutor.executeWithAutoSession(message, prompt);
    }

    private AppExecutionResult handleChatNow(Message message, MessageContext messageContext) {
        String topicId = message.getTopicId();
        boolean inTopic = topicId != null && !topicId.isEmpty();

        if (inTopic && sessionManager.isTopicInitialized(messageContext)) {
            Optional<String> currentSessionId = sessionManager.getSessionId(messageContext);
            if (currentSessionId.isPresent()) {
                return AppExecutionResult.text(
                    messageFormatter.buildSessionInitializedInfo(topicId, currentSessionId.get()));
            }
        }

        log.info("cn 命令：创建新会话并绑定到话题");
        sessionManager.clearSession(message);

        try {
            String result = taskExecutor.createSessionOnly(message);
            Optional<String> newSessionId = sessionManager.getSessionId(message);
            if (newSessionId.isPresent()) {
                return AppExecutionResult.withSession(
                    messageFormatter.buildSessionInitializedInfo(message.getTopicId(), newSessionId.get()),
                    newSessionId.get(),
                    true
                );
            }
            return AppExecutionResult.text(result);
        } catch (Exception e) {
            log.error("创建会话失败", e);
            return AppExecutionResult.text("❌ 创建会话失败: " + e.getMessage());
        }
    }

    private String extractChatContent(String[] parts, Message message) {
        if (parts.length >= 3) {
            return String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        }

        String content = message.getContent().trim();
        int firstSpace = content.indexOf(' ');
        if (firstSpace < 0) {
            return "";
        }

        String remaining = content.substring(firstSpace + 1).trim();
        if (remaining.toLowerCase().startsWith("chat ")) {
            remaining = remaining.substring("chat ".length()).trim();
        }
        return remaining;
    }
}

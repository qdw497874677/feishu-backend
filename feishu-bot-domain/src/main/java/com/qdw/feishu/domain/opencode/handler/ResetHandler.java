package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeMessageFormatter;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * 处理 reset 子命令：清除话题的初始化状态，允许重新绑定会话。
 */
@Slf4j
public class ResetHandler implements SubCommandHandler {

    private final OpenCodeSessionManager sessionManager;
    private final OpenCodeMessageFormatter messageFormatter;

    public ResetHandler(OpenCodeSessionManager sessionManager,
                        OpenCodeMessageFormatter messageFormatter) {
        this.sessionManager = sessionManager;
        this.messageFormatter = messageFormatter;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        String topicId = message.getTopicId();

        if (topicId == null || topicId.isEmpty()) {
            return AppExecutionResult.text(
                "❌ **只能在话题中使用 reset 命令**\n\n" +
                "reset 命令用于清除话题的初始化状态，允许重新绑定会话。\n\n" +
                "💡 使用场景：\n" +
                "  • 需要切换到不同的会话\n" +
                "  • 当前会话已失效\n" +
                "  • 想要重新开始初始化流程");
        }

        Optional<String> currentSession = sessionManager.getSessionId(message);

        sessionManager.clearSession(message);
        sessionManager.clearExplicitlyInitialized(message);

        log.info("已重置话题初始化状态: topicId={}", topicId);

        return AppExecutionResult.text(messageFormatter.buildResetResponse(topicId, currentSession));
    }
}

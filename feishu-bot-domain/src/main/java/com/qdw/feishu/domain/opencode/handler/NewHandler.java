package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeMessageFormatter;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.opencode.OpenCodeTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * 处理 new 子命令：创建新会话并执行任务。
 *
 * <p>支持两种格式：
 * <ul>
 *   <li>{@code /opencode new <prompt>} — 在已绑定项目创建新会话</li>
 *   <li>{@code /opencode new <project> <prompt>} — 指定项目创建新会话</li>
 * </ul>
 */
@Slf4j
public class NewHandler implements SubCommandHandler {

    private final OpenCodeSessionManager sessionManager;
    private final OpenCodeTaskExecutor taskExecutor;
    private final OpenCodeMessageFormatter messageFormatter;

    public NewHandler(OpenCodeSessionManager sessionManager,
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
        boolean isInitialized = inTopic && sessionManager.isTopicInitialized(messageContext);

        if (parts.length < 3) {
            return AppExecutionResult.text(messageFormatter.buildNewCommandUsage(isInitialized));
        }

        String project = null;
        String prompt;

        if (parts.length >= 4) {
            project = parts[2].trim();
            prompt = String.join(" ", Arrays.copyOfRange(parts, 3, parts.length));
        } else {
            prompt = parts[2].trim();

            if (isInitialized) {
                log.info("话题已绑定，将在当前项目创建新会话: topicId={}", topicId);
            } else {
                log.warn("话题未绑定，必须指定项目名称");
                return AppExecutionResult.text(messageFormatter.buildNewCommandUsage(false));
            }
        }

        return taskExecutor.executeWithNewSession(message, prompt, project);
    }
}

package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * OpenCode 任务执行器
 *
 * 负责执行 OpenCode 任务，支持同步和异步两种模式
 */
@Slf4j
@Component
public class OpenCodeTaskExecutor {

    private static final long SYNC_TIMEOUT_MS = 30000;  // 同步超时：30秒
    private static final long ASYNC_THRESHOLD_MS = 5000;  // 异步阈值：5秒

    private final OpenCodeGateway openCodeGateway;
    private final FeishuGateway feishuGateway;
    private final OpenCodeResponseFormatter responseFormatter;
    private final OpenCodeSessionManager sessionManager;

    public OpenCodeTaskExecutor(OpenCodeGateway openCodeGateway,
                                 FeishuGateway feishuGateway,
                                 OpenCodeResponseFormatter responseFormatter,
                                 OpenCodeSessionManager sessionManager) {
        this.openCodeGateway = openCodeGateway;
        this.feishuGateway = feishuGateway;
        this.responseFormatter = responseFormatter;
        this.sessionManager = sessionManager;
    }

    /**
     * 执行任务（自动保持会话）
     *
     * - 如果话题有活跃会话，继续使用
     * - 如果没有，创建新会话并保存
     */
    public String executeWithAutoSession(Message message, String prompt) {
        String topicId = message.getTopicId();

        // 如果不在话题中，使用新会话执行
        if (topicId == null || topicId.isEmpty()) {
            log.info("不在话题中，使用临时会话执行");
            return executeTask(message, prompt, null);
        }

        // 查找话题的活跃会话
        return sessionManager.getSessionId(topicId)
            .map(sessionId -> {
                log.info("找到活跃会话，继续使用: sessionId={}", sessionId);
                return executeTask(message, prompt, sessionId);
            })
            .orElseGet(() -> {
                log.info("话题无活跃会话，创建新会话: topicId={}", topicId);
                return executeWithNewSession(message, prompt);
            });
    }

    /**
     * 使用新会话执行任务
     *
     * - 清除旧会话（如果有）
     * - 执行任务
     */
    public String executeWithNewSession(Message message, String prompt) {
        return executeWithNewSession(message, prompt, null);
    }

    /**
     * 使用新会话执行任务（支持指定项目）
     *
     * - 清除旧会话（如果有）
     * - 在指定项目的工作目录中执行任务
     *
     * @param message 消息对象
     * @param prompt 提示词
     * @param project 项目名称（可选，用于指定工作目录）
     * @return 执行结果
     */
    public String executeWithNewSession(Message message, String prompt, String project) {
        String topicId = message.getTopicId();

        // 如果在话题中，清除旧会话
        sessionManager.clearSession(topicId);

        // 如果指定了项目，在 prompt 前添加工作目录说明
        String enhancedPrompt = enhancePromptWithWorkDirectory(prompt, project);

        // 执行任务（不指定 sessionID，让 OpenCode 创建新会话）
        return executeTask(message, enhancedPrompt, null);
    }

    /**
     * 在 prompt 前添加工作目录说明
     */
    private String enhancePromptWithWorkDirectory(String prompt, String project) {
        if (project == null || project.isEmpty()) {
            // 使用默认路径：/workspace/{YYYY-MM-DD}/
            String date = java.time.LocalDate.now().toString();
            return String.format("[工作目录: /workspace/%s/]\n\n%s", date, prompt);
        }

        // 使用项目路径（这里假设项目名称就是路径，后续可以优化为查询项目列表）
        // 为了简化，假设项目名称直接对应路径
        return String.format("[工作目录: /root/workspace/%s]\n\n%s", project, prompt);
    }

    /**
     * 使用指定会话执行任务
     *
     * @param message 消息对象
     * @param prompt 提示词
     * @param sessionId 指定会话 ID
     * @return 执行结果
     */
    public String executeWithSpecificSession(Message message, String prompt, String sessionId) {
        log.info("使用指定会话执行: sessionId={}", sessionId);

        String topicId = message.getTopicId();
        sessionManager.saveSession(topicId, sessionId);

        // 如果 prompt 为空，返回初始化成功提示
        if (prompt == null || prompt.isEmpty()) {
            return buildInitializationSuccessResponse(topicId, sessionId);
        }

        return executeTask(message, prompt, sessionId);
    }

    /**
      * 执行 OpenCode 任务（同步或异步）
      *
      * @param message 消息对象
      * @param prompt 提示词
      * @param sessionId 会话 ID（null 表示新会话）
      * @return 执行结果
      */
    public String executeTask(Message message, String prompt, String sessionId) {
        long startTime = System.nanoTime();

        try {
            // 尝试同步执行（30秒超时）
            String result = openCodeGateway.executeCommand(prompt, sessionId, 30);

            if (result == null) {
                // 执行时间超过30秒，转为异步执行
                log.info("任务执行超过30秒，转为异步执行");
                feishuGateway.sendMessage(message, "⏳ 任务正在执行中，请稍候...", message.getTopicId());
                executeAsync(message, prompt, sessionId);
                return "⏳ 任务已在后台执行中，请稍候...";
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // 如果执行时间超过5秒，先发送"执行中"消息
            if (durationMs > ASYNC_THRESHOLD_MS) {
                feishuGateway.sendMessage(message, "⏳ 任务执行中...", message.getTopicId());
            }

            // 提取并保存 sessionID
            String extractedSessionId = responseFormatter.extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionManager.saveSession(message.getTopicId(), extractedSessionId);
                log.info("保存会话ID: topicId={}, sessionId={}",
                        message.getTopicId(), extractedSessionId);
            }

            return responseFormatter.format(result, extractedSessionId);

        } catch (Exception e) {
            log.error("OpenCode 执行失败", e);
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
      * 异步执行 OpenCode 任务
      * 使用较长超时时间（60秒），避免用户等待过久
      */
    @Async("opencodeExecutor")
    public void executeAsync(Message message, String prompt, String sessionId) {
        try {
            String result = openCodeGateway.executeCommand(prompt, sessionId, 60);

            if (result == null) {
                log.warn("异步执行超时（60秒），返回错误提示");
                feishuGateway.sendMessage(message,
                    "⚠️ 任务执行超时，请稍后重试或尝试简化问题。", message.getTopicId());
                return;
            }

            // 提取并保存 sessionID
            String extractedSessionId = responseFormatter.extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionManager.saveSession(message.getTopicId(), extractedSessionId);
            }

            String formatted = responseFormatter.format(result, extractedSessionId);
            feishuGateway.sendMessage(message, formatted, message.getTopicId());

        } catch (Exception e) {
            log.error("异步执行失败", e);
            // 立即响应错误给用户
            feishuGateway.sendMessage(message, "❌ 执行失败: " + e.getMessage(), message.getTopicId());
        }
    }

    /**
     * 构建初始化成功响应
     */
    private String buildInitializationSuccessResponse(String topicId, String sessionId) {
        StringBuilder response = new StringBuilder();
        response.append("✅ **话题已初始化成功！**\n\n");
        response.append("📋 会话信息\n");
        response.append("  🆔 Session ID: `").append(sessionId).append("`\n");
        if (topicId != null && !topicId.isEmpty()) {
            response.append("  💬 话题 ID: `").append(topicId).append("`\n");
        }
        response.append("  ✅ 状态: 已绑定\n\n");
        response.append("**💡 现在可以开始对话了！**\n\n");
        response.append("发送命令：\n");
        response.append("  `/opencode chat <你的问题>` - 发送对话\n");
        response.append("  或直接输入问题（无需前缀）\n\n");
        response.append("示例：\n");
        response.append("  `/opencode chat 帮我重构这个函数`\n");
        response.append("  或直接：`帮我重构这个函数`\n");
        return response.toString();
    }
}

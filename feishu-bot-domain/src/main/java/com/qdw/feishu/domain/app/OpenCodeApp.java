package com.qdw.feishu.domain.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.TopicMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * OpenCode 应用 - 支持多轮对话
 */
@Slf4j
@Component
public class OpenCodeApp implements FishuAppI {

    private final OpenCodeGateway openCodeGateway;
    private final FeishuGateway feishuGateway;
    private final OpenCodeSessionGateway sessionGateway;
    private final TopicMappingGateway topicMappingGateway;
    private final ObjectMapper objectMapper;

    // 同步执行超时阈值（5秒）
    private static final long SYNC_TIMEOUT_MS = 5000;
    // 异步执行阈值（2秒）
    private static final long ASYNC_THRESHOLD_MS = 2000;

    public OpenCodeApp(OpenCodeGateway openCodeGateway,
                       FeishuGateway feishuGateway,
                       OpenCodeSessionGateway sessionGateway,
                       TopicMappingGateway topicMappingGateway,
                       ObjectMapper objectMapper) {
        this.openCodeGateway = openCodeGateway;
        this.feishuGateway = feishuGateway;
        this.sessionGateway = sessionGateway;
        this.topicMappingGateway = topicMappingGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAppId() {
        return "opencode";
    }

    @Override
    public String getAppName() {
        return "OpenCode 助手";
    }

    @Override
    public String getDescription() {
        return "通过飞书对话控制 OpenCode，支持多轮对话";
    }

    @Override
    public String getHelp() {
        return "🤖 **OpenCode 助手** - 支持多轮对话\n\n" +
               "📝 **基本命令**：\n" +
               "  `/opencode <提示词>`          - 执行任务（自动保持会话）\n" +
               "  `/opencode new <提示词>`       - 创建新会话并执行\n\n" +
               "🔧 **会话管理**：\n" +
               "  `/opencode session status`    - 查看当前会话信息\n" +
               "  `/opencode session list`      - 查看所有会话\n" +
               "  `/opencode session continue <id>` - 继续指定会话\n\n" +
               "💡 **使用示例**：\n\n" +
               "  /opencode 重构 TimeApp\n" +
               "  /opencode 添加单元测试        # 自动继续上一会话\n" +
               "  /opencode new 优化 BashApp    # 创建新会话\n\n";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("oc", "code");
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;  // 使用话题模式，支持多轮对话
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 3);

        log.info("OpenCodeApp.execute: content='{}'", content);

        // 空命令，返回帮助
        if (parts.length < 2) {
            return getHelp();
        }

        String subCommand = parts[1].toLowerCase();

        // 处理子命令
        switch (subCommand) {
            case "help":
                return getHelp();

            case "new":
                // 创建新会话
                if (parts.length < 3) {
                    return "❌ 用法：`/opencode new <提示词>`\n\n" +
                           "示例：`/opencode new 重构登录模块`";
                }
                String newPrompt = parts[2].trim();
                return executeWithNewSession(message, newPrompt);

            case "session":
                // 会话管理命令
                return handleSessionCommand(parts, message);

            default:
                // 默认：执行命令（自动保持会话）
                String prompt = content.substring(content.indexOf(' ') + 1).trim();
                return executeWithAutoSession(message, prompt);
        }
    }

    /**
     * 处理会话相关命令
     */
    private String handleSessionCommand(String[] parts, Message message) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode session <status|list|continue> [args]`";
        }

        String action = parts[2].toLowerCase();

        switch (action) {
            case "status":
                return getCurrentSessionStatus(message);

            case "list":
                return openCodeGateway.listSessions();

            case "continue":
                if (parts.length < 4) {
                    return "❌ 用法：`/opencode session continue <session_id>`";
                }
                String sessionId = parts[3].trim();
                return executeWithSpecificSession(message, null, sessionId);

            default:
                return "❌ 未知的 session 命令: `" + action + "`\n\n" +
                       "可用命令：`status`, `list`, `continue`";
        }
    }

    /**
     * 获取当前会话状态
     */
    private String getCurrentSessionStatus(Message message) {
        String topicId = message.getTopicId();

        if (topicId == null || topicId.isEmpty()) {
            return "❌ 当前不在话题中，无法查看会话状态";
        }

        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);

        if (sessionIdOpt.isEmpty()) {
            return "📭 当前话题还没有 OpenCode 会话\n\n" +
                   "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        String sessionId = sessionIdOpt.get();
        return "📋 **当前会话信息**\n\n" +
               "  🆔 Session ID: `" + sessionId + "`\n" +
               "  💬 话题 ID: `" + topicId + "`\n" +
               "  ✅ 状态: 活跃\n\n" +
               "💡 继续对话会自动使用此会话";
    }

    /**
     * 执行任务（自动保持会话）
     *
     * - 如果话题有活跃会话，继续使用
     * - 如果没有，创建新会话并保存
     */
    private String executeWithAutoSession(Message message, String prompt) {
        String topicId = message.getTopicId();

        // 如果不在话题中，使用新会话执行
        if (topicId == null || topicId.isEmpty()) {
            log.info("不在话题中，使用临时会话执行");
            return executeOpenCodeTask(message, prompt, null);
        }

        // 查找话题的活跃会话
        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);

        if (sessionIdOpt.isPresent()) {
            String sessionId = sessionIdOpt.get();
            log.info("找到活跃会话，继续使用: sessionId={}", sessionId);
            return executeOpenCodeTask(message, prompt, sessionId);
        } else {
            log.info("话题无活跃会话，创建新会话: topicId={}", topicId);
            return executeWithNewSession(message, prompt);
        }
    }

    /**
     * 使用新会话执行任务
     *
     * - 清除旧会话（如果有）
     * - 执行任务
     * - 保存新会话 ID
     */
    private String executeWithNewSession(Message message, String prompt) {
        String topicId = message.getTopicId();

        // 如果在话题中，清除旧会话
        if (topicId != null && !topicId.isEmpty()) {
            sessionGateway.clearSession(topicId);
            log.info("已清除旧会话: topicId={}", topicId);
        }

        // 执行任务（不指定 sessionID，让 OpenCode 创建新会话）
        String result = executeOpenCodeTask(message, prompt, null);

        // 从结果中提取 sessionID（需要 Gateway 实现）
        // 这里简化处理：假设 Gateway 返回的格式包含 sessionId
        // 实际实现中需要从 JSON 输出中解析

        return result;
    }

    /**
     * 使用指定会话执行任务
     */
    private String executeWithSpecificSession(Message message, String prompt, String sessionId) {
        log.info("使用指定会话执行: sessionId={}", sessionId);

        String topicId = message.getTopicId();

        // 更新会话映射
        if (topicId != null && !topicId.isEmpty()) {
            sessionGateway.saveSession(topicId, sessionId);
            log.info("已更新会话映射: topicId={}, sessionId={}", topicId, sessionId);
        }

        return executeOpenCodeTask(message, prompt, sessionId);
    }

    /**
     * 执行 OpenCode 任务（同步或异步）
     *
     * @param message 消息对象
     * @param prompt 提示词
     * @param sessionId 会话 ID（null 表示新会话）
     * @return 执行结果
     */
    private String executeOpenCodeTask(Message message, String prompt, String sessionId) {
        long startTime = System.nanoTime();

        try {
            // 尝试同步执行（5秒超时）
            String result = openCodeGateway.executeCommand(prompt, sessionId, 5);

            if (result == null) {
                // 执行时间超过5秒，转为异步执行
                log.info("任务执行超过5秒，转为异步执行");
                feishuGateway.sendMessage(message, "⏳ 任务正在执行中，结果将稍后返回...",
                                          message.getTopicId());
                executeOpenCodeAsync(message, prompt, sessionId);
                return null;
            }

            long durationMs = (System.nanoTime() - startTime) / 1_000_000;

            // 如果执行时间超过2秒，先发送"执行中"消息
            if (durationMs > ASYNC_THRESHOLD_MS) {
                feishuGateway.sendMessage(message, "⏳ 任务执行中...",
                                          message.getTopicId());
            }

            // 提取并保存 sessionID
            String extractedSessionId = extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionGateway.saveSession(message.getTopicId(), extractedSessionId);
                log.info("保存会话ID: topicId={}, sessionId={}",
                        message.getTopicId(), extractedSessionId);
            }

            return formatOutput(result, extractedSessionId);

        } catch (Exception e) {
            log.error("OpenCode 执行失败", e);
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
     * 异步执行 OpenCode 任务
     */
    @Async("opencodeExecutor")
    public void executeOpenCodeAsync(Message message, String prompt, String sessionId) {
        try {
            String result = openCodeGateway.executeCommand(prompt, sessionId, 0);  // 0表示无超时限制

            // 提取并保存 sessionID
            String extractedSessionId = extractSessionId(result);
            if (extractedSessionId != null && message.getTopicId() != null) {
                sessionGateway.saveSession(message.getTopicId(), extractedSessionId);
            }

            String formatted = formatOutput(result, extractedSessionId);
            feishuGateway.sendMessage(message, formatted, message.getTopicId());

        } catch (Exception e) {
            log.error("异步执行失败", e);
            feishuGateway.sendMessage(message, "❌ 执行失败: " + e.getMessage(),
                                      message.getTopicId());
        }
    }

    /**
     * 从 OpenCode 输出中提取 sessionID
     * 
     * 通过解析 JSON 输出提取 session_id 字段，而不是简单的字符串匹配
     * OpenCode 输出格式: {"type":"text","content":"...", "session_id":"ses_xxx"}
     */
    private String extractSessionId(String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }

        try {
            // 尝试从 JSON 输出中提取 session_id 字段
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(output);
            
            // 检查是否是消息数组格式
            if (root.isArray()) {
                // 遍历消息数组查找 session_id
                for (com.fasterxml.jackson.databind.JsonNode message : root) {
                    if (message.has("session_id")) {
                        String sessionId = message.get("session_id").asText();
                        if (sessionId != null && sessionId.startsWith("ses_")) {
                            log.debug("从 JSON 中提取到 sessionId: {}", sessionId);
                            return sessionId;
                        }
                    }
                }
            } else if (root.isObject()) {
                // 单个消息对象
                if (root.has("session_id")) {
                    String sessionId = root.get("session_id").asText();
                    if (sessionId != null && sessionId.startsWith("ses_")) {
                        log.debug("从 JSON 中提取到 sessionId: {}", sessionId);
                        return sessionId;
                    }
                }
            }
            
            // JSON 解析未找到 session_id，回退到字符串匹配（向后兼容）
            log.debug("JSON 中未找到 session_id，回退到字符串匹配");
            return extractSessionIdByStringMatching(output);
            
        } catch (Exception e) {
            log.warn("JSON 解析失败，回退到字符串匹配: {}", e.getMessage());
            return extractSessionIdByStringMatching(output);
        }
    }
    
    /**
     * 回退方法：通过字符串匹配提取 sessionID
     * 用于向后兼容或非 JSON 格式输出
     */
    private String extractSessionIdByStringMatching(String output) {
        int sessionIndex = output.indexOf("ses_");
        if (sessionIndex == -1) {
            return null;
        }

        int sessionIdEnd = findEndOfSessionId(output, sessionIndex);
        return output.substring(sessionIndex, sessionIdEnd);
    }

    private int findEndOfSessionId(String output, int startIndex) {
        int index = startIndex;
        while (index < output.length()) {
            char currentChar = output.charAt(index);
            if (isDelimiter(currentChar)) {
                return index;
            }
            index++;
        }
        return output.length();
    }

    private boolean isDelimiter(char c) {
        return c == ' ' || c == '\n' || c == '\r';
    }

    /**
     * 格式化输出结果
     */
    private String formatOutput(String rawOutput, String sessionId) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "✅ 执行完成，无输出";
        }

        // 截断过长的输出（飞书消息限制）
        int maxLength = 2000;
        String output = rawOutput;

        if (rawOutput.length() > maxLength) {
            output = rawOutput.substring(0, maxLength - 50) + "\n\n...(输出过长，已截断)";
        }

        // 如果有 sessionID，添加提示
        if (sessionId != null && !sessionId.isEmpty()) {
            return output + "\n\n💾 _会话ID: `" + sessionId + "` (已自动保存)_";
        }

        return output;
    }
}

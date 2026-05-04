package com.qdw.feishu.domain.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenCode 响应格式化器
 *
 * 负责格式化 OpenCode 执行结果，提取 Session ID
 */
@Slf4j
public class OpenCodeResponseFormatter {

    private final ObjectMapper objectMapper;

    public OpenCodeResponseFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 格式化输出结果
     *
     * @param rawOutput 原始输出
     * @param sessionId 会话 ID
     * @return 格式化后的输出
     */
    public String format(String rawOutput, String sessionId) {
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

    /**
     * 从 OpenCode 输出中提取 sessionID
     *
     * 通过解析 JSON 输出提取 session_id 字段
     * OpenCode 输出格式: {"type":"text","content":"...", "session_id":"ses_xxx"}
     *
     * @param output OpenCode 原始输出
     * @return 提取的 sessionId，如果未找到返回 null
     */
    public String extractSessionId(String output) {
        if (output == null || output.isEmpty()) {
            return null;
        }

        try {
            // 尝试从 JSON 输出中提取 session_id 字段
            JsonNode root = objectMapper.readTree(output);

            // 检查是否是消息数组格式
            if (root.isArray()) {
                for (JsonNode message : root) {
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

            // JSON 解析未找到 session_id，回退到字符串匹配
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
}

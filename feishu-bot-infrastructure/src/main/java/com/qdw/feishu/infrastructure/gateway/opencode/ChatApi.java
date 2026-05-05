package com.qdw.feishu.infrastructure.gateway.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Chat API 资源类
 *
 * 封装所有与聊天/消息相关的 OpenCode HTTP API 调用。
 */
@Slf4j
public class ChatApi {

    private final OpenCodeHttpHelper httpHelper;

    public ChatApi(OpenCodeHttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    /**
     * 执行命令：根据是否有 sessionId 选择新建或继续会话
     */
    public String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception {
        if (sessionId == null || sessionId.isEmpty()) {
            return executeInNewSession(prompt, timeoutSeconds);
        } else {
            return executeInExistingSession(sessionId, prompt, timeoutSeconds);
        }
    }

    /**
     * 同步发送消息并等待响应
     */
    public String sendMessageSync(String sessionId, String prompt, int timeoutSeconds,
                                   boolean returnNullOnTimeout, String directory) {
        if (httpHelper.getProperties().isHealthCheckEnabled() && !isServerHealthy(httpHelper)) {
            log.warn("OpenCode 服务不可达，跳过请求");
            return "❌ 无法连接到 OpenCode 服务，请确保服务已启动";
        }

        return httpHelper.executeWithRetry("sendMessageSync", () -> {
            try {
                String body = String.format(
                        "{\"parts\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
                        httpHelper.escapeJson(prompt)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(buildMessageUrl(sessionId, directory)))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Authorization", httpHelper.getAuthHeader())
                        .timeout(Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : httpHelper.getProperties().getRequestTimeout()))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseMessageResponse(response.body());
                } else if (response.statusCode() == 404) {
                    log.error("会话不存在: {}", sessionId);
                    return "❌ 会话不存在或已失效，请使用 `/opencode cn` 重新创建会话";
                } else {
                    log.error("发送消息失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                    return "❌ OpenCode 服务异常 (状态码: " + response.statusCode() + ")";
                }

            } catch (java.net.http.HttpTimeoutException e) {
                if (returnNullOnTimeout) {
                    log.info("请求超时（{}秒），返回null等待异步执行", timeoutSeconds);
                    return null;
                } else {
                    log.error("发送消息超时", e);
                    throw new RuntimeException("发送消息超时", e);
                }
            } catch (Exception e) {
                log.error("发送消息异常", e);
                if (e.getCause() instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.UnknownHostException) {
                    return "❌ 无法连接到 OpenCode 服务，请确保服务已启动";
                }
                throw new RuntimeException("发送消息失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 异步发送消息（不等待响应）
     */
    public void sendMessageAsync(String sessionId, String prompt) {
        try {
            String body = String.format(
                    "{\"parts\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
                    httpHelper.escapeJson(prompt)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(httpHelper.getServerUrl() + "/session/" + sessionId + "/prompt_async"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", httpHelper.getAuthHeader())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 204) {
                log.info("异步消息发送成功: sessionId={}", sessionId);
            } else {
                log.warn("异步消息发送失败，状态码: {}", response.statusCode());
            }

        } catch (Exception e) {
            log.error("异步发送消息异常", e);
        }
    }

    // ============ 私有方法 ============

    private String executeInNewSession(String prompt, int timeoutSeconds) throws Exception {
        log.info("创建新会话并执行命令: {}", prompt);

        SessionApi sessionApi = new SessionApi(httpHelper);
        String sessionId = sessionApi.createSession(null);
        if (sessionId == null) {
            return "❌ 创建会话失败";
        }

        return sendMessageSync(sessionId, prompt, timeoutSeconds, true, httpHelper.getDefaultDirectory());
    }

    private String executeInExistingSession(String sessionId, String prompt, int timeoutSeconds) throws Exception {
        log.info("在会话 {} 中执行命令: {}", sessionId, prompt);

        if (prompt == null || prompt.isEmpty()) {
            SessionApi sessionApi = new SessionApi(httpHelper);
            return sessionApi.getSessionDetails(sessionId);
        }

        return sendMessageSync(sessionId, prompt, timeoutSeconds, true, httpHelper.getDefaultDirectory());
    }

    /**
     * 构建发送消息的 URL（包含 directory 参数）
     */
    private String buildMessageUrl(String sessionId, String directory) {
        String baseUrl = httpHelper.getServerUrl() + "/session/" + sessionId + "/message";
        if (directory != null && !directory.isEmpty()) {
            return baseUrl + "?directory=" + URLEncoder.encode(directory, StandardCharsets.UTF_8);
        }
        return baseUrl;
    }

    /**
     * 委托给 HealthApi 检查服务健康
     */
    private boolean isServerHealthy(OpenCodeHttpHelper helper) {
        return new HealthApi(helper).isServerHealthy();
    }

    /**
     * 解析消息响应，提取文本内容
     */
    private String parseMessageResponse(String jsonResponse) {
        log.info("解析 OpenCode 响应: {}", jsonResponse);

        try {
            JsonNode json = httpHelper.getObjectMapper().readTree(jsonResponse);

            // 检查 OpenCode 服务端错误（如 info.error）
            if (json.has("info") && json.get("info").has("error")) {
                JsonNode error = json.get("info").get("error");
                String errorName = error.has("name") ? error.get("name").asText() : "UnknownError";
                String errorMsg = "";
                if (error.has("data") && error.get("data").has("message")) {
                    errorMsg = error.get("data").get("message").asText();
                }
                log.warn("OpenCode 服务返回错误: {} - {}", errorName, errorMsg);
                return "⚠️ OpenCode 处理失败: " + (errorMsg.isEmpty() ? errorName : errorMsg);
            }

            StringBuilder textContent = new StringBuilder();

            if (json.has("parts") && json.get("parts").isArray()) {
                JsonNode parts = json.get("parts");
                for (JsonNode part : parts) {
                    String type = part.has("type") ? part.get("type").asText() : "";

                    if ("text".equals(type)) {
                        if (part.has("text")) {
                            JsonNode textNode = part.get("text");
                            if (textNode.isTextual()) {
                                textContent.append(textNode.asText()).append("\n");
                            } else if (textNode.has("content")) {
                                textContent.append(textNode.get("content").asText()).append("\n");
                            }
                        }
                    }

                    if ("tool_use".equals(type)) {
                        if (part.has("toolUse") && part.get("toolUse").has("output")) {
                            String output = part.get("toolUse").get("output").asText();
                            textContent.append("```\n").append(output).append("\n```\n");
                        }
                    }
                }
            }

            String result = textContent.toString().trim();
            if (result.isEmpty()) {
                log.warn("响应解析成功，但无文本内容: parts={}", json.has("parts") ? json.get("parts").size() : "none");
                return "⚠️ OpenCode 返回空内容，请稍后重试";
            }

            log.info("成功提取文本内容，长度: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("解析消息响应失败: {}", jsonResponse, e);
            return "❌ 解析响应失败: " + e.getMessage();
        }
    }
}

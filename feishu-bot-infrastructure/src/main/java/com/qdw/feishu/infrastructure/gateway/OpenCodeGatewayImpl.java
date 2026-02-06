package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.infrastructure.config.OpenCodeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * OpenCode Gateway 实现
 *
 * 通过 HTTP API 与 OpenCode 服务端通信
 */
@Slf4j
@Component
public class OpenCodeGatewayImpl implements OpenCodeGateway {

    private final OpenCodeProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long MAX_RETRY_DELAY_MS = 8000;

    public OpenCodeGatewayImpl(OpenCodeProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
                .build();
        log.info("OpenCode Gateway 初始化完成，服务端: {}", properties.getServerUrl());
    }

    @Override
    public String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception {
        if (sessionId == null || sessionId.isEmpty()) {
            return executeInNewSession(prompt, timeoutSeconds);
        } else {
            return executeInExistingSession(sessionId, prompt, timeoutSeconds);
        }
    }

    /**
     * 在新会话中执行命令
     */
    private String executeInNewSession(String prompt, int timeoutSeconds) throws Exception {
        log.info("创建新会话并执行命令: {}", prompt);

        String sessionId = createSession(null);
        if (sessionId == null) {
            return "❌ 创建会话失败";
        }

        return sendMessageSync(sessionId, prompt, timeoutSeconds, true);
    }

    /**
     * 在现有会话中执行命令
     */
    private String executeInExistingSession(String sessionId, String prompt, int timeoutSeconds) throws Exception {
        log.info("在会话 {} 中执行命令: {}", sessionId, prompt);

        if (prompt == null || prompt.isEmpty()) {
            return getSessionDetails(sessionId);
        }

        return sendMessageSync(sessionId, prompt, timeoutSeconds, true);
    }

    /**
      * 创建新会话
      */
    private String createSession(String parentID) {
        return executeWithRetry("createSession", () -> {
            try {
                String body = parentID != null
                    ? String.format("{\"parentID\":\"%s\"}", parentID)
                    : "{}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/session"))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Authorization", getAuthHeader())
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonNode json = objectMapper.readTree(response.body());
                    if (json.has("id")) {
                        String sessionId = json.get("id").asText();
                        log.info("创建会话成功: {}", sessionId);
                        return sessionId;
                    }
                }

                log.error("创建会话失败: {}", response.body());
                return null;

            } catch (Exception e) {
                log.error("创建会话异常", e);
                throw new RuntimeException("创建会话失败", e);
            }
        });
    }

    /**
      * 同步发送消息并等待响应
      */
    private String sendMessageSync(String sessionId, String prompt, int timeoutSeconds, boolean returnNullOnTimeout) {
        // 首先检查服务连通性（如果启用）
        if (properties.isHealthCheckEnabled() && !isServerHealthy()) {
            log.warn("OpenCode 服务不可达，跳过请求");
            return "❌ 无法连接到 OpenCode 服务，请确保服务已启动";
        }

        return executeWithRetry("sendMessageSync", () -> {
            try {
                String body = String.format(
                        "{\"parts\":[{\"type\":\"text\",\"text\":\"%s\"}]}",
                        escapeJson(prompt)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/session/" + sessionId + "/message"))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Authorization", getAuthHeader())
                        .timeout(Duration.ofSeconds(timeoutSeconds > 0 ? timeoutSeconds : properties.getRequestTimeout()))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseMessageResponse(response.body());
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
                // 对于连接错误等异常，直接返回用户友好的错误消息
                if (e.getCause() instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.UnknownHostException) {
                    return "❌ 无法连接到 OpenCode 服务，请确保服务已启动";
                }
                throw new RuntimeException("发送消息失败: " + e.getMessage(), e);
            }
        });
    }

    /**
      * 检查 OpenCode 服务是否健康
      */
    public boolean isServerHealthy() {
        return executeWithRetry("healthCheck", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/global/health"))
                        .header("Authorization", getAuthHeader())
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    return json.has("healthy") && json.get("healthy").asBoolean();
                }
                return false;
            } catch (Exception e) {
                log.warn("健康检查失败: {}", e.getMessage());
                return false;
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
                    escapeJson(prompt)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getServerUrl() + "/session/" + sessionId + "/prompt_async"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", getAuthHeader())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
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

    @Override
    public String listSessions() {
        return executeWithRetry("listSessions", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/session"))
                        .header("Authorization", getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return formatSessionList(response.body());
                } else {
                    return "❌ 获取会话列表失败: " + response.body();
                }

            } catch (Exception e) {
                log.error("列出会话失败", e);
                return "❌ 获取会话列表失败: " + e.getMessage();
            }
        });
    }

    @Override
    public String listRecentSessions(String project, int limit) {
        return executeWithRetry("listRecentSessions", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/session"))
                        .header("Authorization", getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return formatProjectSessionList(response.body(), project, limit);
                } else {
                    return "❌ 获取会话列表失败: " + response.body();
                }

            } catch (Exception e) {
                log.error("列出项目会话失败: project={}", project, e);
                return "❌ 获取项目会话列表失败: " + e.getMessage();
            }
        });
    }

    @Override
    public String listProjects() {
        return executeWithRetry("listProjects", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/project"))
                        .header("Authorization", getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return formatProjectList(response.body());
                } else {
                    return "❌ 获取项目列表失败: " + response.body();
                }

            } catch (Exception e) {
                log.error("列出项目失败", e);
                return "❌ 获取项目列表失败: " + e.getMessage();
            }
        });
    }

    @Override
    public String listCommands() {
        return executeWithRetry("listCommands", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/command"))
                        .header("Authorization", getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return formatCommandList(response.body());
                } else {
                    return "❌ 获取命令列表失败: " + response.body();
                }

            } catch (Exception e) {
                log.error("列出命令失败", e);
                return "❌ 获取命令列表失败: " + e.getMessage();
            }
        });
    }

    @Override
    public String getServerStatus() {
        return executeWithRetry("getServerStatus", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/global/health"))
                        .header("Authorization", getAuthHeader())
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    boolean healthy = json.get("healthy").asBoolean();
                    String version = json.has("version") ? json.get("version").asText() : "Unknown";

                    if (healthy) {
                        return "✅ OpenCode 服务状态: 正常运行\n\n" +
                               "版本: " + version + "\n" +
                               "服务端: " + properties.getServerUrl();
                    } else {
                        return "⚠️ OpenCode 服务状态: 不可用\n\n" +
                               "服务端: " + properties.getServerUrl();
                    }
                } else {
                    return "❌ OpenCode 服务状态: 无法连接\n\n" +
                           "服务端: " + properties.getServerUrl() + "\n" +
                           "错误: " + response.body();
                }

            } catch (Exception e) {
                log.error("检查服务状态失败", e);
                return "❌ OpenCode 服务状态: 无法连接\n\n" +
                       "服务端: " + properties.getServerUrl() + "\n" +
                       "错误: " + e.getMessage();
            }
        });
    }

    /**
     * 获取会话详情
     */
    private String getSessionDetails(String sessionId) {
        return executeWithRetry("getSessionDetails", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getServerUrl() + "/session/" + sessionId))
                        .header("Authorization", getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return formatSessionDetails(response.body());
                } else {
                    return "❌ 获取会话详情失败: " + response.body();
                }

            } catch (Exception e) {
                log.error("获取会话详情失败", e);
                return "❌ 获取会话详情失败: " + e.getMessage();
            }
        });
    }

    /**
     * 格式化会话列表
     */
    private String formatSessionList(String jsonResponse) {
        try {
            JsonNode json = objectMapper.readTree(jsonResponse);
            if (!json.isArray() || json.size() == 0) {
                return "📋 暂无会话记录";
            }

            StringBuilder sb = new StringBuilder("📋 OpenCode 会话列表:\n\n");

            for (int i = 0; i < json.size() && i < 10; i++) {
                JsonNode session = json.get(i);
                String id = session.get("id").asText();
                String title = session.has("title") && !session.get("title").isNull()
                    ? session.get("title").asText()
                    : "无标题";

                sb.append(String.format("%d. %s\n   ID: %s\n\n", i + 1, title, id));
            }

            if (json.size() > 10) {
                sb.append(String.format("... 还有 %d 个会话\n", json.size() - 10));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("格式化会话列表失败", e);
            return "❌ 格式化会话列表失败: " + e.getMessage();
        }
    }

    /**
     * 格式化项目会话列表（过滤指定项目的最近会话）
     */
    private String formatProjectSessionList(String jsonResponse, String project, int limit) {
        try {
            JsonNode json = objectMapper.readTree(jsonResponse);
            if (!json.isArray() || json.size() == 0) {
                return "📋 暂无会话记录";
            }

            // 过滤属于指定项目的会话
            List<JsonNode> filteredSessions = new ArrayList<>();
            for (JsonNode session : json) {
                if (isSessionBelongToProject(session, project)) {
                    filteredSessions.add(session);
                }
            }

            if (filteredSessions.isEmpty()) {
                return String.format("📋 项目 **%s** 暂无会话记录\n\n" +
                       "💡 提示：\n" +
                       " - 确认项目名称是否正确\n" +
                       " - 使用 `/opencode projects` 查看所有项目\n" +
                       " - 使用 `/opencode new <提示词>` 在此项目中创建新会话", project);
            }

            // 限制返回数量
            int count = Math.min(limit, filteredSessions.size());
            StringBuilder sb = new StringBuilder(String.format("📋 项目 **%s** 的最近 %d 个会话:\n\n", project, count));

            for (int i = 0; i < count; i++) {
                JsonNode session = filteredSessions.get(i);
                String id = session.get("id").asText();
                String title = session.has("title") && !session.get("title").isNull()
                    ? session.get("title").asText()
                    : "无标题";

                String timeInfo = "";
                if (session.has("created_at")) {
                    long createdAt = session.get("created_at").asLong();
                    timeInfo = formatTimestamp(createdAt);
                }

                sb.append(String.format("%d. %s\n   ID: `%s`\n", i + 1, title, id));
                if (!timeInfo.isEmpty()) {
                    sb.append(String.format("   创建时间: %s\n", timeInfo));
                }
                sb.append("\n");
            }

            if (filteredSessions.size() > limit) {
                sb.append(String.format("... 还有 %d 个会话\n", filteredSessions.size() - limit));
            }

            sb.append("💡 选择会话:\n" +
                     "   `/opencode session continue <ID>`\n");

            return sb.toString();

        } catch (Exception e) {
            log.error("格式化项目会话列表失败", e);
            return "❌ 格式化项目会话列表失败: " + e.getMessage();
        }
    }

    /**
     * 判断会话是否属于指定项目
     */
    private boolean isSessionBelongToProject(JsonNode session, String project) {
        // 检查会话的 title 或其他字段是否包含项目名称
        if (session.has("title") && !session.get("title").isNull()) {
            String title = session.get("title").asText().toLowerCase();
            if (title.contains(project.toLowerCase())) {
                return true;
            }
        }

        // 检查其他可能包含项目信息的字段
        if (session.has("project")) {
            String sessionProject = session.get("project").asText();
            if (sessionProject.equalsIgnoreCase(project)) {
                return true;
            }
        }

        if (session.has("worktree")) {
            String worktree = session.get("worktree").asText();
            if (worktree.toLowerCase().contains(project.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        try {
            java.time.Instant instant = java.time.Instant.ofEpochSecond(timestamp);
            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
            java.time.Duration duration = java.time.Duration.between(zdt, java.time.ZonedDateTime.now());

            if (duration.toMinutes() < 60) {
                return String.format("%d 分钟前", duration.toMinutes());
            } else if (duration.toHours() < 24) {
                return String.format("%d 小时前", duration.toHours());
            } else if (duration.toDays() < 7) {
                return String.format("%d 天前", duration.toDays());
            } else {
                return zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        } catch (Exception e) {
            return "未知时间";
        }
    }

    /**
     * 格式化项目列表
     */
    private String formatProjectList(String jsonResponse) {
        try {
            JsonNode json = objectMapper.readTree(jsonResponse);
            if (!json.isArray() || json.size() == 0) {
                return "📁 暂无项目记录";
            }

            StringBuilder sb = new StringBuilder("📁 OpenCode 项目列表:\n\n");

            for (int i = 0; i < json.size() && i < 15; i++) {
                JsonNode project = json.get(i);

                String worktree = project.has("worktree") ? project.get("worktree").asText() : "未知路径";
                String vcs = project.has("vcs") ? project.get("vcs").asText() : "";

                String name = extractProjectName(worktree);

                sb.append(String.format("%d. **%s**\n   路径: %s\n", i + 1, name, worktree));

                if (!vcs.isEmpty()) {
                    sb.append(String.format("   VCS: %s\n", vcs.toUpperCase()));
                }

                sb.append("\n");
            }

            if (json.size() > 15) {
                sb.append(String.format("... 还有 %d 个项目\n", json.size() - 15));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("格式化项目列表失败", e);
            return "❌ 格式化项目列表失败: " + e.getMessage();
        }
    }

    /**
     * 格式化命令列表
     */
    private String formatCommandList(String jsonResponse) {
        try {
            JsonNode json = objectMapper.readTree(jsonResponse);
            if (!json.isArray() || json.size() == 0) {
                return "⚡️ 暂无可用命令";
            }

            StringBuilder sb = new StringBuilder("⚡️ OpenCode 斜杠命令:\n\n");

            for (int i = 0; i < json.size(); i++) {
                JsonNode command = json.get(i);

                String id = command.has("id") ? command.get("id").asText() : "未知";
                String name = command.has("name") ? command.get("name").asText() : "";

                String description = "";
                if (command.has("description")) {
                    description = command.get("description").asText();
                } else if (command.has("doc")) {
                    description = command.get("doc").asText();
                }

                String enabled = command.has("enabled") && command.get("enabled").asBoolean()
                    ? "✅"
                    : "❌";

                sb.append(String.format("**%s** `%s`", enabled, name));

                if (!description.isEmpty()) {
                    sb.append(String.format(" - %s", description));
                }

                sb.append("\n\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("格式化命令列表失败", e);
            return "❌ 格式化命令列表失败: " + e.getMessage();
        }
    }

    /**
     * 从路径中提取项目名称
     */
    private String extractProjectName(String path) {
        if (path == null || path.isEmpty()) {
            return "未命名项目";
        }

        String[] parts = path.split("[/\\\\]");
        if (parts.length > 0) {
            String lastName = parts[parts.length - 1];
            return lastName.isEmpty() ? "未命名项目" : lastName;
        }

        return "未命名项目";
    }

    /**
     * 格式化会话详情
     */
    private String formatSessionDetails(String jsonResponse) {
        try {
            JsonNode json = objectMapper.readTree(jsonResponse);
            String title = json.has("title") && !json.get("title").isNull()
                ? json.get("title").asText()
                : "无标题";

            return "📝 会话详情\n\n" +
                   "标题: " + title + "\n" +
                   "ID: " + json.get("id").asText() + "\n" +
                   "消息数: " + (json.has("messageCount") ? json.get("messageCount").asInt() : "未知");

        } catch (Exception e) {
            log.error("格式化会话详情失败", e);
            return "❌ 格式化会话详情失败: " + e.getMessage();
        }
    }

    /**
     * 解析消息响应，提取文本内容
     */
    private String parseMessageResponse(String jsonResponse) {
        log.info("解析 OpenCode 响应: {}", jsonResponse);

        try {
            JsonNode json = objectMapper.readTree(jsonResponse);
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
                log.warn("响应解析成功，但无文本内容");
                return "✅ 命令已执行，但无返回内容";
            }

            log.info("成功提取文本内容，长度: {}", result.length());
            return result;

        } catch (Exception e) {
            log.error("解析消息响应失败: {}", jsonResponse, e);
            return "❌ 解析响应失败: " + e.getMessage();
        }
    }

    /**
     * 生成 HTTP 基本认证头
     */
    private String getAuthHeader() {
        if (properties.getPassword() == null || properties.getPassword().isEmpty()) {
            return "";
        }

        String auth = properties.getUsername() + ":" + properties.getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encodedAuth;
    }

    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * 使用指数退避策略执行带重试的操作
     */
    private <T> T executeWithRetry(String operationName, java.util.function.Supplier<T> operation) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (java.net.ConnectException e) {
                if (attempt == MAX_RETRIES - 1) {
                    log.error("连接失败: 无法连接到 OpenCode 服务");
                    throw new RuntimeException("❌ 无法连接到 OpenCode 服务，请检查服务是否启动");
                }
                log.warn("连接失败，重试 {}/{}", attempt + 1, MAX_RETRIES);
                long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("RETRY_INTERRUPTED", ie);
                }
            } catch (java.net.http.HttpTimeoutException e) {
                if (attempt == MAX_RETRIES - 1) {
                    log.error("请求超时: OpenCode 服务响应超时");
                    throw new RuntimeException("❌ OpenCode 服务响应超时，请稍后重试");
                }
                log.warn("请求超时，重试 {}/{}", attempt + 1, MAX_RETRIES);
                long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("RETRY_INTERRUPTED", ie);
                }
            } catch (Exception e) {
                if (attempt == MAX_RETRIES - 1) {
                    log.error("未知错误: operation={}, error={}", operationName, e.getMessage(), e);
                    throw new RuntimeException(operationName + " 失败", e);
                }
                log.warn("操作失败，重试 {}/{}: {}", attempt + 1, MAX_RETRIES, operationName, e.getMessage());
                long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("RETRY_INTERRUPTED", ie);
                }
            }
        }
        throw new RuntimeException("All retry attempts failed for: " + operationName);
    }
}

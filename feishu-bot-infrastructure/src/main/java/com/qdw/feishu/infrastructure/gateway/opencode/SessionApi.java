package com.qdw.feishu.infrastructure.gateway.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.qdw.feishu.domain.opencode.SessionInfo;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Session API 资源类
 *
 * 封装所有与会话相关的 OpenCode HTTP API 调用。
 */
@Slf4j
public class SessionApi {

    private final OpenCodeHttpHelper httpHelper;

    public SessionApi(OpenCodeHttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    /**
     * 创建新会话（无参数）
     */
    public String createSession() throws Exception {
        return createSession(null, null);
    }

    /**
     * 创建新会话（指定初始工作目录）
     */
    public String createSession(String initialDirectory) throws Exception {
        return createSession(null, initialDirectory);
    }

    /**
     * 创建新会话（支持指定父会话和工作目录）
     */
    public String createSession(String parentId, String initialDirectory) {
        return httpHelper.executeWithRetry("createSession", () -> {
            try {
                String url = httpHelper.getServerUrl() + "/session";
                if (initialDirectory != null && !initialDirectory.isEmpty()) {
                    url += "?directory=" + URLEncoder.encode(initialDirectory, StandardCharsets.UTF_8);
                }

                String body;
                if (parentId != null && !parentId.isEmpty()) {
                    body = String.format("{\"parentID\":\"%s\"}", httpHelper.escapeJson(parentId));
                } else {
                    body = "{}";
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Authorization", httpHelper.getAuthHeader())
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    JsonNode json = httpHelper.getObjectMapper().readTree(response.body());
                    if (json.has("id")) {
                        String sessionId = json.get("id").asText();
                        log.info("创建会话成功: {}, directory={}", sessionId, initialDirectory);
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
     * 检查会话是否存在
     */
    public boolean checkSessionExists(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(httpHelper.getServerUrl() + "/session/" + sessionId))
                    .header("Authorization", httpHelper.getAuthHeader())
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("检查会话存在失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 列出所有会话（文本格式）
     */
    public String listSessions() {
        return httpHelper.executeWithRetry("listSessions", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/session"))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
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

    /**
     * 列出指定项目的最近会话（文本格式）
     */
    public String listRecentSessions(String project, int limit) {
        try {
            List<SessionInfo> sessions = listRecentSessionsStructured(project, limit);
            return formatSessionsAsText(sessions, project, limit);
        } catch (Exception e) {
            log.error("列出项目会话失败: project={}", project, e);
            return "❌ 获取项目会话列表失败: " + e.getMessage();
        }
    }

    /**
     * 获取项目的最近会话列表（结构化数据）
     */
    public List<SessionInfo> listRecentSessionsStructured(String project, int limit) {
        return httpHelper.executeWithRetry("listRecentSessionsStructured", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/session"))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return parseSessionsStructured(response.body(), project, limit);
                } else {
                    log.error("获取会话列表失败: status={}", response.statusCode());
                    return List.of();
                }

            } catch (Exception e) {
                log.error("获取结构化会话列表失败: project={}", project, e);
                throw new RuntimeException("获取会话列表失败", e);
            }
        });
    }

    /**
     * 获取会话详情（文本格式）
     */
    public String getSessionDetails(String sessionId) {
        return httpHelper.executeWithRetry("getSessionDetails", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/session/" + sessionId))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
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

    // ============ 私有格式化方法 ============

    private List<SessionInfo> parseSessionsStructured(String jsonResponse, String project, int limit) {
        try {
            JsonNode json = httpHelper.getObjectMapper().readTree(jsonResponse);
            if (!json.isArray() || json.size() == 0) {
                return List.of();
            }

            List<SessionInfo> result = new ArrayList<>();
            for (JsonNode session : json) {
                if (!isSessionBelongToProject(session, project)) {
                    continue;
                }

                String sessionId = session.has("id") ? session.get("id").asText() : null;
                if (sessionId == null || sessionId.isBlank()) {
                    continue;
                }

                String title = (session.has("title") && !session.get("title").isNull())
                    ? session.get("title").asText()
                    : "无标题";

                String lastPrompt = null;
                if (session.has("lastPrompt") && !session.get("lastPrompt").isNull()) {
                    String raw = session.get("lastPrompt").asText();
                    lastPrompt = raw.length() > 50 ? raw.substring(0, 47) + "..." : raw;
                }

                String relativeTime = "未知时间";
                if (session.has("created_at")) {
                    relativeTime = formatTimestamp(session.get("created_at").asLong());
                } else if (session.has("updatedAt")) {
                    relativeTime = formatTimestamp(session.get("updatedAt").asLong());
                }

                result.add(SessionInfo.builder()
                    .sessionId(sessionId)
                    .title(title)
                    .lastPrompt(lastPrompt)
                    .relativeTime(relativeTime)
                    .projectName(project)
                    .build());

                if (result.size() >= limit) {
                    break;
                }
            }
            return result;

        } catch (Exception e) {
            log.error("解析结构化会话列表失败", e);
            return List.of();
        }
    }

    private String formatSessionsAsText(List<SessionInfo> sessions, String project, int limit) {
        if (sessions.isEmpty()) {
            return String.format("📋 项目 **%s** 暂无会话记录\n\n" +
                   "💡 提示：\n" +
                   " - 确认项目名称是否正确\n" +
                   " - 使用 `/opencode projects` 查看所有项目\n" +
                   " - 使用 `/opencode new <提示词>` 在此项目中创建新会话", project);
        }

        int count = Math.min(limit, sessions.size());
        StringBuilder sb = new StringBuilder(
            String.format("📋 项目 **%s** 的最近 %d 个会话:\n\n", project, count));

        for (int i = 0; i < sessions.size(); i++) {
            SessionInfo s = sessions.get(i);
            sb.append(String.format("%d. %s\n   ID: `%s`\n", i + 1, s.getTitle(), s.getSessionId()));
            if (s.getLastPrompt() != null && !s.getLastPrompt().isBlank()) {
                sb.append(String.format("   摘要: %s\n", s.getLastPrompt()));
            }
            sb.append(String.format("   时间: %s\n\n", s.getRelativeTime()));
        }

        sb.append("💡 选择会话:\n   `/opencode session continue <ID>`\n");
        return sb.toString();
    }

    private String formatSessionList(String jsonResponse) {
        try {
            JsonNode json = httpHelper.getObjectMapper().readTree(jsonResponse);
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

    private String formatSessionDetails(String jsonResponse) {
        try {
            JsonNode json = httpHelper.getObjectMapper().readTree(jsonResponse);
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

    private boolean isSessionBelongToProject(JsonNode session, String project) {
        if (session.has("title") && !session.get("title").isNull()) {
            String title = session.get("title").asText().toLowerCase();
            if (title.contains(project.toLowerCase())) {
                return true;
            }
        }

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
}

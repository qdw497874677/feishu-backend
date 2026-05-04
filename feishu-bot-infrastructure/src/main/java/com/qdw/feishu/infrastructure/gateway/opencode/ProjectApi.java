package com.qdw.feishu.infrastructure.gateway.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Project API 资源类
 *
 * 封装所有与项目相关的 OpenCode HTTP API 调用。
 */
@Slf4j
public class ProjectApi {

    private final OpenCodeHttpHelper httpHelper;

    public ProjectApi(OpenCodeHttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    /**
     * 列出所有项目（文本格式）
     */
    public String listProjects() {
        return httpHelper.executeWithRetry("listProjects", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/project"))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
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

    /**
     * 列出所有斜杠命令（文本格式）
     */
    public String listCommands() {
        return httpHelper.executeWithRetry("listCommands", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/command"))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
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

    // ============ 私有格式化方法 ============

    private String formatProjectList(String jsonResponse) {
        try {
            JsonNode json = httpHelper.getObjectMapper().readTree(jsonResponse);
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

    private String formatCommandList(String jsonResponse) {
        try {
            JsonNode json = httpHelper.getObjectMapper().readTree(jsonResponse);
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
}

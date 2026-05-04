package com.qdw.feishu.infrastructure.gateway.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Health API 资源类
 *
 * 封装所有与健康检查相关的 OpenCode HTTP API 调用。
 */
@Slf4j
public class HealthApi {

    private final OpenCodeHttpHelper httpHelper;

    public HealthApi(OpenCodeHttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    /**
     * 检查 OpenCode 服务是否健康
     */
    public boolean isServerHealthy() {
        return httpHelper.executeWithRetry("healthCheck", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/global/health"))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = httpHelper.getObjectMapper().readTree(response.body());
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
     * 获取服务器状态（文本格式）
     */
    public String getServerStatus() {
        return httpHelper.executeWithRetry("getServerStatus", () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(httpHelper.getServerUrl() + "/global/health"))
                        .header("Authorization", httpHelper.getAuthHeader())
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = httpHelper.getHttpClient().send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode json = httpHelper.getObjectMapper().readTree(response.body());
                    boolean healthy = json.get("healthy").asBoolean();
                    String version = json.has("version") ? json.get("version").asText() : "Unknown";

                    if (healthy) {
                        return "✅ OpenCode 服务状态: 正常运行\n\n" +
                               "版本: " + version + "\n" +
                               "服务端: " + httpHelper.getServerUrl();
                    } else {
                        return "⚠️ OpenCode 服务状态: 不可用\n\n" +
                               "服务端: " + httpHelper.getServerUrl();
                    }
                } else {
                    return "❌ OpenCode 服务状态: 无法连接\n\n" +
                           "服务端: " + httpHelper.getServerUrl() + "\n" +
                           "错误: " + response.body();
                }

            } catch (Exception e) {
                log.error("检查服务状态失败", e);
                return "❌ OpenCode 服务状态: 无法连接\n\n" +
                       "服务端: " + httpHelper.getServerUrl() + "\n" +
                       "错误: " + e.getMessage();
            }
        });
    }
}

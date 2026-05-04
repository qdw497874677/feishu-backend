package com.qdw.feishu.infrastructure.gateway.opencode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.infrastructure.config.OpenCodeProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OpenCode HTTP 共享工具类
 *
 * 提供 HTTP 客户端、认证头生成、JSON 转义和重试逻辑。
 * 各 API 资源子类共用此类，避免重复。
 */
@Slf4j
public class OpenCodeHttpHelper {

    private final OpenCodeProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long MAX_RETRY_DELAY_MS = 8000;

    public OpenCodeHttpHelper(OpenCodeProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public OpenCodeProperties getProperties() {
        return properties;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public String getServerUrl() {
        return properties.getServerUrl();
    }

    /**
     * 生成 HTTP 基本认证头
     */
    public String getAuthHeader() {
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
    public String escapeJson(String text) {
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
     * 获取默认工作目录
     */
    public String getDefaultDirectory() {
        return System.getProperty("user.dir", "/root/workspace/feishu-backend");
    }

    /**
     * 使用指数退避策略执行带重试的操作
     */
    public <T> T executeWithRetry(String operationName, java.util.function.Supplier<T> operation) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                Throwable cause = e.getCause();

                if (e instanceof java.net.ConnectException ||
                    cause instanceof java.net.ConnectException) {
                    if (attempt == MAX_RETRIES - 1) {
                        log.error("连接失败: 无法连接到 OpenCode 服务");
                        throw new RuntimeException("❌ 无法连接到 OpenCode 服务，请检查服务是否启动");
                    }
                    log.warn("连接失败，重试 {}/{}", attempt + 1, MAX_RETRIES);
                    sleepWithBackoff(attempt);
                } else if (e instanceof java.net.http.HttpTimeoutException ||
                           cause instanceof java.net.http.HttpTimeoutException) {
                    if (attempt == MAX_RETRIES - 1) {
                        log.error("请求超时: OpenCode 服务响应超时");
                        throw new RuntimeException("❌ OpenCode 服务响应超时，请稍后重试");
                    }
                    log.warn("请求超时，重试 {}/{}", attempt + 1, MAX_RETRIES);
                    sleepWithBackoff(attempt);
                } else {
                    if (attempt == MAX_RETRIES - 1) {
                        log.error("未知错误: operation={}, error={}", operationName, e.getMessage(), e);
                        throw new RuntimeException(operationName + " 失败", e);
                    }
                    log.warn("操作失败，重试 {}/{}: {}", attempt + 1, MAX_RETRIES, operationName, e.getMessage());
                    sleepWithBackoff(attempt);
                }
            }
        }
        throw new RuntimeException("All retry attempts failed for: " + operationName);
    }

    private void sleepWithBackoff(int attempt) {
        long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << attempt), MAX_RETRY_DELAY_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RETRY_INTERRUPTED", ie);
        }
    }
}

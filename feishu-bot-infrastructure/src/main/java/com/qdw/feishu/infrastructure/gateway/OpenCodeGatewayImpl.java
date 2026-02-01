package com.qdw.feishu.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.infrastructure.config.OpenCodeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OpenCode Gateway 实现
 *
 * 调用 opencode CLI 并解析 JSON 输出
 */
@Slf4j
@Component
public class OpenCodeGatewayImpl implements OpenCodeGateway {

    private final OpenCodeProperties properties;
    private final String opencodeExecutable;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenCodeGatewayImpl(OpenCodeProperties properties) {
        this.properties = properties;
        this.opencodeExecutable = findExecutable();
        log.info("OpenCode Gateway 初始化完成，可执行文件: {}", opencodeExecutable);
    }

    /**
     * 查找 opencode 可执行文件
     */
    private String findExecutable() {
        String path = properties.getExecutablePath();
        if (path != null && !path.isEmpty()) {
            return path;
        }

        // 尝试从 PATH 中查找
        String[] searchPaths = {"/usr/bin/opencode", "/usr/local/bin/opencode"};
        for (String testPath : searchPaths) {
            try {
                if (new java.io.File(testPath).exists()) {
                    return testPath;
                }
            } catch (Exception e) {
                // 忽略
            }
        }

        // 默认使用 "opencode"，依赖 PATH
        return "opencode";
    }

    @Override
    public String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(opencodeExecutable);
        command.add("run");
        command.add("--format");
        command.add("json");

        // 添加会话继续参数
        if (sessionId != null && !sessionId.isEmpty()) {
            command.add("--session");
            command.add(sessionId);
        }

        // 如果有 prompt，添加为参数
        if (prompt != null && !prompt.isEmpty()) {
            command.add(prompt);
        }

        // 构建进程
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        log.info("执行 OpenCode 命令: {}", String.join(" ", command));

        Process process = pb.start();

        // 如果有超时限制
        if (timeoutSeconds > 0) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(() -> readProcessOutput(process));

            try {
                String output = future.get(timeoutSeconds, TimeUnit.SECONDS);
                executor.shutdown();
                return parseOpenCodeOutput(output);
            } catch (TimeoutException e) {
                process.destroyForcibly();
                executor.shutdownNow();
                log.warn("OpenCode 执行超时（{}秒）", timeoutSeconds);
                return null;  // 超时返回null
            }
        } else {
            // 无超时限制
            String output = readProcessOutput(process);
            return parseOpenCodeOutput(output);
        }
    }

    @Override
    public String listSessions() {
        try {
            List<String> command = List.of(opencodeExecutable, "session", "list");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);

            // 解析输出并格式化
            if (output.isEmpty() || output.contains("No sessions found")) {
                return "📋 暂无会话记录";
            }

            return "📋 OpenCode 会话列表:\n\n" + output;

        } catch (Exception e) {
            log.error("列出会话失败", e);
            return "❌ 获取会话列表失败: " + e.getMessage();
        }
    }

    @Override
    public String getServerStatus() {
        // TODO: 实现服务器状态检查
        return "✅ OpenCode 可用\n\n版本: 1.1.48\n可执行文件: " + opencodeExecutable;
    }

    /**
     * 读取进程输出
     */
    private String readProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();
            return output.toString();

        } catch (Exception e) {
            log.error("读取进程输出失败", e);
            return "错误: " + e.getMessage();
        }
    }

    /**
     * 解析 OpenCode JSON 输出，提取文本内容
     */
    private String parseOpenCodeOutput(String jsonOutput) {
        if (jsonOutput == null || jsonOutput.isEmpty()) {
            return "";
        }

        StringBuilder textContent = new StringBuilder();

        // 解析 JSON Lines 格式
        String[] lines = jsonOutput.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            try {
                JsonNode node = objectMapper.readTree(line);

                // 提取 text 类型消息
                if (node.has("type") && "text".equals(node.get("type").asText())) {
                    if (node.has("part") && node.get("part").has("text")) {
                        String text = node.get("part").get("text").asText();
                        textContent.append(text).append("\n");
                    }
                }

                // 提取 tool_use 输出
                if (node.has("type") && "tool_use".equals(node.get("type").asText())) {
                    if (node.has("part") && node.get("part").has("state")) {
                        var state = node.get("part").get("state");
                        if (state.has("output")) {
                            String output = state.get("output").asText();
                            textContent.append("```\n").append(output).append("\n```\n");
                        }
                    }
                }

            } catch (Exception e) {
                // JSON 解析失败，保留原始行
                textContent.append(line).append("\n");
            }
        }

        return textContent.toString().trim();
    }
}

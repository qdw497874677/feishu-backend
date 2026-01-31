package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.history.BashHistoryManager;
import com.qdw.feishu.domain.history.CommandExecution;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.validation.CommandWhitelistValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BashApp implements FishuAppI {

    private static final String WORKSPACE_DIR = ".workspace";

    private final CommandWhitelistValidator validator;
    private final BashHistoryManager historyManager;

    public BashApp(CommandWhitelistValidator validator, BashHistoryManager historyManager) {
        this.validator = validator;
        this.historyManager = historyManager;
    }

    @Override
    public String getAppId() {
        return "bash";
    }

    @Override
    public String getAppName() {
        return "命令执行";
    }

    @Override
    public String getDescription() {
        return "执行安全的bash命令";
    }

    @Override
    public String getHelp() {
        return "用法：/bash <命令>\n" +
               "允许的命令：ls, ll, dir, cat, less, head, tail, find, grep, ping\n" +
               "示例：/bash ls -la\n" +
               "查看历史：/bash history";
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 2);

        if (parts.length < 2) {
            return getHelp();
        }

        String command = parts[1].trim();

        if (command.equals("history")) {
            return formatHistory();
        }

        if (!validator.isValidCommand(command)) {
            return "错误：命令不在白名单中或包含非法操作符";
        }

        File workspaceDir = ensureWorkspaceExists();
        String baseCommand = extractBaseCommand(command);

        try {
            ProcessBuilder pb = new ProcessBuilder(baseCommand, getArgs(command));
            pb.directory(workspaceDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            boolean success = exitCode == 0;
            historyManager.recordExecution(command, truncateOutput(output), success);

            return output.isEmpty() ? "命令执行完成，无输出" : output;

        } catch (Exception e) {
            log.error("Command execution failed", e);
            historyManager.recordExecution(command, "错误: " + e.getMessage(), false);
            return "错误：" + e.getMessage();
        }
    }

    private File ensureWorkspaceExists() {
        String userDir = System.getProperty("user.dir");
        File workspace = new File(userDir, WORKSPACE_DIR);
        if (!workspace.exists()) {
            workspace.mkdirs();
        }
        return workspace;
    }

    private String extractBaseCommand(String command) {
        String trimmed = command.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex);
        }
        return trimmed;
    }

    private String getArgs(String command) {
        String trimmed = command.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(spaceIndex + 1);
        }
        return "";
    }

    private String readProcessOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            return reader.lines()
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "错误读取输出: " + e.getMessage();
        }
    }

    private String truncateOutput(String output) {
        if (output.length() > 2000) {
            return output.substring(0, 1997) + "...(truncated)";
        }
        return output;
    }

    private String formatHistory() {
        List<CommandExecution> history = historyManager.getHistory(10);

        if (history.isEmpty()) {
            return "暂无命令历史";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 命令历史 ===\n\n");

        for (int i = 0; i < history.size(); i++) {
            CommandExecution entry = history.get(i);
            sb.append(String.format("%d. [%s] %s\n%s\n\n",
                i + 1,
                entry.success() ? "✓" : "✗",
                entry.command(),
                entry.output().lines().count() > 3
                    ? entry.output().lines().limit(3).collect(Collectors.joining("\n")) + "\n..."
                    : entry.output()
            ));
        }

        return sb.toString();
    }
}

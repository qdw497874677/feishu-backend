package com.qdw.feishu.domain.command;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CommandWhitelistValidator {

    private static final Set<String> WHITELIST = Set.of(
        "pwd",
        "ls", "ll", "dir",
        "cat", "less",
        "head", "tail",
        "find", "grep", "ping",
        "mkdir", "opencode"
    );

    private static final Pattern CHAINING_PATTERN = Pattern.compile(
        "[|;&]|" +
        "\\|\\||&&|\\|\\|"
    );

    public boolean isValidCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        if (containsCommandChaining(command)) {
            return false;
        }

        String baseCommand = extractBaseCommand(command);
        return WHITELIST.contains(baseCommand);
    }

    private boolean containsCommandChaining(String command) {
        if (command.contains("\n") || command.contains("\r")) {
            return true;
        }

        return CHAINING_PATTERN.matcher(command).find();
    }

    private String extractBaseCommand(String command) {
        String trimmed = command.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex > 0) {
            return trimmed.substring(0, spaceIndex);
        }
        return trimmed;
    }
}

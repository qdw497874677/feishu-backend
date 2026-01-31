package com.qdw.feishu.domain.history;

import java.time.LocalDateTime;

public record CommandExecution(
    String command,
    String output,
    boolean success,
    LocalDateTime timestamp
) {
    public CommandExecution {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}

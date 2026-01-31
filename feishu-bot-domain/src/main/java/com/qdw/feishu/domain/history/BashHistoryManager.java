package com.qdw.feishu.domain.history;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

@Service
public class BashHistoryManager {

    private static final int MAX_HISTORY_SIZE = 100;

    private final BlockingQueue<CommandExecution> history;
    private final ConcurrentHashMap<String, CommandExecution> historyMap;

    public BashHistoryManager() {
        this.history = new LinkedBlockingQueue<>(MAX_HISTORY_SIZE);
        this.historyMap = new ConcurrentHashMap<>();
    }

    public void recordExecution(String command, String output, boolean success) {
        CommandExecution execution = new CommandExecution(command, output, success, LocalDateTime.now());

        while (history.remainingCapacity() == 0) {
            CommandExecution removed = history.poll();
            if (removed != null) {
                historyMap.remove(removed.timestamp().toString());
            }
        }

        history.offer(execution);
        historyMap.put(execution.timestamp().toString(), execution);
    }

    public List<CommandExecution> getHistory(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return history.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(limit)
            .collect(Collectors.toList());
    }
}

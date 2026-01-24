package com.qdw.feishu.app.router;

import com.qdw.feishu.app.executor.CommandExecutorI;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 命令路由器
 * 根据消息内容路由到对应的命令执行器
 */
@Slf4j
@Component
public class CommandRouter {

    private final Map<String, CommandExecutorI> executors;

    public CommandRouter(List<CommandExecutorI> allExecutors) {
        this.executors = allExecutors.stream()
            .collect(Collectors.toMap(
                CommandExecutorI::getCommand,
                Function.identity()
            ));
        log.info("CommandRouter initialized with {} commands: {}",
            executors.size(), executors.keySet());
    }

    /**
     * 路由命令
     * @param message 消息对象
     * @return 命令执行结果，如果不是命令则返回 null
     */
    public String route(Message message) {
        String content = message.getContent().trim();

        if (!content.startsWith("/")) {
            return null;
        }

        String[] parts = content.split("\\s+", 2);
        String commandKey = parts[0].substring(1).toLowerCase();

        CommandExecutorI executor = executors.get(commandKey);
        if (executor == null) {
            return "未知命令: " + commandKey + "\n输入 /help 查看帮助";
        }

        try {
            return executor.execute(message);
        } catch (Exception e) {
            log.error("Command execution failed: {}", commandKey, e);
            return "命令执行失败: " + e.getMessage();
        }
    }
}

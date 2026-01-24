package com.qdw.feishu.app.executor;

import com.qdw.feishu.domain.message.Message;

/**
 * 命令执行器接口
 * 每个命令实现此接口来处理对应的命令逻辑
 */
public interface CommandExecutorI {
    /**
     * 获取命令关键字
     * @return 命令关键字（例如 "time"）
     */
    String getCommand();

    /**
     * 执行命令
     * @param message 消息对象
     * @return 执行结果
     */
    String execute(Message message);
}

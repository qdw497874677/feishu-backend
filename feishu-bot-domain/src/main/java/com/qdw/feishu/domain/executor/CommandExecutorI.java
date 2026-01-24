package com.qdw.feishu.domain.executor;

import com.qdw.feishu.domain.message.Message;

/**
 * 命令执行器接口
 * 所有命令执行器都需要实现此接口
 */
public interface CommandExecutorI {

    /**
     * 获取命令关键词（如 "time" 对应 "/time"）
     */
    String getCommand();

    /**
     * 执行命令
     * @param message 消息对象
     * @return 命令执行结果（回复内容）
     */
    String execute(Message message);
}

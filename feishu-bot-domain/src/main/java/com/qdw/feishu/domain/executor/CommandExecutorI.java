package com.qdw.feishu.domain.executor;

import com.qdw.feishu.domain.message.Message;

public interface CommandExecutorI {
    String getCommand();

    String execute(Message message);
}

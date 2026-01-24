package com.qdw.feishu.domain.router;

import com.qdw.feishu.domain.message.Message;

public interface CommandRouter {
    String route(Message message);
}

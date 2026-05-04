package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;

/**
 * 处理 status 子命令：返回当前话题的会话绑定状态。
 */
public class StatusHandler implements SubCommandHandler {

    private final OpenCodeSessionManager sessionManager;

    public StatusHandler(OpenCodeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        return AppExecutionResult.text(sessionManager.getCurrentSessionStatus(messageContext));
    }
}

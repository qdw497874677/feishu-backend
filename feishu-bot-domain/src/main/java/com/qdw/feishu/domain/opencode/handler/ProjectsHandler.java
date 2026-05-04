package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;

/**
 * 处理 projects / p 子命令：列出可用项目。
 */
public class ProjectsHandler implements SubCommandHandler {

    private final OpenCodeGateway openCodeGateway;

    public ProjectsHandler(OpenCodeGateway openCodeGateway) {
        this.openCodeGateway = openCodeGateway;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        return AppExecutionResult.text(openCodeGateway.listProjects());
    }
}

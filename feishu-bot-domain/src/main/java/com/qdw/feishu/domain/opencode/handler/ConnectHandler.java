package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeMessageFormatter;

/**
 * 处理 connect 子命令：测试连接并返回服务状态和项目列表。
 */
public class ConnectHandler implements SubCommandHandler {

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeMessageFormatter messageFormatter;

    public ConnectHandler(OpenCodeGateway openCodeGateway,
                          OpenCodeMessageFormatter messageFormatter) {
        this.openCodeGateway = openCodeGateway;
        this.messageFormatter = messageFormatter;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        String status;
        try {
            status = openCodeGateway.getServerStatus();
        } catch (Exception e) {
            status = "❌ 无法获取 (" + e.getMessage() + ")";
        }

        String projects;
        try {
            projects = openCodeGateway.listProjects();
        } catch (Exception e) {
            projects = "❌ 无法获取项目列表 (" + e.getMessage() + ")";
        }

        return AppExecutionResult.text(messageFormatter.buildConnectSuccessResponse(status, projects));
    }
}

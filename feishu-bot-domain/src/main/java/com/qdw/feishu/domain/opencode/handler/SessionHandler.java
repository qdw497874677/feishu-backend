package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.opencode.OpenCodeTaskExecutor;

/**
 * 处理 session / sc 子命令：会话管理（状态、列表、续接、绑定）。
 *
 * <ul>
 *   <li>{@code sc <sessionId>} — 绑定指定会话</li>
 *   <li>{@code session status} — 查看当前会话状态</li>
 *   <li>{@code session list} — 查看所有会话</li>
 *   <li>{@code session continue <sessionId>} — 续接指定会话</li>
 * </ul>
 */
public class SessionHandler implements SubCommandHandler {

    private final OpenCodeSessionManager sessionManager;
    private final OpenCodeTaskExecutor taskExecutor;

    public SessionHandler(OpenCodeSessionManager sessionManager,
                          OpenCodeTaskExecutor taskExecutor) {
        this.sessionManager = sessionManager;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        String subCommand = parts[1].toLowerCase();

        if (subCommand.equals("sc")) {
            if (parts.length < 3) {
                return AppExecutionResult.text(
                    "❌ 用法：`/opencode sc <session_id>`\n\n示例：`/opencode sc ses_abc123`");
            }
            String sessionId = parts[2].trim();
            String project = parts.length >= 4 ? parts[3].trim() : null;
            if (project == null || project.isEmpty()) {
                return taskExecutor.executeWithSpecificSession(message, null, sessionId);
            }
            return taskExecutor.executeWithSpecificSession(message, null, sessionId, project);
        }

        if (parts.length < 3) {
            return AppExecutionResult.text(
                "❌ 用法：`/opencode session <status|list|continue> [args]`");
        }

        String action = parts[2].toLowerCase();

        return switch (action) {
            case "status" -> AppExecutionResult.text(
                sessionManager.getCurrentSessionStatus(messageContext));
            case "list" -> AppExecutionResult.text(
                sessionManager.handleListSessions());
            case "continue" -> handleSessionContinue(parts, message);
            default -> AppExecutionResult.text(
                "❌ 未知的 session 命令: `" + action + "`\n\n" +
                "可用命令：`status`, `list`, `continue` 或简写 `sc <id>`");
        };
    }

    private AppExecutionResult handleSessionContinue(String[] parts, Message message) {
        if (parts.length < 4) {
            return AppExecutionResult.text(
                "❌ 用法：`/opencode session continue <session_id>`\n\n" +
                "或使用简写：`/opencode sc <session_id>`");
        }
        String sessionId = parts[3].trim();
        String project = parts.length >= 5 ? parts[4].trim() : null;
        if (project == null || project.isEmpty()) {
            return taskExecutor.executeWithSpecificSession(message, null, sessionId);
        }
        return taskExecutor.executeWithSpecificSession(message, null, sessionId, project);
    }
}

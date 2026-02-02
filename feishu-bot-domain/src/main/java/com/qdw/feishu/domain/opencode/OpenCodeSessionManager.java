package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OpenCode 会话管理器
 *
 * 负责会话的创建、查询、状态管理和绑定
 */
@Slf4j
@Component
public class OpenCodeSessionManager {

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeSessionGateway sessionGateway;

    public OpenCodeSessionManager(OpenCodeGateway openCodeGateway,
                                   OpenCodeSessionGateway sessionGateway) {
        this.openCodeGateway = openCodeGateway;
        this.sessionGateway = sessionGateway;
    }

    /**
     * 检查话题是否已初始化（绑定了会话）
     */
    public boolean isTopicInitialized(Message message) {
        String topicId = message.getTopicId();
        if (topicId == null || topicId.isEmpty()) {
            return false;
        }
        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);
        return sessionIdOpt.isPresent();
    }

    /**
     * 获取当前会话状态信息
     */
    public String getCurrentSessionStatus(Message message) {
        String topicId = message.getTopicId();

        if (topicId == null || topicId.isEmpty()) {
            return "❌ 当前不在话题中，无法查看会话状态";
        }

        Optional<String> sessionIdOpt = sessionGateway.getSessionId(topicId);

        if (sessionIdOpt.isEmpty()) {
            return "📭 当前话题还没有 OpenCode 会话\n\n" +
                   "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        String sessionId = sessionIdOpt.get();
        return "📋 **当前会话信息**\n\n" +
               "  🆔 Session ID: `" + sessionId + "`\n" +
               "  💬 话题 ID: `" + topicId + "`\n" +
               "  ✅ 状态: 活跃\n\n" +
               "💡 继续对话会自动使用此会话";
    }

    /**
     * 处理会话列表命令
     */
    public String handleListSessions() {
        return openCodeGateway.listSessions();
    }

    /**
     * 处理项目会话查询命令
     *
     * @param parts 命令解析结果
     * @return 命令响应
     */
    public String handleSessionsCommand(String[] parts) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode sessions <项目名称>`\n\n" +
                   "示例：`/opencode sessions my-project`\n\n" +
                   "💡 提示：\n" +
                   " - 使用 `/opencode projects` 查看所有项目\n" +
                   " - 项目名称支持部分匹配（不区分大小写）";
        }

        String project = parts[2].trim();
        int limit = 5;

        if (parts.length >= 4) {
            try {
                limit = Integer.parseInt(parts[3].trim());
                if (limit < 1 || limit > 20) {
                    return "❌ 数量必须在 1-20 之间";
                }
            } catch (NumberFormatException e) {
                // 忽略，使用默认值
            }
        }

        log.info("查询项目会话: project={}, limit={}", project, limit);
        return openCodeGateway.listRecentSessions(project, limit);
    }

    /**
     * 保存会话到话题映射
     */
    public void saveSession(String topicId, String sessionId) {
        if (topicId != null && !topicId.isEmpty()) {
            sessionGateway.saveSession(topicId, sessionId);
            log.info("已更新会话映射: topicId={}, sessionId={}", topicId, sessionId);
        }
    }

    /**
     * 清除话题的会话映射
     */
    public void clearSession(String topicId) {
        if (topicId != null && !topicId.isEmpty()) {
            sessionGateway.clearSession(topicId);
            log.info("已清除旧会话: topicId={}", topicId);
        }
    }

    /**
     * 获取话题绑定的会话 ID
     */
    public Optional<String> getSessionId(String topicId) {
        return sessionGateway.getSessionId(topicId);
    }
}

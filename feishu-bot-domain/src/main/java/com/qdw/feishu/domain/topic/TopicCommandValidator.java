package com.qdw.feishu.domain.topic;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.command.ValidationResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.session.ContextSessionState;
import lombok.extern.slf4j.Slf4j;

/**
 * 话题命令验证器
 *
 * 提供通用的命令验证方法，支持根据话题状态限制可用命令
 */
@Slf4j
public class TopicCommandValidator {

    /**
     * 检测话题状态
     *
     * @param message 消息对象
     * @param app 应用实例
     * @return 话题状态
     */
    public ContextSessionState detectState(Message message, FishuAppI app) {
        String topicId = message.getTopicId();

        // 非话题
        if (topicId == null || topicId.isEmpty()) {
            log.debug("检测到非话题消息");
            return ContextSessionState.UNBOUND;
        }

        // 检测是否已初始化
        boolean initialized = app.isTopicInitialized(message);
        log.debug("话题状态检测: topicId={}, initialized={}", topicId, initialized);
        return initialized ? ContextSessionState.IN_APP_WITH_SESSION : ContextSessionState.IN_APP_NO_SESSION;
    }

    /**
     * 验证命令是否允许
     *
     * @param subCommand 子命令
     * @param state 话题状态
     * @param whitelist 命令白名单
     * @return 验证结果
     */
    public ValidationResult validateCommand(String subCommand, ContextSessionState state, CommandWhitelist whitelist) {
        if (whitelist == null) {
            // null 白名单表示允许所有命令
            return ValidationResult.allowed();
        }

        boolean allowed = whitelist.isCommandAllowed(subCommand, state);

        if (allowed) {
            return ValidationResult.allowed();
        } else {
            String message = buildRestrictedMessage(state, subCommand);
            return ValidationResult.restricted(message);
        }
    }

    /**
     * 获取受限命令的提示消息
     *
     * @param state 话题状态
     * @param appId 应用ID
     * @param command 命令
     * @return 提示消息
     */
    public String getRestrictedCommandMessage(ContextSessionState state, String appId, String command) {
        return buildRestrictedMessage(state, command);
    }

    private String buildRestrictedMessage(ContextSessionState state, String command) {
        return switch (state) {
            case UNBOUND -> String.format(
                "⚠️ 命令 `%s` 需要在话题中操作\n\n" +
                "💡 在群聊中，你可以：\n" +
                " - 使用 `/oc cn <问题>` 快速创建话题并对话（推荐）\n" +
                " - 使用 `/oc new <项目> <问题>` 在指定项目创建话题\n" +
                " - 或进入已有话题后直接输入问题\n\n" +
                "📋 其他可用命令：\n" +
                " - `/oc connect` 查看连接状态\n" +
                " - `/oc projects` 查看项目列表\n" +
                " - `/oc help` 查看完整帮助",
                command
            );

            case IN_APP_NO_SESSION -> String.format(
                "⚠️ 命令 `%s` 需要话题已初始化\n\n" +
                "💡 请先初始化话题：\n" +
                " - `/oc sc <会话ID>` - 绑定已有会话\n" +
                " - `/oc session list` - 查看所有可用的 session\n" +
                " - `/oc cn <问题>` - 快速创建新会话并对话\n\n" +
                "💡 初始化后即可使用此命令",
                command
            );

            case IN_APP_WITH_SESSION -> String.format(
                "⚠️ 命令 `%s` 不可用",
                command
            );

            default -> String.format("⚠️ 命令 `%s` 不可用", command);
        };
    }
}

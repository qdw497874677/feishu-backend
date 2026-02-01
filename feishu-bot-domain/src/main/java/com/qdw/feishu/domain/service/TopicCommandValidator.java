package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.CommandWhitelist;
import com.qdw.feishu.domain.model.TopicState;
import com.qdw.feishu.domain.model.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 话题命令验证器
 * 
 * 提供通用的命令验证方法，支持根据话题状态限制可用命令
 */
@Slf4j
@Service
public class TopicCommandValidator {

    /**
     * 检测话题状态
     * 
     * @param message 消息对象
     * @param app 应用实例
     * @return 话题状态
     */
    public TopicState detectState(Message message, FishuAppI app) {
        String topicId = message.getTopicId();
        
        // 非话题
        if (topicId == null || topicId.isEmpty()) {
            log.debug("检测到非话题消息");
            return TopicState.NON_TOPIC;
        }
        
        // 检测是否已初始化
        boolean initialized = app.isTopicInitialized(message);
        log.debug("话题状态检测: topicId={}, initialized={}", topicId, initialized);
        return initialized ? TopicState.INITIALIZED : TopicState.UNINITIALIZED;
    }

    /**
     * 验证命令是否允许
     * 
     * @param subCommand 子命令
     * @param state 话题状态
     * @param whitelist 命令白名单
     * @return 验证结果
     */
    public ValidationResult validateCommand(String subCommand, TopicState state, CommandWhitelist whitelist) {
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
    public String getRestrictedCommandMessage(TopicState state, String appId, String command) {
        return buildRestrictedMessage(state, command);
    }

    private String buildRestrictedMessage(TopicState state, String command) {
        return switch (state) {
            case NON_TOPIC -> String.format(
                "⚠️ 命令 `/%s` 只能在话题中使用\n\n" +
                "💡 在非话题中，你可以：\n" +
                " - 使用 `/%s connect` 查看健康信息、帮助和项目列表\n" +
                " - 使用 `/%s help` 查看完整帮助\n" +
                " - 使用 `/%s projects` 查看近期项目\n\n" +
                "💡 如需使用此命令，请先发送 `/%s <内容>` 创建话题",
                command, "opencode", "opencode", "opencode", "opencode"
            );
            
            case UNINITIALIZED -> String.format(
                "⚠️ 命令 `/%s` 需要话题已初始化\n\n" +
                "💡 请先初始化话题：\n" +
                " - `/%s session list` - 查看所有可用的 session\n" +
                " - `/%s session continue <id>` - 绑定指定 session\n\n" +
                "💡 初始化后即可使用此命令",
                command, "opencode", "opencode"
            );
            
            case INITIALIZED -> String.format(
                "⚠️ 命令 `/%s` 不可用",
                command
            );
        };
    }
}

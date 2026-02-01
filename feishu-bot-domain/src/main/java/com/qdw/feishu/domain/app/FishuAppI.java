package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.CommandWhitelist;
import com.qdw.feishu.domain.model.TopicState;
import java.util.Collections;
import java.util.List;

public interface FishuAppI {

    String getAppId();

    String getAppName();

    String getDescription();

    default String getHelp() {
        return "用法：" + getTriggerCommand();
    }

    String execute(Message message);

    default String getTriggerCommand() {
        return "/" + getAppId();
    }

    default ReplyMode getReplyMode() {
        return ReplyMode.DEFAULT;
    }

    /**
     * 获取应用的命令别名列表
     *
     * @return 别名列表，默认为空列表
     */
    default List<String> getAppAliases() {
        return Collections.emptyList();
    }

    /**
     * 获取所有可以触发此应用的命令（包括主命令和别名）
     *
     * @return 命令列表，格式：["/bash", "/cmd", "/shell"]
     */
    default List<String> getAllTriggerCommands() {
        List<String> commands = new java.util.ArrayList<>();
        commands.add(getTriggerCommand());
        getAppAliases().forEach(alias -> commands.add("/" + alias));
        return commands;
    }

    /**
     * 获取命令白名单（可选实现）
     *
     * 每个应用可以根据话题状态定义允许的命令集合。
     * 返回 null 表示允许所有命令（默认行为，向后兼容）。
     *
     * @param state 话题状态
     * @return 允许的命令集合，null 表示允许所有命令
     */
    default CommandWhitelist getCommandWhitelist(TopicState state) {
        return null;
    }

    /**
     * 检测话题是否已初始化（可选实现）
     *
     * 每个应用可以定义自己的"初始化"含义：
     * - OpenCode：已绑定 session（通过 sessionGateway.getSessionId(topicId) 检测）
     * - 其他应用：可能完成配置向导、设置参数、创建上下文等
     *
     * 默认实现返回 true（视为已初始化），确保向后兼容。
     *
     * @param message 消息对象
     * @return true 表示已初始化，false 表示未初始化
     */
    default boolean isTopicInitialized(Message message) {
        return true;
    }
}

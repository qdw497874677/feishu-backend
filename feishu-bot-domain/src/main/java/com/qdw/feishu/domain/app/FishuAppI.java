package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
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
}

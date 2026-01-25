package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;

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
}

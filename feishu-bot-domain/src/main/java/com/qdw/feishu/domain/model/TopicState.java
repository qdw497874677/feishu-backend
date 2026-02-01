package com.qdw.feishu.domain.model;

/**
 * 话题状态枚举
 * 
 * 用于表示对话所在的话题状态，支持分层命令限制
 */
public enum TopicState {

    /** 非话题（独立消息，不在任何话题中） */
    NON_TOPIC("非话题"),

    /** 话题未初始化（应用特定的初始化状态，如未绑定 session） */
    UNINITIALIZED("未初始化"),

    /** 话题已初始化（应用就绪，可以执行所有操作） */
    INITIALIZED("已初始化");

    private final String description;

    TopicState(String description) {
        this.description = description;
    }

    /**
     * 获取状态描述
     * 
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}

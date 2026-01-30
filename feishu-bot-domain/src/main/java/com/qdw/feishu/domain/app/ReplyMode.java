package com.qdw.feishu.domain.app;

/**
 * 回复模式枚举
 */
public enum ReplyMode {

    DEFAULT,  // 默认回复模式
    DIRECT,   // 直接回复模式（不创建话题）
    TOPIC     // 话题回复模式（创建或使用话题）
}

package com.qdw.feishu.domain.message;

/**
 * 消息状态枚举
 */
public enum MessageStatus {
    RECEIVED,    // 已接收
    PROCESSING,  // 处理中
    PROCESSED,   // 已处理
    FAILED        // 失败
}

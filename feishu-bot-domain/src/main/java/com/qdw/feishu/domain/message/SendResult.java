package com.qdw.feishu.domain.message;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发送结果值对象
 */
@Data
@AllArgsConstructor
public class SendResult {

    private boolean success;
    private String messageId;
    private String errorMessage;
    private String threadId;

    public static SendResult success(String messageId) {
        return new SendResult(true, messageId, null, null);
    }

    public static SendResult success(String messageId, String threadId) {
        return new SendResult(true, messageId, null, threadId);
    }

    public static SendResult failure(String errorMessage) {
        return new SendResult(false, null, errorMessage, null);
    }
}

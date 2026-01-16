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

    public static SendResult success(String messageId) {
        return new SendResult(true, messageId, null);
    }

    public static SendResult failure(String errorMessage) {
        return new SendResult(false, null, errorMessage);
    }
}

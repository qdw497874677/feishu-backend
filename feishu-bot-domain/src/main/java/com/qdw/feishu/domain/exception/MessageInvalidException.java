package com.qdw.feishu.domain.exception;

public class MessageInvalidException extends MessageBizException {
    public MessageInvalidException(String message) {
        super("MESSAGE_INVALID", message);
    }
}

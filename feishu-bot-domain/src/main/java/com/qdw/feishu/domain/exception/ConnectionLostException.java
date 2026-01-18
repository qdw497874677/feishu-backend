package com.qdw.feishu.domain.exception;

public class ConnectionLostException extends MessageSysException {
    public ConnectionLostException(String message) {
        super("CONNECTION_LOST", message);
    }

    public ConnectionLostException(String message, Throwable cause) {
        super("CONNECTION_LOST", message, cause);
    }
}

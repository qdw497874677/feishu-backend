package com.qdw.feishu.domain.exception;

import com.alibaba.cola.exception.SysException;

/**
 * 消息系统异常
 */
public class MessageSysException extends SysException {
    public MessageSysException(String errCode, String errMsg) {
        super(errCode, errMsg);
    }

    public MessageSysException(String errCode, String errMsg, Throwable cause) {
        super(errCode, errMsg, cause);
    }
}

package com.qdw.feishu.domain.exception;

import com.alibaba.cola.exception.BizException;

/**
 * 消息业务异常
 */
public class MessageBizException extends BizException {
    public MessageBizException(String errCode, String errMsg) {
        super(errCode, errMsg);
    }

public MessageBizException(String errMsg) {
    super("MESSAGE_BIZ_ERROR", errMsg);
}
}

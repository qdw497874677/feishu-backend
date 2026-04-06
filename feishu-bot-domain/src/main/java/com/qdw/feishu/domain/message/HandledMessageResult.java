package com.qdw.feishu.domain.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HandledMessageResult {

    private final SendResult sendResult;
    private final String appId;
    private final String replyContent;
}

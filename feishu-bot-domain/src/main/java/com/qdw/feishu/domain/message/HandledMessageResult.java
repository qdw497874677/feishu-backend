package com.qdw.feishu.domain.message;

import com.qdw.feishu.domain.app.AppExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HandledMessageResult {

    private final SendResult sendResult;
    private final String appId;
    private final AppExecutionResult executionResult;

    /**
     * Convenience delegate: returns the reply content from the execution result.
     *
     * @return reply content string, or null if no execution result or no reply
     */
    public String getReplyContent() {
        return executionResult != null ? executionResult.getReplyContent() : null;
    }
}

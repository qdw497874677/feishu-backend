package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.result.BizResult;

public interface ResponseAdapter {
    void respond(UnifiedCommand command, BizResult result);
    boolean supports(EventSource source, UnifiedCommand command);
}

package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.UnifiedCommand;

public interface CommandAdapter {
    UnifiedCommand adapt(Object event);
    boolean supports(Object event);
}

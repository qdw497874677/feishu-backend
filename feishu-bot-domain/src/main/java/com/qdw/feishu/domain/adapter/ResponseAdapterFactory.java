package com.qdw.feishu.domain.adapter;

import com.qdw.feishu.domain.command.EventSource;
import com.qdw.feishu.domain.command.UnifiedCommand;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ResponseAdapterFactory {
    private final List<ResponseAdapter> adapters;
    
    public ResponseAdapterFactory(List<ResponseAdapter> adapters) {
        this.adapters = adapters;
        log.info("ResponseAdapterFactory initialized with {} adapters", adapters.size());
    }
    
    public ResponseAdapter getAdapter(EventSource source, UnifiedCommand command) {
        return adapters.stream()
            .filter(a -> a.supports(source, command))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported response type: " + source));
    }
}

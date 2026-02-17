package com.qdw.feishu.domain.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class CommandAdapterFactory {
    private final List<CommandAdapter> adapters;
    
    public CommandAdapterFactory(List<CommandAdapter> adapters) {
        this.adapters = adapters;
        log.info("CommandAdapterFactory initialized with {} adapters", adapters.size());
    }
    
    public CommandAdapter getAdapter(Object event) {
        return adapters.stream()
            .filter(a -> a.supports(event))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported event type: " + event.getClass().getSimpleName()));
    }
}

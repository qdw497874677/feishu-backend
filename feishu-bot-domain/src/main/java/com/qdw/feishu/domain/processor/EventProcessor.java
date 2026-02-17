package com.qdw.feishu.domain.processor;

import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.adapter.CommandAdapterFactory;
import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.adapter.ResponseAdapterFactory;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.result.BizResult;
import com.qdw.feishu.domain.router.CommandRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventProcessor {
    private final CommandAdapterFactory commandAdapterFactory;
    private final CommandRouter commandRouter;
    private final ResponseAdapterFactory responseAdapterFactory;
    
    public EventProcessor(CommandAdapterFactory commandAdapterFactory,
                         CommandRouter commandRouter,
                         ResponseAdapterFactory responseAdapterFactory) {
        this.commandAdapterFactory = commandAdapterFactory;
        this.commandRouter = commandRouter;
        this.responseAdapterFactory = responseAdapterFactory;
        log.info("EventProcessor initialized");
    }
    
    public void process(Object event) {
        log.info("=== EventProcessor.process 开始 ===");
        
        try {
            CommandAdapter adapter = commandAdapterFactory.getAdapter(event);
            UnifiedCommand command = adapter.adapt(event);
            log.debug("Command adapted: appId={}, source={}", 
                command.getAppId(), command.getSource());
            
            BizResult result = commandRouter.route(command);
            log.debug("Command routed, result: success={}", result.isSuccess());
            
            ResponseAdapter responseAdapter = responseAdapterFactory.getAdapter(
                command.getSource(), command);
            responseAdapter.respond(command, result);
            
            log.info("=== EventProcessor.process 完成 ===");
            
        } catch (Exception e) {
            log.error("Event processing failed", e);
            throw e;
        }
    }
}

package com.qdw.feishu.domain.router;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnifiedCommandRouter {
    private final AppRegistry appRegistry;
    
    public UnifiedCommandRouter(AppRegistry appRegistry) {
        this.appRegistry = appRegistry;
        log.info("UnifiedCommandRouter initialized");
    }
    
    public BizResult route(UnifiedCommand command) {
        log.info("Routing command: appId={}, subCommand={}", 
            command.getAppId(), command.getSubCommand());
        
        return appRegistry.getApp(command.getAppId())
            .map(app -> executeApp(app, command))
            .orElse(BizResult.failure("未知应用: " + command.getAppId() + 
                "\n\n可用应用:\n" + appRegistry.getAppHelp()));
    }
    
    private BizResult executeApp(FishuAppI app, UnifiedCommand command) {
        try {
            log.debug("Executing app: {}", app.getAppId());
            // 先使用旧接口兼容，后续迁移应用后切换
            return app.execute(command);
        } catch (Exception e) {
            log.error("App execution failed: {}", app.getAppId(), e);
            return BizResult.failure("应用执行失败: " + e.getMessage());
        }
    }
}

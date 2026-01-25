package com.qdw.feishu.domain.router;

import com.qdw.feishu.domain.app.AppRegistry;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppRouter {

    private final AppRegistry appRegistry;

    public AppRouter(AppRegistry appRegistry) {
        log.info("=== AppRouter 初始化开始 ===");
        this.appRegistry = appRegistry;
        log.info("AppRouter 初始化完成，已连接到 AppRegistry");
        log.info("=== AppRouter 初始化完成 ===\n");
    }

    public String route(Message message) {
        log.debug("AppRouter 收到消息路由请求: {}", message.getContent());

        String content = message.getContent().trim();

        if (!content.startsWith("/")) {
            log.debug("消息不是命令格式，返回 null");
            return null;
        }

        String appId = extractAppId(content);
        log.debug("提取的应用 ID: {}", appId);

        return appRegistry.getApp(appId)
            .map(app -> {
                try {
                    String result = app.execute(message);
                    log.info("应用 {} 执行成功", appId);
                    return result;
                } catch (Exception e) {
                    log.error("应用 {} 执行失败", appId, e);
                    return "应用执行失败: " + e.getMessage();
                }
            })
            .orElse("未知应用: " + appId +
                "\n\n可用应用:\n" + appRegistry.getAppHelp());
    }

    private String extractAppId(String content) {
        String[] parts = content.split("\\s+", 2);
        return parts[0].substring(1).toLowerCase();
    }
}

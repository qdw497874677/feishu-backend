package com.qdw.feishu.domain.core;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.config.FeishuReplyProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AppRegistry {

    private final Map<String, FishuAppI> apps;
    private final FeishuReplyProperties replyProperties;

    public AppRegistry(List<FishuAppI> allApps, FeishuReplyProperties replyProperties) {
        log.info("=== AppRegistry 初始化开始 ===");
        log.info("检测到的应用类数量: {}", allApps.size());
        allApps.forEach(app -> log.info("  - {} ({})", app.getAppName(), app.getAppId()));

        this.apps = allApps.stream()
            .collect(Collectors.toMap(
                FishuAppI::getAppId,
                app -> app,
                (existing, replacement) -> existing
            ));

        this.replyProperties = replyProperties;

        log.info("应用注册完成，共注册 {} 个应用: {}",
            apps.size(), apps.keySet());
        log.info("=== AppRegistry 初始化完成 ===\n");
    }

    public Optional<FishuAppI> getApp(String appId) {
        return Optional.ofNullable(apps.get(appId));
    }

    public List<FishuAppI> getAllApps() {
        return new ArrayList<>(apps.values());
    }

    public String getAppHelp() {
        return apps.values().stream()
            .map(app -> String.format("%s - %s",
                app.getTriggerCommand(), app.getDescription()))
            .collect(Collectors.joining("\n"));
    }

    public ReplyMode getDefaultReplyMode() {
        return replyProperties != null ? replyProperties.getMode() : ReplyMode.DEFAULT;
    }
}

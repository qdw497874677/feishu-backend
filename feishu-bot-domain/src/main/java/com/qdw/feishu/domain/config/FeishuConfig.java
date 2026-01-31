package com.qdw.feishu.domain.config;

/**
 * 飞书配置接口（Domain 层）
 *
 * 定义应用需要的配置方法，由 infrastructure 层实现。
 * 遵循依赖倒置原则：domain 定义接口，infrastructure 实现接口。
 */
public interface FeishuConfig {

    String getAppId();

    String getAppSecret();

    String getMode();

    boolean isListenerEnabled();

    ListenerConfig getListener();

    interface ListenerConfig {
        boolean isEnabled();
    }
}

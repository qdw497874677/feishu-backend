package com.qdw.feishu.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {

    /**
     * 飞书应用 ID
     */
    private String appid;

    /**
     * 飞书应用 Secret
     */
    private String appsecret;

    /**
     * 加密 Key
     */
    private String encryptKey;

    /**
     * 验证 Token
     */
    private String verificationToken;

    /**
     * 运行模式
     */
    private String mode = "webhook";

    /**
     * 长连接监听器配置
     */
    private Listener listener = new Listener();

    @Data
    public static class Listener {
        private boolean enabled = false;
    }
}

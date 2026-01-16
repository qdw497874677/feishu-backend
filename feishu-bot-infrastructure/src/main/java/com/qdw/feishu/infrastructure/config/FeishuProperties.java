package com.qdw.feishu.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 飞书配置属性
 */
@Data
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
}

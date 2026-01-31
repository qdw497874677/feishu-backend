package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.config.FeishuConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties implements FeishuConfig {

    /**
     * 飞书应用 ID
     */
    private String appId;

    /**
     * 飞书应用 Secret
     */
    private String appSecret;

    /**
     * 加密 Key (注意：getter 方法名为 getEncryptKey，匹配字段 encryptKey)
     */
    private String encryptKey;

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

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

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isListenerEnabled() {
        return listener != null && listener.isEnabled();
    }

    public void setListenerEnabled(boolean enabled) {
        if (this.listener == null) {
            this.listener = new Listener();
        }
        this.listener.setEnabled(enabled);
    }

    public static class Listener implements ListenerConfig {
        private boolean enabled = false;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

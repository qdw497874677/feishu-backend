package com.qdw.feishu.domain.config;

import com.qdw.feishu.domain.app.ReplyMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书回复配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "feishu.reply")
public class FeishuReplyProperties {

    /**
     * 回复模式
     */
    private ReplyMode mode = ReplyMode.DEFAULT;
}

package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.config.FeishuReplyProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Infrastructure-side configuration properties for Feishu reply settings.
 * Extends the domain POJO and adds Spring @ConfigurationProperties binding.
 */
@Component
@ConfigurationProperties(prefix = "feishu.reply")
public class FeishuReplyPropertiesImpl extends FeishuReplyProperties {
}

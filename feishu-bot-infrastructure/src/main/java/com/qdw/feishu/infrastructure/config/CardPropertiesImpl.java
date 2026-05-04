package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.config.CardProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Infrastructure-side configuration properties for card streaming settings.
 * Extends the domain POJO and adds Spring @ConfigurationProperties binding.
 */
@Component
@ConfigurationProperties(prefix = "opencode.card")
public class CardPropertiesImpl extends CardProperties {
}

package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.service.BotMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public BotMessageService botMessageService(FeishuGateway feishuGateway) {
        return new BotMessageService(feishuGateway);
    }
}

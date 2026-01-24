package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.router.CommandRouter;
import com.qdw.feishu.domain.service.BotMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public BotMessageService botMessageService(FeishuGateway feishuGateway, CommandRouter commandRouter) {
        return new BotMessageService(feishuGateway, commandRouter);
    }
}

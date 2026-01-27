package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.app.AppRegistry;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.router.AppRouter;
import com.qdw.feishu.domain.service.BotMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public BotMessageService botMessageService(FeishuGateway feishuGateway, AppRouter appRouter, AppRegistry appRegistry, TopicMappingGateway topicMappingGateway) {
        return new BotMessageService(feishuGateway, appRouter, appRegistry, topicMappingGateway);
    }
}

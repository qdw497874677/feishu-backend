package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.router.AppRouter;
import com.qdw.feishu.domain.service.BotMessageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DomainServiceConfig {

    @Bean
    public ReplyStrategyFactory replyStrategyFactory(List<ReplyStrategy> strategies) {
        return new ReplyStrategyFactory(strategies);
    }

    @Bean
    public BotMessageService botMessageService(FeishuGateway feishuGateway, 
                                              AppRouter appRouter, 
                                              AppRegistry appRegistry, 
                                              TopicMappingGateway topicMappingGateway,
                                              ReplyStrategyFactory replyStrategyFactory) {
        return new BotMessageService(feishuGateway, appRouter, appRegistry, topicMappingGateway, replyStrategyFactory);
    }
}

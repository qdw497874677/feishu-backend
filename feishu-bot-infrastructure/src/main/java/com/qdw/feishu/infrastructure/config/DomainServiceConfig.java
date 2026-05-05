package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.adapter.CommandAdapter;
import com.qdw.feishu.domain.adapter.CommandAdapterFactory;
import com.qdw.feishu.domain.adapter.ResponseAdapter;
import com.qdw.feishu.domain.adapter.ResponseAdapterFactory;
import com.qdw.feishu.domain.app.*;
import com.qdw.feishu.domain.card.StreamingCardManager;
import com.qdw.feishu.domain.command.CommandWhitelistValidator;
import com.qdw.feishu.domain.config.FeishuReplyProperties;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.gateway.*;
import com.qdw.feishu.domain.history.BashHistoryManager;
import com.qdw.feishu.domain.opencode.*;
import com.qdw.feishu.domain.processor.EventProcessor;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.router.AppRouter;
import com.qdw.feishu.domain.router.UnifiedCommandRouter;
import com.qdw.feishu.domain.service.BotMessageService;
import com.qdw.feishu.domain.service.MessageDeduplicator;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * Domain Service Configuration.
 *
 * Registers all domain-layer beans since domain module has no Spring dependency.
 * Each domain class is constructed here via @Bean factory methods.
 */
@Configuration
public class DomainServiceConfig {

    // ============ Config Properties ============
    // FeishuReplyProperties and CardProperties are registered via @Component
    // on FeishuReplyPropertiesImpl / CardPropertiesImpl — no @Bean needed here.

    // ============ Core ============

    @Bean
    public AppRegistry appRegistry(List<FishuAppI> apps, FeishuReplyProperties replyProperties) {
        return new AppRegistry(apps, replyProperties);
    }

    // ============ Services ============

    @Bean
    public BotMessageService botMessageService(AppRegistry appRegistry,
                                                ImContextBindingGateway bindingGateway) {
        return new BotMessageService(appRegistry, bindingGateway);
    }

    @Bean
    public MessageDeduplicator messageDeduplicator() {
        return new MessageDeduplicator();
    }

    @Bean
    public TopicCommandValidator topicCommandValidator() {
        return new TopicCommandValidator();
    }

    @Bean
    public CommandWhitelistValidator commandWhitelistValidator() {
        return new CommandWhitelistValidator();
    }

    // ============ Reply Strategy ============

    @Bean
    public ReplyStrategyFactory replyStrategyFactory(List<ReplyStrategy> strategies) {
        return new ReplyStrategyFactory(strategies);
    }

    // ============ Routers ============

    @Bean
    public AppRouter appRouter(AppRegistry appRegistry) {
        return new AppRouter(appRegistry);
    }

    @Bean
    public UnifiedCommandRouter unifiedCommandRouter(AppRegistry appRegistry) {
        return new UnifiedCommandRouter(appRegistry);
    }

    // ============ Adapters ============

    @Bean
    public CommandAdapterFactory commandAdapterFactory(List<CommandAdapter> adapters) {
        return new CommandAdapterFactory(adapters);
    }

    @Bean
    public ResponseAdapterFactory responseAdapterFactory(List<ResponseAdapter> adapters) {
        return new ResponseAdapterFactory(adapters);
    }

    // ============ Processor ============

    @Bean
    public EventProcessor eventProcessor(CommandAdapterFactory commandAdapterFactory,
                                          UnifiedCommandRouter commandRouter,
                                          ResponseAdapterFactory responseAdapterFactory) {
        return new EventProcessor(commandAdapterFactory, commandRouter, responseAdapterFactory);
    }

    // ============ History ============

    @Bean
    public BashHistoryManager bashHistoryManager() {
        return new BashHistoryManager();
    }

    // ============ Apps ============

    @Bean
    public BashApp bashApp(CommandWhitelistValidator validator,
                            BashHistoryManager historyManager,
                            FeishuGateway feishuGateway) {
        return new BashApp(validator, historyManager, feishuGateway);
    }

    @Bean
    public HelpApp helpApp(@Lazy AppRegistry appRegistry,
                            FeishuGateway feishuGateway,
                            CardRenderer cardRenderer) {
        return new HelpApp(appRegistry, feishuGateway, cardRenderer);
    }

    @Bean
    public HistoryApp historyApp(FeishuGateway feishuGateway) {
        return new HistoryApp(feishuGateway);
    }

    @Bean
    public TimeApp timeApp() {
        return new TimeApp();
    }

    // ============ OpenCode ============

    @Bean
    public OpenCodeResponseFormatter openCodeResponseFormatter(ObjectMapper objectMapper) {
        return new OpenCodeResponseFormatter(objectMapper);
    }

    @Bean
    public OpenCodeMessageFormatter openCodeMessageFormatter() {
        return new OpenCodeMessageFormatter();
    }

    @Bean
    public NextStepSuggester nextStepSuggester() {
        return new NextStepSuggester();
    }

    @Bean
    public OpenCodeSessionManager openCodeSessionManager(OpenCodeGateway openCodeGateway,
                                                          AppSessionGateway appSessionGateway,
                                                          ImContextBindingGateway bindingGateway) {
        return new OpenCodeSessionManager(openCodeGateway, appSessionGateway, bindingGateway);
    }

    @Bean
    public StreamingCardManager streamingCardManager(CardGateway cardGateway,
                                                      com.qdw.feishu.domain.config.CardProperties cardProperties) {
        return new StreamingCardManager(cardGateway, cardProperties);
    }

    @Bean
    public OpenCodeStreamingHandler openCodeStreamingHandler(FeishuGateway feishuGateway,
                                                              StreamingCardManager cardManager,
                                                              com.qdw.feishu.domain.config.CardProperties cardProperties) {
        return new OpenCodeStreamingHandler(feishuGateway, cardManager, cardProperties);
    }

    @Bean
    public OpenCodeTaskExecutor openCodeTaskExecutor(OpenCodeGateway openCodeGateway,
                                                      FeishuGateway feishuGateway,
                                                      OpenCodeResponseFormatter responseFormatter,
                                                      OpenCodeSessionManager sessionManager,
                                                      OpenCodeStreamingHandler streamingHandler) {
        return new OpenCodeTaskExecutor(openCodeGateway, feishuGateway, responseFormatter, sessionManager, streamingHandler);
    }

    @Bean
    public WizardManager wizardManager(OpenCodeGateway openCodeGateway,
                                        OpenCodeSessionManager sessionManager,
                                        CardRenderer cardRenderer) {
        return new WizardManager(openCodeGateway, sessionManager, cardRenderer);
    }

    @Bean
    public OpenCodeCommandHandler openCodeCommandHandler(OpenCodeGateway openCodeGateway,
                                                          OpenCodeTaskExecutor taskExecutor,
                                                          OpenCodeSessionManager sessionManager,
                                                          TopicCommandValidator commandValidator,
                                                          NextStepSuggester nextStepSuggester,
                                                          OpenCodeMessageFormatter messageFormatter,
                                                          CardRenderer cardRenderer,
                                                          FeishuGateway feishuGateway,
                                                          WizardManager wizardManager) {
        return new OpenCodeCommandHandler(openCodeGateway, taskExecutor, sessionManager,
            commandValidator, nextStepSuggester, messageFormatter, cardRenderer, feishuGateway, wizardManager);
    }

    @Bean
    public OpenCodeApp openCodeApp(OpenCodeGateway openCodeGateway,
                                    OpenCodeCommandHandler commandHandler,
                                    OpenCodeSessionManager sessionManager) {
        return new OpenCodeApp(openCodeGateway, commandHandler, sessionManager);
    }
}

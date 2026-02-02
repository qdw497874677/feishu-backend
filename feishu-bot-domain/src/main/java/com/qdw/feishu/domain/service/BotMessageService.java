package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.TopicMapping;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.router.AppRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotMessageService {

    private final FeishuGateway feishuGateway;
    private final AppRouter appRouter;
    private final AppRegistry appRegistry;
    private final TopicMappingGateway topicMappingGateway;
    private final ReplyStrategyFactory replyStrategyFactory;

    public BotMessageService(FeishuGateway feishuGateway,
                            AppRouter appRouter,
                            AppRegistry appRegistry,
                            TopicMappingGateway topicMappingGateway,
                            ReplyStrategyFactory replyStrategyFactory) {
        this.feishuGateway = feishuGateway;
        this.appRouter = appRouter;
        this.appRegistry = appRegistry;
        this.topicMappingGateway = topicMappingGateway;
        this.replyStrategyFactory = replyStrategyFactory;
    }

    private String extractAppId(String content) {
        String[] parts = content.split("\\s+", 2);
        String appId = parts[0].substring(1).toLowerCase();
        if (appId.isEmpty()) {
            return null;
        }
        return appId;
    }

    /**
     * 根据命令前缀或别名查找应用
     *
     * @param command 命令前缀（不含 /）
     * @return 找到的应用，如果不存在则返回 null
     */
    private FishuAppI findAppByCommandOrAlias(String command) {
        String commandLower = command.toLowerCase();

        for (FishuAppI app : appRegistry.getAllApps()) {
            if (app.getAppId().equalsIgnoreCase(commandLower)) {
                return app;
            }

            for (String alias : app.getAppAliases()) {
                if (alias.equalsIgnoreCase(commandLower)) {
                    log.info("通过别名找到应用: command={}, alias={}, appId={}",
                            command, alias, app.getAppId());
                    return app;
                }
            }
        }

        return null;
    }

    private void handleUnknownTopic(Message message) {
        String errorReply = "话题已失效，请重新发送命令触发应用。";
        SendResult result = feishuGateway.sendMessage(message, errorReply, null);
        if (!result.isSuccess()) {
            log.warn("Failed to send error reply: {}", result.getErrorMessage());
        }
    }

    private void sendErrorReply(Message message, String error) {
        SendResult result = feishuGateway.sendMessage(message, "错误: " + error, null);
        if (!result.isSuccess()) {
            log.warn("Failed to send error reply: {}", result.getErrorMessage());
        }
    }

    public SendResult handleMessage(Message message) {
        log.info("=== BotMessageService.handleMessage 开始 ===");
        log.info("消息内容: {}", message.getDisplayContent());

        try {
            message.validate();
            log.info("消息验证通过");

            String topicId = message.getTopicId();
            boolean inTopicWithMapping = false;
            FishuAppI app;

            if (topicId != null && !topicId.isEmpty()) {
                log.info("消息来自话题: topicId={}", topicId);
                var mapping = topicMappingGateway.findByTopicId(topicId);
                if (mapping.isPresent()) {
                    TopicMapping topicMapping = mapping.get();
                    String appId = topicMapping.getAppId();
                    log.info("找到话题映射: topicId={}, appId={}", topicId, appId);
                    app = appRegistry.getApp(appId).orElse(null);
                    if (app == null) {
                        log.error("应用不存在: appId={}", appId);
                        sendErrorReply(message, "应用不可用");
                        message.markProcessed();
                        return SendResult.failure("应用不可用");
                    }
                    inTopicWithMapping = true;
                    topicMapping.activate();
                    topicMappingGateway.save(topicMapping);
                } else {
                    log.warn("话题映射不存在: topicId={}，降级为默认处理", topicId);
                    handleUnknownTopic(message);
                    message.markProcessed();
                    return SendResult.failure("话题已失效");
                }
                } else {
                    String content = message.getContent().trim();
                    if (!content.startsWith("/")) {
                        log.info("不是命令，路由到 help 应用");
                        app = appRegistry.getApp("help").orElse(null);
                        if (app == null) {
                            log.warn("未找到帮助应用");
                            message.markProcessed();
                            return SendResult.failure("未找到帮助应用");
                        }
                    } else {
                        String command = extractAppId(content);
                        log.info("检测到命令: {}", command);
                        
                        app = findAppByCommandOrAlias(command);
                        if (app == null) {
                            log.warn("应用不存在: command={}", command);

                            String availableApps = appRegistry.getAllApps().stream()
                                    .flatMap(a -> a.getAllTriggerCommands().stream())
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("无");

                            String errorMessage = String.format(
                                    "❌ 未找到应用: `%s`\n\n" +
                                    "📋 可用应用列表:\n%s\n\n" +
                                    "💡 提示: 请使用正确的命令前缀",
                                    command, availableApps
                            );

                            log.info("发送应用不存在提示: {}", errorMessage);
                            feishuGateway.sendDirectReply(message, errorMessage);

                            message.markProcessed();
                            return SendResult.failure("应用不存在: " + command);
                        }
                        
                        log.info("找到应用: appId={}, appName={}", app.getAppId(), app.getAppName());
                    }
                }

            if (inTopicWithMapping) {
                String content = message.getContent().trim();
                String appId = app.getAppId();
                String expectedPrefix = "/" + appId;
                
                if (content.startsWith(expectedPrefix + " ") || content.equals(expectedPrefix)) {
                    log.info("话题中的消息包含命令前缀，去除前缀: {}", content);
                    if (content.length() > expectedPrefix.length()) {
                        content = content.substring(expectedPrefix.length()).trim();
                    } else {
                        content = "";
                    }
                    message.setContent(content);
                    log.info("话题消息处理后的内容: '{}'", content);
                } else {
                    log.info("话题中的消息不包含前缀，添加前缀: '{}'", content);
                    content = expectedPrefix + " " + content;
                    message.setContent(content);
                    log.info("话题消息处理后的内容: '{}'", content);
                }
            }

            String replyContent = app.execute(message);
            if (replyContent == null || replyContent.isEmpty()) {
                log.warn("应用返回空回复");
                message.markProcessed();
                return SendResult.failure("应用返回空回复");
            }

            // 使用策略模式处理回复
            ReplyMode replyMode = app.getReplyMode();
            ReplyStrategy strategy = replyStrategyFactory.getStrategy(replyMode);
            
            if (strategy == null) {
                log.warn("未找到回复模式 {} 的策略，使用默认策略", replyMode);
                strategy = replyStrategyFactory.getStrategy(ReplyMode.DEFAULT);
            }

            SendResult result = strategy.reply(message, replyContent, topicId);

            if (result.isSuccess()) {
                log.info("发送回复成功: topicId={}", result.getThreadId());

                String actualThreadId = result.getThreadId();
                // 只要返回了 threadId，就应该保存话题映射（无论哪种回复模式）
                if (actualThreadId != null && !actualThreadId.isEmpty()) {
                    log.info("获取到飞书返回的 threadId: {}", actualThreadId);
                    TopicMapping mapping = new TopicMapping(actualThreadId, app.getAppId());
                    topicMappingGateway.save(mapping);
                    log.info("话题映射已保存: topicId={}, appId={}", actualThreadId, app.getAppId());
                }
            } else {
                log.error("发送回复失败: error={}", result.getErrorMessage());
            }

            message.markProcessed();
            log.info("=== BotMessageService.handleMessage 完成 ===\n");

            return result;

        } catch (MessageBizException e) {
            log.error("业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("系统异常: 消息处理失败", e);
            throw new MessageSysException("MESSAGE_HANDLE_FAILED", "消息处理失败", e);
        }
    }
}

package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.ReactionEmoji;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.TopicMapping;
import com.qdw.feishu.domain.reply.ReplyStrategy;
import com.qdw.feishu.domain.reply.ReplyStrategyFactory;
import com.qdw.feishu.domain.router.AppRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class BotMessageService {

    private final FeishuGateway feishuGateway;
    private final AppRouter appRouter;
    private final AppRegistry appRegistry;
    private final TopicMappingGateway topicMappingGateway;
    private final ReplyStrategyFactory replyStrategyFactory;
    private final OpenCodeSessionGateway sessionGateway;

    public BotMessageService(FeishuGateway feishuGateway,
                            AppRouter appRouter,
                            AppRegistry appRegistry,
                            TopicMappingGateway topicMappingGateway,
                            ReplyStrategyFactory replyStrategyFactory,
                            OpenCodeSessionGateway sessionGateway) {
        this.feishuGateway = feishuGateway;
        this.appRouter = appRouter;
        this.appRegistry = appRegistry;
        this.topicMappingGateway = topicMappingGateway;
        this.replyStrategyFactory = replyStrategyFactory;
        this.sessionGateway = sessionGateway;
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
            
            FishuAppI app = resolveApp(message);
            if (app == null) {
                return SendResult.failure("应用不存在");
            }
            
            preprocessContent(message, app);
            addDefaultReaction(message);
            
            String replyContent = app.execute(message);
            if (isEmpty(replyContent)) {
                log.warn("应用返回空回复");
                message.markProcessed();
                return SendResult.failure("应用返回空回复");
            }
            
            SendResult result = sendReply(message, app, replyContent);
            saveTopicMapping(result, app, replyContent);
            
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

    private FishuAppI resolveApp(Message message) {
        String topicId = message.getTopicId();
        
        if (topicId != null && !topicId.isEmpty()) {
            log.info("消息来自话题: topicId={}", topicId);
            return resolveAppFromTopic(message, topicId);
        } else {
            return resolveAppFromCommand(message);
        }
    }

    private FishuAppI resolveAppFromTopic(Message message, String topicId) {
        var mapping = topicMappingGateway.findByTopicId(topicId);
        if (!mapping.isPresent()) {
            log.warn("话题映射不存在: topicId={}，降级为默认处理", topicId);
            handleUnknownTopic(message);
            message.markProcessed();
            return null;
        }
        
        TopicMapping topicMapping = mapping.get();
        String appId = topicMapping.getAppId();
        log.info("找到话题映射: topicId={}, appId={}", topicId, appId);
        
        FishuAppI app = appRegistry.getApp(appId).orElse(null);
        if (app == null) {
            log.error("应用不存在: appId={}", appId);
            sendErrorReply(message, "应用不可用");
            message.markProcessed();
            return null;
        }
        
        topicMapping.activate();
        topicMappingGateway.save(topicMapping);
        return app;
    }

    private FishuAppI resolveAppFromCommand(Message message) {
        String content = message.getContent().trim();
        
        if (!content.startsWith("/")) {
            log.info("不是命令，路由到 help 应用");
            FishuAppI app = appRegistry.getApp("help").orElse(null);
            if (app == null) {
                log.warn("未找到帮助应用");
                message.markProcessed();
                return null;
            }
            return app;
        }
        
        String command = extractAppId(content);
        log.info("检测到命令: {}", command);
        
        FishuAppI app = findAppByCommandOrAlias(command);
        if (app == null) {
            handleUnknownApp(message, command);
            return null;
        }
        
        log.info("找到应用: appId={}, appName={}", app.getAppId(), app.getAppName());
        return app;
    }

    private void handleUnknownApp(Message message, String command) {
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
    }

    private void preprocessContent(Message message, FishuAppI app) {
        String topicId = message.getTopicId();
        
        if (topicId == null || topicId.isEmpty()) {
            return;
        }
        
        var mapping = topicMappingGateway.findByTopicId(topicId);
        if (!mapping.isPresent()) {
            return;
        }
        
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

    private void addDefaultReaction(Message message) {
        boolean success = feishuGateway.addReaction(message.getMessageId(), ReactionEmoji.THUMBSUP);
        if (!success) {
            log.debug("表情添加失败，但不影响主流程");
        }
    }

    private SendResult sendReply(Message message, FishuAppI app, String replyContent) {
        ReplyMode replyMode = app.getReplyMode();
        ReplyStrategy strategy = replyStrategyFactory.getStrategy(replyMode);
        
        if (strategy == null) {
            log.warn("未找到回复模式 {} 的策略，使用默认策略", replyMode);
            strategy = replyStrategyFactory.getStrategy(ReplyMode.DEFAULT);
        }

        String topicId = message.getTopicId();
        SendResult result = strategy.reply(message, replyContent, topicId);
        
        if (result.isSuccess()) {
            log.info("发送回复成功: topicId={}", result.getThreadId());
        } else {
            log.error("发送回复失败: error={}", result.getErrorMessage());
        }
        
        return result;
    }

    private void saveTopicMapping(SendResult result, FishuAppI app, String replyContent) {
        if (!result.isSuccess()) {
            return;
        }
        
        String actualThreadId = result.getThreadId();
        if (actualThreadId == null || actualThreadId.isEmpty()) {
            return;
        }
        
        log.info("获取到飞书返回的 threadId: {}", actualThreadId);
        
        Optional<TopicMapping> existingMapping = topicMappingGateway.findByTopicId(actualThreadId);
        
        TopicMapping mapping;
        if (existingMapping.isPresent()) {
            TopicMapping old = existingMapping.get();
            mapping = new TopicMapping(old.getTopicId(), old.getAppId(), old.getMetadata());
            mapping.setLastActiveAt(System.currentTimeMillis());
            log.debug("话题映射已存在，保留 metadata: topicId={}", actualThreadId);
        } else {
            mapping = new TopicMapping(actualThreadId, app.getAppId());
            log.debug("创建新话题映射: topicId={}", actualThreadId);
        }

        topicMappingGateway.save(mapping);
        log.info("话题映射已保存: topicId={}, appId={}", actualThreadId, app.getAppId());

        if (app.getAppId().equals("opencode") && replyContent.contains("Session ID: `")) {
            extractAndSaveSessionId(replyContent, actualThreadId);
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    private void extractAndSaveSessionId(String replyContent, String topicId) {
        try {
            int startIndex = replyContent.indexOf("Session ID: `");
            if (startIndex == -1) {
                return;
            }

            startIndex += "Session ID: `".length();
            int endIndex = replyContent.indexOf("`", startIndex);
            if (endIndex == -1) {
                return;
            }

            String sessionId = replyContent.substring(startIndex, endIndex);
            log.info("从回复中提取到 sessionID: {}, topicId: {}", sessionId, topicId);

            sessionGateway.saveSession(topicId, sessionId);
            log.info("OpenCode 会话已自动绑定到话题: topicId={}, sessionId={}", topicId, sessionId);
        } catch (Exception e) {
            log.error("提取或保存 sessionID 失败", e);
        }
    }
}

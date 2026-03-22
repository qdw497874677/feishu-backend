package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.ReactionEmoji;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
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
    private final ImContextBindingGateway bindingGateway;
    private final ReplyStrategyFactory replyStrategyFactory;
    private final OpenCodeSessionManager openCodeSessionManager;

    public BotMessageService(FeishuGateway feishuGateway,
                            AppRouter appRouter,
                            AppRegistry appRegistry,
                            ImContextBindingGateway bindingGateway,
                            ReplyStrategyFactory replyStrategyFactory,
                            OpenCodeSessionManager openCodeSessionManager) {
        this.feishuGateway = feishuGateway;
        this.appRouter = appRouter;
        this.appRegistry = appRegistry;
        this.bindingGateway = bindingGateway;
        this.replyStrategyFactory = replyStrategyFactory;
        this.openCodeSessionManager = openCodeSessionManager;
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

    private void handleUnknownContext(Message message) {
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
            saveContextBinding(message, result, app, replyContent);
            
            message.markProcessed();
            log.info("=== BotMessageService.handleMessage 完成 ===\n");
            return result;

        } catch (MessageBizException e) {
            log.error("业务异常: {}", e.getMessage());
            // 发送错误回复
            String errorReply = e.getMessage();
            SendResult result = feishuGateway.sendMessage(message, errorReply, message.getTopicId());
            message.markProcessed();
            return result;
        } catch (Exception e) {
            log.error("系统异常: 消息处理失败", e);
            throw new MessageSysException("MESSAGE_HANDLE_FAILED", "消息处理失败", e);
        }
    }

    private FishuAppI resolveApp(Message message) {
        String topicId = message.getTopicId();
        
        if (topicId != null && !topicId.isEmpty()) {
            log.info("消息来自话题: topicId={}", topicId);
            return resolveAppFromContext(message, topicId);
        } else {
            return resolveAppFromCommand(message);
        }
    }

    private FishuAppI resolveAppFromContext(Message message, String topicId) {
        // 使用 ImContextBinding 统一处理路由
        Optional<ImContextRef> contextRefOpt = resolveContextRef(message);
        
        if (contextRefOpt.isEmpty()) {
            log.warn("无法解析 IM 上下文: topicId={}，降级为默认处理", topicId);
            handleUnknownContext(message);
            message.markProcessed();
            return null;
        }
        
        ImContextRef contextRef = contextRefOpt.get();
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef);
        
        if (bindingOpt.isEmpty()) {
            log.warn("IM 上下文未绑定: contextRef={}，降级为默认处理", contextRef.toStorageKey());
            handleUnknownContext(message);
            message.markProcessed();
            return null;
        }
        
        ImContextBinding binding = bindingOpt.get();
        String appId = binding.getAppId();
        log.info("找到上下文绑定: contextRef={}, appId={}", contextRef.toStorageKey(), appId);
        
        FishuAppI app = appRegistry.getApp(appId).orElse(null);
        if (app == null) {
            log.error("应用不存在: appId={}", appId);
            sendErrorReply(message, "应用不可用");
            message.markProcessed();
            return null;
        }
        
        // 更新绑定活跃时间
        bindingGateway.touchBinding(contextRef);
        return app;
    }
    
    /**
     * 解析消息的 IM 上下文引用
     */
    private Optional<ImContextRef> resolveContextRef(Message message) {
        try {
            return Optional.of(FeishuContextResolver.resolve(message));
        } catch (IllegalArgumentException e) {
            log.debug("无法解析 IM 上下文: {}", e.getMessage());
            return Optional.empty();
        }
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
        Optional<ImContextRef> contextRefOpt = resolveContextRef(message);
        
        if (contextRefOpt.isEmpty()) {
            return;
        }
        
        ImContextRef contextRef = contextRefOpt.get();
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef);
        
        if (bindingOpt.isEmpty()) {
            return;
        }
        
        String content = message.getContent().trim();
        String appId = app.getAppId();
        String expectedPrefix = "/" + appId;
        
        // 检查是否以 / 开头但不是当前应用的前缀
        if (content.startsWith("/") && !content.startsWith(expectedPrefix + " ") && !content.equals(expectedPrefix)) {
            // 提取命令
            String otherCommand = content.split("\\s+", 2)[0].substring(1);
            log.warn("话题中禁止使用其他应用命令: command={}, expectedApp={}", otherCommand, appId);
            
            // 抛出业务异常，阻止继续处理
            String errorReply = String.format(
                "❌ 当前在 **%s** 话题模式中\n\n" +
                "💡 只能使用本应用的命令，不能使用其他应用命令（/%s）\n\n" +
                "📋 可用命令：直接输入子命令（如 `projects`, `sessions`, `chatnow`）\n" +
                "🔄 退出话题：发送新消息到群聊（不回复话题）",
                app.getAppName(), otherCommand
            );
            throw new MessageBizException("TOPIC_COMMAND_NOT_ALLOWED", errorReply);
        }
        
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

    /**
     * 保存上下文绑定
     * 
     * 对于无状态应用（help, time, bash, history）：sessionId = null
     * 对于有状态应用（opencode）：sessionId 由 OpenCodeSessionManager 管理
     */
    private void saveContextBinding(Message message, SendResult result, FishuAppI app, String replyContent) {
        if (!result.isSuccess()) {
            return;
        }
        
        String actualThreadId = result.getThreadId();
        if (actualThreadId == null || actualThreadId.isEmpty()) {
            return;
        }
        
        log.info("获取到飞书返回的 threadId: {}", actualThreadId);
        
        // 解析 IM 上下文
        Optional<ImContextRef> contextRefOpt = resolveContextRef(message);
        if (contextRefOpt.isEmpty()) {
            log.warn("无法解析 IM 上下文，跳过绑定保存");
            return;
        }
        
        ImContextRef contextRef = contextRefOpt.get();
        
        // 检查现有绑定
        Optional<ImContextBinding> existingBinding = bindingGateway.findBinding(contextRef);
        
        if (existingBinding.isPresent()) {
            // 更新现有绑定的活跃时间
            bindingGateway.touchBinding(contextRef);
            log.debug("上下文绑定已存在，更新活跃时间: contextRef={}", contextRef.toStorageKey());
        } else {
            // 创建新绑定（无状态应用 sessionId = null）
            BindingResult bindingResult = bindingGateway.bind(contextRef, app.getAppId(), null);
            log.info("创建新上下文绑定: contextRef={}, appId={}, result={}", 
                    contextRef.toStorageKey(), app.getAppId(), bindingResult);
        }

        // 对于 OpenCode，提取并保存 session ID
        if (app.getAppId().equals("opencode") && replyContent.contains("Session ID: `")) {
            extractAndSaveSessionId(message, replyContent);
        }
    }

    private boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    private void extractAndSaveSessionId(Message message, String replyContent) {
        try {
            String topicId = message.getTopicId();
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

            openCodeSessionManager.saveSession(message, sessionId);
            log.info("OpenCode 会话已自动绑定到话题: topicId={}, sessionId={}", topicId, sessionId);
        } catch (Exception e) {
            log.error("提取或保存 sessionID 失败", e);
        }
    }
}

package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.message.BotRoutingDecision;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class BotMessageService {

    private final AppRegistry appRegistry;
    private final ImContextBindingGateway bindingGateway;

    public BotMessageService(AppRegistry appRegistry,
                            ImContextBindingGateway bindingGateway) {
        this.appRegistry = appRegistry;
        this.bindingGateway = bindingGateway;
    }

    public BotRoutingDecision routeMessage(Message message) {
        message.validate();

        Optional<ImContextRef> contextRefOpt = resolveContextRef(message);
        if (isExplicitCommand(message)) {
            return routeExplicitCommand(message, contextRefOpt);
        }

        return routeImplicitMessage(contextRefOpt);
    }

    private BotRoutingDecision routeExplicitCommand(Message message, Optional<ImContextRef> contextRefOpt) {
        FishuAppI commandApp = resolveAppFromCommandOnly(message);
        if (commandApp == null) {
            return new BotRoutingDecision(null, null, false);
        }

        if (contextRefOpt.isPresent()) {
            Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRefOpt.get());
            if (bindingOpt.isPresent()) {
                ImContextBinding binding = bindingOpt.get();
                if (isCrossAppCommand(binding, commandApp)) {
                    throwCrossAppCommandRejected(commandApp, binding);
                }
            }
        }

        return new BotRoutingDecision(commandApp.getAppId(), commandApp, isSessionAwareApp(commandApp));
    }

    private BotRoutingDecision routeImplicitMessage(Optional<ImContextRef> contextRefOpt) {
        if (contextRefOpt.isEmpty()) {
            return routeToHelp();
        }

        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRefOpt.get());
        if (bindingOpt.isEmpty()) {
            return routeToHelp();
        }

        String appId = bindingOpt.get().getAppId();
        Optional<FishuAppI> appOpt = appRegistry.getApp(appId);
        if (appOpt.isEmpty()) {
            return routeToHelp();
        }

        return new BotRoutingDecision(appId, appOpt.get(), false);
    }

    private BotRoutingDecision routeToHelp() {
        FishuAppI helpApp = appRegistry.getApp("help").orElse(null);
        return new BotRoutingDecision(helpApp != null ? helpApp.getAppId() : null, helpApp, false);
    }

    private boolean isExplicitCommand(Message message) {
        String content = message.getContent();
        return content != null && content.trim().startsWith("/");
    }

    private FishuAppI resolveAppFromCommandOnly(Message message) {
        String content = message.getContent();
        if (content == null) {
            return null;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }

        String command = extractAppId(trimmed);
        return findAppByCommandOrAlias(command);
    }

    private boolean isCrossAppCommand(ImContextBinding binding, FishuAppI commandApp) {
        return "opencode".equals(binding.getAppId()) && !binding.getAppId().equals(commandApp.getAppId());
    }

    private void throwCrossAppCommandRejected(FishuAppI commandApp, ImContextBinding binding) {
        String errorReply = String.format(
                "❌ 当前在 **%s** 话题模式中\n\n" +
                        "💡 只能使用本应用的命令，不能使用其他应用命令（/%s）\n\n" +
                        "📋 可用命令：直接输入子命令（如 `projects`, `sessions`, `chatnow`）\n" +
                        "🔄 退出话题：发送新消息到群聊（不回复话题）",
                "OpenCode", commandApp.getAppId()
        );
        throw new MessageBizException("TOPIC_COMMAND_NOT_ALLOWED", errorReply);
    }

    private boolean isSessionAwareApp(FishuAppI app) {
        return app != null && "opencode".equals(app.getAppId());
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
}

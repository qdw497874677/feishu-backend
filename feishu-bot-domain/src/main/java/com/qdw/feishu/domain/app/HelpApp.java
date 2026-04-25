package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.card.CardButton;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.card.CardElement;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HelpApp implements FishuAppI {

    @Autowired
    @Lazy
    private AppRegistry appRegistry;

    @Autowired
    @Lazy
    private FeishuGateway feishuGateway;

    @Autowired
    @Lazy
    private CardRenderer cardRenderer;

    @Override
    public String getAppId() {
        return "help";
    }

    @Override
    public String getAppName() {
        return "帮助信息";
    }

    @Override
    public String getDescription() {
        return "显示所有可用命令和使用说明";
    }

    @Override
    public String getHelp() {
        return "用法：/help\n说明：显示所有可用应用的命令和使用说明";
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("h", "?", "man");
    }

    @Override
    public AppExecutionResult execute(Message message) {
        log.info("=== HelpApp.execute 开始 ===");
        log.info("应用 ID: {}", getAppId());
        log.info("输入消息: {}", message.getContent());

        // 1. 尝试发送卡片帮助
        if (trySendCardHelp(message)) {
            log.info("卡片帮助发送成功: chatId={}", message.getChatId());
            return AppExecutionResult.text(null);  // 卡片发送成功，不需要返回文本
        }

        // 2. 降级：返回文本帮助
        log.info("降级为文本帮助: chatId={}", message.getChatId());
        return AppExecutionResult.text(generateTextHelp());
    }

    private boolean trySendCardHelp(Message message) {
        try {
            String cardJson = buildCardHelpJson();
            feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
            log.info("卡片帮助发送成功: chatId={}", message.getChatId());
            return true;
        } catch (Exception e) {
            log.warn("卡片帮助发送失败: error={}", e.getMessage());
            return false;
        }
    }

    /**
     * 使用 CardContent + CardRenderer 构建帮助卡片 JSON。
     */
    private String buildCardHelpJson() {
        List<FishuAppI> apps = appRegistry.getAllApps();

        List<CardButton> buttons = apps.stream()
            .map(app -> CardButton.builder()
                .label(getAppIcon(app.getAppId()) + " " + app.getAppName())
                .action(getDefaultAction(app.getAppId()))
                .style(getButtonType(app.getAppId()))
                .build())
            .collect(Collectors.toList());

        CardContent card = CardContent.builder()
            .headerTitle("🤖 应用菜单")
            .headerTemplate("blue")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("点击按钮选择应用，或直接输入命令"))
            .addElement(CardElement.buttonGroup(buttons))
            .build();

        return cardRenderer.render(card, null);
    }

    private String generateTextHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 应用菜单\n\n");

        List<FishuAppI> apps = appRegistry.getAllApps();
        for (int i = 0; i < apps.size(); i++) {
            FishuAppI app = apps.get(i);
            sb.append(String.format("%d. %s %s\n",
                i + 1,
                getAppIcon(app.getAppId()),
                app.getAppName()));
            sb.append(String.format("   %s\n", app.getDescription()));
            sb.append(String.format("   示例: %s\n\n", app.getHelp()));
        }

        sb.append("回复编号或应用名称选择");
        return sb.toString();
    }

    public String getAppIcon(String appId) {
        Map<String, String> icons = Map.of(
            "opencode", "🤖",
            "bash", "💻",
            "help", "❓",
            "history", "📊",
            "time", "⏰"
        );
        return icons.getOrDefault(appId, "📦");
    }

    public String getButtonType(String appId) {
        List<String> primaryApps = Arrays.asList("opencode", "bash", "help");
        return primaryApps.contains(appId) ? "primary" : "default";
    }

    public String getDefaultAction(String appId) {
        return switch (appId) {
            case "opencode" -> "opencode projects";
            case "bash" -> "bash help";
            default -> appId;
        };
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.DIRECT;
    }
}

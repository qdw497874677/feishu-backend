package com.qdw.feishu.domain.app;

import com.alibaba.cola.exception.SysException;
import com.qdw.feishu.domain.command.UnifiedCommand;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.result.BizResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HelpApp implements FishuAppI {

    @Autowired
    @Lazy
    private AppRegistry appRegistry;
    
    @Autowired
    @Lazy
    private FeishuGateway feishuGateway;

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
    public BizResult execute(UnifiedCommand command) {
        String helpText = generateTextHelp();
        return BizResult.of(helpText);
    }

    @Override
    public String execute(Message message) {
        log.info("=== HelpApp.execute 开始 ===");
        log.info("应用 ID: {}", getAppId());
        log.info("输入消息: {}", message.getContent());

        // 1. 尝试发送卡片帮助
        if (trySendCardHelp(message)) {
            log.info("卡片帮助发送成功: chatId={}", message.getChatId());
            return null;  // 卡片发送成功，不需要返回文本
        }

        // 2. 降级：返回文本帮助
        log.info("降级为文本帮助: chatId={}", message.getChatId());
        return generateTextHelp();
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

    private String buildCardHelpJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schema\": \"2.0\",\n");
        json.append("  \"config\": {\"wide_screen_mode\": true},\n");
        json.append("  \"header\": {\n");
        json.append("    \"title\": {\"content\": \"🤖 应用菜单\", \"tag\": \"plain_text\"},\n");
        json.append("    \"template\": \"blue\"\n");
        json.append("  },\n");
        json.append("  \"elements\": [\n");
        json.append("    {\"tag\": \"markdown\", \"content\": \"点击按钮选择应用，或直接输入命令\"},\n");
        json.append("    {\"tag\": \"action\", \"actions\": [");
        
        List<FishuAppI> apps = appRegistry.getAllApps();
        for (int i = 0; i < apps.size(); i++) {
            FishuAppI app = apps.get(i);
            if (i > 0) json.append(",");
            
            json.append(String.format(
                "{\"tag\": \"button\", " +
                "\"text\": {\"content\": \"%s %s\", \"tag\": \"plain_text\"}, " +
                "\"type\": \"%s\", " +
                "\"value\": {\"message\": \"%s\"}}",
                getAppIcon(app.getAppId()),
                app.getAppName(),
                getButtonType(app.getAppId()),
                app.getAppId()
            ));
        }
        
        json.append("]}\n");
        json.append("  ]\n");
        json.append("}");
        
        return json.toString();
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

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.DIRECT;
    }
}

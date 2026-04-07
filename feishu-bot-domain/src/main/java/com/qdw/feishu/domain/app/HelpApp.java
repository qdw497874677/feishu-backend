package com.qdw.feishu.domain.app;

import com.alibaba.cola.exception.SysException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.core.AppRegistry;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

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
    private ObjectMapper objectMapper;

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

    private String buildCardHelpJson() {
        try {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("schema", "2.0");
            card.put("config", Map.of("wide_screen_mode", true));
            
            // Header
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("title", Map.of("content", "🤖 应用菜单", "tag", "plain_text"));
            header.put("template", "blue");
            card.put("header", header);
            
            // Body elements
            List<Map<String, Object>> elements = new ArrayList<>();
            
            // Markdown element
            elements.add(Map.of("tag", "markdown", "content", "点击按钮选择应用，或直接输入命令"));
            
            // 每个按钮单独一行（垂直布局）
            List<FishuAppI> apps = appRegistry.getAllApps();
            
            for (FishuAppI app : apps) {
                Map<String, Object> button = new LinkedHashMap<>();
                button.put("tag", "button");
                button.put("text", Map.of(
                    "content", getAppIcon(app.getAppId()) + " " + app.getAppName(),
                    "tag", "plain_text"
                ));
                button.put("type", getButtonType(app.getAppId()));
                // value 必须是对象格式，避免 SDK 反序列化错误
                // 特殊处理：opencode 显示项目列表，bash 显示帮助
                String actionValue = getDefaultAction(app.getAppId());
                button.put("value", Map.of("action", actionValue));
                
                // 每个按钮作为单独的元素
                elements.add(button);
            }
            
            card.put("body", Map.of("elements", elements));
            
            return objectMapper.writeValueAsString(card);
        } catch (Exception e) {
            log.error("构建卡片JSON失败", e);
            throw new SysException("BUILD_CARD_ERROR", "Failed to build card JSON", e);
        }
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

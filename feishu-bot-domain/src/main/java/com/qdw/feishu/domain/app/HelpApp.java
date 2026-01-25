package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HelpApp implements FishuAppI {

    @Autowired
    @Lazy
    private AppRegistry appRegistry;

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
    public String execute(Message message) {
        log.info("=== HelpApp.execute 开始 ===");
        log.info("应用 ID: {}", getAppId());
        log.info("输入消息: {}", message.getContent());

        StringBuilder helpText = new StringBuilder();
        helpText.append("飞书机器人命令帮助\n\n");

        appRegistry.getAllApps().forEach(app -> {
            helpText.append(String.format("%s - %s\n", 
                app.getTriggerCommand(), 
                app.getAppName()));
            helpText.append(String.format("  %s\n\n", 
                app.getDescription()));
        });

        helpText.append("💡 提示：发送任意非命令消息也会显示此帮助信息");

        String result = helpText.toString();
        log.info("HelpApp.execute 完成，返回帮助信息");
        log.info("=== HelpApp.execute 结束 ===");

        return result;
    }
}

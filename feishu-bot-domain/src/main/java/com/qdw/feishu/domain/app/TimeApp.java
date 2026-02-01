package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class TimeApp implements FishuAppI {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINA);

    @Override
    public String getAppId() {
        return "time";
    }

    @Override
    public String getAppName() {
        return "时间查询";
    }

    @Override
    public String getDescription() {
        return "查询当前系统时间";
    }

    @Override
    public String getHelp() {
        return "用法：/time\n说明：返回当前系统时间";
    }

    @Override
    public java.util.List<String> getAppAliases() {
        return java.util.Arrays.asList("t", "now", "date");
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;
    }

    @Override
    public String execute(Message message) {
        log.info("=== TimeApp.execute 开始 ===");
        log.info("应用 ID: {}", getAppId());
        log.info("输入消息: {}", message.getContent());

        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(FORMATTER);

        log.info("格式化时间: {}", formattedTime);

        String result = "当前时间：" + formattedTime;
        log.info("TimeApp.execute 完成，返回: {}", result);
        log.info("=== TimeApp.execute 结束 ===");

        return result;
    }
}

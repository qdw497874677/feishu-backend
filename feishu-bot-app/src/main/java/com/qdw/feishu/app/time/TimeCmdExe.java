package com.qdw.feishu.app.time;

import com.qdw.feishu.app.executor.CommandExecutorI;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class TimeCmdExe implements CommandExecutorI {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINA);

    @Override
    public String getCommand() {
        return "time";
    }

    @Override
    public String execute(Message message) {
        log.debug("Executing /time command");

        LocalDateTime now = LocalDateTime.now();
        String formattedTime = now.format(FORMATTER);

        log.info("Current time: {}", formattedTime);
        return "当前时间：" + formattedTime;
    }
}

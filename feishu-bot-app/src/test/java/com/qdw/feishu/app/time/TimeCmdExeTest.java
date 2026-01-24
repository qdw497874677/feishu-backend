package com.qdw.feishu.app.time;

import com.qdw.feishu.app.executor.CommandExecutorI;
import com.qdw.feishu.domain.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class TimeCmdExeTest {

    private TimeCmdExe executor;

    @BeforeEach
    void setUp() {
        executor = new TimeCmdExe();
    }

    @Test
    void shouldImplementCommandExecutorI() {
        assertTrue(executor instanceof CommandExecutorI);
    }

    @Test
    void shouldReturnTimeCommand() {
        assertEquals("time", executor.getCommand());
    }

    @Test
    void shouldExecuteTimeCommand() {
        Message message = new Message();
        message.setContent("/time");

        String result = executor.execute(message);

        assertNotNull(result);
        assertTrue(result.startsWith("当前时间："));
        assertFalse(result.isEmpty());
    }

    @Test
    void shouldReturnFormattedTimeInChinese() {
        Message message = new Message();
        message.setContent("/time");

        String result = executor.execute(message);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINA);
        String expectedPrefix = "当前时间：" + LocalDateTime.now().format(formatter);

        assertTrue(result.startsWith("当前时间："));
        assertTrue(result.contains("年") && result.contains("月") && result.contains("日"));
        assertTrue(result.matches(".*\\d{4}年.*\\d{1,2}月.*\\d{1,2}日.*"));
    }

    @Test
    void shouldReturnDifferentTimeOnEachExecution() throws InterruptedException {
        Message message = new Message();
        message.setContent("/time");

        String result1 = executor.execute(message);
        Thread.sleep(1000);
        String result2 = executor.execute(message);

        assertNotEquals(result1, result2, "Time should be different on each execution");
    }
}

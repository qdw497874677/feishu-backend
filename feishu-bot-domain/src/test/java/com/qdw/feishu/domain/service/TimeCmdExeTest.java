package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.executor.CommandExecutorI;
import com.qdw.feishu.domain.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        assertTrue(result.startsWith("当前时间："));
        assertTrue(result.contains("年") && result.contains("月") && result.contains("日"));
        assertTrue(result.matches(".*\\d{4}年.*\\d{1,2}月.*\\d{1,2}日.*"));
    }

    @Test
    void shouldReturnNonEmptyResult() {
        Message message = new Message();
        String result = executor.execute(message);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.startsWith("当前时间："));
    }
}

package com.qdw.feishu.domain.router;

import com.qdw.feishu.domain.executor.CommandExecutorI;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.router.CommandRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandRouter tests")
class CommandRouterTest {

    @Mock
    private CommandExecutorI mockExecutor1;

    @Mock
    private CommandExecutorI mockExecutor2;

    private CommandRouter commandRouter;

    private Message testMessage;

    @BeforeEach
    void setUp() {
        when(mockExecutor1.getCommand()).thenReturn("time");
        when(mockExecutor2.getCommand()).thenReturn("help");

        commandRouter = new CommandRouter(List.of(mockExecutor1, mockExecutor2));

        Sender sender = Sender.builder().openId("user123").build();
        testMessage = new Message("msg001", "test content", sender);
    }

    @Test
    @DisplayName("Should route to correct executor")
    void shouldRouteToCorrectExecutor() {
        testMessage.setContent("/time");
        when(mockExecutor1.execute(testMessage)).thenReturn("Current time is 12:00");

        String result = commandRouter.route(testMessage);

        assertThat(result).isEqualTo("Current time is 12:00");
    }

    @Test
    @DisplayName("Should return null for non-command messages")
    void shouldReturnNullForNonCommand() {
        testMessage.setContent("hello world");

        String result = commandRouter.route(testMessage);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle unknown commands")
    void shouldHandleUnknownCommands() {
        testMessage.setContent("/unknown");

        String result = commandRouter.route(testMessage);

        assertThat(result).contains("未知命令: unknown");
        assertThat(result).contains("/help");
    }

    @Test
    @DisplayName("Should handle command with arguments")
    void shouldHandleCommandWithArguments() {
        testMessage.setContent("/time format=24h");
        when(mockExecutor1.execute(testMessage)).thenReturn("24:00");

        String result = commandRouter.route(testMessage);

        assertThat(result).isEqualTo("24:00");
    }

    @Test
    @DisplayName("Should handle command execution exceptions")
    void shouldHandleExecutionExceptions() {
        testMessage.setContent("/time");
        when(mockExecutor1.execute(testMessage)).thenThrow(new RuntimeException("Service unavailable"));

        String result = commandRouter.route(testMessage);

        assertThat(result).contains("命令执行失败");
        assertThat(result).contains("Service unavailable");
    }

    @Test
    @DisplayName("Should handle case-insensitive commands")
    void shouldHandleCaseInsensitiveCommands() {
        testMessage.setContent("/TIME");
        when(mockExecutor1.execute(testMessage)).thenReturn("Time command executed");

        String result = commandRouter.route(testMessage);

        assertThat(result).isEqualTo("Time command executed");
    }

    @Test
    @DisplayName("Should trim whitespace from command")
    void shouldTrimWhitespace() {
        testMessage.setContent("  /time  ");
        when(mockExecutor1.execute(testMessage)).thenReturn("Trimmed");

        String result = commandRouter.route(testMessage);

        assertThat(result).isEqualTo("Trimmed");
    }

    @Test
    @DisplayName("Should initialize with empty executor list")
    void shouldInitializeWithEmptyList() {
        CommandRouter emptyRouter = new CommandRouter(Collections.emptyList());
        testMessage.setContent("/test");

        String result = emptyRouter.route(testMessage);

        assertThat(result).contains("未知命令: test");
    }
}

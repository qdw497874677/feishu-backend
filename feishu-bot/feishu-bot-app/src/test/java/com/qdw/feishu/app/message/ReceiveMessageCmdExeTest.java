package com.qdw.feishu.app.message;

import com.alibaba.cola.dto.Response;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.BotMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReceiveMessageCmdExe tests")
class ReceiveMessageCmdExeTest {

    @Mock
    private BotMessageService botMessageService;

    private ReceiveMessageCmdExe commandExecutor;

    @BeforeEach
    void setUp() {
        commandExecutor = new ReceiveMessageCmdExe();
        commandExecutor.setBotMessageService(botMessageService);
    }

    @Test
    @DisplayName("Should execute command successfully")
    void shouldExecuteCommandSuccessfully() {
        ReceiveMessageCmd cmd = new ReceiveMessageCmd();
        cmd.setMessageId("msg_123");
        cmd.setContent("Hello Bot");
        cmd.setSenderOpenId("open_id");
        cmd.setSenderUserId("user_id");
        cmd.setSenderName("Test User");

        SendResult mockResult = SendResult.success("msg_456");
        when(botMessageService.handleMessage(any(Message.class))).thenReturn(mockResult);

        Response response = commandExecutor.execute(cmd);

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(botMessageService).handleMessage(messageCaptor.capture());

        Message capturedMessage = messageCaptor.getValue();
        assertThat(capturedMessage.getId()).isEqualTo("msg_123");
        assertThat(capturedMessage.getContent()).isEqualTo("Hello Bot");
        assertThat(capturedMessage.getSender().getName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should throw exception for empty content")
    void shouldThrowExceptionForEmptyContent() {
        ReceiveMessageCmd cmd = new ReceiveMessageCmd();
        cmd.setMessageId("msg_123");
        cmd.setContent("");
        cmd.setSenderOpenId("open_id");
        cmd.setSenderUserId("user_id");

        assertThatThrownBy(() -> commandExecutor.execute(cmd))
            .isInstanceOf(MessageBizException.class)
            .hasMessageContaining("消息内容为空");
    }

    @Test
    @DisplayName("Should throw exception for null content")
    void shouldThrowExceptionForNullContent() {
        ReceiveMessageCmd cmd = new ReceiveMessageCmd();
        cmd.setMessageId("msg_123");
        cmd.setContent(null);
        cmd.setSenderOpenId("open_id");
        cmd.setSenderUserId("user_id");

        assertThatThrownBy(() -> commandExecutor.execute(cmd))
            .isInstanceOf(MessageBizException.class)
            .hasMessageContaining("消息内容为空");
    }
}

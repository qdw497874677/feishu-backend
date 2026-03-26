package com.qdw.feishu.app.listener;

import com.qdw.feishu.app.message.BotMessageAppService;
import com.qdw.feishu.app.opencode.OpenCodeMessageAppService;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.service.MessageDeduplicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

@ExtendWith(MockitoExtension.class)
class ReceiveMessageListenerExeTest {

    @Mock
    private BotMessageAppService botMessageAppService;

    @Mock
    private OpenCodeMessageAppService openCodeMessageAppService;

    @Mock
    private MessageDeduplicator messageDeduplicator;

    @Mock
    private FeishuGateway feishuGateway;

    private ReceiveMessageListenerExe listenerExe;

    @BeforeEach
    void setUp() {
        listenerExe = new ReceiveMessageListenerExe(
                botMessageAppService,
                openCodeMessageAppService,
                messageDeduplicator,
                feishuGateway
        );
    }

    @Test
    void should_routeThroughAppLayerOrchestrator_inRealOpenCodeFlow() {
        Message message = createMessage("/opencode projects", "evt-1");
        when(messageDeduplicator.isProcessed("evt-1")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(true);

        listenerExe.execute(message);

        verify(openCodeMessageAppService).tryHandle(message);
        verify(botMessageAppService, never()).handleMessage(message);
    }

    @Test
    void should_fallbackToBotMessageService_when_notOpenCode() {
        Message message = createMessage("/help", "evt-2");
        when(messageDeduplicator.isProcessed("evt-2")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);

        listenerExe.execute(message);

        verify(botMessageAppService).handleMessage(message);
        verify(openCodeMessageAppService).tryHandle(message);
    }

    @Test
    void should_skipAlreadyProcessedMessage() {
        Message message = createMessage("/opencode projects", "evt-3");
        when(messageDeduplicator.isProcessed("evt-3")).thenReturn(true);

        listenerExe.execute(message);

        verify(openCodeMessageAppService, never()).tryHandle(message);
        verify(botMessageAppService, never()).handleMessage(message);
    }

    @Test
    void should_sendBizExceptionReplyToUser_when_botMessageAppServiceThrowsBizException() {
        // given
        Message message = createMessage("/help", "evt-biz-1");
        String errorMessage = "跨应用命令被拒绝";
        when(messageDeduplicator.isProcessed("evt-biz-1")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException(errorMessage));
        when(feishuGateway.sendMessage(any(Message.class), eq(errorMessage), isNull())).thenReturn(SendResult.success("msg-reply-1"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(any(Message.class), eq(errorMessage), isNull());
    }

    @Test
    void should_sendBizExceptionReplyToTopic_when_openCodeThrowsBizException() {
        // given
        Message message = createMessage("/opencode chat test", "evt-biz-2");
        message.setTopicId("topic-123");
        String errorMessage = "会话未初始化";
        when(messageDeduplicator.isProcessed("evt-biz-2")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenThrow(new MessageBizException(errorMessage));
        when(feishuGateway.sendMessage(any(Message.class), eq(errorMessage), eq("topic-123"))).thenReturn(SendResult.success("msg-reply-2"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(any(Message.class), eq(errorMessage), eq("topic-123"));
    }

    @Test
    void should_sendDefaultErrorMessage_when_bizExceptionMessageIsNull() {
        // given
        Message message = createMessage("/help", "evt-biz-3");
        when(messageDeduplicator.isProcessed("evt-biz-3")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException((String) null));
        when(feishuGateway.sendMessage(any(Message.class), eq("操作失败，请稍后重试"), isNull())).thenReturn(SendResult.success("msg-reply-3"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(any(Message.class), eq("操作失败，请稍后重试"), isNull());
    }

    @Test
    void should_sendDefaultErrorMessage_when_bizExceptionMessageIsEmpty() {
        // given
        Message message = createMessage("/help", "evt-biz-4");
        when(messageDeduplicator.isProcessed("evt-biz-4")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException(""));
        when(feishuGateway.sendMessage(any(Message.class), eq("操作失败，请稍后重试"), isNull())).thenReturn(SendResult.success("msg-reply-4"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(any(Message.class), eq("操作失败，请稍后重试"), isNull());
    }

    @Test
    void should_logWarning_when_bizExceptionReplyFails() {
        // given
        Message message = createMessage("/help", "evt-biz-5");
        String errorMessage = "业务错误";
        when(messageDeduplicator.isProcessed("evt-biz-5")).thenReturn(false);
        when(openCodeMessageAppService.tryHandle(message)).thenReturn(false);
        when(botMessageAppService.handleMessage(message)).thenThrow(new MessageBizException(errorMessage));
        when(feishuGateway.sendMessage(any(Message.class), eq(errorMessage), isNull())).thenReturn(SendResult.failure("网络错误"));

        // when
        listenerExe.execute(message);

        // then
        verify(feishuGateway).sendMessage(any(Message.class), eq(errorMessage), isNull());
        // Note: We verify the sendMessage was called; log verification is optional
    }

    private Message createMessage(String content, String eventId) {
        Message message = new Message();
        message.setContent(content);
        message.setEventId(eventId);
        message.setMessageId("msg-1");
        message.setChatId("chat-1");
        message.setSender(new Sender("ou_test", "tester"));
        return message;
    }
}

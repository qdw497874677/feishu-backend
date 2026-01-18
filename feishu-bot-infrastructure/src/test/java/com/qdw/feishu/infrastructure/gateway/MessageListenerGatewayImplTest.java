package com.qdw.feishu.infrastructure.gateway;

import com.lark.oapi.Client;
import com.lark.oapi.core.event.EventDispatcher;
import com.qdw.feishu.domain.gateway.MessageListenerGateway;
import com.qdw.feishu.infrastructure.config.FeishuProperties;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageListenerGatewayImplTest {

    @Mock
    private Client mockClient;

    @Mock
    private EventDispatcher mockEventDispatcher;

    @Mock
    private FeishuProperties mockProperties;

    private MessageListenerGateway gateway;

    @BeforeEach
    void setUp() {
        when(mockClient.event()).thenReturn(mockEventDispatcher);
        gateway = new MessageListenerGatewayImpl(mockClient, mockProperties);
    }

    @Test
    void shouldChangeStatusToConnectingOnStart() {
        Consumer messageHandler = mock(Consumer.class);

        gateway.startListening(messageHandler);

        verify(mockClient).event();
        assertEquals(MessageListenerGateway.ConnectionStatus.CONNECTED, gateway.getConnectionStatus());
    }

    @Test
    void shouldStopListeningAndChangeStatus() {
        gateway.startListening(mock(Consumer.class));

        gateway.stopListening();

        assertEquals(MessageListenerGateway.ConnectionStatus.DISCONNECTED, gateway.getConnectionStatus());
    }

    @Test
    void shouldReturnCorrectInitialStatus() {
        MessageListenerGateway.ConnectionStatus status = gateway.getConnectionStatus();

        assertEquals(MessageListenerGateway.ConnectionStatus.DISCONNECTED, status);
    }
}

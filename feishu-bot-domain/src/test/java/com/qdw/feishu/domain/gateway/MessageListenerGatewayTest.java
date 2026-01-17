package com.qdw.feishu.domain.gateway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageListenerGatewayTest {

    @Test
    void connectionStatusEnumExists() {
        // Verify all enum values exist
        assertNotNull(MessageListenerGateway.ConnectionStatus.DISCONNECTED);
        assertNotNull(MessageListenerGateway.ConnectionStatus.CONNECTING);
        assertNotNull(MessageListenerGateway.ConnectionStatus.CONNECTED);
        assertNotNull(MessageListenerGateway.ConnectionStatus.RECONNECTING);

        // Verify enum values count
        assertEquals(4, MessageListenerGateway.ConnectionStatus.values().length);
    }
}

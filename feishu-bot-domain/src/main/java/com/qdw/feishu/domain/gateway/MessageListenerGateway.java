package com.qdw.feishu.domain.gateway;

import java.util.function.Consumer;

/**
 * 飞书消息监听网关接口
 * 定义长连接的生命周期管理
 */
public interface MessageListenerGateway {

    /**
     * 启动长连接监听
     * @param messageHandler 消息处理器
     */
    void startListening(Consumer<Message> messageHandler);

    /**
     * 停止监听
     */
    void stopListening();

    /**
     * 获取连接状态
     * @return 当前连接状态
     */
    ConnectionStatus getConnectionStatus();

    /**
     * 连接状态枚举
     */
    enum ConnectionStatus {
        DISCONNECTED,   // 已断开
        CONNECTING,     // 连接中
        CONNECTED,      // 已连接
        RECONNECTING    // 重连中
    }
}
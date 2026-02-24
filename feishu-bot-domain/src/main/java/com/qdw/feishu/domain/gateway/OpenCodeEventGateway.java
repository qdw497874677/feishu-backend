package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.opencode.OpenCodeEvent;

import java.util.function.Consumer;

/**
 * OpenCode 事件订阅网关接口
 *
 * 用于订阅 OpenCode SSE 事件流
 */
public interface OpenCodeEventGateway {

    /**
     * 启动 SSE 连接并订阅所有事件
     *
     * @param handler 事件处理器
     */
    void subscribe(Consumer<OpenCodeEvent> handler);

    /**
     * 检查 SSE 连接是否活跃
     *
     * @return true 如果连接活跃
     */
    boolean isConnected();

    /**
     * 断开 SSE 连接
     */
    void disconnect();
}

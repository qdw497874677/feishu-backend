package com.qdw.feishu.client.message;

import com.alibaba.cola.dto.Response;

/**
 * 消息服务接口
 * 
 * 定义应用层对外提供的消息处理能力
 */
public interface MessageServiceI {

    /**
     * 接收并处理消息
     * 
     * @param cmd 接收消息命令
     * @return 处理结果
     */
    Response receiveMessage(ReceiveMessageCmd cmd);
}

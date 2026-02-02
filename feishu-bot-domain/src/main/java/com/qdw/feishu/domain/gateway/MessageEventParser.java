package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.message.Message;

/**
 * 消息事件解析器接口（防腐层）
 * 
 * 职责：
 * - 将外部系统（飞书 SDK）的数据格式转换为领域模型
 * - 封装飞书 SDK 的实现细节，保护领域层免受外部变化影响
 * - 提供稳定的抽象接口，便于单元测试
 * 
 * 优势：
 * - 领域层不依赖飞书 SDK 的具体类
 * - 如果飞书 SDK 变更，只需修改此接口的实现
 * - 便于 mock 进行单元测试
 * 
 * @see com.qdw.feishu.domain.message.Message
 */
public interface MessageEventParser {

    /**
     * 解析飞书事件并转换为领域消息对象
     * 
     * @param rawEvent 原始飞书事件数据（SDK 特定格式）
     * @param <T> 飞书 SDK 的事件类型
     * @return 领域消息对象
     */
    <T> Message parse(T rawEvent);

    /**
     * 检查是否支持解析指定类型的事件
     * 
     * @param eventClass 事件类类型
     * @return true 表示支持
     */
    boolean supports(Class<?> eventClass);
}

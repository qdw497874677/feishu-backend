package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;

/**
 * OpenCode 子命令处理器接口。
 *
 * <p>每个子命令组（connect、chat、sessions 等）实现此接口，
 * 由 {@link com.qdw.feishu.domain.opencode.OpenCodeCommandHandler} 统一调度。
 */
public interface SubCommandHandler {

    /**
     * 处理子命令。
     *
     * @param message       原始消息
     * @param parts         命令拆分后的数组（如 ["/opencode", "chat", "帮我写代码"]）
     * @param messageContext 消息上下文（含绑定信息）
     * @return 执行结果
     */
    AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext);
}

package com.qdw.feishu.domain.opencode.router;

import com.qdw.feishu.domain.message.Message;

/**
 * 命令执行器
 *
 * 定义命令的执行逻辑，与状态判断解耦
 */
@FunctionalInterface
public interface ICommandExecutor {
    /**
     * 执行命令
     *
     * @param parts 命令参数数组
     * @param message 消息对象
     * @return 执行结果
     */
    String execute(String[] parts, Message message);

    /**
     * 创建带参数验证的执行器
     *
     * @param minParts 最小参数数量
     * @param executor 实际执行器
     * @return 带验证的执行器
     */
    static ICommandExecutor withValidation(int minParts, ICommandExecutor executor) {
        return (parts, message) -> {
            if (parts.length < minParts) {
                return "❌ 参数不足\n\n💡 使用 `/opencode help` 查看正确用法";
            }
            return executor.execute(parts, message);
        };
    }
}

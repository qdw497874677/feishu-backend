package com.qdw.feishu.domain.gateway;

/**
 * OpenCode Gateway 接口
 *
 * 定义与 OpenCode 服务端 API 交互的抽象
 */
public interface OpenCodeGateway {

    /**
     * 执行 OpenCode 命令
     *
     * @param prompt 提示词（可为 null，如果继续会话）
     * @param sessionId 会话 ID（可为 null，如果是新会话）
     * @param timeoutSeconds 超时时间（秒），0 表示无限制
     * @return 执行结果，如果超时返回 null
     * @throws Exception 执行异常
     */
    String executeCommand(String prompt, String sessionId, int timeoutSeconds) throws Exception;

    /**
     * 列出所有会话
     *
     * @return 格式化的会话列表
     */
    String listSessions();

    /**
     * 列出所有项目
     *
     * @return 格式化的项目列表
     */
    String listProjects();

    /**
     * 列出所有斜杠命令
     *
     * @return 格式化的命令列表
     */
    String listCommands();

    /**
     * 获取服务器状态
     *
     * @return 状态信息
     */
    String getServerStatus();
}

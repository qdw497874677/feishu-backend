package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.opencode.ProjectInfo;
import com.qdw.feishu.domain.opencode.SessionInfo;

import java.util.List;

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
      * 在指定项目目录上下文中执行 OpenCode 命令。
      *
      * @param prompt 提示词
      * @param sessionId 会话 ID（可为 null）
      * @param timeoutSeconds 超时时间（秒）
      * @param directory 项目工作目录（可为 null；null 表示不覆盖 OpenCode session 自身目录）
      * @return 执行结果，如果超时返回 null
      * @throws Exception 执行异常
      */
    String executeCommand(String prompt, String sessionId, int timeoutSeconds, String directory) throws Exception;

    /**
      * 创建新会话（不执行任务）
      *
      * @return 新创建的会话 ID
      * @throws Exception 创建异常
      */
    String createSession() throws Exception;

    /**
      * 创建新会话并指定初始工作目录
      *
      * @param initialDirectory 初始工作目录（可为 null）
      * @return 新创建的会话 ID
      * @throws Exception 创建异常
      */
    String createSession(String initialDirectory) throws Exception;

    /**
      * 列出所有会话
      *
      * @return 格式化的会话列表
      */
    String listSessions();

    /**
     * 列出指定项目的最近会话
     *
     * @param project 项目名称或路径
     * @param limit 返回的会话数量限制（最近 N 个）
     * @return 格式化的会话列表，包含 session ID 和摘要
     */
    String listRecentSessions(String project, int limit);

    /**
     * 获取项目的最近会话列表（结构化数据）。
     *
     * 用于卡片渲染，返回解析后的会话对象列表而非纯文本。
     *
     * @param project 项目名称
     * @param limit 返回的会话数量上限
     * @return 结构化会话列表（空列表表示无会话）
     */
    List<SessionInfo> listRecentSessionsStructured(String project, int limit);

    /**
     * 列出所有项目
     *
     * @return 格式化的项目列表
     */
    String listProjects();

    /**
     * 获取项目列表（结构化数据）。
     *
     * 用于卡片渲染，返回解析后的项目对象列表而非纯文本。
     *
     * @return 结构化项目列表（空列表表示无项目）
     */
    List<ProjectInfo> listProjectsStructured();

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

package com.qdw.feishu.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenCode 配置属性
 *
 * 支持两种模式：
 * 1. CLI 模式：使用 opencode 可执行文件（已废弃）
 * 2. Server 模式：通过 HTTP API 连接到 OpenCode 服务端（推荐）
 */
@Data
@Component
@ConfigurationProperties(prefix = "opencode")
public class OpenCodeProperties {

    /**
     * OpenCode 服务端 URL
     * 默认: http://localhost:4096
     */
    private String serverUrl = "http://localhost:4096";

    /**
     * HTTP 基本认证用户名
     * 默认: opencode
     */
    private String username = "opencode";

    /**
     * HTTP 基本认证密码
     * 通过环境变量 OPENCODE_SERVER_PASSWORD 设置
     */
    private String password;

    /**
     * 连接超时时间（秒）
     */
    private int connectTimeout = 10;

    /**
     * 请求超时时间（秒）
     */
    private int requestTimeout = 120;

    /**
     * 默认超时时间（秒）- 用于兼容旧代码
     * @deprecated 使用 requestTimeout
     */
    @Deprecated
    private int defaultTimeout = 30;

    /**
     * 最大输出长度（字符）
     */
    private int maxOutputLength = 5000;

    /**
     * 是否启用异步执行
     */
    private boolean asyncEnabled = true;

    // ===== 以下字段已废弃，仅用于兼容 CLI 模式 =====

    /**
     * OpenCode 可执行文件路径
     * @deprecated CLI 模式已废弃，请使用 Server 模式
     */
    @Deprecated
    private String executablePath;
}

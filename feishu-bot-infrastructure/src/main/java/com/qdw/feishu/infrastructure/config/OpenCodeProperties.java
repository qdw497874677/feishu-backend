package com.qdw.feishu.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenCode 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "opencode")
public class OpenCodeProperties {

    /**
     * OpenCode 可执行文件路径
     * 如果为null，则从PATH中查找
     */
    private String executablePath;

    /**
     * 默认超时时间（秒）
     */
    private int defaultTimeout = 30;

    /**
     * 最大输出长度（字符）
     */
    private int maxOutputLength = 2000;

    /**
     * 是否启用异步执行
     */
    private boolean asyncEnabled = true;
}

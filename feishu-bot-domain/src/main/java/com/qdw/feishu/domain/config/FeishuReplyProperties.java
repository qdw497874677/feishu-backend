package com.qdw.feishu.domain.config;

import com.qdw.feishu.domain.core.ReplyMode;
import lombok.Data;

/**
 * 飞书回复配置属性
 *
 * Domain-layer POJO — no Spring dependency.
 * Infrastructure layer provides the @ConfigurationProperties implementation.
 */
@Data
public class FeishuReplyProperties {

    /**
     * 回复模式
     */
    private ReplyMode mode = ReplyMode.DEFAULT;
}

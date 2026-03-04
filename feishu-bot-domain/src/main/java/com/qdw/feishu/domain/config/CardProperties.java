package com.qdw.feishu.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 卡片流式输出配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "opencode.card")
public class CardProperties {

    /**
     * 是否启用卡片流式输出
     */
    private boolean enabled = true;

    /**
     * 卡片创建/更新失败时是否降级为普通消息
     */
    private boolean fallbackOnError = true;

    /**
     * 卡片标题
     */
    private String title = "🤖 AI 助手";

    /**
     * 思考中文本
     */
    private String thinkingText = "⏳ 正在思考...";

    /**
     * 处理中文本
     */
    private String processingText = "⏳ 处理中...";

    /**
     * 完成文本
     */
    private String completeText = "✅ 完成";
}

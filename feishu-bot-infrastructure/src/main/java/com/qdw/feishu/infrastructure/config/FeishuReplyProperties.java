package com.qdw.feishu.infrastructure.config;

import com.qdw.feishu.domain.app.ReplyMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "feishu.reply")
public class FeishuReplyProperties {

    private ReplyMode mode = ReplyMode.DEFAULT;
}

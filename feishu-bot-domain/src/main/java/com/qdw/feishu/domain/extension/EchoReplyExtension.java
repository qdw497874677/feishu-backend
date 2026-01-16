package com.qdw.feishu.domain.extension;

import com.alibaba.cola.extension.Extension;
import com.qdw.feishu.domain.message.Message;
import org.springframework.stereotype.Component;

/**
 * 默认回显回复实现
 */
@Extension(bizId = "feishu-bot", useCase = "echo")
@Component
public class EchoReplyExtension implements ReplyExtensionPt {

    @Override
    public String enhanceReply(String originalReply, Message message) {
        // 简单回显策略
        return String.format("你说: %s\n我回复: %s", 
            message.getContent(), originalReply);
    }
}

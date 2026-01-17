package com.qdw.feishu.domain.extension;

import com.alibaba.cola.extension.Extension;
import com.qdw.feishu.domain.message.Message;

/**
 * 回复扩展点
 * 
 * 支持不同的回复策略，实现插件化
 * 符合 COLA 的扩展点设计
 */
@Extension(bizId = "feishu-bot")
public interface ReplyExtensionPt {

    /**
     * 增强回复内容
     * 
     * @param originalReply 原始回复
     * @param message 消息上下文
     * @return 增强后的回复
     */
    String enhanceReply(String originalReply, Message message);
}

package com.qdw.feishu.domain.gateway;

import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.card.CardContent;

/**
 * 将 IM 无关的 CardContent 转换为具体 IM 平台的卡片 JSON。
 *
 * 遵循 COLA：接口定义在 domain，实现在 infrastructure。
 */
public interface CardRenderer {

    /**
     * 渲染卡片为 JSON 字符串。
     *
     * @param cardContent IM 无关的卡片内容
     * @param context 卡片上下文（chatId/topicId/sessionId），嵌入按钮 value；可为 null
     * @return 具体 IM 平台的卡片 JSON
     */
    String render(CardContent cardContent, CardActionContext context);
}

package com.qdw.feishu.infrastructure.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.card.CardButton;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.card.CardElement;
import com.qdw.feishu.domain.gateway.CardRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 CardContent 渲染为飞书 schema 2.0 卡片 JSON。
 */
@Slf4j
@Component
public class FeishuCardRenderer implements CardRenderer {

    private final ObjectMapper objectMapper;

    public FeishuCardRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String render(CardContent cardContent, CardActionContext context) {
        try {
            Map<String, Object> card = buildCardMap(cardContent, context);
            return objectMapper.writeValueAsString(card);
        } catch (Exception e) {
            log.error("渲染卡片 JSON 失败", e);
            throw new RuntimeException("渲染卡片 JSON 失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildCardMap(CardContent cardContent, CardActionContext context) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("schema", "2.0");
        card.put("config", Map.of("wide_screen_mode", cardContent.isWideScreenMode()));

        // Header
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", Map.of("content", cardContent.getHeaderTitle(), "tag", "plain_text"));
        if (cardContent.getHeaderTemplate() != null) {
            header.put("template", cardContent.getHeaderTemplate());
        }
        card.put("header", header);

        // Body elements
        List<Map<String, Object>> elements = new ArrayList<>();
        for (CardElement element : cardContent.getElements()) {
            if (element.isMarkdown()) {
                elements.add(Map.of("tag", "markdown", "content", element.getMarkdownContent()));
            } else if (element.isButtonGroup()) {
                for (CardButton btn : element.getButtons()) {
                    Map<String, Object> buttonMap = new LinkedHashMap<>();
                    buttonMap.put("tag", "button");
                    buttonMap.put("text", Map.of("content", btn.getLabel(), "tag", "plain_text"));
                    buttonMap.put("type", btn.getStyle());
                    Map<String, Object> value = context != null
                        ? context.toValueMap(btn.getAction())
                        : Map.of("action", btn.getAction());
                    buttonMap.put("value", value);
                    elements.add(buttonMap);
                }
            }
        }
        card.put("body", Map.of("elements", elements));
        return card;
    }
}

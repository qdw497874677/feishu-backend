package com.qdw.feishu.domain.card;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 卡片元素。支持 markdown 段落和按钮组两种类型。
 */
public class CardElement {

    private final String type;
    private final String markdownContent;
    private final List<CardButton> buttons;

    private CardElement(String type, String markdownContent, List<CardButton> buttons) {
        this.type = type;
        this.markdownContent = markdownContent;
        this.buttons = buttons;
    }

    public static CardElement markdown(String content) {
        return new CardElement("markdown", content, Collections.emptyList());
    }

    public static CardElement buttonGroup(List<CardButton> buttons) {
        return new CardElement("button_group", null, Collections.unmodifiableList(buttons));
    }

    public static CardElement buttonGroup(CardButton... buttons) {
        return new CardElement("button_group", null, Collections.unmodifiableList(Arrays.asList(buttons)));
    }

    public boolean isMarkdown() {
        return "markdown".equals(type);
    }

    public boolean isButtonGroup() {
        return "button_group".equals(type);
    }

    public String getType() {
        return type;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }

    public List<CardButton> getButtons() {
        return buttons;
    }
}

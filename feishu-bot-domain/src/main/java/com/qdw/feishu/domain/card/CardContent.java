package com.qdw.feishu.domain.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * IM 无关的卡片内容模型。
 *
 * 描述卡片的结构化内容，由 CardRenderer 转换为具体 IM 平台的卡片格式。
 */
public class CardContent {

    private final String headerTitle;
    private final String headerTemplate;
    private final boolean wideScreenMode;
    private final List<CardElement> elements;

    private CardContent(Builder builder) {
        this.headerTitle = builder.headerTitle;
        this.headerTemplate = builder.headerTemplate;
        this.wideScreenMode = builder.wideScreenMode;
        this.elements = Collections.unmodifiableList(new ArrayList<>(builder.elements));
    }

    public String getHeaderTitle() {
        return headerTitle;
    }

    public String getHeaderTemplate() {
        return headerTemplate;
    }

    public boolean isWideScreenMode() {
        return wideScreenMode;
    }

    public List<CardElement> getElements() {
        return elements;
    }

    /** 创建一个新的 builder，可修改已有卡片内容 */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.headerTitle = this.headerTitle;
        b.headerTemplate = this.headerTemplate;
        b.wideScreenMode = this.wideScreenMode;
        b.elements = new ArrayList<>(this.elements);
        return b;
    }

    /** 便捷方法：添加 markdown 段落，返回新的 CardContent */
    public CardContent addMarkdown(String content) {
        return toBuilder().addElement(CardElement.markdown(content)).build();
    }

    /** 便捷方法：添加按钮组，返回新的 CardContent */
    public CardContent addButtonGroup(List<CardButton> buttons) {
        return toBuilder().addElement(CardElement.buttonGroup(buttons)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String headerTitle;
        private String headerTemplate;
        private boolean wideScreenMode;
        private List<CardElement> elements = new ArrayList<>();

        public Builder headerTitle(String headerTitle) {
            this.headerTitle = headerTitle;
            return this;
        }

        public Builder headerTemplate(String headerTemplate) {
            this.headerTemplate = headerTemplate;
            return this;
        }

        public Builder wideScreenMode(boolean wideScreenMode) {
            this.wideScreenMode = wideScreenMode;
            return this;
        }

        public Builder addElement(CardElement element) {
            this.elements.add(element);
            return this;
        }

        public Builder elements(List<CardElement> elements) {
            this.elements = new ArrayList<>(elements);
            return this;
        }

        public CardContent build() {
            return new CardContent(this);
        }
    }
}

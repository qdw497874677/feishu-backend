package com.qdw.feishu.domain.card;

/**
 * 卡片按钮值对象。
 *
 * action 是按钮的业务语义标识（如 "wizard_select_project:feishu-backend"），
 * label 是按钮显示文本，style 控制视觉样式。
 */
public class CardButton {

    private final String label;
    private final String action;
    private final String style;

    private CardButton(String label, String action, String style) {
        this.label = label;
        this.action = action;
        this.style = style;
    }

    public static CardButton primary(String label, String action) {
        return new CardButton(label, action, "primary");
    }

    public static CardButton defaults(String label, String action) {
        return new CardButton(label, action, "default");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLabel() {
        return label;
    }

    public String getAction() {
        return action;
    }

    public String getStyle() {
        return style;
    }

    public static class Builder {
        private String label;
        private String action;
        private String style = "default";

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder style(String style) {
            this.style = style;
            return this;
        }

        public CardButton build() {
            return new CardButton(label, action, style);
        }
    }
}

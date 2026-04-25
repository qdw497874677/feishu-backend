package com.qdw.feishu.domain.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardContent 领域模型测试
 */
class CardContentTest {

    @Test
    @DisplayName("builder 构建含 header + markdown + buttons 的卡片内容")
    void should_buildCardContent_withHeaderMarkdownButtons() {
        CardContent card = CardContent.builder()
            .headerTitle("测试标题")
            .headerTemplate("blue")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("测试内容"))
            .addElement(CardElement.buttonGroup(
                CardButton.primary("按钮1", "action1"),
                CardButton.defaults("按钮2", "action2")
            ))
            .build();

        assertEquals("测试标题", card.getHeaderTitle());
        assertEquals("blue", card.getHeaderTemplate());
        assertTrue(card.isWideScreenMode());
        assertEquals(2, card.getElements().size());
        assertTrue(card.getElements().get(0).isMarkdown());
        assertEquals("测试内容", card.getElements().get(0).getMarkdownContent());
        assertTrue(card.getElements().get(1).isButtonGroup());
        assertEquals(2, card.getElements().get(1).getButtons().size());
    }

    @Test
    @DisplayName("toBuilder 可修改已有卡片内容")
    void should_modifyCardContent_withToBuilder() {
        CardContent original = CardContent.builder()
            .headerTitle("原始标题")
            .headerTemplate("blue")
            .wideScreenMode(true)
            .addElement(CardElement.markdown("段落1"))
            .build();

        CardContent modified = original.toBuilder()
            .headerTitle("修改后标题")
            .addElement(CardElement.markdown("段落2"))
            .build();

        assertEquals("原始标题", original.getHeaderTitle());
        assertEquals(1, original.getElements().size());
        assertEquals("修改后标题", modified.getHeaderTitle());
        assertEquals(2, modified.getElements().size());
    }

    @Test
    @DisplayName("addMarkdown 便捷方法返回新 CardContent")
    void should_addMarkdown_returningNewInstance() {
        CardContent original = CardContent.builder()
            .headerTitle("标题")
            .build();

        CardContent withMarkdown = original.addMarkdown("新增段落");

        assertEquals(0, original.getElements().size());
        assertEquals(1, withMarkdown.getElements().size());
        assertTrue(withMarkdown.getElements().get(0).isMarkdown());
        assertEquals("新增段落", withMarkdown.getElements().get(0).getMarkdownContent());
    }

    @Test
    @DisplayName("addButtonGroup 便捷方法返回新 CardContent")
    void should_addButtonGroup_returningNewInstance() {
        CardContent original = CardContent.builder()
            .headerTitle("标题")
            .build();

        List<CardButton> buttons = Arrays.asList(
            CardButton.primary("确认", "confirm"),
            CardButton.defaults("取消", "cancel")
        );
        CardContent withButtons = original.addButtonGroup(buttons);

        assertEquals(0, original.getElements().size());
        assertEquals(1, withButtons.getElements().size());
        assertTrue(withButtons.getElements().get(0).isButtonGroup());
        assertEquals(2, withButtons.getElements().get(0).getButtons().size());
    }

    @Test
    @DisplayName("CardButton builder 正确设置属性")
    void should_buildCardButton_withAllProperties() {
        CardButton button = CardButton.builder()
            .label("测试按钮")
            .action("test_action")
            .style("primary")
            .build();

        assertEquals("测试按钮", button.getLabel());
        assertEquals("test_action", button.getAction());
        assertEquals("primary", button.getStyle());
    }

    @Test
    @DisplayName("CardButton 工厂方法设置正确样式")
    void should_createButtons_withFactoryMethods() {
        CardButton primary = CardButton.primary("主按钮", "primary_action");
        CardButton defaultBtn = CardButton.defaults("默认按钮", "default_action");

        assertEquals("primary", primary.getStyle());
        assertEquals("default", defaultBtn.getStyle());
    }

    @Test
    @DisplayName("CardElement markdown 和 buttonGroup 类型区分正确")
    void should_distinguishElementTypes() {
        CardElement md = CardElement.markdown("content");
        CardElement bg = CardElement.buttonGroup(CardButton.primary("btn", "act"));

        assertTrue(md.isMarkdown());
        assertFalse(md.isButtonGroup());
        assertFalse(bg.isMarkdown());
        assertTrue(bg.isButtonGroup());
    }
}

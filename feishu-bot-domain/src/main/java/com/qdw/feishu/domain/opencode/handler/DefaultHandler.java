package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeMessageFormatter;
import com.qdw.feishu.domain.opencode.WizardManager;
import lombok.extern.slf4j.Slf4j;

/**
 * 处理未识别的子命令和向导 action（wizard_*）。
 *
 * <p>向导 action 由卡片按钮点击触发，以 "wizard_" 前缀标识。
 * 非向导的未知命令返回帮助提示。
 */
@Slf4j
public class DefaultHandler implements SubCommandHandler {

    private final OpenCodeMessageFormatter messageFormatter;
    private final WizardManager wizardManager;
    private final CardRenderer cardRenderer;
    private final FeishuGateway feishuGateway;

    public DefaultHandler(OpenCodeMessageFormatter messageFormatter,
                          WizardManager wizardManager,
                          CardRenderer cardRenderer,
                          FeishuGateway feishuGateway) {
        this.messageFormatter = messageFormatter;
        this.wizardManager = wizardManager;
        this.cardRenderer = cardRenderer;
        this.feishuGateway = feishuGateway;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        String subCommand = parts.length > 1 ? parts[1] : "";
        if (isWizardAction(subCommand)) {
            return handleWizardAction(subCommand, message, messageContext);
        }
        return AppExecutionResult.text(messageFormatter.buildUnknownCommandResponse(subCommand, ""));
    }

    private boolean isWizardAction(String subCommand) {
        return subCommand != null && subCommand.startsWith("wizard_");
    }

    private AppExecutionResult handleWizardAction(String subCommand, Message message, MessageContext messageContext) {
        String topicId = message.getTopicId();
        String chatId = message.getChatId();

        if (wizardManager == null) {
            return AppExecutionResult.text("❌ 向导功能不可用");
        }

        try {
            WizardManager.WizardResult wizardResult = wizardManager.handleAction(subCommand, chatId, topicId);

            if (wizardResult == null) {
                return AppExecutionResult.text(
                    messageFormatter.buildUnknownCommandResponse(subCommand, ""));
            }

            if (wizardResult.isCompleted()) {
                return handleWizardCompleted(wizardResult, message, messageContext, topicId);
            }

            if (wizardResult.getCardContent() != null && cardRenderer != null) {
                try {
                    CardActionContext actionCtx = CardActionContext.from(messageContext);
                    String cardJson = cardRenderer.render(wizardResult.getCardContent(), actionCtx);
                    feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
                    return AppExecutionResult.noReply();
                } catch (Exception e) {
                    log.warn("向导卡片发送失败，降级为文本: {}", e.getMessage());
                }
            }

            if (wizardResult.getTextContent() != null) {
                return AppExecutionResult.text(wizardResult.getTextContent());
            }

            return AppExecutionResult.noReply();

        } catch (Exception e) {
            log.error("处理向导 action 失败: subCommand={}", subCommand, e);
            return AppExecutionResult.text("❌ 向导处理失败：" + e.getMessage());
        }
    }

    private AppExecutionResult handleWizardCompleted(WizardManager.WizardResult wizardResult,
                                                      Message message, MessageContext messageContext,
                                                      String topicId) {
        String sessionId = wizardResult.getOpenCodeSessionId();
        if (wizardResult.getCardContent() != null && cardRenderer != null) {
            try {
                CardActionContext actionCtx = CardActionContext.from(messageContext);
                String cardJson = cardRenderer.render(wizardResult.getCardContent(), actionCtx);
                feishuGateway.sendInteractiveMessage(message, cardJson, topicId);
                return AppExecutionResult.withSession(null, sessionId, false);
            } catch (Exception e) {
                log.warn("向导完成卡片发送失败，降级为文本: {}", e.getMessage());
            }
        }
        return AppExecutionResult.withSession(
            "✅ 已绑定会话 `" + sessionId + "`\n\n💬 现在可以直接输入问题开始对话！",
            sessionId, false);
    }
}

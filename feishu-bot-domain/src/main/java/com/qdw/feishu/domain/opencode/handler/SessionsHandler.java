package com.qdw.feishu.domain.opencode.handler;

import com.qdw.feishu.domain.app.AppExecutionResult;
import com.qdw.feishu.domain.card.CardActionContext;
import com.qdw.feishu.domain.card.CardButton;
import com.qdw.feishu.domain.card.CardContent;
import com.qdw.feishu.domain.card.CardElement;
import com.qdw.feishu.domain.gateway.CardRenderer;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.opencode.OpenCodeMessageFormatter;
import com.qdw.feishu.domain.opencode.OpenCodeSessionManager;
import com.qdw.feishu.domain.opencode.SessionInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理 sessions / s 子命令：查询项目的最近会话列表。
 *
 * <p>话题中优先使用卡片格式（含按钮），失败时降级为纯文本。
 */
@Slf4j
public class SessionsHandler implements SubCommandHandler {

    private final OpenCodeSessionManager sessionManager;
    private final OpenCodeMessageFormatter messageFormatter;
    private final OpenCodeGateway openCodeGateway;
    private final CardRenderer cardRenderer;
    private final FeishuGateway feishuGateway;

    public SessionsHandler(OpenCodeSessionManager sessionManager,
                           OpenCodeMessageFormatter messageFormatter,
                           OpenCodeGateway openCodeGateway,
                           CardRenderer cardRenderer,
                           FeishuGateway feishuGateway) {
        this.sessionManager = sessionManager;
        this.messageFormatter = messageFormatter;
        this.openCodeGateway = openCodeGateway;
        this.cardRenderer = cardRenderer;
        this.feishuGateway = feishuGateway;
    }

    @Override
    public AppExecutionResult handle(Message message, String[] parts, MessageContext messageContext) {
        String project = parts.length >= 3 ? parts[2] : (parts.length >= 2 ? parts[1] : null);
        if (project == null || project.isBlank()
                || project.equalsIgnoreCase("sessions") || project.equalsIgnoreCase("s")) {
            return AppExecutionResult.text(messageFormatter.buildNewCommandUsage(false));
        }

        AppExecutionResult cardResult = trySendSessionListCard(project, message, messageContext);
        if (cardResult != null) {
            return cardResult;
        }

        return AppExecutionResult.text(sessionManager.handleSessionsCommand(parts));
    }

    /**
     * 尝试发送会话列表卡片。
     * 成功时直接通过 feishuGateway 发送卡片，返回 noReply。
     * 失败时返回 null，调用者降级为文本。
     */
    private AppExecutionResult trySendSessionListCard(String project, Message message, MessageContext messageContext) {
        try {
            List<SessionInfo> sessions = openCodeGateway.listRecentSessionsStructured(project, 10);

            CardActionContext actionCtx = CardActionContext.from(messageContext);
            List<CardElement> elements = new ArrayList<>();

            if (sessions.isEmpty()) {
                elements.add(CardElement.markdown(
                    "**" + project + "** 暂无会话记录\n\n使用下方按钮创建新会话，或 `/oc new " + project + " <问题>` 直接开始"));
                elements.add(CardElement.buttonGroup(
                    CardButton.primary("+ 新建会话", "wizard_new_session:" + project)
                ));
            } else {
                elements.add(CardElement.markdown("**" + project + "** 的最近会话："));

                List<CardButton> sessionButtons = new ArrayList<>();
                for (SessionInfo session : sessions) {
                    String label = session.getTitle()
                        + (session.getLastPrompt() != null && !session.getLastPrompt().isBlank()
                            ? " — " + session.getLastPrompt() : "")
                        + " (" + session.getRelativeTime() + ")";
                    if (label.length() > 40) {
                        label = label.substring(0, 37) + "...";
                    }
                    sessionButtons.add(CardButton.defaults(label, "sc " + session.getSessionId()));
                }
                elements.add(CardElement.buttonGroup(sessionButtons));

                elements.add(CardElement.buttonGroup(
                    CardButton.primary("+ 新建会话", "wizard_new_session:" + project)
                ));
            }

            CardContent card = CardContent.builder()
                .headerTitle("📋 会话列表 — " + project)
                .headerTemplate("turquoise")
                .wideScreenMode(true)
                .elements(elements)
                .build();

            String cardJson = cardRenderer.render(card, actionCtx);
            feishuGateway.sendInteractiveMessage(message, cardJson, message.getTopicId());
            return AppExecutionResult.noReply();

        } catch (Exception e) {
            log.warn("卡片会话列表渲染失败，降级为文本: {}", e.getMessage());
            return null;
        }
    }
}

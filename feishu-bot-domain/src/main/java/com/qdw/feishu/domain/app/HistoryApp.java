package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.ChatHistory;
import com.qdw.feishu.domain.message.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class HistoryApp implements FishuAppI {

    private final FeishuGateway feishuGateway;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public HistoryApp(FeishuGateway feishuGateway) {
        this.feishuGateway = feishuGateway;
    }

    @Override
    public String getAppId() {
        return "history";
    }

    @Override
    public String getAppName() {
        return "历史查询";
    }

    @Override
    public String getDescription() {
        return "查询对话历史消息，显示最近10条消息";
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;
    }

    @Override
    public String execute(Message message) {
        log.info("=== HistoryApp.execute 开始 ===");
        log.info("应用 ID: {}", getAppId());
        log.info("输入消息: {}", message.getContent());

        ChatHistory history = feishuGateway.listMessages(
            message.getChatId(),
            message.getTopicId(),
            10,
            null
        );

        if (history.getMessages() == null || history.getMessages().isEmpty()) {
            log.info("历史消息为空");
            return "暂无历史消息";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 历史消息 ===\n\n");

        for (ChatHistory.HistoryMessage msg : history.getMessages()) {
            sb.append(String.format("[%s] %s\n",
                    msg.getSendTime().format(FORMATTER),
                    msg.getContent()));
        }

        if (history.isHasMore()) {
            sb.append("\n(还有更多消息...)");
        }

        log.info("HistoryApp.execute 完成，返回历史消息: {} 条", history.getMessages().size());
        log.info("=== HistoryApp.execute 结束 ===\n");

        return sb.toString();
    }
}

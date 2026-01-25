package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.AppRegistry;
import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.app.ReplyMode;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.model.TopicMapping;
import com.qdw.feishu.domain.router.AppRouter;
import com.lark.oapi.service.im.v1.model.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class BotMessageService {

    private final FeishuGateway feishuGateway;
    private final AppRouter appRouter;
    private final AppRegistry appRegistry;
    private final TopicMappingGateway topicMappingGateway;

    public BotMessageService(FeishuGateway feishuGateway,
                            AppRouter appRouter,
                            AppRegistry appRegistry,
                            TopicMappingGateway topicMappingGateway) {
        this.feishuGateway = feishuGateway;
        this.appRouter = appRouter;
        this.appRegistry = appRegistry;
        this.topicMappingGateway = topicMappingGateway;
    }

    private String getOpenIdFromSender(Object sender) {
        if (sender instanceof UserId) {
            UserId userId = (UserId) sender;
            return userId.getOpenId() != null ? userId.getOpenId() : "";
        }
        if (sender instanceof String) {
            return (String) sender;
        }
        return "";
    }

    public void processMessage(Message message) {
        log.info("开始处理消息: content={}", message.getContent());

        String topicId = message.getTopicId();
        FishuAppI app;

        if (topicId != null && !topicId.isEmpty()) {
            log.info("消息来自话题: topicId={}", topicId);
            var mapping = topicMappingGateway.findByTopicId(topicId);
            if (mapping.isPresent()) {
                String appId = mapping.get().getAppId();
                log.info("找到话题映射: topicId={}, appId={}", topicId, appId);
                app = appRegistry.getApp(appId).orElse(null);
                if (app == null) {
                    log.error("应用不存在: appId={}", appId);
                    sendErrorReply(message, "应用不可用");
                    return;
                }
                mapping.get().activate();
                topicMappingGateway.save(mapping.get());
            } else {
                log.warn("话题映射不存在: topicId={}，降级为默认处理", topicId);
                handleUnknownTopic(message);
                return;
            }
        } else {
            String content = message.getContent().trim();
            if (!content.startsWith("/")) {
                log.warn("未找到匹配的应用");
                return;
            }
            String appId = extractAppId(content);
            app = appRegistry.getApp(appId).orElse(null);
            if (app == null) {
                log.warn("应用不存在: appId={}", appId);
                return;
            }
        }

        String replyContent = app.execute(message);
        if (replyContent == null || replyContent.isEmpty()) {
            log.warn("应用返回空回复");
            return;
        }

        ReplyMode replyMode = app.getReplyMode();
        String finalTopicId = topicId;

        if (replyMode == ReplyMode.TOPIC && (topicId == null || topicId.isEmpty())) {
            String newTopicId = "topic_" + System.currentTimeMillis();
            log.info("创建新话题: topicId={}", newTopicId);

            TopicMapping mapping = new TopicMapping(newTopicId, app.getAppId());
            topicMappingGateway.save(mapping);

            finalTopicId = newTopicId;
        }

        SendResult result = feishuGateway.sendMessage(message, replyContent, finalTopicId);

        if (result.isSuccess()) {
            log.info("发送回复成功: topicId={}", finalTopicId);
        } else {
            log.error("发送回复失败: error={}", result.getErrorMessage());
        }
    }

    private String extractAppId(String content) {
        String[] parts = content.split("\\s+", 2);
        return parts[0].substring(1).toLowerCase();
    }

    private void handleUnknownTopic(Message message) {
        String errorReply = "话题已失效，请重新发送命令触发应用。";
        feishuGateway.sendMessage(message, errorReply, null);
    }

    private void sendErrorReply(Message message, String error) {
        feishuGateway.sendMessage(message, "错误: " + error, null);
    }

    public SendResult handleMessage(Message message) {
        log.info("=== BotMessageService.handleMessage 开始 ===");
        log.info("消息内容: {}", message.getContent());

        try {
            message.validate();
            log.info("消息验证通过");

            String reply = null;
            if (message.getContent().trim().startsWith("/")) {
                log.info("检测到命令，使用 AppRouter 路由");
                reply = appRouter.route(message);
                log.info("应用路由完成, 回复内容: {}", reply);
            }

            if (reply == null) {
                log.info("不是命令，路由到 help 应用");
                reply = appRegistry.getApp("help")
                    .map(app -> app.execute(message))
                    .orElse("未找到帮助应用");
            }

            String openId = getOpenIdFromSender(message.getSender().getOpenId());
            log.info("准备发送回复给: {}", openId);

            SendResult result = feishuGateway.sendReply(openId, reply);
            log.info("回复发送结果: {}", result.isSuccess());

            message.markProcessed();
            log.info("=== BotMessageService.handleMessage 完成 ===\n");

            return result;

        } catch (MessageBizException e) {
            log.error("业务异常: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("系统异常: 消息处理失败", e);
            throw new MessageSysException("MESSAGE_HANDLE_FAILED", "消息处理失败", e);
        }
    }
}

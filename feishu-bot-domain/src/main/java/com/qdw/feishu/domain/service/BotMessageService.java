package com.qdw.feishu.domain.service;

import com.qdw.feishu.domain.app.AppRegistry;
import com.qdw.feishu.domain.exception.MessageBizException;
import com.qdw.feishu.domain.exception.MessageSysException;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.SendResult;
import com.qdw.feishu.domain.router.AppRouter;
import com.lark.oapi.service.im.v1.model.UserId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BotMessageService {

    private final FeishuGateway feishuGateway;
    private final AppRouter appRouter;
    private final AppRegistry appRegistry;

    public BotMessageService(FeishuGateway feishuGateway,
                            AppRouter appRouter,
                            AppRegistry appRegistry) {
        this.feishuGateway = feishuGateway;
        this.appRouter = appRouter;
        this.appRegistry = appRegistry;
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

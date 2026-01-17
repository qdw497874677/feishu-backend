package com.qdw.feishu.adapter.web;

import com.alibaba.cola.dto.Response;
import com.qdw.feishu.client.message.MessageServiceI;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import com.qdw.feishu.domain.gateway.WebhookValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 飞书 Webhook 适配器
 *
 * 负责接收飞书平台的事件推送
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class FeishuWebhookController {

    @Autowired
    private MessageServiceI messageService;

    @Autowired
    private WebhookValidator webhookValidator;

    /**
     * 飞书事件推送接口
     *
     * 验证签名后转发到应用层处理
     *
     * @param payload 事件 JSON 数据
     * @param request HTTP 请求对象
     * @return 处理结果
     */
    @PostMapping("/feishu")
    public Response handleEvent(@RequestBody String payload, HttpServletRequest request) {
        log.info("Received Feishu webhook");

        try {
            Map<String, String> headers = extractHeaders(request);

            WebhookValidator.WebhookValidationResult validationResult =
                webhookValidator.validate(headers, payload);

            if (!validationResult.isValid()) {
                log.warn("Webhook validation failed: {}", validationResult.getError());
                return Response.buildFailure("SIGNATURE_INVALID", validationResult.getError());
            }

            log.debug("Webhook signature validated successfully, payload: {}", payload);

            ReceiveMessageCmd cmd = new ReceiveMessageCmd();
            cmd.setMessageId("msg_" + System.currentTimeMillis());
            cmd.setContent(payload);
            cmd.setSenderOpenId("user_open_id");
            cmd.setSenderUserId("user_id");
            cmd.setSenderName("Feishu User");

            return messageService.receiveMessage(cmd);

        } catch (Exception e) {
            log.error("Error handling Feishu webhook", e);
            return Response.buildFailure("PROCESS_ERROR", "处理失败: " + e.getMessage());
        }
    }

    /**
     * 提取 HTTP 请求头
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }

        return headers;
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Response health() {
        return Response.of("OK");
    }
}

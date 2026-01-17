package com.qdw.feishu.adapter.web;

import com.alibaba.cola.dto.Response;
import com.qdw.feishu.client.message.MessageServiceI;
import com.qdw.feishu.client.message.ReceiveMessageCmd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 飞书事件推送接口
     * 
     * 验证签名后转发到应用层处理
     * 
     * @param payload 事件 JSON 数据
     * @return 处理结果
     */
    @PostMapping("/feishu")
    public Response handleEvent(@RequestBody String payload) {
        log.info("Received Feishu webhook: {}", payload);

        try {
            // 解析事件（简化版，实际需要 JSON 解析）
            // 这里假设直接调用消息处理
            // 生产环境需要完整的事件解析和签名验证

            // 构造命令并执行
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
     * 健康检查接口
     */
    @GetMapping("/health")
    public Response health() {
        return Response.of("OK");
    }
}

package com.qdw.feishu.adapter.test;

import com.qdw.feishu.app.message.BotMessageAppService;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 消息测试控制器
 * 
 * 用途：在不依赖飞书客户端的情况下测试消息处理功能
 * 
 * 安全限制：
 * - 仅在 dev 和 test 环境启用
 */
@Slf4j
@RestController
@RequestMapping("/test/message")
@Profile({"dev", "test", "default"})
public class MessageTestController {
    
    @Autowired
    private BotMessageAppService botMessageAppService;
    
    /**
     * 发送测试消息
     * 
     * @param content 消息内容
     * @param topicId 话题ID（可选）
     * @param openId 用户OpenID（可选，默认 test_open_id）
     * @return 处理结果
     */
    @PostMapping
    public ResponseEntity<String> sendTestMessage(
        @RequestParam String content,
        @RequestParam(required = false) String topicId,
        @RequestParam(required = false, defaultValue = "test_open_id") String openId
    ) {
        log.info("=== 测试消息开始 ===");
        log.info("内容: {}, 话题ID: {}, 用户: {}", content, topicId, openId);
        
        try {
            // 1. 构造测试消息
            Message message = new Message();
            message.setContent(content);
            message.setChatId("test_chat_id");
            message.setMessageId("test_msg_" + System.currentTimeMillis());
            message.setEventId("test_event_" + System.currentTimeMillis());
            
            // 设置发送者
            Sender sender = new Sender(openId, "测试用户");
            message.setSender(sender);
            
            // 设置话题（如果有）
            if (topicId != null && !topicId.isEmpty()) {
                message.setTopicId(topicId);
                message.setRootId(topicId);
            }
            
            // 2. 调用消息处理服务
            botMessageAppService.handleMessage(message);
            
            log.info("=== 测试消息处理完成 ===");
            return ResponseEntity.ok("✅ 消息已处理，请查看日志: tail -f /tmp/feishu-run.log");
            
        } catch (Exception e) {
            log.error("测试消息处理失败", e);
            return ResponseEntity.internalServerError()
                .body("❌ 处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试帮助命令
     */
    @PostMapping("/help")
    public ResponseEntity<String> testHelp() {
        return sendTestMessage("/help", null, "test_user");
    }
    
    /**
     * 测试时间命令
     */
    @PostMapping("/time")
    public ResponseEntity<String> testTime() {
        return sendTestMessage("/time", null, "test_user");
    }
    
    /**
     * 测试 Bash 命令
     */
    @PostMapping("/bash")
    public ResponseEntity<String> testBash(@RequestParam String command) {
        return sendTestMessage("/bash " + command, null, "test_user");
    }
}

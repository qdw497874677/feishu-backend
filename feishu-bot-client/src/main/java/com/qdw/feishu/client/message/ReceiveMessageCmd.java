package com.qdw.feishu.client.message;

import com.alibaba.cola.dto.Command;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 接收消息命令对象
 * 
 * 用于传递飞书 Webhook 接收到的消息数据到应用层
 */
@Data
public class ReceiveMessageCmd extends Command {

    @NotBlank(message = "消息ID不能为空")
    private String messageId;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    @NotBlank(message = "发送者Open ID不能为空")
    private String senderOpenId;

    private String senderUserId;

    private String senderName;
}

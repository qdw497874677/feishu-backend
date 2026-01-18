package com.qdw.feishu.client.message;

import com.alibaba.cola.dto.Query;
import lombok.Data;

/**
 * 接收消息查询对象
 * 用于查询消息历史记录
 */
@Data
public class ReceiveMessageQry extends Query {

    private String messageId;

    private String senderOpenId;
}

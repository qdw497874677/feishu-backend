package com.qdw.feishu.client.message;

import com.alibaba.cola.dto.Query;
import lombok.Data;

/**
 * 接收消息查询对象
 * 用于查询消息历史记录
 */
@Data
@Query
public class ReceiveMessageQry {

    private String messageId;

    private String senderOpenId;
}

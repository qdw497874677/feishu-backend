package com.qdw.feishu.domain.message;

import com.qdw.feishu.domain.gateway.Sender;
import lombok.AllArgsConstructor;

/**
 * 发送者值对象
 */
@AllArgsConstructor
public class Sender {

    private String openId;
    private String userId;
    private String name;
}

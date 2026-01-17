package com.qdw.feishu.domain.message;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发送者值对象
 */
@AllArgsConstructor
@Data
public class Sender {

    private String openId;
    private String userId;
    private String name;
}

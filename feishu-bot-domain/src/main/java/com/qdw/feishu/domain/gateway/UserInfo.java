package com.qdw.feishu.domain.gateway;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户信息值对象
 */
@Data
@AllArgsConstructor
public class UserInfo {

    private String openId;
    private String userId;
    private String name;
}

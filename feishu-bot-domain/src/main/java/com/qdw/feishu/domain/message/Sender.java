package com.qdw.feishu.domain.message;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.lark.oapi.service.im.v1.model.UserId;

@Data
@NoArgsConstructor
public class Sender {

    private Object userId;
    private String openId;
    private String name;

    public String getOpenId() {
        if (userId instanceof UserId) {
            UserId uid = (UserId) userId;
            return uid.getOpenId() != null ? uid.getOpenId() : "";
        }
        if (userId instanceof String) {
            return (String) userId;
        }
        return "";
    }
}

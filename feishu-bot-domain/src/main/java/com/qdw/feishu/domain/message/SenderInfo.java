package com.qdw.feishu.domain.message;

import lombok.Data;

/**
 * 发送者信息值对象
 *
 * 封装飞书发送者信息，不依赖外部 SDK
 */
@Data
public class SenderInfo {

    private final String openId;
    private final String name;

    public SenderInfo(String openId, String name) {
        this.openId = openId != null ? openId : "";
        this.name = name != null ? name : "";
    }

    public static SenderInfo from(String openId) {
        return new SenderInfo(openId, "");
    }

    public boolean isEmpty() {
        return openId.isEmpty();
    }
}

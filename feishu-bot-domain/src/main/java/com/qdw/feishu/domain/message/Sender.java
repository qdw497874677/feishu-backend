package com.qdw.feishu.domain.message;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Sender {

    private String openId;
    private String name;

    public Sender(String openId, String name) {
        this.openId = openId != null ? openId : "";
        this.name = name != null ? name : "";
    }

    public boolean isEmpty() {
        return openId == null || openId.isEmpty();
    }
}

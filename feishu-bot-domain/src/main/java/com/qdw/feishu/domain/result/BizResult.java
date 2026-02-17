package com.qdw.feishu.domain.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizResult {
    private boolean success;
    private Object data;
    private String message;
    
    public static BizResult success(Object data) {
        return BizResult.builder()
            .success(true)
            .data(data)
            .build();
    }
    
    public static BizResult success(String message, Object data) {
        return BizResult.builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    public static BizResult failure(String message) {
        return BizResult.builder()
            .success(false)
            .message(message)
            .build();
    }
    
    public static BizResult of(String message) {
        return BizResult.builder()
            .success(true)
            .message(message)
            .build();
    }
}

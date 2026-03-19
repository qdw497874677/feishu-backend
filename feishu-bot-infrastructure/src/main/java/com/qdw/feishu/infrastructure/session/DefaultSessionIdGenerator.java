package com.qdw.feishu.infrastructure.session;

import com.qdw.feishu.domain.session.SessionIdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 默认会话 ID 生成器
 * 
 * 格式: {appId}_{timestamp}_{random}
 */
@Component
public class DefaultSessionIdGenerator implements SessionIdGenerator {
    
    @Override
    public String generate(String appId) {
        String random = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s_%d_%s", appId, System.currentTimeMillis(), random);
    }
}

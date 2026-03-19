package com.qdw.feishu.domain.session;

/**
 * 会话 ID 生成器接口
 */
public interface SessionIdGenerator {
    
    /**
     * 生成新的会话 ID
     * 
     * @param appId 应用 ID
     * @return 新的会话 ID
     */
    String generate(String appId);
}

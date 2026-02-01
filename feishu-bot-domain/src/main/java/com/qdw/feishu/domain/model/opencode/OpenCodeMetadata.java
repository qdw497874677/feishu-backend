package com.qdw.feishu.domain.model.opencode;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenCode 应用特定的元数据
 *
 * 存储在 TopicMapping.metadata 的 "opencode" 命名空间下
 */
@Data
@NoArgsConstructor
public class OpenCodeMetadata {

    /** OpenCode 会话 ID */
    private String sessionId;

    /** 最后执行的命令 */
    private String lastCommand;

    /** 命令执行计数 */
    private int commandCount;

    /** 会话创建时间 */
    private long sessionCreatedAt;

    /** 最后活跃时间 */
    private long lastActiveAt;

    /**
     * 创建默认元数据
     *
     * @return 新的 OpenCodeMetadata 实例
     */
    public static OpenCodeMetadata create() {
        OpenCodeMetadata metadata = new OpenCodeMetadata();
        metadata.setCommandCount(0);
        metadata.setSessionCreatedAt(System.currentTimeMillis());
        metadata.setLastActiveAt(System.currentTimeMillis());
        return metadata;
    }

    /**
     * 增加命令计数
     *
     * 增加计数并更新最后活跃时间
     */
    public void incrementCommandCount() {
        this.commandCount++;
        this.lastActiveAt = System.currentTimeMillis();
    }
}

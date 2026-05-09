package com.qdw.feishu.domain.model.opencode;

import com.qdw.feishu.domain.session.AppSessionData;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenCode 会话数据
 */
@Data
@NoArgsConstructor
public class OpenCodeSessionData implements AppSessionData {
    
    /** OpenCode 会话 ID */
    private String openCodeSessionId;

    /** OpenCode 会话对应的项目工作目录 */
    private String projectDirectory;
    
    /** 最后执行的命令 */
    private String lastCommand;
    
    /** 命令执行计数 */
    private int commandCount;
    
    /** 是否显式初始化 */
    private boolean explicitlyInitialized;
    
    public static OpenCodeSessionData create(String openCodeSessionId) {
        return create(openCodeSessionId, null);
    }

    public static OpenCodeSessionData create(String openCodeSessionId, String projectDirectory) {
        OpenCodeSessionData data = new OpenCodeSessionData();
        data.setOpenCodeSessionId(openCodeSessionId);
        data.setProjectDirectory(projectDirectory);
        data.setCommandCount(0);
        data.setExplicitlyInitialized(false);
        return data;
    }
    
    public void incrementCommandCount() {
        this.commandCount++;
    }
}

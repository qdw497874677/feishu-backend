package com.qdw.feishu.domain.session;

/**
 * Unified state enum representing the state of a context-session binding.
 *
 * Replaces the former dual state model (TopicState + ContextSessionState).
 * This is the single source of truth for all state detection.
 *
 * States:
 * - UNBOUND: No binding to any application (formerly NON_TOPIC).
 * - BOUND_TO_OTHER_APP: The IM context is bound to a different application.
 * - IN_APP_NO_SESSION: Bound to target app but without an active session (formerly UNINITIALIZED).
 * - IN_APP_WITH_SESSION: Bound to target app with an active session (formerly INITIALIZED).
 */
public enum ContextSessionState {

    /** 未绑定（非话题，无任何应用绑定） */
    UNBOUND("未绑定"),

    /** 已绑定其他应用 */
    BOUND_TO_OTHER_APP("已绑定其他应用"),

    /** 未初始化（已绑定目标应用但无活跃会话） */
    IN_APP_NO_SESSION("未初始化"),

    /** 已初始化（已绑定目标应用且有活跃会话） */
    IN_APP_WITH_SESSION("已初始化");

    private final String description;

    ContextSessionState(String description) {
        this.description = description;
    }

    /**
     * 获取状态描述
     *
     * @return 中文描述
     */
    public String getDescription() {
        return description;
    }
}

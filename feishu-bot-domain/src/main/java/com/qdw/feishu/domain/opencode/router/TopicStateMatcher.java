package com.qdw.feishu.domain.opencode.router;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.session.ContextSessionState;

/**
 * 话题状态匹配器
 *
 * 用于判断命令在特定状态下是否可用
 */
@FunctionalInterface
public interface TopicStateMatcher {
    /**
     * 判断是否匹配当前状态
     *
     * @param state 当前话题状态
     * @param message 消息对象
     * @return true 表示匹配，false 表示不匹配
     */
    boolean matches(ContextSessionState state, Message message);

    /**
     * 创建精确匹配器
     *
     * @param targetState 目标状态
     * @return 精确匹配该状态的匹配器
     */
    static TopicStateMatcher exactState(ContextSessionState targetState) {
        return (state, message) -> state == targetState;
    }

    /**
     * 创建多状态匹配器（满足任一即可）
     *
     * @param states 允许的状态列表
     * @return 匹配任一状态的匹配器
     */
    static TopicStateMatcher anyOf(ContextSessionState... states) {
        return (state, message) -> {
            for (ContextSessionState s : states) {
                if (state == s) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * 创建非状态匹配器
     *
     * @param excludedState 排除的状态
     * @return 不匹配该状态的匹配器
     */
    static TopicStateMatcher not(ContextSessionState excludedState) {
        return (state, message) -> state != excludedState;
    }

    /**
     * 创建组合匹配器（两个条件都满足）
     *
     * @param matcher1 第一个匹配器
     * @param matcher2 第二个匹配器
     * @return 组合匹配器
     */
    static TopicStateMatcher and(TopicStateMatcher matcher1, TopicStateMatcher matcher2) {
        return (state, message) -> matcher1.matches(state, message) && matcher2.matches(state, message);
    }

    /**
     * 创建组合匹配器（满足任一条件即可）
     *
     * @param matcher1 第一个匹配器
     * @param matcher2 第二个匹配器
     * @return 组合匹配器
     */
    static TopicStateMatcher or(TopicStateMatcher matcher1, TopicStateMatcher matcher2) {
        return (state, message) -> matcher1.matches(state, message) || matcher2.matches(state, message);
    }
}

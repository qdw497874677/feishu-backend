package com.qdw.feishu.domain.opencode.router;

import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.session.ContextSessionState;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 状态感知命令路由器
 *
 * 根据话题状态智能选择命令执行逻辑
 * 这是一个通用能力，避免在每个命令中硬编码状态判断
 */
@Slf4j
public class StateAwareCommandRouter {

    /**
     * 状态感知处理器
     *
     * @param stateMatcher 状态匹配器
     * @param executor 命令执行器
     */
    public record Handler(
        TopicStateMatcher stateMatcher,
        ICommandExecutor executor
    ) {}

    private final List<Handler> handlers = new ArrayList<>();

    /**
     * 注册命令处理器
     *
     * @param stateMatcher 状态匹配器
     * @param executor 命令执行器
     * @return this，支持链式调用
     */
    public StateAwareCommandRouter register(TopicStateMatcher stateMatcher, ICommandExecutor executor) {
        handlers.add(new Handler(stateMatcher, executor));
        return this;
    }

    /**
     * 路由到合适的处理器并执行
     *
     * @param parts 命令参数数组
     * @param message 消息对象
     * @param currentState 当前话题状态
     * @return 执行结果
     */
    public String route(String[] parts, Message message, ContextSessionState currentState) {
        log.debug("路由命令: currentState={}, partsCount={}", currentState, parts.length);

        Optional<Handler> matchedHandler = handlers.stream()
            .filter(h -> h.stateMatcher().matches(currentState, message))
            .findFirst();

        if (matchedHandler.isPresent()) {
            log.debug("找到匹配的处理器，执行命令");
            return matchedHandler.get().executor().execute(parts, message);
        }

        log.warn("未找到匹配的处理器: currentState={}", currentState);
        return buildNotAvailableMessage(currentState);
    }

    /**
     * 构建命令不可用提示
     */
    private String buildNotAvailableMessage(ContextSessionState state) {
        return switch (state) {
            case UNBOUND -> "❌ 此命令在话题外不可用\n\n💡 提示：请使用 `/opencode chatnow` 立即开始对话";
            case IN_APP_NO_SESSION -> "❌ 此命令需要先绑定会话\n\n💡 提示：使用 `/opencode sc <会话ID>` 绑定会话";
            case IN_APP_WITH_SESSION -> "❌ 此命令在当前状态下不可用";
            default -> "❌ 此命令在当前状态下不可用";
        };
    }

    /**
     * 清空所有注册的处理器
     */
    public void clear() {
        handlers.clear();
    }

    /**
     * 获取已注册的处理器数量
     */
    public int size() {
        return handlers.size();
    }
}

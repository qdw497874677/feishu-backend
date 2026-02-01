package com.qdw.feishu.domain.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 命令白名单配置
 * 
 * 定义不同话题状态下允许的命令集合
 */
public class CommandWhitelist {

    private final Set<String> nonTopicAllowed;
    private final Set<String> uninitializedAllowed;
    private final Set<String> initializedAllowed;

    private CommandWhitelist(Builder builder) {
        this.nonTopicAllowed = Collections.unmodifiableSet(new HashSet<>(builder.nonTopicAllowed));
        this.uninitializedAllowed = Collections.unmodifiableSet(new HashSet<>(builder.uninitializedAllowed));
        this.initializedAllowed = Collections.unmodifiableSet(new HashSet<>(builder.initializedAllowed));
    }

    /**
     * 创建构建器
     * 
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 允许所有命令（在所有状态下）
     * 
     * @return 白名单实例
     */
    public static CommandWhitelist all() {
        return builder().allowAllInAllStates().build();
    }

    /**
     * 不允许任何命令
     * 
     * @return 白名单实例
     */
    public static CommandWhitelist none() {
        return new Builder().build();
    }

    /**
     * 允许除指定命令外的所有命令
     * 
     * @param commands 要排除的命令
     * @return 白名单实例
     */
    public static CommandWhitelist allExcept(String... commands) {
        Set<String> excluded = Set.of(commands);
        return builder()
            .allowAllInState(TopicState.NON_TOPIC, excluded)
            .allowAllInState(TopicState.UNINITIALIZED, excluded)
            .allowAllInState(TopicState.INITIALIZED, excluded)
            .build();
    }

    /**
     * 检查命令是否在指定状态下被允许
     * 
     * @param command 命令名称
     * @param state 话题状态
     * @return 如果允许返回 true，否则返回 false
     */
    public boolean isCommandAllowed(String command, TopicState state) {
        Set<String> allowedSet = getAllowedSet(state);
        return allowedSet.isEmpty() || allowedSet.contains(command);
    }

    private Set<String> getAllowedSet(TopicState state) {
        return switch (state) {
            case NON_TOPIC -> nonTopicAllowed;
            case UNINITIALIZED -> uninitializedAllowed;
            case INITIALIZED -> initializedAllowed;
        };
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final Set<String> nonTopicAllowed = new HashSet<>();
        private final Set<String> uninitializedAllowed = new HashSet<>();
        private final Set<String> initializedAllowed = new HashSet<>();

        /**
         * 添加命令到所有状态
         * 
         * @param commands 命令列表
         * @return this
         */
        public Builder add(String... commands) {
            for (String command : commands) {
                nonTopicAllowed.add(command);
                uninitializedAllowed.add(command);
                initializedAllowed.add(command);
            }
            return this;
        }

        /**
         * 添加命令到指定状态
         * 
         * @param state 话题状态
         * @param commands 命令列表
         * @return this
         */
        public Builder addForState(TopicState state, String... commands) {
            Set<String> targetSet = getTargetSet(state);
            for (String command : commands) {
                targetSet.add(command);
            }
            return this;
        }

        /**
         * 允许所有命令（在所有状态下）
         * 
         * @return this
         */
        public Builder allowAllInAllStates() {
            nonTopicAllowed.clear();
            uninitializedAllowed.clear();
            initializedAllowed.clear();
            return this;
        }

        /**
         * 允许所有命令（在指定状态下，排除部分命令）
         * 
         * @param state 话题状态
         * @param except 要排除的命令集合
         * @return this
         */
        public Builder allowAllInState(TopicState state, Set<String> except) {
            Set<String> targetSet = getTargetSet(state);
            targetSet.clear();
            if (!except.isEmpty()) {
                targetSet.addAll(except);
            }
            return this;
        }

        private Set<String> getTargetSet(TopicState state) {
            return switch (state) {
                case NON_TOPIC -> nonTopicAllowed;
                case UNINITIALIZED -> uninitializedAllowed;
                case INITIALIZED -> initializedAllowed;
            };
        }

        /**
         * 构建白名单
         * 
         * @return 白名单实例
         */
        public CommandWhitelist build() {
            return new CommandWhitelist(this);
        }
    }
}

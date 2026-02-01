package com.qdw.feishu.domain.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 命令白名单配置
 *
 * 定义不同话题状态下允许的命令集合
 *
 * 支持两种模式：
 * 1. 白名单模式：只允许列表中的命令（使用 builder().add()）
 * 2. 允许所有模式：允许所有命令，除了指定的命令（使用 allExcept()）
 */
public class CommandWhitelist {

    private final Set<String> nonTopicAllowed;
    private final Set<String> uninitializedAllowed;
    private final Set<String> initializedAllowed;

    // 新增：标志位，表示是否是"允许所有"模式
    private final boolean nonTopicAllowAll;
    private final boolean uninitializedAllowAll;
    private final boolean initializedAllowAll;

    // 新增：排除集合（用于 allExcept 模式）
    private final Set<String> nonTopicExcluded;
    private final Set<String> uninitializedExcluded;
    private final Set<String> initializedExcluded;

    private CommandWhitelist(Builder builder) {
        this.nonTopicAllowed = Collections.unmodifiableSet(new HashSet<>(builder.nonTopicAllowed));
        this.uninitializedAllowed = Collections.unmodifiableSet(new HashSet<>(builder.uninitializedAllowed));
        this.initializedAllowed = Collections.unmodifiableSet(new HashSet<>(builder.initializedAllowed));

        this.nonTopicAllowAll = builder.nonTopicAllowAll;
        this.uninitializedAllowAll = builder.uninitializedAllowAll;
        this.initializedAllowAll = builder.initializedAllowAll;

        this.nonTopicExcluded = Collections.unmodifiableSet(new HashSet<>(builder.nonTopicExcluded));
        this.uninitializedExcluded = Collections.unmodifiableSet(new HashSet<>(builder.uninitializedExcluded));
        this.initializedExcluded = Collections.unmodifiableSet(new HashSet<>(builder.initializedExcluded));
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
        return switch (state) {
            case NON_TOPIC -> isCommandAllowedInState(command, nonTopicAllowAll, nonTopicAllowed, nonTopicExcluded);
            case UNINITIALIZED -> isCommandAllowedInState(command, uninitializedAllowAll, uninitializedAllowed, uninitializedExcluded);
            case INITIALIZED -> isCommandAllowedInState(command, initializedAllowAll, initializedAllowed, initializedExcluded);
        };
    }

    /**
     * 检查命令是否在指定状态下被允许（内部方法）
     *
     * @param command 命令名称
     * @param allowAll 是否是"允许所有"模式
     * @param allowedSet 允许的命令集合（白名单模式）
     * @param excludedSet 排除的命令集合（黑名单模式）
     * @return 如果允许返回 true，否则返回 false
     */
    private boolean isCommandAllowedInState(String command, boolean allowAll,
                                             Set<String> allowedSet, Set<String> excludedSet) {
        if (allowAll) {
            // 允许所有模式：只有排除列表中的命令被拒绝
            return !excludedSet.contains(command);
        } else {
            // 白名单模式：空集合表示允许所有，否则只允许列表中的命令
            return allowedSet.isEmpty() || allowedSet.contains(command);
        }
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final Set<String> nonTopicAllowed = new HashSet<>();
        private final Set<String> uninitializedAllowed = new HashSet<>();
        private final Set<String> initializedAllowed = new HashSet<>();

        private boolean nonTopicAllowAll = false;
        private boolean uninitializedAllowAll = false;
        private boolean initializedAllowAll = false;

        private final Set<String> nonTopicExcluded = new HashSet<>();
        private final Set<String> uninitializedExcluded = new HashSet<>();
        private final Set<String> initializedExcluded = new HashSet<>();

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
            nonTopicAllowAll = true;
            uninitializedAllowAll = true;
            initializedAllowAll = true;
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
            switch (state) {
                case NON_TOPIC -> {
                    nonTopicAllowAll = true;
                    nonTopicExcluded.clear();
                    if (except != null && !except.isEmpty()) {
                        nonTopicExcluded.addAll(except);
                    }
                }
                case UNINITIALIZED -> {
                    uninitializedAllowAll = true;
                    uninitializedExcluded.clear();
                    if (except != null && !except.isEmpty()) {
                        uninitializedExcluded.addAll(except);
                    }
                }
                case INITIALIZED -> {
                    initializedAllowAll = true;
                    initializedExcluded.clear();
                    if (except != null && !except.isEmpty()) {
                        initializedExcluded.addAll(except);
                    }
                }
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

        private Set<String> getTargetExcludedSet(TopicState state) {
            return switch (state) {
                case NON_TOPIC -> nonTopicExcluded;
                case UNINITIALIZED -> uninitializedExcluded;
                case INITIALIZED -> initializedExcluded;
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

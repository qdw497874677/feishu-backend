package com.qdw.feishu.domain.reply;

import com.qdw.feishu.domain.core.ReplyMode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 消息回复策略工厂
 * 
 * 职责：
 * - 管理所有 ReplyStrategy 实例
 * - 根据 ReplyMode 查找对应的策略实现
 * - 提供策略的注册和查找功能
 * 
 * 优势：
 * - 集中管理策略，便于扩展和维护
 * - 支持运行时动态切换策略（如果需要）
 * - 消除 if-else 或 switch 分支
 */
public class ReplyStrategyFactory {

    private final Map<ReplyMode, ReplyStrategy> strategies;

    public ReplyStrategyFactory(List<ReplyStrategy> strategyList) {
        this.strategies = new EnumMap<>(ReplyMode.class);
        // 自动注册所有策略
        for (ReplyStrategy strategy : strategyList) {
            strategies.put(strategy.getReplyMode(), strategy);
        }
    }

    /**
     * 根据回复模式获取对应的策略
     * 
     * @param mode 回复模式
     * @return 对应的策略实现，如果未找到则返回 null
     */
    public ReplyStrategy getStrategy(ReplyMode mode) {
        return strategies.get(mode);
    }

    /**
     * 检查是否支持指定的回复模式
     * 
     * @param mode 回复模式
     * @return true 表示支持
     */
    public boolean supports(ReplyMode mode) {
        return strategies.containsKey(mode);
    }

    /**
     * 获取所有已注册的策略数量
     * 
     * @return 策略数量
     */
    public int getStrategyCount() {
        return strategies.size();
    }
}

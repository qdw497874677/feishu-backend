package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.topic.TopicState;
import org.springframework.stereotype.Component;

/**
 * 集中式下一步建议生成器。
 *
 * <p>根据刚执行的命令和当前话题状态，生成上下文感知的下一步操作建议。
 * 所有建议逻辑集中在此类，避免各命令处理器重复编写。
 *
 * <p>建议规则：
 * <ul>
 *   <li>对话类命令（chat/chatnow/cn）返回 null — 用户已在对话中</li>
 *   <li>信息类命令（help/commands）返回 null — 不需要提示</li>
 *   <li>其他命令根据当前状态给出最合理的下一步</li>
 * </ul>
 */
@Component
public class NextStepSuggester {

    /**
     * 根据刚执行的命令和当前状态，生成下一步建议。
     *
     * @param executedCommand 刚执行的子命令（如 "projects", "sc" 等）
     * @param state 当前话题状态
     * @param messageContext 消息上下文（可选，用于获取绑定信息）
     * @return 建议文本，或 null 表示不需要建议
     */
    public String suggest(String executedCommand, TopicState state, MessageContext messageContext) {
        return switch (executedCommand) {
            case "projects", "p" -> "💡 下一步：`/oc sessions <项目名>` 查看会话列表";
            case "sessions", "s" -> "💡 下一步：`/oc sc <会话ID>` 绑定会话到当前话题";
            case "sc", "session" -> suggestAfterSessionBind(state);
            case "connect" -> "💡 下一步：`/oc projects` 查看项目列表";
            case "reset" -> "💡 下一步：`/oc sc <会话ID>` 重新绑定，或 `/oc sessions` 查看会话";
            case "status" -> suggestAfterStatus(state);
            case "new" -> "💡 下一步：直接输入问题开始对话";
            case "chat", "chatnow", "cn" -> null;
            case "commands", "help" -> null;
            default -> null;
        };
    }

    private String suggestAfterSessionBind(TopicState state) {
        if (state == TopicState.INITIALIZED) {
            return "💡 下一步：直接输入问题开始对话，或 `/oc chat <内容>`";
        }
        return "💡 下一步：`/oc chat <内容>` 开始对话";
    }

    private String suggestAfterStatus(TopicState state) {
        if (state == TopicState.INITIALIZED) {
            return "💡 下一步：直接输入问题继续对话";
        }
        return "💡 下一步：`/oc sc <会话ID>` 绑定会话";
    }
}

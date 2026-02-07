package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.app.FishuAppI;
import com.qdw.feishu.domain.command.CommandWhitelist;
import com.qdw.feishu.domain.core.ReplyMode;
import com.qdw.feishu.domain.gateway.FeishuGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.gateway.OpenCodeSessionGateway;
import com.qdw.feishu.domain.gateway.TopicMappingGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.topic.TopicCommandValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * OpenCode 应用 - 支持多轮对话
 *
 * 主应用协调者，负责入口和元数据，具体逻辑委托给专用组件
 */
@Slf4j
@Component
public class OpenCodeApp implements FishuAppI {

    private final OpenCodeGateway openCodeGateway;
    private final OpenCodeCommandHandler commandHandler;
    private final OpenCodeSessionManager sessionManager;

    public OpenCodeApp(OpenCodeGateway openCodeGateway,
                       OpenCodeCommandHandler commandHandler,
                       OpenCodeSessionManager sessionManager) {
        this.openCodeGateway = openCodeGateway;
        this.commandHandler = commandHandler;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getAppId() {
        return "opencode";
    }

    @Override
    public String getAppName() {
        return "OpenCode 助手";
    }

    @Override
    public String getDescription() {
        return "通过飞书对话控制 OpenCode，支持多轮对话";
    }

    @Override
    public String getHelp() {
        return """
            🤖 **OpenCode 助手** - AI代码助手，支持多轮对话

            🚀 **快速开始**（任选一种）

              ⚡️ **方式1：话题外快速对话**（推荐，最简单）
                `/opencode c 帮我写代码`
                或 `/oc c 帮我写代码`
                系统会在项目启动目录/workspace/{日期}/创建会话

              📋 **方式2：话题内继续对话**（已绑定话题）
                `/opencode chat 继续优化`
                或直接输入问题（无需命令前缀）

              🔧 **方式3：选择现有会话**（高级，精细控制）
                1. `/opencode sessions feishu-backend`
                2. `/opencode sc <会话ID>`
                3. `/opencode chat <问题>`

            📝 **对话命令**
              `/opencode chat <内容>`       - 发送对话（话题内，使用现有会话）
              `/opencode c <内容>`          - 快速对话（话题外，创建新会话，推荐）
              `/opencode new <内容>`        - 在默认路径创建新会话
              `/opencode new <项目> <内容>`  - 在指定项目中创建新会话

            📁 **项目管理**
              `/opencode projects`          - 查看项目列表
              `/opencode sessions <项目名>`  - 查看项目的最近会话

            🔧 **会话管理**
              `/opencode session status`    - 查看当前会话信息
              `/opencode session list`      - 查看所有会话
              `/opencode sc <会话ID>`       - 绑定会话到话题（简写）
              `/opencode reset`             - 重置话题（允许重新绑定）

            ⚡️ **其他命令**
              `/opencode commands`          - 查看所有可用斜杠命令

            💡 **使用示例**

              话题外快速对话：
              `/oc c 帮我写个排序函数`
              → 在 /root/workspace/feishu-backend/workspace/2026-02-07/ 创建会话

              话题内继续对话：
              `/opencode chat 添加单元测试`
              或直接：`添加单元测试`

              指定项目：
              `/opencode new feishu-backend 重构登录模块`
              → 在 /root/workspace/feishu-backend/ 创建会话

            💡 **提示**

              - `c` 命令用于话题外快速对话，自动创建日期文件夹
              - `chat` 命令用于话题内继续对话
              - 默认路径：项目启动目录/workspace/{YYYY-MM-DD}/
              - 在已绑定的话题中可直接输入问题（无需前缀）
            """;
    }

    @Override
    public List<String> getAppAliases() {
        return Arrays.asList("oc", "code");
    }

    @Override
    public ReplyMode getReplyMode() {
        return ReplyMode.TOPIC;
    }

    @Override
    public CommandWhitelist getCommandWhitelist(com.qdw.feishu.domain.topic.TopicState state) {
        return switch (state) {
            case NON_TOPIC -> CommandWhitelist.builder()
                .add("connect", "help", "projects")
                .build();
            case UNINITIALIZED -> CommandWhitelist.allExcept("chat", "new");
            case INITIALIZED -> CommandWhitelist.all();
        };
    }

    @Override
    public boolean isTopicInitialized(Message message) {
        return sessionManager.isTopicInitialized(message);
    }

    @Override
    public String execute(Message message) {
        String content = message.getContent().trim();
        String[] parts = content.split("\\s+", 3);

        log.info("OpenCodeApp.execute: content='{}'", content);

        // 空命令，返回帮助
        if (parts.length < 2) {
            return getHelp();
        }

        String subCommand = parts[1].toLowerCase();

        // help 命令直接返回帮助信息
        if (subCommand.equals("help")) {
            return getHelp();
        }

        // 委托给命令处理器
        String result = commandHandler.handle(message, subCommand, parts);
        if (result != null) {
            return result;
        }

        // 如果处理器返回 null，说明是需要进一步处理的情况
        log.warn("命令处理器返回 null: subCommand={}", subCommand);
        return getHelp();
    }
}

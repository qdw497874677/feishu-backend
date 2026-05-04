package com.qdw.feishu.domain.opencode;

import java.util.Optional;

/**
 * OpenCode 消息格式化器
 *
 * 负责构建各类用户提示、引导和状态消息，
 * 从 OpenCodeCommandHandler 中提取以保持单一职责。
 */
public class OpenCodeMessageFormatter {

    public String buildInitializationGuide() {
        StringBuilder response = new StringBuilder();

        response.append("🎯 **欢迎来到 OpenCode 助手！**\n\n");
        response.append("📋 **开始使用前，需要完成以下初始化步骤：**\n\n");

        response.append("**第 1 步：查看可用项目**\n");
        response.append("  `/opencode p` （或 `/opencode projects`）\n\n");

        response.append("**第 2 步：查看项目的最近会话**\n");
        response.append("  `/opencode s <项目名称>` （或 `/opencode sessions`）\n");
        response.append("  示例：`/opencode s feishu-backend`\n\n");

        response.append("**第 3 步：选择会话并绑定到话题**\n");
        response.append("  `/opencode sc <会话ID>` （或 `session continue <id>`）\n\n");

        response.append("✅ **完成！** 初始化后可以：\n");
        response.append("  • 使用 `/opencode chat <问题>` 开始对话\n");
        response.append("  • 直接输入问题（无需命令前缀）\n\n");

        response.append("**💡 简化别名：**\n");
        response.append("  `p` → projects，`s` → sessions，`sc` → session continue\n\n");

        response.append("**🔄 其他命令：**\n");
        response.append("  `/opencode reset` - 重置话题（允许重新绑定会话）\n");
        response.append("  `/opencode help` - 查看完整帮助\n");
        response.append("  `/opencode commands` - 查看所有可用命令\n");

        return response.toString();
    }

    public String buildConnectGuide() {
        StringBuilder response = new StringBuilder();

        response.append("🔗 **OpenCode 连接引导**\n\n");
        response.append("**请先连接到 OpenCode 服务：**\n\n");
        response.append("  `/opencode connect`\n\n");
        response.append("连接成功后，可以：\n");
        response.append("  • 查看项目列表\n");
        response.append("  • 创建会话并开始对话\n");

        return response.toString();
    }

    public String buildNewCommandUsage() {
        return buildNewCommandUsage(false);
    }

    public String buildNewCommandUsage(boolean isTopicInitialized) {
        if (isTopicInitialized) {
            // 话题已绑定的使用说明
            return "❌ **命令格式错误**\n\n" +
                   "💡 **当前状态：话题已绑定**\n\n" +
                   "✅ **使用方式（在当前项目创建新会话）**：\n" +
                   "  `/opencode new <提示词>`\n" +
                   "  示例：`/opencode new 重构登录模块`\n\n" +
                   "🔧 **可选：指定其他项目**：\n" +
                   "  `/opencode new <项目名称> <提示词>`\n" +
                   "  示例：`/opencode new ai-study 优化算法`\n\n" +
                   "💡 **提示**：不指定项目时，将在当前绑定的项目下创建新会话并更换话题绑定";
        }

        // 话题未绑定的使用说明
        return "❌ **命令格式错误**\n\n" +
               "💡 **当前状态：话题未绑定**\n\n" +
               "❌ **必须指定项目**：\n" +
               "  `/opencode new <项目名称> <提示词>`\n" +
               "  示例：`/opencode new feishu-backend 重构登录模块`\n\n" +
               "💡 **提示**：话题未绑定时，必须明确指定在哪个项目下创建会话";
    }

    public String buildChatQuickStart() {
        return "💬 **OpenCode 对话**\n\n" +
               "🚀 **快速开始**\n" +
               "  `/opencode chatnow 帮我写一个排序函数`\n" +
               "  或 `/opencode cn 帮我写一个排序函数`\n\n" +
               "✅ **系统会自动**\n" +
               "  • 创建新会话\n" +
               "  • 绑定到当前话题\n" +
               "  • 开始对话\n\n" +
               "💡 **提示**：首次使用会话会自动创建并绑定，无需手动配置";
    }

    public String buildInitializationRequiredMessage() {
        return "❌ **话题未初始化**\n\n" +
               "请先完成以下初始化步骤：\n\n" +
               "**第 1 步：查看可用项目**\n" +
               "  `/opencode projects`\n\n" +
               "**第 2 步：查看项目的最近会话**\n" +
               "  `/opencode sessions <项目名称>`\n" +
               "  示例：`/opencode sessions feishu-backend`\n\n" +
               "**第 3 步：选择会话并绑定到话题**\n" +
               "  `/opencode session continue <会话ID>`\n\n" +
               "✅ **完成后即可使用 chat 命令**\n\n" +
               "💡 使用方式：\n" +
               "  `/opencode chat <你的问题>`\n" +
               "  或直接输入问题（无需命令前缀）";
    }

    public String buildChatStatusWithSession(String topicId, String sessionId) {
        StringBuilder response = new StringBuilder();

        response.append("💬 **当前会话信息**\n\n");
        response.append("  🆔 Session ID: `").append(sessionId).append("`\n");
        response.append("  💬 话题 ID: `").append(topicId).append("`\n");
        response.append("  ✅ 状态: 已绑定\n\n");

        response.append("**💡 使用方式：**\n");
        response.append("  `/opencode chat <你的问题>` - 发送对话\n");
        response.append("  或直接输入问题（无需命令前缀）\n\n");

        response.append("**示例：**\n");
        response.append("  `/opencode chat 帮我重构这个函数`\n");
        response.append("  或直接：`帮我重构这个函数`\n");

        return response.toString();
    }

    public String buildSessionInitializedInfo(String topicId, String sessionId) {
        StringBuilder response = new StringBuilder();

        response.append("✅ **会话已创建并绑定到话题**\n\n");
        response.append("📋 **会话信息**\n");
        response.append("  🆔 Session ID: `").append(sessionId).append("`\n");
        response.append("  💬 话题 ID: `").append(topicId).append("`\n");
        response.append("  ✅ 状态: 已绑定\n\n");

        response.append("💡 **开始对话**\n");
        response.append("  在当前话题中发送：\n");
        response.append("  `chat <你的问题>`\n");
        response.append("  或直接输入问题\n\n");

        response.append("📁 **默认工作目录**\n");
        response.append("  `/root/workspace/feishu-backend/workspace/").append(java.time.LocalDate.now()).append("/`\n\n");

        return response.toString();
    }

    public String buildUnknownCommandResponse(String subCommand, String prompt) {
        return String.format(
            "❌ 未知的子命令: `%s`\n\n" +
            "📝 可用子命令：\n" +
            "  `/opencode chat <内容>` - 对话（推荐）\n" +
            "  `/opencode new <内容>` - 创建新会话\n" +
            "  `/opencode projects` - 查看项目\n" +
            "  `/opencode commands` - 查看命令\n" +
            "  `/opencode session <status|list>` - 会话管理\n\n" +
            "💡 如果你想对话，请使用：`/opencode chat %s`",
            subCommand, prompt
        );
    }

    /**
     * 构建 connect 命令成功后的响应消息。
     *
     * @param status  服务状态信息（可能为错误信息）
     * @param projects 项目列表（可能为错误信息）
     * @return 格式化后的响应
     */
    public String buildConnectSuccessResponse(String status, String projects) {
        StringBuilder response = new StringBuilder();
        response.append("🔗 **OpenCode 连接成功**\n\n");
        response.append("**服务状态**\n").append(status).append("\n\n");
        response.append("**📁 可用项目**\n\n").append(projects).append("\n");
        response.append("**💡 下一步操作**\n\n");
        response.append("1️⃣ 查看项目的最近会话：\n");
        response.append("   `/opencode sessions <项目名称>`\n");
        response.append("   示例：`/opencode sessions feishu-backend`\n\n");
        response.append("2️⃣ 选择会话并绑定：\n");
        response.append("   `/opencode session continue <会话ID>`\n\n");
        response.append("3️⃣ 开始对话：\n");
        response.append("   `/opencode chat <你的问题>`\n");
        response.append("   或直接输入（在已初始化的话题中）\n\n");
        response.append("**📝 其他命令**\n");
        response.append(" `/opencode help` - 查看完整帮助\n");
        response.append(" `/opencode commands` - 查看所有斜杠命令\n");
        return response.toString();
    }

    /**
     * 构建 reset 命令的响应消息。
     */
    public String buildResetResponse(String topicId, Optional<String> previousSessionId) {
        StringBuilder response = new StringBuilder();
        response.append("🔄 **话题已重置**\n\n");

        if (previousSessionId.isPresent()) {
            response.append("已解除绑定的会话: `").append(previousSessionId.get()).append("`\n\n");
        }

        response.append("✅ **可以重新初始化了**\n\n");
        response.append("**下一步操作**：\n\n");
        response.append("1️⃣ 查看可用项目：\n");
        response.append("   `/opencode p` （或 `/opencode projects`）\n\n");
        response.append("2️⃣ 查看项目的最近会话：\n");
        response.append("   `/opencode s <项目名称>` （或 `/opencode sessions`）\n");
        response.append("   示例：`/opencode s feishu-backend`\n\n");
        response.append("3️⃣ 选择会话并绑定：\n");
        response.append("   `/opencode sc <会话ID>` （或 `session continue <id>`）\n\n");

        return response.toString();
    }
}

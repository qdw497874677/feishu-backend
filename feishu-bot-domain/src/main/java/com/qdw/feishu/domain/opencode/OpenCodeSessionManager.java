package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
import com.qdw.feishu.domain.model.MessageContext;
import com.qdw.feishu.domain.model.opencode.OpenCodeSessionData;
import com.qdw.feishu.domain.session.AppSession;
import com.qdw.feishu.domain.session.TypeToken;
import com.qdw.feishu.domain.topic.TopicState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OpenCode 会话管理器
 *
 * Phase 2 重构：使用 ImContextBinding 管理会话与 IM 上下文的绑定关系。
 * - IM 上下文（话题/聊天）通过 ImContextBindingGateway 绑定到应用会话
 * - 应用会话数据通过 AppSessionGateway 管理
 */
@Slf4j
@Component
public class OpenCodeSessionManager {

    private static final String APP_ID = "opencode";
    private static final TypeToken<OpenCodeSessionData> TYPE_TOKEN = new TypeToken<OpenCodeSessionData>() {};

    static final int DEFAULT_SESSION_LIMIT = 5;
    static final int MIN_SESSION_LIMIT = 1;
    static final int MAX_SESSION_LIMIT = 20;
    static final int MAX_PROJECT_NAME_LENGTH = 100;

    private final OpenCodeGateway openCodeGateway;
    private final AppSessionGateway appSessionGateway;
    private final ImContextBindingGateway bindingGateway;

    public OpenCodeSessionManager(OpenCodeGateway openCodeGateway,
                                  AppSessionGateway appSessionGateway,
                                  ImContextBindingGateway bindingGateway) {
        this.openCodeGateway = openCodeGateway;
        this.appSessionGateway = appSessionGateway;
        this.bindingGateway = bindingGateway;
    }

    private Optional<ImContextRef> resolveContext(Message message) {
        try {
            return Optional.of(FeishuContextResolver.resolve(message));
        } catch (IllegalArgumentException e) {
            log.debug("Cannot resolve IM context from message: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @deprecated Use {@link #isTopicInitialized(MessageContext)} instead.
     */
    @Deprecated
    public boolean isTopicInitialized(Message message) {
        return resolveContext(message)
            .flatMap(bindingGateway::findBinding)
            .filter(binding -> binding.isForApp(APP_ID))
            .filter(binding -> binding.getSessionId() != null)
            .isPresent();
    }

    /**
     * Check if topic is initialized using pre-resolved MessageContext.
     * Skips findBinding() — uses binding from MessageContext directly.
     */
    public boolean isTopicInitialized(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved()) {
            return false;
        }
        return messageContext.isBoundToApp(APP_ID)
            && messageContext.getBinding().getSessionId() != null;
    }

    /**
     * @deprecated Use {@link #detectTopicState(MessageContext)} instead.
     */
    @Deprecated
    public TopicState detectTopicState(Message message) {
        return resolveContext(message)
            .map(contextRef -> bindingGateway.findBinding(contextRef)
                .filter(binding -> binding.isForApp(APP_ID))
                .map(binding -> binding.getSessionId() == null ? TopicState.UNINITIALIZED : TopicState.INITIALIZED)
                .orElse(TopicState.UNINITIALIZED))
            .orElse(TopicState.NON_TOPIC);
    }

    /**
     * Detect topic state using pre-resolved MessageContext.
     * Skips findBinding() — uses binding from MessageContext directly.
     */
    public TopicState detectTopicState(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved()) {
            return TopicState.NON_TOPIC;
        }
        if (!messageContext.isBoundToApp(APP_ID)) {
            return TopicState.UNINITIALIZED;
        }
        ImContextBinding binding = messageContext.getBinding();
        return binding.getSessionId() == null ? TopicState.UNINITIALIZED : TopicState.INITIALIZED;
    }

    /**
     * @deprecated Use {@link #getCurrentSessionStatus(MessageContext)} instead.
     */
    @Deprecated
    public String getCurrentSessionStatus(Message message) {
        Optional<ImContextRef> contextOpt = resolveContext(message);
        if (contextOpt.isEmpty()) {
            return "❌ 当前不在话题中，无法查看会话状态";
        }

        ImContextRef contextRef = contextOpt.get();
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID));

        if (bindingOpt.isEmpty()) {
            return "📭 当前话题还没有 OpenCode 会话\n\n" +
                "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        ImContextBinding binding = bindingOpt.get();
        if (binding.getSessionId() == null) {
            return "📭 当前话题已进入 OpenCode 上下文，但还没有激活会话\n\n" +
                "  📍 Context: `" + contextRef.toStorageKey() + "`\n\n" +
                "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        Optional<AppSession<OpenCodeSessionData>> sessionOpt =
            appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN);
        if (sessionOpt.isEmpty()) {
            return "📭 当前话题已进入 OpenCode 上下文，但还没有激活会话\n\n" +
                "  📍 Context: `" + contextRef.toStorageKey() + "`\n\n" +
                "💡 当前绑定的会话已失效，请重新选择会话或发送 `/opencode <提示词>` 创建新会话";
        }

        return "📋 **当前会话信息**\n\n" +
            "  🆔 Session ID: `" + binding.getSessionId() + "`\n" +
            "  📍 Context: `" + contextRef.toStorageKey() + "`\n" +
            "  ✅ 状态: 活跃\n\n" +
            "💡 继续对话会自动使用此会话";
    }

    /**
     * Get current session status using pre-resolved MessageContext.
     * Skips findBinding() — uses binding from MessageContext directly.
     */
    public String getCurrentSessionStatus(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved()) {
            return "❌ 当前不在话题中，无法查看会话状态";
        }

        ImContextRef contextRef = messageContext.getContextRef();
        Optional<ImContextBinding> bindingOpt = Optional.ofNullable(messageContext.getBinding())
            .filter(b -> b.isForApp(APP_ID));

        if (bindingOpt.isEmpty()) {
            return "📭 当前话题还没有 OpenCode 会话\n\n" +
                "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        ImContextBinding binding = bindingOpt.get();
        if (binding.getSessionId() == null) {
            return "📭 当前话题已进入 OpenCode 上下文，但还没有激活会话\n\n" +
                "  📍 Context: `" + contextRef.toStorageKey() + "`\n\n" +
                "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        Optional<AppSession<OpenCodeSessionData>> sessionOpt =
            appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN);
        if (sessionOpt.isEmpty()) {
            return "📭 当前话题已进入 OpenCode 上下文，但还没有激活会话\n\n" +
                "  📍 Context: `" + contextRef.toStorageKey() + "`\n\n" +
                "💡 当前绑定的会话已失效，请重新选择会话或发送 `/opencode <提示词>` 创建新会话";
        }

        return "📋 **当前会话信息**\n\n" +
            "  🆔 Session ID: `" + binding.getSessionId() + "`\n" +
            "  📍 Context: `" + contextRef.toStorageKey() + "`\n" +
            "  ✅ 状态: 活跃\n\n" +
            "💡 继续对话会自动使用此会话";
    }

    public String handleListSessions() {
        return openCodeGateway.listSessions();
    }

    public String handleSessionsCommand(String[] parts) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode sessions <项目名称>`\n\n" +
                "示例：`/opencode sessions my-project`\n\n" +
                "💡 提示：\n" +
                " - 使用 `/opencode projects` 查看所有项目\n" +
                " - 项目名称支持部分匹配（不区分大小写）";
        }

        String project = parts[2].trim();
        if (project.isEmpty()) {
            return "❌ 项目名称不能为空\n\n" +
                "用法：`/opencode sessions <项目名称>`";
        }
        if (project.length() > MAX_PROJECT_NAME_LENGTH) {
            return "❌ 项目名称过长（最多" + MAX_PROJECT_NAME_LENGTH + "个字符）";
        }

        int limit = DEFAULT_SESSION_LIMIT;
        if (parts.length >= 4) {
            try {
                limit = Integer.parseInt(parts[3].trim());
                if (limit < MIN_SESSION_LIMIT || limit > MAX_SESSION_LIMIT) {
                    return "❌ 数量必须在 " + MIN_SESSION_LIMIT + "-" + MAX_SESSION_LIMIT + " 之间";
                }
            } catch (NumberFormatException e) {
                log.warn("无效的数量参数，使用默认值: {}", parts[3]);
            }
        }

        log.info("查询项目会话: project={}, limit={}", project, limit);
        return openCodeGateway.listRecentSessions(project, limit);
    }

    public void saveSession(Message message, String openCodeSessionId) {
        resolveContext(message).ifPresent(contextRef -> saveSession(contextRef, openCodeSessionId));
    }

    public void saveSession(ImContextRef contextRef, String openCodeSessionId) {
        Optional<ImContextBinding> existingBinding = bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID));

        if (existingBinding.isPresent()) {
            String sessionId = existingBinding.get().getSessionId();
            if (sessionId == null) {
                OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
                String newSessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
                BindingResult result = bindingGateway.bind(contextRef, APP_ID, newSessionId);
                log.info("升级会话绑定: contextRef={}, null -> sessionId={}, result={}",
                    contextRef.toStorageKey(), newSessionId, result);
                return;
            }

            Optional<AppSession<OpenCodeSessionData>> sessionOpt =
                appSessionGateway.getSession(APP_ID, sessionId, TYPE_TOKEN);
            if (sessionOpt.isPresent()) {
                AppSession<OpenCodeSessionData> session = sessionOpt.get();
                OpenCodeSessionData data = session.getData();
                if (!data.getOpenCodeSessionId().equals(openCodeSessionId)) {
                    data.setOpenCodeSessionId(openCodeSessionId);
                    appSessionGateway.updateSession(APP_ID, sessionId, data, TYPE_TOKEN, session.getVersion());
                    log.info("更新会话数据: contextRef={}, sessionId={}", contextRef.toStorageKey(), sessionId);
                }
            }
            return;
        }

        OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
        String sessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
        BindingResult result = bindingGateway.bind(contextRef, APP_ID, sessionId);
        log.info("创建并绑定会话: contextRef={}, sessionId={}, result={}",
            contextRef.toStorageKey(), sessionId, result);
    }

    public void clearSession(Message message) {
        resolveContext(message).ifPresent(this::clearSession);
    }

    public void clearSession(ImContextRef contextRef) {
        bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID))
            .ifPresent(binding -> {
                if (binding.getSessionId() != null) {
                    appSessionGateway.deleteSession(APP_ID, binding.getSessionId());
                }
                bindingGateway.clearBinding(contextRef);
                log.info("清除会话绑定: contextRef={}, sessionId={}",
                    contextRef.toStorageKey(), binding.getSessionId());
            });
    }

    /**
     * @deprecated Use {@link #getSessionId(MessageContext)} instead.
     */
    @Deprecated
    public Optional<String> getSessionId(Message message) {
        return resolveContext(message).flatMap(this::getSessionId);
    }

    /**
     * Get session ID using pre-resolved MessageContext.
     * Skips findBinding() — uses binding from MessageContext directly.
     */
    public Optional<String> getSessionId(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved() || !messageContext.isBoundToApp(APP_ID)) {
            return Optional.empty();
        }
        ImContextBinding binding = messageContext.getBinding();
        if (binding.getSessionId() == null) {
            return Optional.empty();
        }
        return appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN)
            .map(session -> session.getData().getOpenCodeSessionId());
    }

    public Optional<String> getSessionId(ImContextRef contextRef) {
        return bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID))
            .flatMap(binding -> {
                if (binding.getSessionId() == null) {
                    return Optional.empty();
                }
                return appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN)
                    .map(session -> session.getData().getOpenCodeSessionId());
            });
    }

    /**
     * @deprecated Use {@link #isExplicitlyInitialized(MessageContext)} instead.
     */
    @Deprecated
    public boolean isExplicitlyInitialized(Message message) {
        return resolveContext(message)
            .flatMap(this::isExplicitlyInitializedOpt)
            .orElse(false);
    }

    /**
     * Check if explicitly initialized using pre-resolved MessageContext.
     * Skips findBinding() — uses binding from MessageContext directly.
     */
    public boolean isExplicitlyInitialized(MessageContext messageContext) {
        if (messageContext == null || !messageContext.isResolved() || !messageContext.isBoundToApp(APP_ID)) {
            return false;
        }
        ImContextBinding binding = messageContext.getBinding();
        if (binding.getSessionId() == null) {
            return false;
        }
        return appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN)
            .map(session -> session.getData().isExplicitlyInitialized())
            .orElse(false);
    }

    public boolean isExplicitlyInitialized(ImContextRef contextRef) {
        return isExplicitlyInitializedOpt(contextRef).orElse(false);
    }

    private Optional<Boolean> isExplicitlyInitializedOpt(ImContextRef contextRef) {
        return bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID))
            .flatMap(binding -> {
                if (binding.getSessionId() == null) {
                    return Optional.empty();
                }
                return appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN)
                    .map(session -> session.getData().isExplicitlyInitialized());
            });
    }

    public void setExplicitlyInitialized(Message message) {
        resolveContext(message).ifPresent(this::setExplicitlyInitialized);
    }

    public void setExplicitlyInitialized(ImContextRef contextRef) {
        bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID))
            .filter(binding -> binding.getSessionId() != null)
            .flatMap(binding -> appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN))
            .ifPresent(session -> {
                OpenCodeSessionData data = session.getData();
                data.setExplicitlyInitialized(true);
                appSessionGateway.updateSession(APP_ID, session.getSessionId(), data, TYPE_TOKEN, session.getVersion());
                log.info("设置显式初始化标记: contextRef={}", contextRef.toStorageKey());
            });
    }

    public void clearExplicitlyInitialized(ImContextRef contextRef) {
        bindingGateway.findBinding(contextRef)
            .filter(binding -> binding.isForApp(APP_ID))
            .filter(binding -> binding.getSessionId() != null)
            .flatMap(binding -> appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN))
            .ifPresent(session -> {
                OpenCodeSessionData data = session.getData();
                data.setExplicitlyInitialized(false);
                appSessionGateway.updateSession(APP_ID, session.getSessionId(), data, TYPE_TOKEN, session.getVersion());
                log.info("清除显式初始化标记: contextRef={}", contextRef.toStorageKey());
            });
    }

    public void clearExplicitlyInitialized(Message message) {
        resolveContext(message).ifPresent(this::clearExplicitlyInitialized);
    }
}

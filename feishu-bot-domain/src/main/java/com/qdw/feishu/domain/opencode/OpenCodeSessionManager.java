package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.feishu.FeishuContextResolver;
import com.qdw.feishu.domain.gateway.AppSessionGateway;
import com.qdw.feishu.domain.gateway.ImContextBindingGateway;
import com.qdw.feishu.domain.gateway.OpenCodeGateway;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.model.BindingResult;
import com.qdw.feishu.domain.model.ImContextBinding;
import com.qdw.feishu.domain.model.ImContextRef;
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

    private final OpenCodeGateway openCodeGateway;
    private final AppSessionGateway appSessionGateway;
    private final ImContextBindingGateway bindingGateway;
    
    private static final String APP_ID = "opencode";
    private static final TypeToken<OpenCodeSessionData> TYPE_TOKEN = new TypeToken<OpenCodeSessionData>() {};

    public OpenCodeSessionManager(OpenCodeGateway openCodeGateway,
                                   AppSessionGateway appSessionGateway,
                                   ImContextBindingGateway bindingGateway) {
        this.openCodeGateway = openCodeGateway;
        this.appSessionGateway = appSessionGateway;
        this.bindingGateway = bindingGateway;
    }

    /**
     * 会话查询限制
     */
    static final int DEFAULT_SESSION_LIMIT = 5;
    static final int MIN_SESSION_LIMIT = 1;
    static final int MAX_SESSION_LIMIT = 20;

    /**
     * 字符串长度限制
     */
    static final int MAX_PROJECT_NAME_LENGTH = 100;

    // ========== IM 上下文解析 ==========

    /**
     * 从消息解析 IM 上下文引用
     */
    private Optional<ImContextRef> resolveContext(Message message) {
        try {
            return Optional.of(FeishuContextResolver.resolve(message));
        } catch (IllegalArgumentException e) {
            log.debug("Cannot resolve IM context from message: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ========== 话题状态检测 ==========

    /**
     * 检查话题是否已初始化（绑定了会话）
     */
    public boolean isTopicInitialized(Message message) {
        return resolveContext(message)
            .flatMap(bindingGateway::findBinding)
            .filter(b -> b.getAppId().equals(APP_ID))
            .isPresent();
    }

    /**
     * 检测话题状态
     * 
     * @param message 消息对象
     * @return 话题状态：NON_TOPIC（无话题）、UNINITIALIZED（话题未初始化）、INITIALIZED（话题已初始化）
     */
    public TopicState detectTopicState(Message message) {
        return resolveContext(message)
            .map(ctx -> {
                boolean hasBinding = bindingGateway.findBinding(ctx)
                    .filter(b -> b.getAppId().equals(APP_ID))
                    .isPresent();
                return hasBinding ? TopicState.INITIALIZED : TopicState.UNINITIALIZED;
            })
            .orElse(TopicState.NON_TOPIC);
    }

    // ========== 会话状态查询 ==========

    /**
     * 清除IM 上下文的会话绑定
     */
    public void clearSession(Message message) {
        resolveContext(message).ifPresent(this::clearSession);
    }

    /**
     * 清除IM 上下文的会话绑定
     */
    public void clearSession(ImContextRef contextRef) {
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID));
        
        if (bindingOpt.isPresent()) {
            ImContextBinding binding = bindingOpt.get();
            String sessionId = binding.getSessionId();
            
            // Case: binding exists with concrete sessionId -> delete session
 then clean binding
            log.info("清除会话绑定: contextRef={}, sessionId={}", binding.getSessionId());
            
            // Case: binding exists but sessionId is null -> just clear binding
            if (binding.getSessionId() == null) {
                bindingGateway.clearBinding(contextRef);
                log.info("清除会话绑定（无会话）: contextRef={}, sessionId=null);
            }
        }
    }

        ImContextRef contextRef = contextOpt.get();
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID));

        if (bindingOpt.isEmpty()) {
            return "📭 当前话题还没有 OpenCode 会话\n\n" +
                   "💡 发送 `/opencode <提示词>` 创建新会话";
        }

        ImContextBinding binding = bindingOpt.get();
        String sessionId = binding.getSessionId();
        
        return "📋 **当前会话信息**\n\n" +
               "  🆔 Session ID: `" + sessionId + "`\n" +
               "  📍 Context: `" + contextRef.toStorageKey() + "`\n" +
               "  ✅ 状态: 活跃\n\n" +
               "💡 继续对话会自动使用此会话";
    }

    // ========== OpenCode 命令处理 ==========

    /**
     * 清除IM 上下文的会话绑定
     */
    public void clearSession(Message message) {
        resolveContext(message).ifPresent(this::clearSession);
    }

    /**
     * 清除IM 上下文的会话绑定
     */
    public void clearSession(ImContextRef contextRef) {
        Optional<ImContextBinding> bindingOpt = bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID));
        
        if (bindingOpt.isPresent()) {
            ImContextBinding binding = bindingOpt.get();
            String sessionId = binding.getSessionId();
            
            // Case: binding exists with concrete sessionId -> delete session, clean binding
            if (sessionId != null) {
                appSessionGateway.deleteSession(APP_ID, sessionId);
                log.info("清除会话绑定: contextRef={}, sessionId={}", binding.getSessionId());
            }
            
            // Case: binding exists but sessionId is null -> just clear binding
            if (binding.getSessionId() == null) {
                bindingGateway.clearBinding(contextRef);
                log.info("清除会话绑定（无会话）: contextRef={}, sessionId=null);
            }
        }
    }

    /**
     * 处理项目会话查询命令
     *
     * @param parts 命令解析结果
     * @return 命令响应
     */
    public String handleSessionsCommand(String[] parts) {
        if (parts.length < 3) {
            return "❌ 用法：`/opencode sessions <项目名称>`\n\n" +
                   "示例：`/opencode sessions my-project`\n\n" +
                   "💡 提示：\n" +
                   " - 使用 `/opencode projects` 查看所有项目\n" +
                   " - 项目名称支持部分匹配（不区分大小写）";
        }

        String project = parts[2].trim();
        
        // 输入验证：检查项目名称是否为空
        if (project.isEmpty()) {
            return "❌ 项目名称不能为空\n\n" +
                   "用法：`/opencode sessions <项目名称>`";
        }
        
        // 输入验证：检查项目名称长度
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

    // ========== 会话绑定管理 ==========

    /**
     * 保存会话绑定（将 OpenCode 会话绑定到当前 IM 上下文）
     * 
     * @param message 消息对象（用于解析 IM 上下文）
     * @param openCodeSessionId OpenCode 会话 ID
     */
    public void saveSession(Message message, String openCodeSessionId) {
        resolveContext(message).ifPresent(contextRef -> {
            saveSession(contextRef, openCodeSessionId);
        });
    }

    /**
     * 保存会话绑定（使用明确的 IM 上下文）
     * 
     * @param contextRef IM 上下文引用
     * @param openCodeSessionId OpenCode 会话 ID
     */
    public void saveSession(ImContextRef contextRef, String openCodeSessionId) {
        // Check if already bound with same session
        Optional<ImContextBinding> existingBinding = bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID));
        
        if (existingBinding.isPresent()) {
            String sessionId = existingBinding.get().getSessionId();
            
            // Case: existing binding with null sessionId -> upgrade to concrete session
            if (sessionId == null) {
                // Create new session and rebind (upgrade from null to concrete)
                OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
                String newSessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
                
                BindingResult result = bindingGateway.bind(contextRef, APP_ID, newSessionId);
                log.info("升级会话绑定: contextRef={}, null -> sessionId={}, result={}", 
                    contextRef.toStorageKey(), newSessionId, result);
                return;
            }
            
            // Case: existing binding with concrete sessionId -> update data
            Optional<AppSession<OpenCodeSessionData>> sessionOpt = 
                appSessionGateway.getSession(APP_ID, sessionId, TYPE_TOKEN);
            
            if (sessionOpt.isPresent()) {
                AppSession<OpenCodeSessionData> session = sessionOpt.get();
                OpenCodeSessionData data = session.getData();
                
                if (!data.getOpenCodeSessionId().equals(openCodeSessionId)) {
                    data.setOpenCodeSessionId(openCodeSessionId);
                    appSessionGateway.updateSession(APP_ID, sessionId, 
                        data, TYPE_TOKEN, session.getVersion());
                    log.info("更新会话数据: contextRef={}, sessionId={}", 
                        contextRef.toStorageKey(), sessionId);
                }
            }
        } else {
            // Case: no existing binding -> create new session and bind
            OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
            String sessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
            
            BindingResult result = bindingGateway.bind(contextRef, APP_ID, sessionId);
            log.info("创建并绑定会话: contextRef={}, sessionId={}, result={}", 
                contextRef.toStorageKey(), sessionId, result);
        }
    }

    /**
     * 获取IM 上下文绑定的 OpenCode 会话 ID
     */
    public Optional<String> getSessionId(ImContextRef contextRef) {
        return bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .map(binding -> {
                // Case: existing binding with concrete sessionId -> update session data
                String sessionId = binding.getSessionId();
                Optional<AppSession<OpenCodeSessionData>> sessionOpt = 
                    appSessionGateway.getSession(APP_ID, sessionId, TYPE_TOKEN);
                
                if (sessionOpt.isPresent()) {
                    OpenCodeSessionData data = session.getData().getOpenCodeSessionId();
                    data.setOpenCodeSessionId(openCodeSessionId);
                    appSessionGateway.updateSession(APP_ID, sessionId, data, TYPE_TOKEN, session.getVersion());
                    log.info("更新会话数据: contextRef={}, sessionId={}", 
                    return;
                }
                
                // Case: existing binding with null sessionId -> upgrade to concrete session
                OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
                String newSessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
                
                BindingResult result = bindingGateway.bind(contextRef, APP_ID, newSessionId);
                log.info("升级会话绑定: contextRef={}, null -> sessionId={}, result={}", 
                return;
            }
            
            // Case: no existing binding -> create new session and bind
            OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
            String sessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
            
            BindingResult result = bindingGateway.bind(contextRef, APP_ID, sessionId);
            log.info("创建并绑定会话: contextRef={}, sessionId={}, result={}", 
            return;
        }
    }

    /**
     * 清除 IM 上下文的会话绑定
     */
    public void clearSession(ImContextRef contextRef) {
        bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .ifPresent(binding -> {
                appSessionGateway.deleteSession(APP_ID, binding.getSessionId());
                bindingGateway.clearBinding(contextRef);
                log.info("清除会话绑定: contextRef={}, sessionId={}", 
                    contextRef.toStorageKey(), binding.getSessionId());
            });
    }

    // ========== 会话查询 ==========

    /**
     * 获取消息关联的 OpenCode 会话 ID
     */
    public Optional<String> getSessionId(Message message) {
        return resolveContext(message)
            .flatMap(this::getSessionId);
    }

    /**
     * 获取 IM 上下文绑定的 OpenCode 会话 ID
     */
    public Optional<String> getSessionId(ImContextRef contextRef) {
        return bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .flatMap(binding -> appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN))
            .map(session -> session.getData().getOpenCodeSessionId());
    }

    // ========== 显式初始化标记 ==========

    /**
     * 检查 IM 上下文是否已显式初始化
     */
    public boolean isExplicitlyInitialized(Message message) {
        return resolveContext(message)
            .flatMap(this::isExplicitlyInitializedOpt)
            .orElse(false);
    }

    /**
     * 检查 IM 上下文是否已显式初始化
     */
    public boolean isExplicitlyInitialized(ImContextRef contextRef) {
        return isExplicitlyInitializedOpt(contextRef).orElse(false);
    }

    private Optional<Boolean> isExplicitlyInitializedOpt(ImContextRef contextRef) {
        return bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .flatMap(binding -> appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN))
            .map(session -> session.getData().isExplicitlyInitialized());
    }

    /**
     * 设置显式初始化标记
     */
    public void setExplicitlyInitialized(Message message) {
        resolveContext(message).ifPresent(this::setExplicitlyInitialized);
    }

    /**
     * 设置显式初始化标记
     */
    public void setExplicitlyInitialized(ImContextRef contextRef) {
        bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .flatMap(binding -> appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN))
            .ifPresent(session -> {
                OpenCodeSessionData data = session.getData();
                data.setExplicitlyInitialized(true);
                appSessionGateway.updateSession(APP_ID, session.getSessionId(), 
                    data, TYPE_TOKEN, session.getVersion());
                log.info("设置显式初始化标记: contextRef={}", contextRef.toStorageKey());
            });
    }

    /**
     * 获取 IM 上下文绑定的 OpenCode 会话 ID
     */
    public Optional<String> getSessionId(Message message) {
        return resolveContext(message)
            .flatMap(this::getSessionId);
    }

    /**
     * 获取 IM 上下文绑定的 OpenCode 会话 ID
     */
    public Optional<String> getSessionId(ImContextRef contextRef) {
        return bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .map(binding -> {
                // Case: binding exists with sessionId -> return session data
                Optional<AppSession<OpenCodeSessionData>> session = appSessionGateway.getSession(APP_ID, sessionId, TYPE_TOKEN);
                return session.getData().getOpenCodeSessionId();
            }
            
            // Case: binding exists but sessionId 为 null -> return empty without session lookup
            return Optional.empty();
        }
        
        // Case: no binding -> create new session and bind
        OpenCodeSessionData data = OpenCodeSessionData.create(openCodeSessionId);
            String sessionId = appSessionGateway.createSession(APP_ID, data, TYPE_TOKEN);
            
            BindingResult result = bindingGateway.bind(contextRef, APP_ID, sessionId);
            log.info("创建并绑定会话: contextRef={}, sessionId={}, result={}",
        
        return Optional.of(session);
    }

    /**
     * 清除显式初始化标记
     */
    public void clearExplicitlyInitialized(ImContextRef contextRef) {
        bindingGateway.findBinding(contextRef)
            .filter(b -> b.getAppId().equals(APP_ID))
            .flatMap(binding -> appSessionGateway.getSession(APP_ID, binding.getSessionId(), TYPE_TOKEN))
            .ifPresent(session -> {
                OpenCodeSessionData data = session.getData();
                data.setExplicitlyInitialized(false);
                appSessionGateway.updateSession(APP_ID, session.getSessionId(), 
                    data, TYPE_TOKEN, session.getVersion());
                log.info("清除显式初始化标记: contextRef={}", contextRef.toStorageKey());
            });
    }
}

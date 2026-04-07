package com.qdw.feishu.domain.app;

/**
 * Structured result from application execution.
 *
 * Replaces the raw String return from FishuAppI.execute().
 * Carries reply content and optional session metadata for session-aware apps.
 *
 * <p>Session ID semantics:
 * <ul>
 *   <li>{@code openCodeSessionId} = external OpenCode session ID (ses_xxx), NOT the internal app session UUID</li>
 *   <li>Simple apps use {@link #text(String)} and never touch session fields</li>
 *   <li>Async apps use {@link #noReply()} to signal "no synchronous reply needed"</li>
 * </ul>
 */
public class AppExecutionResult {

    private final String replyContent;
    private final String openCodeSessionId;
    private final boolean sessionCreated;

    private AppExecutionResult(String replyContent, String openCodeSessionId, boolean sessionCreated) {
        this.replyContent = replyContent;
        this.openCodeSessionId = openCodeSessionId;
        this.sessionCreated = sessionCreated;
    }

    /**
     * Create a simple text reply result.
     * Used by stateless apps (Help, Time, Bash, History).
     *
     * @param content the reply text (nullable — null means "I sent a card/reaction directly, skip text reply")
     * @return result with text content only
     */
    public static AppExecutionResult text(String content) {
        return new AppExecutionResult(content, null, false);
    }

    /**
     * Create a no-reply result for async execution paths.
     * Signals that the app will reply asynchronously (e.g., via streaming card).
     *
     * @return result with null replyContent
     */
    public static AppExecutionResult noReply() {
        return new AppExecutionResult(null, null, false);
    }

    /**
     * Create a result with session metadata.
     * Used by OpenCode when a command produces or connects a session.
     *
     * @param content the reply text
     * @param openCodeSessionId the external OpenCode session ID (ses_xxx)
     * @param created true if a new session was created (vs. connecting to existing)
     * @return result with session info
     */
    public static AppExecutionResult withSession(String content, String openCodeSessionId, boolean created) {
        return new AppExecutionResult(content, openCodeSessionId, created);
    }

    /** @return reply text, or null if async/no-reply */
    public String getReplyContent() {
        return replyContent;
    }

    /** @return external OpenCode session ID (ses_xxx), or null for non-session results */
    public String getOpenCodeSessionId() {
        return openCodeSessionId;
    }

    /** @return true if a new session was created during this execution */
    public boolean isSessionCreated() {
        return sessionCreated;
    }
}

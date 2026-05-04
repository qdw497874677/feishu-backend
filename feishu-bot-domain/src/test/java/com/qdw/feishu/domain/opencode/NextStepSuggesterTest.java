package com.qdw.feishu.domain.opencode;

import com.qdw.feishu.domain.session.ContextSessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NextStepSuggester 单元测试")
class NextStepSuggesterTest {

    private NextStepSuggester suggester;

    @BeforeEach
    void setUp() {
        suggester = new NextStepSuggester();
    }

    @Test
    @DisplayName("projects 命令后建议 sessions")
    void should_suggestSessions_after_projects() {
        String suggestion = suggester.suggest("projects", ContextSessionState.IN_APP_WITH_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("sessions"), "应包含 sessions 建议");
    }

    @Test
    @DisplayName("projects 别名 p 命令后建议 sessions")
    void should_suggestSessions_after_projectsAliasP() {
        String suggestion = suggester.suggest("p", ContextSessionState.IN_APP_WITH_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("sessions"), "应包含 sessions 建议");
    }

    @Test
    @DisplayName("sessions 命令后建议 sc")
    void should_suggestSc_after_sessions() {
        String suggestion = suggester.suggest("sessions", ContextSessionState.IN_APP_WITH_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("sc"), "应包含 sc 建议");
    }

    @Test
    @DisplayName("sc 命令后建议直接输入")
    void should_suggestDirectInput_after_sc() {
        String suggestion = suggester.suggest("sc", ContextSessionState.IN_APP_WITH_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("直接输入"), "应包含直接输入建议");
    }

    @Test
    @DisplayName("chat 命令后不返回建议")
    void should_returnNull_after_chat() {
        String suggestion = suggester.suggest("chat", ContextSessionState.IN_APP_WITH_SESSION);
        assertNull(suggestion, "chat 命令后不需要建议");
    }

    @Test
    @DisplayName("chatnow 命令后不返回建议")
    void should_returnNull_after_chatnow() {
        String suggestion = suggester.suggest("chatnow", ContextSessionState.IN_APP_WITH_SESSION);
        assertNull(suggestion, "chatnow 命令后不需要建议");
    }

    @Test
    @DisplayName("cn 别名后不返回建议")
    void should_returnNull_after_cn() {
        String suggestion = suggester.suggest("cn", ContextSessionState.IN_APP_WITH_SESSION);
        assertNull(suggestion, "cn 命令后不需要建议");
    }

    @Test
    @DisplayName("reset 命令后建议 sc 或 sessions")
    void should_suggestScOrSessions_after_reset() {
        String suggestion = suggester.suggest("reset", ContextSessionState.IN_APP_NO_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("sc") || suggestion.contains("sessions"),
                "应包含 sc 或 sessions 建议");
    }

    @Test
    @DisplayName("status 命令后建议 chat（已初始化）")
    void should_suggestChat_after_status_initialized() {
        String suggestion = suggester.suggest("status", ContextSessionState.IN_APP_WITH_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("直接输入") || suggestion.contains("chat"),
                "已初始化状态 status 后应建议对话");
    }

    @Test
    @DisplayName("status 命令后建议 sc（未初始化）")
    void should_suggestSc_after_status_uninitialized() {
        String suggestion = suggester.suggest("status", ContextSessionState.IN_APP_NO_SESSION);
        assertNotNull(suggestion);
        assertTrue(suggestion.contains("sc"), "未初始化状态 status 后应建议绑定");
    }

    @Test
    @DisplayName("help 命令后不返回建议")
    void should_returnNull_after_help() {
        String suggestion = suggester.suggest("help", ContextSessionState.IN_APP_WITH_SESSION);
        assertNull(suggestion, "help 命令后不需要建议");
    }

    @Test
    @DisplayName("commands 命令后不返回建议")
    void should_returnNull_after_commands() {
        String suggestion = suggester.suggest("commands", ContextSessionState.IN_APP_WITH_SESSION);
        assertNull(suggestion, "commands 命令后不需要建议");
    }
}

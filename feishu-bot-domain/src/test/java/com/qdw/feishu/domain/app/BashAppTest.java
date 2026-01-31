package com.qdw.feishu.domain.app;

import com.qdw.feishu.domain.history.BashHistoryManager;
import com.qdw.feishu.domain.message.Message;
import com.qdw.feishu.domain.message.Sender;
import com.qdw.feishu.domain.validation.CommandWhitelistValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BashAppTest {

    private BashApp bashApp;
    private CommandWhitelistValidator validator;
    private BashHistoryManager historyManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = new CommandWhitelistValidator();
        historyManager = new BashHistoryManager();
        bashApp = new BashApp(validator, historyManager);
    }

    @Test
    void testGetAppId_returnsBash() {
        assertEquals("bash", bashApp.getAppId());
    }

    @Test
    void testGetAppName_returns命令执行() {
        assertEquals("命令执行", bashApp.getAppName());
    }

    @Test
    void testGetDescription_returns执行安全的bash命令() {
        assertEquals("执行安全的bash命令", bashApp.getDescription());
    }

    @Test
    void testExecute_lsCommand_returnsFileListing() {
        Message message = createMessage("/bash ls");

        String result = bashApp.execute(message);

        assertNotNull(result);
        assertFalse(result.contains("错误"));
    }

    @Test
    void testExecute_invalidCommand_returnsErrorMessage() {
        Message message = createMessage("/bash rm -rf /");

        String result = bashApp.execute(message);

        assertTrue(result.contains("不在白名单中") || result.contains("不允许"));
    }

    @Test
    void testExecute_workspaceCreatedIfNotExists() {
        File originalWorkspace = new File(".workspace");
        if (originalWorkspace.exists()) {
            originalWorkspace.delete();
        }

        Message message = createMessage("/bash ls");
        bashApp.execute(message);

        File workspace = new File(".workspace");
        assertTrue(workspace.exists() || originalWorkspace.exists(), "Workspace directory should be created");
        assertTrue(workspace.isDirectory() || originalWorkspace.isDirectory(), "Workspace should be a directory");
    }

    @Test
    void testExecute_commandRecordedInHistory() {
        Message message = createMessage("/bash ls");

        bashApp.execute(message);

        var history = historyManager.getHistory(10);
        assertFalse(history.isEmpty());
        assertEquals("ls", history.get(0).command());
    }

    @Test
    void testExecute_emptyCommand_returnsHelp() {
        Message message = createMessage("/bash");

        String result = bashApp.execute(message);

        assertTrue(result.contains("用法") || result.contains("help"));
    }

    @Test
    void testExecute_historyCommand_returnsHistory() {
        historyManager.recordExecution("ls", "file1", true);
        historyManager.recordExecution("cat", "content", true);

        Message message = createMessage("/bash history");

        String result = bashApp.execute(message);

        assertTrue(result.contains("ls") || result.contains("历史"));
    }

    @Test
    void testExecute_createsWorkspaceInCurrentDir() {
        Message message = createMessage("/bash ls");

        String result = bashApp.execute(message);

        assertNotNull(result);
        assertFalse(result.contains("错误"));

        File workspace = new File(".workspace");
        assertTrue(workspace.exists(), "Workspace should exist after command execution");
    }

    private Message createMessage(String content) {
        Message message = new Message();
        message.setContent(content);
        message.setChatId("test-chat");
        message.setMessageId("test-msg");
        message.setSender(new Sender());
        return message;
    }
}

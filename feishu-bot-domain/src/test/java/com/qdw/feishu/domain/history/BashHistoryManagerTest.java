package com.qdw.feishu.domain.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BashHistoryManagerTest {

    private BashHistoryManager historyManager;

    @BeforeEach
    void setUp() {
        historyManager = new BashHistoryManager();
    }

    @Test
    void testRecordExecution_addsEntry() {
        historyManager.recordExecution("ls -la", "file1.txt\nfile2.txt", true);

        List<CommandExecution> history = historyManager.getHistory(10);

        assertEquals(1, history.size());
        assertEquals("ls -la", history.get(0).command());
        assertEquals("file1.txt\nfile2.txt", history.get(0).output());
        assertTrue(history.get(0).success());
        assertNotNull(history.get(0).timestamp());
    }

    @Test
    void testGetHistory_returnsRecentEntries() {
        historyManager.recordExecution("cmd1", "output1", true);
        historyManager.recordExecution("cmd2", "output2", true);
        historyManager.recordExecution("cmd3", "output3", true);

        List<CommandExecution> history = historyManager.getHistory(2);

        assertEquals(2, history.size());
        assertEquals("cmd3", history.get(0).command());
        assertEquals("cmd2", history.get(1).command());
    }

    @Test
    void testHistoryLimit_100EntriesMax() {
        for (int i = 0; i < 105; i++) {
            historyManager.recordExecution("cmd" + i, "output" + i, true);
        }

        List<CommandExecution> history = historyManager.getHistory(200);

        assertEquals(100, history.size());
        assertEquals("cmd104", history.get(0).command());
        assertEquals("cmd5", history.get(99).command());
    }

    @Test
    void testThreadSafety_concurrentRecordings() throws InterruptedException {
        int threadCount = 10;
        int commandsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < commandsPerThread; j++) {
                    historyManager.recordExecution(
                        "thread" + threadId + "_cmd" + j,
                        "output",
                        true
                    );
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        List<CommandExecution> history = historyManager.getHistory(200);

        assertEquals(threadCount * commandsPerThread, history.size());
    }

    @Test
    void testGetHistory_limitZero_returnsEmpty() {
        historyManager.recordExecution("cmd1", "output1", true);

        List<CommandExecution> history = historyManager.getHistory(0);

        assertTrue(history.isEmpty());
    }

    @Test
    void testGetHistory_limitLargerThanActual_returnsAll() {
        historyManager.recordExecution("cmd1", "output1", true);
        historyManager.recordExecution("cmd2", "output2", true);

        List<CommandExecution> history = historyManager.getHistory(100);

        assertEquals(2, history.size());
    }

    @Test
    void testRecordExecution_failedCommand() {
        historyManager.recordExecution("rm -rf /", "Permission denied", false);

        List<CommandExecution> history = historyManager.getHistory(10);

        assertEquals(1, history.size());
        assertFalse(history.get(0).success());
        assertEquals("Permission denied", history.get(0).output());
    }
}

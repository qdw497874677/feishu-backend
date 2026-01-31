package com.qdw.feishu.domain.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandWhitelistValidatorTest {

    private CommandWhitelistValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CommandWhitelistValidator();
    }

    @Test
    void testValidCommand_ls_returnsTrue() {
        assertTrue(validator.isValidCommand("ls"));
    }

    @Test
    void testValidCommand_grep_withArgs_returnsTrue() {
        assertTrue(validator.isValidCommand("grep pattern file.txt"));
    }

    @Test
    void testInvalidCommand_rm_returnsFalse() {
        assertFalse(validator.isValidCommand("rm -rf /"));
    }

    @Test
    void testCommandChaining_pipe_returnsFalse() {
        assertFalse(validator.isValidCommand("ls | grep test"));
    }

    @Test
    void testCommandChaining_semicolon_returnsFalse() {
        assertFalse(validator.isValidCommand("ls; rm -rf /"));
    }

    @Test
    void testCommandChaining_doubleAmpersand_returnsFalse() {
        assertFalse(validator.isValidCommand("ls && rm -rf /"));
    }

    @Test
    void testCommandChaining_doublePipe_returnsFalse() {
        assertFalse(validator.isValidCommand("ls || rm -rf /"));
    }

    @Test
    void testValidCommand_ll_returnsTrue() {
        assertTrue(validator.isValidCommand("ll"));
    }

    @Test
    void testValidCommand_dir_returnsTrue() {
        assertTrue(validator.isValidCommand("dir"));
    }

    @Test
    void testValidCommand_cat_returnsTrue() {
        assertTrue(validator.isValidCommand("cat file.txt"));
    }

    @Test
    void testValidCommand_less_returnsTrue() {
        assertTrue(validator.isValidCommand("less file.txt"));
    }

    @Test
    void testValidCommand_head_returnsTrue() {
        assertTrue(validator.isValidCommand("head -n 10 file.txt"));
    }

    @Test
    void testValidCommand_tail_returnsTrue() {
        assertTrue(validator.isValidCommand("tail -n 10 file.txt"));
    }

    @Test
    void testValidCommand_find_returnsTrue() {
        assertTrue(validator.isValidCommand("find . -name *.txt"));
    }

    @Test
    void testValidCommand_ping_returnsTrue() {
        assertTrue(validator.isValidCommand("ping -c 4 localhost"));
    }

    @Test
    void testEmptyCommand_returnsFalse() {
        assertFalse(validator.isValidCommand(""));
    }

    @Test
    void testNullCommand_returnsFalse() {
        assertFalse(validator.isValidCommand(null));
    }

    @Test
    void testCommandWithNewline_returnsFalse() {
        assertFalse(validator.isValidCommand("ls\nrm -rf /"));
    }
}

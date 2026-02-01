# OpenCode Testing Guide

## Prerequisites

Before testing, ensure:
- ✅ Application is running (check: `ps aux | grep spring-boot:run`)
- ✅ WebSocket is connected (check logs for "connected to wss://")
- ✅ OpenCodeApp is registered (check logs for "OpenCode 助手")
- ✅ You have access to Feishu platform
- ✅ You can send messages to the bot

## Test Environment Setup

### Current Application Status
```
Application: RUNNING
PID: 35567
Port: 17777
WebSocket: Connected
OpenCodeApp: Registered
Database: SQLite with metadata support
```

### Feishu Test Chat Requirements
- Use a test chat or group where the bot is added
- Ensure you can send messages to the bot
- Test in both normal chat and topic contexts

---

## Test Cases

### Test 1: Basic Command Execution

**Objective**: Verify OpenCodeApp responds to commands

**Steps**:
1. In Feishu, send: `/opencode help`
2. Observe the response

**Expected Result**:
- Bot should return help information
- Should list available commands
- Should explain how to use OpenCode

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 2: Simple Command Execution

**Objective**: Verify basic OpenCode command works

**Steps**:
1. In Feishu, send: `/opencode echo "Hello World"`
2. Observe the response

**Expected Result**:
- Bot should execute the echo command
- Should return "Hello World"
- Should create a new session (first command in topic)

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 3: Multi-turn Conversation

**Objective**: Verify session persistence across messages

**Steps**:
1. Send first command: `/opencode list all java files`
2. Note the response (should show file list)
3. Send second command: `/opencode count them`
4. Observe the response

**Expected Result**:
- First command creates a session and saves sessionId to metadata
- Second command reuses the same session
- Context is maintained between commands
- Bot remembers the previous command context

**Verification**:
```bash
# After Test 3, check database for session
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT topic_id, app_id, metadata FROM topic_mapping WHERE app_id='opencode';"
```

Expected: Should see a row with opencode app_id and metadata containing sessionId

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 4: Session Status Command

**Objective**: Verify session status reporting

**Steps**:
1. After Test 3, send: `/opencode session status`
2. Observe the response

**Expected Result**:
- Bot should display current session information
- Should show sessionId
- Should show command count
- Should show session creation time

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 5: Session List Command

**Objective**: Verify session listing functionality

**Steps**:
1. Send: `/opencode session list`
2. Observe the response

**Expected Result**:
- Bot should list all active sessions
- Should show session IDs and metadata
- Format should be readable

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 6: New Session Creation

**Objective**: Verify explicit new session creation

**Steps**:
1. Send: `/opencode new analyze this project`
2. Observe the response

**Expected Result**:
- Bot should clear any existing session
- Should create a brand new session
- Should execute the command in new session
- Old session should be replaced in metadata

**Verification**:
```bash
# Check database - should have new sessionId
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT topic_id, app_id, metadata FROM topic_mapping WHERE app_id='opencode';"
```

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 7: Async Execution (Long Task)

**Objective**: Verify async execution for tasks >5 seconds

**Steps**:
1. Send: `/opencode sleep 10` (or any long-running command)
2. Wait for response
3. Observe initial and final responses

**Expected Result**:
- Within 5 seconds: Should receive "⏳ 任务正在执行中..." message
- After task completes: Should receive actual result
- Should not timeout or error

**Notes**: This test verifies the async executor works correctly

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

### Test 8: Command Aliases

**Objective**: Verify command aliases work

**Steps**:
1. Send: `/oc help`
2. Observe response
3. Send: `/code help`
4. Observe response

**Expected Result**:
- Both aliases should work
- Should return same help information
- Should behave identically to `/opencode help`

**Actual Result**: [To be filled during testing]

**Status**: ⏳ PENDING

---

## Post-Test Verification

### Database Verification Script

After testing, run this script to verify session persistence:

```bash
#!/bin/bash
echo "=== OpenCode Session Verification ==="
echo ""
echo "1. Checking for opencode topic mappings:"
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT topic_id, metadata FROM topic_mapping WHERE app_id='opencode';"
echo ""
echo "2. Checking metadata content:"
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT json_extract(metadata, '$.opencode.sessionId') as session_id FROM topic_mapping WHERE app_id='opencode';"
echo ""
echo "3. Total opencode sessions:"
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT COUNT(*) FROM topic_mapping WHERE app_id='opencode';"
```

### Log Verification

Check application logs for errors:
```bash
echo "Checking for errors in logs:"
grep -i "error\|exception" /tmp/feishu-manual-start.log | grep -i "opencode" | tail -20
```

---

## Test Results Summary

### Test Execution Matrix

| Test # | Test Name | Expected | Actual | Status |
|--------|-----------|----------|--------|--------|
| 1 | Basic Command Execution | Help text returned | ⏳ TBD | PENDING |
| 2 | Simple Command Execution | Echo works | ⏳ TBD | PENDING |
| 3 | Multi-turn Conversation | Session persists | ⏳ TBD | PENDING |
| 4 | Session Status | Status displayed | ⏳ TBD | PENDING |
| 5 | Session List | All sessions shown | ⏳ TBD | PENDING |
| 6 | New Session | New session created | ⏳ TBD | PENDING |
| 7 | Async Execution | Async callback works | ⏳ TBD | PENDING |
| 8 | Command Aliases | Aliases work | ⏳ TBD | PENDING |

---

## Success Criteria

All tests must pass for the feature to be considered complete:

- ✅ All 8 tests execute without errors
- ✅ Expected results match actual results
- ✅ Database shows correct session persistence
- ✅ No errors in application logs
- ✅ Multi-turn conversations work correctly
- ✅ Async execution works for long tasks

---

## Issue Reporting

If any test fails:

1. Document the actual result
2. Copy the error message
3. Check application logs: `tail -100 /tmp/feishu-manual-start.log`
4. Note the exact command sent
5. Record the topic ID if applicable
6. Report all findings for debugging

---

## Next Steps After Testing

If all tests pass:
1. Mark all checkboxes in plan file as complete
2. Update BLOCKERS.md with testing results
3. Create user documentation
4. Mark feature as production-ready

If any test fails:
1. Document the failure
2. Analyze root cause
3. Fix the issue
4. Re-test
5. Verify regression tests still pass

---

**Last Updated**: 2026-02-01 09:40
**Status**: ⏳ READY FOR TESTING (awaiting Feishu access)
**Prepared By**: Implementation Session

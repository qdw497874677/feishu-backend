# Testing Guide for OpenCode Multi-turn Conversation

## Prerequisites ✅

- [x] Application is running (PID 35567)
- [x] OpenCode app is registered
- [x] Database schema is ready
- [x] All code is committed
- [x] Build is successful
- [ ] Feishu platform access (USER)

---

## Test Cases

### Test 1: Basic Command - Help

**Command**:
```
/opencode help
```

**Expected Result**:
- Returns help information with command list
- Shows usage examples
- No errors

**Acceptance**:
- [ ] Help command returns correct information
- [ ] Command list is displayed
- [ ] Format is readable

---

### Test 2: Create New Session

**Command**:
```
/opencode echo hello world
```

**Expected Result**:
- OpenCode executes the command
- Returns result: "hello world"
- Session ID is saved to database

**Acceptance**:
- [ ] Command executes successfully
- [ ] Result is returned
- [ ] Session is created in database (verify with sqlite3)

**Database Verification**:
```bash
sqlite3 data/feishu-topic-mappings.db "SELECT topic_id, metadata FROM topic_mapping WHERE app_id='opencode' ORDER BY rowid DESC LIMIT 1;"
```

---

### Test 3: Multi-turn Conversation (Auto Session Reuse)

**Commands** (execute in sequence in same topic):
```
/opencode echo first message
/openco de echo second message
/openco de echo third message
```

**Expected Result**:
- All commands use the same session
- Session ID persists across messages
- OpenCode maintains conversation context

**Acceptance**:
- [ ] First message creates session
- [ ] Second message reuses session
- [ ] Third message reuses session
- [ ] All messages in same topic use same session ID

**Verification**:
```bash
# Check metadata contains same sessionId for all messages
sqlite3 data/feishu-topic-mappings.db "SELECT topic_id, json_extract(metadata, '$.opencode.sessionId') FROM topic_mapping WHERE app_id='opencode';"
```

---

### Test 4: Session Status

**Command**:
```
/opencode session status
```

**Expected Result**:
- Displays current session information
- Shows session ID
- Shows command count
- Shows creation time

**Acceptance**:
- [ ] Session ID is displayed
- [ ] Command count is correct
- [ ] Creation time is shown
- [ ] Format is readable

---

### Test 5: Session List

**Command**:
```
/opencode session list
```

**Expected Result**:
- Lists all OpenCode sessions
- Shows session IDs
- Shows command counts
- Shows last active times

**Acceptance**:
- [ ] All sessions are listed
- [ ] Session information is complete
- [ ] Format is readable

---

### Test 6: Explicit New Session

**Commands** (execute in sequence):
```
/openco de echo this is session 1
/openco de new echo this is session 2
```

**Expected Result**:
- First command uses existing session
- Second command creates NEW session
- Old session is not reused

**Acceptance**:
- [ ] First message uses session 1
- [ ] Second message creates session 2
- [ ] Session IDs are different
- [ ] Database shows two different session IDs

---

### Test 7: Async Execution

**Command**:
```
/openco de sleep 10
```

**Expected Result**:
- For tasks > 5 seconds: Returns "⏳ 任务正在执行中..." first
- Then returns complete result
- Does not block the bot

**Acceptance**:
- [ ] Initial response is "⏳ 任务正在执行中..."
- [ ] Complete result is returned later
- [ ] Bot remains responsive
- [ ] No timeout errors

---

### Test 8: Command Alias

**Command**:
```
/oc help
```

**Expected Result**:
- Same as `/opencode help`
- Returns help information

**Acceptance**:
- [ ] Alias works
- [ ] Returns same result as full command

---

## Test Execution Checklist

### Before Testing
- [ ] Application is running
- [ ] Log monitoring is active: `tail -f /tmp/feishu-manual-start.log`
- [ ] Database backup (optional): `cp data/feishu-topic-mappings.db data/backup.db`

### During Testing
- [ ] Execute each test case
- [ ] Verify expected results
- [ ] Capture evidence (screenshots, logs)
- [ ] Document any issues

### After Testing
- [ ] Update plan file with test results
- [ ] Mark completed checkboxes
- [ ] Document any bugs or issues
- [ ] Create test report

---

## Evidence Capture

For each test, capture:

1. **Screenshot**: Feishu message with bot response
2. **Log Output**: Relevant log lines from `/tmp/feishu-manual-start.log`
3. **Database Query**: Result of verification query
4. **Notes**: Any observations or issues

---

## Troubleshooting

### Issue: Command not recognized

**Check**:
```bash
# Verify app is registered
grep "OpenCode" /tmp/feishu-manual-start.log

# Verify command router
grep "AppRouter" /tmp/feishu-manual-start.log
```

### Issue: Session not persisting

**Check**:
```bash
# Check database
sqlite3 data/feishu-topic-mappings.db ".schema topic_mapping"
sqlite3 data/feishu-topic-mappings.db "SELECT * FROM topic_mapping WHERE app_id='opencode';"

# Check logs for errors
grep -i "error\|exception" /tmp/feishu-manual-start.log | grep -i opencode
```

### Issue: Async execution not working

**Check**:
```bash
# Verify opencodeExecutor bean
grep "opencodeExecutor" /tmp/feishu-manual-start.log

# Check async config
grep -A 10 "AsyncConfig" /tmp/feishu-manual-start.log
```

---

## Test Report Template

```markdown
# Test Execution Report

**Date**: 2026-02-01
**Tester**: [Your Name]
**Environment**: Dev

## Test Results

| Test | Status | Notes |
|------|--------|-------|
| Test 1: Help | ⏳ PENDING | |
| Test 2: Create Session | ⏳ PENDING | |
| Test 3: Multi-turn | ⏳ PENDING | |
| Test 4: Session Status | ⏳ PENDING | |
| Test 5: Session List | ⏳ PENDING | |
| Test 6: New Session | ⏳ PENDING | |
| Test 7: Async Execution | ⏳ PENDING | |
| Test 8: Command Alias | ⏳ PENDING | |

## Issues Found

[Document any issues]

## Evidence

[Link to evidence files]

## Conclusion

[Overall assessment]
```

---

## Quick Reference

**Application Status**: `ps aux | grep feishu | grep -v grep`
**Log Monitor**: `tail -f /tmp/feishu-manual-start.log`
**Database Query**: `sqlite3 data/feishu-topic-mappings.db "SELECT * FROM topic_mapping WHERE app_id='opencode';"`
**Restart App**: `pkill -9 -f feishu && ./start-feishu.sh`

---

**Ready to test! Good luck!** 🚀

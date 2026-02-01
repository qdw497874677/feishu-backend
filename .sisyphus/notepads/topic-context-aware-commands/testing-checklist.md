# Testing Checklist for User

## Pre-Test Verification

### Application Status Check ✅
```bash
# Check if application is running
ps aux | grep -E "java.*Application" | grep -v grep
# Expected: 1 process running

# Check port 17777
lsof -i:17777
# Expected: java process LISTEN on port 17777

# Check WebSocket connection
grep "connected to wss://" /tmp/feishu-run.log | tail -1
# Expected: Connection to msg-frontier.feishu.cn

# Check apps registered
grep "AppRegistry: 应用注册完成" /tmp/feishu-run.log | tail -1
# Expected: 4 个应用: [help, bash, history, time]
```

### Code Verification ✅
```bash
# Verify topic-aware code is present
grep "话题中的消息不包含前缀，添加前缀" /root/workspace/feishu-backend/feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
# Expected: Code line exists

# Verify whitelist updated
grep "mkdir.*opencode" /root/workspace/feishu-backend/feishu-bot-domain/src/main/java/com/qdw/feishu/domain/validation/CommandWhitelistValidator.java
# Expected: Both commands in WHITELIST
```

## Test Cases

### Test 1: Topic without Prefix (MAIN FEATURE)

**Objective:** Verify users can execute commands without prefix in topics

**Steps:**
1. Open Feishu client
2. Go to 1:1 chat with bot
3. Send message: `/bash pwd`
4. Wait for bot to create topic
5. **In that topic**, send: `pwd` (NO PREFIX)

**Expected Results:**
- Bot executes `pwd` command
- Bot returns current directory listing
- Log shows: `"话题中的消息不包含前缀，添加前缀: 'pwd'"`
- Log shows: `"话题消息处理后的内容: '/bash pwd'"`

**How to Check Logs:**
```bash
tail -f /tmp/feishu-run.log | grep -E "(话题|消息|pwd)"
```

**Pass Criteria:** ✅ Bot executes command without requiring `/bash` prefix

---

### Test 2: Topic with Prefix (BACKWARD COMPAT)

**Objective:** Verify backward compatibility - prefixed commands still work

**Steps:**
1. In bash topic (from Test 1)
2. Send message: `/bash ls -la`

**Expected Results:**
- Bot executes `ls -la` command
- Bot returns detailed directory listing
- Log shows: `"话题中的消息包含命令前缀，去除前缀: '/bash ls -la'"`
- Log shows: `"话题消息处理后的内容: '/bash ls -la'"` (after strip and re-add)

**Pass Criteria:** ✅ Bot executes command with prefix correctly

---

### Test 3: Normal Chat (NO REGRESSION)

**Objective:** Verify normal (non-topic) chat still works

**Steps:**
1. Go to 1:1 chat with bot (not in topic)
2. Send message: `/bash pwd`

**Expected Results:**
- Bot executes `pwd` command
- Bot returns current directory
- No topic-related log messages
- Normal message processing flow

**Pass Criteria:** ✅ Normal chat execution unchanged

---

### Test 4: Whitelist Commands

**Objective:** Verify new whitelisted commands work

**Steps:**
1. In bash topic
2. Send message: `mkdir test_dir`
3. (Optional) Send message: `opencode --version`

**Expected Results:**
- Bot executes `mkdir test_dir` successfully
- `test_dir` is created in current directory
- No "command not in whitelist" error

**How to Verify:**
```bash
# In another terminal, check if directory was created
ls -la | grep test_dir
```

**Pass Criteria:** ✅ Whitelisted commands execute successfully

---

## Post-Test Verification

### Evidence Collection ✅

**For Each Test Case, Capture:**
1. Bot response (screenshot or copy text)
2. Log lines showing message processing
3. Command execution output

**Example Log Evidence:**
```
2026-01-31 17:40:15.123 [feishu-ws-listener] INFO  c.q.f.a.l.FeishuEventListener - Received message from user
2026-01-31 17:40:15.124 [feishu-ws-listener] DEBUG c.q.f.d.s.BotMessageService - 话题中的消息不包含前缀，添加前缀: 'pwd'
2026-01-31 17:40:15.125 [feishu-ws-listener] DEBUG c.q.f.d.s.BotMessageService - 话题消息处理后的内容: '/bash pwd'
2026-01-31 17:40:15.130 [bash-async-1] INFO  c.q.f.a.b.BashApp - Executing: pwd
2026-01-31 17:40:15.135 [bash-async-1] INFO  c.q.f.a.b.BashApp - Output: /root/workspace/feishu-backend
```

### Success Criteria

**All Tests Pass If:**
- ✅ Test 1: `pwd` works in topic without prefix
- ✅ Test 2: `/bash ls -la` works in topic with prefix
- ✅ Test 3: `/bash pwd` works in normal chat
- ✅ Test 4: `mkdir test_dir` executes successfully

**Partial Success:**
- If any test fails, capture error logs
- Document which test failed
- Note exact error message
- Report to developer

---

## Troubleshooting

### Issue: Bot doesn't respond

**Check:**
1. Is application running? `ps aux | grep Application`
2. Is WebSocket connected? `grep "connected to wss://" /tmp/feishu-run.log`
3. Are there error logs? `tail -50 /tmp/feishu-run.log | grep ERROR`

### Issue: Command not executed

**Check:**
1. Is command in whitelist? Check CommandWhitelistValidator.java
2. Was topic mapping found? Check logs for "话题映射不存在"
3. Was app routed correctly? Check logs for "AppRouter"

### Issue: Wrong behavior

**Check:**
1. Log file: `tail -100 /tmp/feishu-run.log`
2. Look for unexpected log messages
3. Verify code is deployed: `grep "话题中的消息" feishu-bot-domain/src/.../BotMessageService.java`

---

## Ready to Test?

**Pre-flight Checklist:**
- [ ] Application is running (PID 10646)
- [ ] Port 17777 is open
- [ ] WebSocket is connected
- [ ] Log monitoring is active
- [ ] Feishu client is open

**When Ready:**
1. Start log monitoring: `tail -f /tmp/feishu-run.log | grep -E "(话题|消息)"`
2. Open Feishu client
3. Execute Test 1
4. Verify results
5. Execute Test 2
6. Verify results
7. Execute Test 3
8. Verify results
9. Execute Test 4
10. Verify results

**Report Results:**
- All tests pass → Ready to commit
- Any test fails → Report errors with logs

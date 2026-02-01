# Test Scenario Documentation

## 2026-01-31 17:58 - Detailed Test Scenarios for Manual Testing

### Overview

This document provides detailed test scenarios for manual testing in Feishu UI. Each scenario includes step-by-step instructions, expected results, and verification steps.

---

## Test Scenario 1: Topic without Prefix (Main Feature)

### Objective
Verify users can execute commands in topics WITHOUT the command prefix.

### Prerequisites
- ✅ Application is running (PID 10646)
- ✅ WebSocket connected to Feishu
- ✅ Feishu client is open
- ✅ 1:1 chat with bot is available

### Test Steps

#### Step 1: Create Topic
1. Open 1:1 chat with Feishu bot
2. Send message: `/bash pwd`
3. **Expected:**
   - Bot responds with current directory
   - Bot creates a topic
   - Topic shows "bash" icon or label
4. **Verification:**
   - Check log for: `"话题中的消息不包含前缀，添加前缀: 'pwd'"` (MAY NOT APPEAR - this is first message)
   - Check log for: `"Executing: pwd"`
   - Check response shows directory path: `/root/workspace/feishu-backend`

#### Step 2: Execute Command without Prefix
1. **In the topic created in Step 1**, send message: `pwd`
2. **Expected:**
   - Bot executes `pwd` command
   - Bot returns current directory listing
   - No error message
3. **Verification (CRITICAL):**
   - **Check log for:** `"话题中的消息不包含前缀，添加前缀: 'pwd'"`
   - **Check log for:** `"话题消息处理后的内容: '/bash pwd'"`
   - **Check log for:** `"Executing: pwd"`
   - **Check response shows:** `/root/workspace/feishu-backend`

#### Step 3: Verify Behavior
1. The command should execute exactly as if you typed `/bash pwd`
2. Response time should be similar to prefixed command
3. No difference in output format

### Success Criteria
- [ ] Bot responds to `pwd` without prefix in topic
- [ ] Log shows prefix was added: `"添加前缀: 'pwd'"`
- [ ] Command executes successfully
- [ ] Output is correct: `/root/workspace/feishu-backend`

### Failure Indicators
- ❌ Bot doesn't respond to `pwd`
- ❌ Bot returns help text instead of executing
- ❌ Log shows error or exception
- ❌ Output is incorrect

### Common Issues and Solutions

**Issue:** Bot returns help text
- **Cause:** Topic mapping not found
- **Check:** Log for `"话题映射不存在"`
- **Solution:** This is normal if topic expired, try creating new topic

**Issue:** Bot doesn't respond
- **Cause:** Application not running or WebSocket disconnected
- **Check:** `ps aux | grep Application`
- **Solution:** Restart application

**Issue:** Wrong directory returned
- **Cause:** Different working directory
- **Check:** This is normal, working directory varies
- **Solution:** None expected, verify directory is valid

---

## Test Scenario 2: Topic with Prefix (Backward Compatibility)

### Objective
Verify backward compatibility - commands WITH prefix still work in topics.

### Prerequisites
- ✅ Test Scenario 1 completed (topic exists)
- ✅ Topic is active and bot is responsive

### Test Steps

#### Step 1: Execute Command with Prefix
1. **In the same bash topic**, send message: `/bash ls -la`
2. **Expected:**
   - Bot executes `ls -la` command
   - Bot returns detailed directory listing
   - Format: long listing with permissions
3. **Verification (CRITICAL):**
   - **Check log for:** `"话题中的消息包含命令前缀，去除前缀: '/bash ls -la'"`
   - **Check log for:** `"话题消息处理后的内容: '/bash ls -la'"`
   - **Check log for:** `"Executing: ls -la"`
   - **Check response shows:** Directory listing in long format

#### Step 2: Verify Transformation
1. Log should show:
   - Input: `/bash ls -la`
   - After strip: `ls -la`
   - After add: `/bash ls -la`
2. This proves the strip-and-re-add logic works

### Success Criteria
- [ ] Bot responds to `/bash ls -la` in topic
- [ ] Log shows prefix was stripped: `"去除前缀: '/bash ls -la'"`
- [ ] Command executes successfully
- [ ] Output shows detailed directory listing

### Failure Indicators
- ❌ Bot doesn't respond
- ❌ Bot returns error
- ❌ Log shows incorrect transformation
- ❌ Output format is wrong

### Common Issues and Solutions

**Issue:** Bot says command not found
- **Cause:** Command not in whitelist (unlikely for ls)
- **Check:** CommandWhitelistValidator.java
- **Solution:** Use different command

**Issue:** Permission denied
- **Cause:** Normal for some directories
- **Check:** Try command in local terminal
- **Solution:** This is expected behavior, not a bug

---

## Test Scenario 3: Normal Chat (No Regression)

### Objective
Verify normal (non-topic) chat is unaffected by changes.

### Prerequisites
- ✅ Application is running
- ✅ 1:1 chat with bot (NOT in topic)

### Test Steps

#### Step 1: Execute Normal Command
1. **In 1:1 chat (NOT in topic)**, send message: `/bash pwd`
2. **Expected:**
   - Bot executes `pwd` command
   - Bot returns current directory
   - Bot creates a new topic (as per existing behavior)
3. **Verification:**
   - **Check log for NO topic-related messages**
   - **Check log for:** `"Executing: pwd"`
   - **Check response shows:** `/root/workspace/feishu-backend`
   - **Verify:** A new topic is created (existing behavior preserved)

#### Step 2: Verify No Transformation
1. Log should NOT contain:
   - `"话题中的消息不包含前缀，添加前缀"`
   - `"话题中的消息包含命令前缀，去除前缀"`
2. This confirms normal chat is unchanged

### Success Criteria
- [ ] Bot responds to `/bash pwd` in normal chat
- [ ] Log shows NO topic transformation messages
- [ ] Command executes normally
- [ ] Topic is created (existing behavior)
- [ ] No regression from previous behavior

### Failure Indicators
- ❌ Bot doesn't respond
- [ ] Bot responds differently than before
- ❌ Topic not created
- ❌ Output format changed

### Common Issues and Solutions

**Issue:** Bot doesn't create topic
- **Cause:** Topic creation logic unchanged
- **Check:** Was topic creation working before?
- **Solution:** This should work exactly as before

**Issue:** Different response format
- **Cause:** Changes shouldn't affect normal chat
- **Check:** Log for topic-related messages
- **Solution:** Report issue, this is a regression

---

## Test Scenario 4: Whitelist Commands

### Objective
Verify newly whitelisted commands work in topics.

### Prerequisites
- ✅ Test Scenario 1 completed (bash topic exists)
- ✅ `mkdir` is in WHITELIST
- ✅ `opencode` is in WHITELIST

### Test Steps

#### Step 1: Execute `mkdir` Command
1. **In bash topic**, send message: `mkdir test_dir`
2. **Expected:**
   - Bot executes `mkdir test_dir`
   - Bot confirms directory created
   - No "command not in whitelist" error
3. **Verification:**
   - **Check log for:** `"话题中的消息不包含前缀，添加前缀: 'mkdir test_dir'"`
   - **Check log for:** `"Executing: mkdir test_dir"`
   - **Verify directory exists:**
     ```bash
     ls -la | grep test_dir
     ```

#### Step 2: Execute `opencode` Command (Optional)
1. **In bash topic**, send message: `opencode --version`
2. **Expected:**
   - Bot executes command (if opencode exists)
   - Or returns "command not found" (if opencode not installed)
   - But NOT "command not in whitelist"
3. **Verification:**
   - **Check log for:** `"话题中的消息不包含前缀，添加前缀: 'opencode --version'"`
   - **Check error message:** Should be OS-level, not whitelist error

#### Step 3: Cleanup
1. **In bash topic**, send message: `rmdir test_dir`
2. Or in terminal: `rmdir test_dir`

### Success Criteria
- [ ] `mkdir test_dir` executes in topic
- [ ] No whitelist error
- [ ] Directory is created
- [ ] Log shows prefix was added
- [ ] Cleanup command works

### Failure Indicators
- ❌ Bot says "command not in whitelist"
- ❌ Bot says "command not allowed"
- ❌ Directory not created
- ❌ Permission denied (unexpected)

### Common Issues and Solutions

**Issue:** "command not in whitelist"
- **Cause:** Command not actually added to whitelist
- **Check:** `grep "mkdir" feishu-bot-domain/src/.../CommandWhitelistValidator.java`
- **Solution:** Rebuild and redeploy

**Issue:** Permission denied
- **Cause:** Working directory permissions
- **Check:** `pwd` and `ls -la` to check permissions
- **Solution:** Try different directory or command

---

## Test Evidence Collection

### For Each Test Scenario, Collect:

#### 1. Log Evidence
```bash
# Capture relevant log lines
grep "话题中的消息" /tmp/feishu-run.log | tail -20 > test_scenario_X.log
```

#### 2. Screenshot Evidence
- Bot response in Feishu UI
- Topic creation confirmation
- Command execution output

#### 3. Terminal Evidence
```bash
# Verify application status
ps aux | grep Application > app_status.txt
lsof -i:17777 > port_status.txt

# Verify command execution (for mkdir test)
ls -la | grep test_dir > mkdir_result.txt
```

### Evidence Checklist

**Test Scenario 1:**
- [ ] Log shows: `"添加前缀: 'pwd'"`
- [ ] Log shows: `"话题消息处理后的内容: '/bash pwd'"`
- [ ] Bot response screenshot captured
- [ ] Command output correct

**Test Scenario 2:**
- [ ] Log shows: `"去除前缀: '/bash ls -la'"`
- [ ] Log shows: `"话题消息处理后的内容: '/bash ls -la'`
- [ ] Bot response screenshot captured
- [ ] Output format correct

**Test Scenario 3:**
- [ ] Log shows NO topic messages
- [ ] Bot response screenshot captured
- [ ] Topic created (existing behavior)
- [ ] No regressions

**Test Scenario 4:**
- [ ] Log shows: `"添加前缀: 'mkdir test_dir'"`
- [ ] No whitelist error
- [ ] Directory created (verified in terminal)
- [ ] Cleanup successful

---

## Test Execution Checklist

### Pre-Test Setup
- [ ] Application running: `ps aux | grep Application`
- [ ] WebSocket connected: `grep "connected to wss://" /tmp/feishu-run.log`
- [ ] Log monitoring active: `tail -f /tmp/feishu-run.log | grep "话题"`
- [ ] Feishu client open
- [ ] 1:1 chat with bot accessible

### Execute Tests
- [ ] Test Scenario 1 completed
- [ ] Test Scenario 2 completed
- [ ] Test Scenario 3 completed
- [ ] Test Scenario 4 completed

### Post-Test Verification
- [ ] All log evidence captured
- [ ] All screenshots captured
- [ ] All terminal outputs captured
- [ ] Evidence organized by scenario
- [ ] Results documented

---

## Test Results Template

### Test Scenario 1: Topic without Prefix
- **Status:** [ ] PASS / [ ] FAIL / [ ] SKIP
- **Evidence:** [Attached]
- **Notes:**

### Test Scenario 2: Topic with Prefix
- **Status:** [ ] PASS / [ ] FAIL / [ ] SKIP
- **Evidence:** [Attached]
- **Notes:**

### Test Scenario 3: Normal Chat
- **Status:** [ ] PASS / [ ] FAIL / [ ] SKIP
- **Evidence:** [Attached]
- **Notes:**

### Test Scenario 4: Whitelist Commands
- **Status:** [ ] PASS / [ ] FAIL / [ ] SKIP
- **Evidence:** [Attached]
- **Notes:**

---

## Summary

**Total Test Scenarios:** 4
**Estimated Time:** 15 minutes
**Expected Result:** All 4 scenarios PASS
**Confidence:** VERY HIGH (based on 100% automated test pass rate)

**After Testing:**
- If all PASS → Ready to commit ✅
- If any FAIL → Report to developer for fix 🔧

---

**Document:** test-scenarios.md
**Purpose:** Detailed step-by-step test procedures
**Target:** User performing manual testing
**Status:** READY FOR USE

# ✅ Test Execution Checklist

**Print this checklist and use it while testing in Feishu**
**Estimated Time:** 15 minutes total

---

## Pre-Test Setup (3 minutes)

### Environment Verification
- [ ] Application is running
  ```bash
  ps aux | grep "[A]pplication.*feishu"
  ```
  Expected: 1 process (PID 10646)

- [ ] WebSocket is connected
  ```bash
  grep "connected to wss://" /tmp/feishu-run.log | tail -1
  ```
  Expected: Connection to msg-frontier.feishu.cn

- [ ] Code is deployed
  ```bash
  grep "话题中的消息不包含前缀，添加前缀" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
  ```
  Expected: Found in code

### Log Monitoring Setup
- [ ] Open terminal for log monitoring
  ```bash
  tail -f /tmp/feishu-run.log | grep -E "(话题|消息)"
  ```
  Keep this terminal visible while testing

- [ ] Create evidence directory
  ```bash
  mkdir -p .sisyphus/notepads/topic-context-aware-commands/evidence
  cd .sisyphus/notepads/topic-context-aware-commands/evidence
  ```

- [ ] Pre-test log snapshot
  ```bash
  cp /tmp/feishu-run.log ./feishu-run-before-test.log
  ```

### Preparation Checklist
- [ ] Feishu client is open
- [ ] Can see the bot conversation
- [ ] Log monitoring terminal is visible
- [ ] Evidence directory is ready
- [ ] Read Test 1 steps below
- [ ] Ready to begin

**Pre-Test Setup Time:** ~3 minutes

---

## Test 1: Topic Without Prefix (4 minutes)

### Objective
Verify commands WITHOUT prefix work in topics

### Steps
- [ ] **Step 1.1:** In Feishu normal chat, send: `/bash pwd`
- [ ] **Step 1.2:** Wait for bot to respond
- [ ] **Step 1.3:** Bot creates a topic
- [ ] **Step 1.4:** In that topic, send: `pwd` (NO PREFIX!)
- [ ] **Step 1.5:** Wait for bot to respond

### Expected Results
- [ ] Bot executes pwd command
- [ ] Bot returns: `/root/workspace/feishu-backend`
- [ ] Log shows: `"话题中的消息不包含前缀，添加前缀: 'pwd'"`
- [ ] Log shows: `"话题消息处理后的内容: '/bash pwd'"`

### Evidence Capture
- [ ] Capture log output:
  ```bash
  grep -A 3 -B 1 "话题中的消息不包含前缀，添加前缀" /tmp/feishu-run.log | tail -10 > evidence-test1-logs.txt
  ```
- [ ] Take screenshot of bot response
- [ ] Save as: `evidence-test1-response.png`

### Verification Checklist
- [ ] Bot responded with correct directory
- [ ] Log shows prefix was added
- [ ] No errors in log
- [ ] Evidence files created

### Test Result
- [ ] ✅ PASS
- [ ] ❌ FAIL

**If Fail:** Note error description _________________________

**Test 1 Time:** ~4 minutes

---

## Test 2: Topic With Prefix (3 minutes)

### Objective
Verify commands WITH prefix still work (backward compatibility)

### Steps
- [ ] **Step 2.1:** In the SAME bash topic from Test 1
- [ ] **Step 2.2:** Send: `/bash ls -la`
- [ ] **Step 2.3:** Wait for bot to respond

### Expected Results
- [ ] Bot executes ls -la command
- [ ] Bot returns directory listing
- [ ] Log shows: `"话题中的消息包含命令前缀，去除前缀: '/bash ls -la'"`
- [ ] Log shows: `"话题消息处理后的内容: 'ls -la'"`

### Evidence Capture
- [ ] Capture log output:
  ```bash
  grep -A 3 -B 1 "话题中的消息包含命令前缀" /tmp/feishu-run.log | tail -10 > evidence-test2-logs.txt
  ```
- [ ] Take screenshot of bot response
- [ ] Save as: `evidence-test2-response.png`

### Verification Checklist
- [ ] Bot responded with directory listing
- [ ] Log shows prefix was stripped
- [ ] No errors in log
- [ ] Evidence files created

### Test Result
- [ ] ✅ PASS
- [ ] ❌ FAIL

**If Fail:** Note error description _________________________

**Test 2 Time:** ~3 minutes

---

## Test 3: Normal Chat (3 minutes)

### Objective
Verify normal chat (no topic) still works - no regression

### Steps
- [ ] **Step 3.1:** Go to normal chat (NOT in a topic)
- [ ] **Step 3.2:** Send: `/bash pwd`
- [ ] **Step 3.3:** Wait for bot to respond

### Expected Results
- [ ] Bot executes pwd command normally
- [ ] Bot creates a new topic
- [ ] Bot returns: `/root/workspace/feishu-backend`
- [ ] Log shows normal message processing
- [ ] Log does NOT show any topic messages

### Evidence Capture
- [ ] Capture log output:
  ```bash
  grep -A 5 "Processing message" /tmp/feishu-run.log | tail -10 > evidence-test3-logs.txt
  ```
- [ ] Verify no topic messages:
  ```bash
  grep "话题" /tmp/feishu-run.log | tail -20 > evidence-test3-no-topic.txt
  ```
- [ ] Take screenshot of bot response
- [ ] Save as: `evidence-test3-response.png`

### Verification Checklist
- [ ] Bot responded with correct directory
- [ ] New topic was created
- [ ] No topic messages in log
- [ ] Evidence files created

### Test Result
- [ ] ✅ PASS
- [ ] ❌ FAIL

**If Fail:** Note error description _________________________

**Test 3 Time:** ~3 minutes

---

## Test 4: Whitelist Commands (3 minutes)

### Objective
Verify newly whitelisted commands work

### Steps
- [ ] **Step 4.1:** Go to the bash topic from Test 1
- [ ] **Step 4.2:** Send: `mkdir test_dir`
- [ ] **Step 4.3:** Wait for bot to respond

### Expected Results
- [ ] Bot creates test_dir directory
- [ ] No whitelist error
- [ ] Log shows: `"话题中的消息不包含前缀，添加前缀: 'mkdir test_dir'"`
- [ ] Log shows: `"话题消息处理后的内容: '/bash mkdir test_dir'"`

### Evidence Capture
- [ ] Capture log output:
  ```bash
  grep -A 3 -B 1 "mkdir" /tmp/feishu-run.log | tail -10 > evidence-test4-logs.txt
  ```
- [ ] Verify directory created:
  ```bash
  ls -la | grep test_dir > evidence-test4-verification.txt
  ls -ld test_dir > evidence-test4-directory.txt
  ```
- [ ] Take screenshot of bot response
- [ ] Save as: `evidence-test4-response.png`

### Verification Checklist
- [ ] Bot responded (success or appropriate message)
- [ ] Directory was created
- [ ] No whitelist error in log
- [ ] Evidence files created

### Cleanup (After Verification)
- [ ] Remove test directory:
  ```bash
  rm -rf test_dir
  ```
- [ ] Verify removed:
  ```bash
  ls | grep test_dir
  ```
  Expected: No results

### Test Result
- [ ] ✅ PASS
- [ ] ❌ FAIL

**If Fail:** Note error description _________________________

**Test 4 Time:** ~3 minutes

---

## Post-Test Summary (2 minutes)

### Evidence Review
- [ ] All evidence files created in evidence directory
- [ ] Check file count: `ls -lh` (should see ~12 files)
- [ ] Review log files for expected patterns
- [ ] Verify screenshots are clear

### Create Summary Document
- [ ] Use EVIDENCE-SUMMARY.md template
- [ ] Fill in results for all 4 tests
- [ ] Add overall conclusion
- [ ] Save in evidence directory

### Final Checklist
- [ ] Test 1: PASS / FAIL
- [ ] Test 2: PASS / FAIL
- [ ] Test 3: PASS / FAIL
- [ ] Test 4: PASS / FAIL

**Total Tests Passed:** ___ / 4

**Post-Test Time:** ~2 minutes

---

## Report Results

### If All Tests Pass ✅

```
SUCCESS: All 4 tests passed!

Test Results:
- Test 1 (pwd without prefix): ✅ PASS
- Test 2 (pwd with prefix): ✅ PASS
- Test 3 (normal chat): ✅ PASS
- Test 4 (mkdir): ✅ PASS

Evidence:
- Location: .sisyphus/notepads/topic-context-aware-commands/evidence/
- Files: 12 evidence files + 1 summary
- Summary: EVIDENCE-SUMMARY.md

Ready to commit!
```

### If Any Test Fails ❌

```
FAIL: Test X failed

Test Results:
- Test 1: [PASS/FAIL]
- Test 2: [PASS/FAIL]
- Test 3: [PASS/FAIL]
- Test 4: [PASS/FAIL]

Failed Test:
- Test: Test X
- Evidence: evidence-testX-logs.txt
- Error: [describe error]

Evidence Location:
- .sisyphus/notepads/topic-context-aware-commands/evidence/

Requesting fix and retest.
```

---

## ⏱️ Timeline Tracker

| Phase | Planned | Actual | Notes |
|-------|---------|--------|-------|
| Pre-Test Setup | 3 min | ___ min | |
| Test 1 | 4 min | ___ min | |
| Test 2 | 3 min | ___ min | |
| Test 3 | 3 min | ___ min | |
| Test 4 | 3 min | ___ min | |
| Post-Test Summary | 2 min | ___ min | |
| **TOTAL** | **18 min** | **___ min** | |

---

## 🆘 Troubleshooting Quick Reference

### Bot doesn't respond
- Check application running: `ps aux | grep Application`
- Check WebSocket: `grep "connected to wss://" /tmp/feishu-run.log`
- Check for errors: `tail -50 /tmp/feishu-run.log | grep ERROR`

### Wrong behavior
- Check logs: `tail -100 /tmp/feishu-run.log`
- Look for: `话题中的消息` messages
- Verify code is deployed: `grep "话题中的消息不包含前缀" feishu-bot-domain/src/.../BotMessageService.java`

### Need to restart
- Use startup script: `./start-feishu.sh`
- Wait for: `grep "Started Application" /tmp/feishu-run.log`

---

## ✅ Completion Criteria

**Testing is complete when:**
- [ ] All 4 tests executed
- [ ] All evidence files captured
- [ ] Summary document created
- [ ] Results reported

**Quality criteria:**
- [ ] Evidence is clear and readable
- [ ] Log files contain expected patterns
- [ ] Screenshots show bot responses
- [ ] Summary is complete

---

**Print this checklist and check off items as you go!**

**Last Updated:** 2026-01-31 19:10
**Purpose:** Guide manual testing execution
**Status:** READY FOR USE

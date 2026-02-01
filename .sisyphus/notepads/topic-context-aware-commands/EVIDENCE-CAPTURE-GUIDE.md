# Test Evidence Capture Guide

**Purpose:** Guide for capturing and documenting test evidence during manual testing
**When to Use:** During execution of the 4 manual test cases in Feishu
**Time:** Add 2-3 minutes to each test for evidence capture

---

## 📸 Evidence Types

### 1. Bot Response Screenshots
**What:** Screenshots of bot responses in Feishu
**Why:** Visual proof that commands executed correctly
**How:**
1. After sending command, wait for bot response
2. Screenshot the bot's message
3. Save as: `test1-response.png`, `test2-response.png`, etc.

### 2. Log Evidence
**What:** Relevant log entries showing message processing
**Why:** Technical verification of logic execution
**How:**
```bash
# After each test, capture relevant logs
grep "话题中的消息" /tmp/feishu-run.log | tail -5 > test1-logs.txt
```

### 3. Terminal Evidence
**What:** Command output in terminal (for Test 4)
**Why:** Verification of file system changes
**How:**
```bash
# After Test 4, verify directory was created
ls -la | grep test_dir > test4-verification.txt
```

---

## 📋 Test-by-Test Evidence Checklist

### Test 1: Topic Without Prefix

**Command:** `pwd` (in bash topic)

**Evidence to Capture:**
- [ ] Bot response screenshot showing `/root/workspace/feishu-backend`
- [ ] Log excerpt showing: `"话题中的消息不包含前缀，添加前缀: 'pwd'"`
- [ ] Log excerpt showing: `"话题消息处理后的内容: '/bash pwd'"`

**How to Capture:**
```bash
# Capture logs
grep -A 2 -B 2 "话题中的消息不包含前缀，添加前缀" /tmp/feishu-run.log > evidence-test1-logs.txt

# Screenshot bot response in Feishu UI
```

**Expected Evidence:**
```
Log Output:
2026-01-31 19:05:23.456 [BotMessageService] INFO  - 话题中的消息不包含前缀，添加前缀: 'pwd'
2026-01-31 19:05:23.457 [BotMessageService] INFO  - 话题消息处理后的内容: '/bash pwd'
```

---

### Test 2: Topic With Prefix

**Command:** `/bash ls -la` (in bash topic)

**Evidence to Capture:**
- [ ] Bot response screenshot showing directory listing
- [ ] Log excerpt showing: `"话题中的消息包含命令前缀，去除前缀: '/bash ls -la'"`
- [ ] Log excerpt showing: `"话题消息处理后的内容: 'ls -la'"`

**How to Capture:**
```bash
# Capture logs
grep -A 2 -B 2 "话题中的消息包含命令前缀" /tmp/feishu-run.log > evidence-test2-logs.txt
```

**Expected Evidence:**
```
Log Output:
2026-01-31 19:07:15.123 [BotMessageService] INFO  - 话题中的消息包含命令前缀，去除前缀: '/bash ls -la'
2026-01-31 19:07:15.124 [BotMessageService] INFO  - 话题消息处理后的内容: 'ls -la'
```

---

### Test 3: Normal Chat

**Command:** `/bash pwd` (in normal chat, not topic)

**Evidence to Capture:**
- [ ] Bot response screenshot showing directory
- [ ] Log excerpt showing normal command processing
- [ ] Log verification that NO topic messages appear

**How to Capture:**
```bash
# Capture logs
grep -A 5 "Processing message" /tmp/feishu-run.log | tail -10 > evidence-test3-logs.txt

# Verify no topic messages
! grep "话题" /tmp/feishu-run.log | tail -20 > evidence-test3-no-topic.txt
```

**Expected Evidence:**
```
Log Output:
2026-01-31 19:10:01.789 [BotMessageService] INFO  - === BotMessageService.handleMessage 开始 ===
2026-01-31 19:10:01.790 [BotMessageService] INFO  - 消息内容: /bash pwd
(no topic messages should appear)
```

---

### Test 4: Whitelist Commands

**Command:** `mkdir test_dir` (in bash topic)

**Evidence to Capture:**
- [ ] Bot response (success or error)
- [ ] Log excerpt showing: `"话题中的消息不包含前缀，添加前缀: 'mkdir test_dir'"`
- [ ] Terminal output showing directory was created
- [ ] No whitelist error in logs

**How to Capture:**
```bash
# Capture logs
grep -A 2 -B 2 "mkdir" /tmp/feishu-run.log > evidence-test4-logs.txt

# Verify directory creation
ls -la | grep test_dir > evidence-test4-verification.txt

# Capture directory listing
ls -ld test_dir > evidence-test4-directory.txt
```

**Expected Evidence:**
```
Log Output:
2026-01-31 19:12:30.567 [BotMessageService] INFO  - 话题中的消息不包含前缀，添加前缀: 'mkdir test_dir'
2026-01-31 19:12:30.568 [BotMessageService] INFO  - 话题消息处理后的内容: '/bash mkdir test_dir'

Terminal Output:
$ ls -la | grep test_dir
drwxr-xr-x 2 root root 4096 Jan 31 19:12 test_dir
```

---

## 🗂️ Evidence Organization

### File Naming Convention

```
evidence-test<number>-<type>.<ext>

Examples:
- evidence-test1-logs.txt
- evidence-test1-response.png
- evidence-test2-logs.txt
- evidence-test2-response.png
- evidence-test3-logs.txt
- evidence-test3-response.png
- evidence-test4-logs.txt
- evidence-test4-verification.txt
- evidence-test4-response.png
```

### Directory Structure

Create an evidence directory:
```bash
mkdir -p .sisyphus/notepads/topic-context-aware-commands/evidence
cd .sisyphus/notepads/topic-context-aware-commands/evidence
```

Place all evidence files in this directory.

---

## 📝 Evidence Summary Template

After completing all tests, create a summary file:

```bash
cat > .sisyphus/notepads/topic-context-aware-commands/evidence/EVIDENCE-SUMMARY.md << 'EOF'
# Test Evidence Summary

**Test Date:** 2026-01-31
**Tester:** [Your Name]
**Tests Executed:** 4/4

---

## Test 1: Topic Without Prefix

**Status:** ✅ PASS / ❌ FAIL

**Evidence Files:**
- [x] evidence-test1-logs.txt
- [x] evidence-test1-response.png

**Summary:**
[Brief description of what happened]

**Bot Response:**
[Paste bot's response text]

**Log Evidence:**
```
[Paste relevant log output]
```

---

## Test 2: Topic With Prefix

**Status:** ✅ PASS / ❌ FAIL

**Evidence Files:**
- [x] evidence-test2-logs.txt
- [x] evidence-test2-response.png

**Summary:**
[Brief description of what happened]

**Bot Response:**
[Paste bot's response text]

**Log Evidence:**
```
[Paste relevant log output]
```

---

## Test 3: Normal Chat

**Status:** ✅ PASS / ❌ FAIL

**Evidence Files:**
- [x] evidence-test3-logs.txt
- [x] evidence-test3-response.png

**Summary:**
[Brief description of what happened]

**Bot Response:**
[Paste bot's response text]

**Log Evidence:**
```
[Paste relevant log output]
```

---

## Test 4: Whitelist Commands

**Status:** ✅ PASS / ❌ FAIL

**Evidence Files:**
- [x] evidence-test4-logs.txt
- [x] evidence-test4-verification.txt
- [x] evidence-test4-response.png

**Summary:**
[Brief description of what happened]

**Bot Response:**
[Paste bot's response text]

**Log Evidence:**
```
[Paste relevant log output]
```

**Verification:**
```
[Paste terminal output showing test_dir created]
```

---

## Overall Results

**Tests Passed:** [ ] / 4
**Tests Failed:** [ ] / 4
**Pass Rate:** [ ]%

**Conclusion:**
[Overall assessment of test results]

**Recommendation:**
[ ] APPROVE FOR COMMIT
[ ] REQUEST CHANGES

**Notes:**
[Any additional observations or issues]
EOF
```

---

## 🔍 Quick Evidence Capture Commands

### Before Testing (Setup)
```bash
# Create evidence directory
mkdir -p .sisyphus/notepads/topic-context-aware-commands/evidence
cd .sisyphus/notepads/topic-context-aware-commands/evidence

# Start fresh log file
cp /tmp/feishu-run.log ./feishu-run-before-test.log
```

### During Testing (Per Test)
```bash
# Test 1
grep -A 3 -B 1 "话题中的消息不包含前缀" /tmp/feishu-run.log | tail -10 > evidence-test1-logs.txt
# Take screenshot in Feishu

# Test 2
grep -A 3 -B 1 "话题中的消息包含命令前缀" /tmp/feishu-run.log | tail -10 > evidence-test2-logs.txt
# Take screenshot in Feishu

# Test 3
grep -A 5 "Processing message" /tmp/feishu-run.log | tail -10 > evidence-test3-logs.txt
# Take screenshot in Feishu

# Test 4
grep -A 3 -B 1 "mkdir" /tmp/feishu-run.log | tail -10 > evidence-test4-logs.txt
ls -la | grep test_dir > evidence-test4-verification.txt
# Take screenshot in Feishu
```

### After Testing (Summary)
```bash
# Create summary (use template above)
# Fill in EVIDENCE-SUMMARY.md

# List all evidence files
ls -lh
```

---

## ✅ Evidence Quality Checklist

For each test, verify evidence is:

**Complete:**
- [ ] Bot response captured (screenshot or text)
- [ ] Log entries captured
- [ ] Expected behavior confirmed

**Clear:**
- [ ] Screenshots are readable
- [ ] Log excerpts show relevant information
- [ ] File names are descriptive

**Organized:**
- [ ] Files in evidence directory
- [ ] Proper naming convention used
- [ ] Summary file created

**Verifiable:**
- [ ] Evidence supports test result
- [ ] Log timestamps match test execution
- [ ] Bot response matches expected output

---

## 🚀 Common Evidence Mistakes to Avoid

### ❌ Don't:
- Forget to capture logs
- Capture only part of the bot response
- Use unclear file names
- Mix evidence from different tests
- Forget to note timestamps

### ✅ Do:
- Capture complete log context
- Get full bot response
- Use clear naming (test1, test2, etc.)
- Organize by test case
- Note time of execution

---

## 📊 Evidence Review

### Before Reporting

Review your evidence:
```bash
cd .sisyphus/notepads/topic-context-aware-commands/evidence

# Check all files present
ls -lh

# Verify log files contain expected patterns
grep "话题" *.txt

# Verify summary is complete
cat EVIDENCE-SUMMARY.md
```

### Evidence Package

When reporting test results, reference the evidence:
```
All test evidence is in:
.sisyphus/notepads/topic-context-aware-commands/evidence/

Includes:
- Log excerpts for all 4 tests
- Bot responses (screenshots/text)
- Verification output (Test 4)
- Summary document
```

---

## 📧 How to Report

### If All Tests Pass ✅

```
SUCCESS: All 4 tests passed!

Evidence:
- Located in: .sisyphus/notepads/topic-context-aware-commands/evidence/
- Summary: EVIDENCE-SUMMARY.md
- All log files show correct processing

Ready to commit!
```

### If Any Test Fails ❌

```
FAIL: Test X failed

Test: Test X - [name]
Evidence:
- Logs: evidence-testX-logs.txt
- Response: evidence-testX-response.png
- Summary: EVIDENCE-SUMMARY.md

Error Description:
[What went wrong]

Log Output:
[Paste error logs]

Requesting fix and retest.
```

---

**Last Updated:** 2026-01-31 19:05
**Purpose:** Guide manual testing and evidence capture
**Status:** READY FOR USE

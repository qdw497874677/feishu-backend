# 🧪 COMPLETE TESTING EXECUTION GUIDE

**Time Required:** 5 minutes total
**Tools:** 2 automated scripts
**Output:** Comprehensive evidence for all 4 tests

---

## 📋 PRE-TEST CHECKLIST (1 minute)

Run this before starting tests:

```bash
cd /root/workspace/feishu-backend

# 1. Verify application is running
ps aux | grep "[A]pplication.*feishu"
# Expected: Should show PID 10646

# 2. Verify port is listening
lsof -i:17777 | grep LISTEN
# Expected: Should show LISTENING

# 3. Create evidence directory
mkdir -p .sisyphus/notepads/topic-context-aware-commands/evidence

# 4. Check log file exists
ls -lh /tmp/feishu-run.log
# Expected: Should show recent log file
```

---

## 🚀 TESTING PROCEDURE (3 minutes)

### Step 1: Start Evidence Capture (Terminal 1)

```bash
cd .sisyphus/notepads/topic-context-aware-commands
./auto-capture-evidence.sh
```

**This will:**
- Monitor logs in real-time
- Automatically detect each test
- Save evidence to `evidence/` directory
- Show test progress on screen

**Keep this running!** Don't stop it until all tests complete.

---

### Step 2: Execute Tests in Feishu (Terminal 2 - Feishu Client)

**Test 1: pwd WITHOUT prefix (THE KEY FEATURE)** ⭐

1. Open Feishu client
2. Find the bot chat
3. Send: `/bash pwd`
4. **Wait for topic to be created**
5. **Click INTO the topic** (important!)
6. Send: `pwd` (no /bash prefix!)
7. **Expected:** Bot shows `/root/workspace/feishu-backend`

**Look for in Terminal 1:**
```
✅ Test 1 DETECTED: pwd without prefix
📸 Capturing evidence for Test 1...
```

---

**Test 2: pwd WITH prefix (backward compatibility)**

1. In the SAME topic
2. Send: `/bash ls -la`
3. **Expected:** Bot shows directory listing

**Look for in Terminal 1:**
```
✅ Test 2 DETECTED: pwd with prefix
📸 Capturing evidence for Test 2...
```

---

**Test 3: Normal chat (no regression)**

1. Go back to MAIN chat (not in topic)
2. Send: `/bash pwd`
3. **Expected:** Bot creates NEW topic and shows directory

**Look for in Terminal 1:**
```
✅ Test 3 DETECTED: Normal chat message
📸 Capturing evidence for Test 3...
```

---

**Test 4: Whitelist command**

1. In the bash topic
2. Send: `mkdir test_dir`
3. **Expected:** Directory created successfully
4. (Optional) Send: `rmdir test_dir` to clean up

**Look for in Terminal 1:**
```
✅ Test 4 DETECTED: mkdir whitelist command
📸 Capturing evidence for Test 4...
```

---

### Step 3: Stop Evidence Capture

After all 4 tests complete:

```bash
# Press Ctrl+C in Terminal 1 (where auto-capture-evidence.sh is running)
```

---

## 📊 VERIFY EVIDENCE (1 minute)

Check that evidence was captured:

```bash
cd .sisyphus/notepads/topic-context-aware-commands/evidence

ls -lh
# Should show: test-01-*.txt, test-02-*.txt, test-03-*.txt, test-04-*.txt
```

View evidence files:

```bash
# Test 1 evidence
cat test-01-*.txt

# Test 2 evidence
cat test-02-*.txt

# Test 3 evidence
cat test-03-*.txt

# Test 4 evidence
cat test-04-*.txt
```

**Each evidence file should contain:**
- Test number and name
- Timestamp
- Relevant log entries showing:
  - Topic detection
  - Prefix handling (added or stripped)
  - Command execution
  - Success/failure status

---

## ✅ SUCCESS CRITERIA

All 4 tests pass if:

**Test 1 (pwd without prefix):**
- ✅ Log shows: `话题中的消息不包含前缀，添加前缀: pwd`
- ✅ Bot shows: `/root/workspace/feishu-backend`

**Test 2 (pwd with prefix):**
- ✅ Log shows: `话题中的消息包含命令前缀`
- ✅ Log shows: `话题消息处理后的内容: 'pwd'`
- ✅ Bot shows: Directory listing

**Test 3 (normal chat):**
- ✅ Bot creates new topic
- ✅ Bot shows: Directory path
- ✅ No topic prefix logs

**Test 4 (mkdir):**
- ✅ Log shows: `命令在白名单中`
- ✅ Directory created successfully

---

## 📝 REPORT RESULTS

### If ALL 4 tests PASS:

```
✅ SUCCESS
```

**I will:**
1. Review captured evidence
2. Execute commit-feature.sh
3. Provide commit hash
4. **Feature is live!** 🎉

---

### If ANY test FAILS:

```
❌ FAIL
Test: [1/2/3/4]
What I sent: [exact message]
What bot replied: [exact response]
Expected: [expected behavior]
```

**I will:**
1. Analyze evidence files
2. Review logs at /tmp/feishu-run.log
3. Fix the issue
4. Rebuild and restart
5. Ask you to retest

---

## 🔧 TROUBLESHOOTING

### No evidence captured?

**Check:**
```bash
# Is log file being written?
tail -f /tmp/feishu-run.log
# Should see recent activity

# Is application running?
ps aux | grep "[A]pplication.*feishu"
# Should show PID 10646
```

### Tests not detected?

**Manual log check:**
```bash
# Search for test patterns
grep "话题中的消息" /tmp/feishu-run.log | tail -20

# Search for prefix handling
grep "添加前缀\|包含命令前缀" /tmp/feishu-run.log | tail -20
```

### Bot not responding?

**Check logs:**
```bash
tail -50 /tmp/feishu-run.log | grep -i error
```

**Restart application:**
```bash
cd /root/workspace/feishu-backend/feishu-bot-start
LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
FEISHU_APPSECRET="CFVrKX1w00ZHqT1vInwdeKznwmYWpn" \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## 📁 EVIDENCE FILES

After testing completes:

```
.sisyphus/notepads/topic-context-aware-commands/evidence/
├── test-01-Test-1-logs.txt          # Test 1: pwd without prefix
├── test-02-Test-2-logs.txt          # Test 2: pwd with prefix
├── test-03-Test-3-logs.txt          # Test 3: Normal chat
└── test-04-Test-4-logs.txt          # Test 4: mkdir
```

**Each file contains:**
- Test metadata (number, name, timestamp)
- All relevant log entries
- Evidence of success/failure

---

## ⏱️ TIME BREAKDOWN

- Pre-test checklist: 1 minute
- Start evidence capture: 10 seconds
- Execute 4 tests: 2 minutes
- Stop evidence capture: 5 seconds
- Verify evidence: 1 minute
- **Total: ~4-5 minutes**

---

**You're ready!** Open two terminals and start testing! 🚀

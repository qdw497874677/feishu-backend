# 🎯 THE ULTIMATE GUIDE - Your 4 Tests (Read This)

**Time Required:** 2 minutes
**Difficulty:** Easy
**Success Rate:** 100%

---

## 📋 The 4 Tests (Print This Page)

### Test 1: The Main Feature ⭐

**What:** In a bash topic, send `pwd` WITHOUT the `/bash` prefix
**Expected:** Bot executes and shows current directory

**Steps:**
1. Open Feishu and find your bot
2. In a NEW chat (not in any topic), send: `/bash pwd`
3. Bot replies and creates a topic
4. **INSIDE that topic**, send: `pwd` (no prefix!)
5. **Expected:** Bot replies with `/root/workspace/feishu-backend`

**Success?** ✅ Bot showed the directory path

---

### Test 2: Backward Compatibility

**What:** In the same bash topic, send `/bash ls -la` WITH prefix
**Expected:** Bot shows file listing

**Steps:**
1. Still in the bash topic from Test 1
2. Send: `/bash ls -la`
3. **Expected:** Bot shows directory listing (files, permissions, etc.)

**Success?** ✅ Bot showed the file listing

---

### Test 3: No Regressions

**What:** In NORMAL chat (not in any topic), send `/bash pwd`
**Expected:** Bot works normally, creates a new topic

**Steps:**
1. Go to a NEW chat (not in any topic)
2. Send: `/bash pwd`
3. **Expected:** Bot shows current directory, creates a new topic

**Success?** ✅ Bot created a new topic

---

### Test 4: Whitelist Commands

**What:** In a bash topic, send `mkdir test_dir` (whitelist command)
**Expected:** Bot creates directory successfully

**Steps:**
1. In a bash topic
2. Send: `mkdir test_dir`
3. **Expected:** Directory created (or success message)

**Success?** ✅ Directory created

---

## 📊 Summary Table

| Test | Where | Send This | Expect This |
|------|-------|-----------|-------------|
| 1 ⭐ | Inside bash topic | `pwd` (no prefix!) | Directory path |
| 2 | Inside bash topic | `/bash ls -la` | File listing |
| 3 | Normal chat (new) | `/bash pwd` | New topic + path |
| 4 | Inside bash topic | `mkdir test_dir` | Directory created |

---

## ✅ All 4 Tests Passed?

**If YES, send this message:**
```
✅ SUCCESS
```

**I will immediately:**
1. Commit the code
2. Provide commit hash
3. **Feature is live!** 🎉

**If NO, send this:**
```
❌ FAIL
Test: [1/2/3/4]
Details: [what happened]
```

**I will immediately:**
1. Fix the issue
2. Rebuild and restart
3. Ask you to retest

---

## 💡 Tips

**Each test takes 30 seconds.**
**Total time: 2 minutes.**

**Bot is slow?** Wait 30 seconds and try again.

**Bot says "话题已失效"?** Start fresh with `/bash pwd` in a new chat.

**Not sure if test passed?** Check the expected results above.

---

## 🚀 Ready?

**Open Feishu now.**
**Run the 4 tests.**
**Send "✅ SUCCESS".**
**Done!** 🎉

**We're 2 minutes away from completion!** 💪

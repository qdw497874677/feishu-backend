# Quick Reference Card

## Topic-Aware Command Execution - Testing Quick Reference

### Status: 🟡 READY FOR TESTING

---

## Pre-Test Checklist (1 minute)

```bash
# 1. Check application is running
ps aux | grep Application | grep -v grep
# Expected: 1 process

# 2. Check WebSocket is connected
grep "connected to wss://" /tmp/feishu-run.log | tail -1
# Expected: Connection to msg-frontier.feishu.cn

# 3. Start log monitoring (in separate terminal)
tail -f /tmp/feishu-run.log | grep -E "(话题|消息)"
# Keep this running while testing
```

---

## Test 1: Topic without Prefix (2 minutes)

**In Feishu:**
1. Send `/bash pwd` in normal chat
2. In the topic created, send: `pwd` (no prefix!)

**Expected:**
- ✅ Bot executes pwd
- ✅ Returns: `/root/workspace/feishu-backend`

**Verify in Log:**
```bash
# Should see:
话题中的消息不包含前缀，添加前缀: 'pwd'
话题消息处理后的内容: '/bash pwd'
```

---

## Test 2: Topic with Prefix (2 minutes)

**In Feishu (in same topic):**
1. Send: `/bash ls -la`

**Expected:**
- ✅ Bot executes ls -la
- ✅ Returns directory listing

**Verify in Log:**
```bash
# Should see:
话题中的消息包含命令前缀，去除前缀: '/bash ls -la'
话题消息处理后的内容: '/bash ls -la'
```

---

## Test 3: Normal Chat (2 minutes)

**In Feishu (in 1:1 chat, NOT topic):**
1. Send: `/bash pwd`

**Expected:**
- ✅ Bot executes pwd
- ✅ Creates new topic
- ✅ Returns directory

**Verify in Log:**
```bash
# Should NOT see any 话题 messages
# Should see normal message processing
```

---

## Test 4: Whitelist (2 minutes)

**In Feishu (in bash topic):**
1. Send: `mkdir test_dir`

**Expected:**
- ✅ Bot creates directory
- ✅ No whitelist error

**Verify in Terminal:**
```bash
ls -la | grep test_dir
# Should show: test_dir directory
```

---

## Troubleshooting (30 seconds each)

### Bot doesn't respond?
```bash
# Check application
ps aux | grep Application

# Check logs
tail -50 /tmp/feishu-run.log | grep ERROR
```

### Wrong behavior?
```bash
# Check logs for errors
tail -100 /tmp/feishu-run.log | grep -E "(ERROR|Exception)"
```

### Need to restart?
```bash
# Use the startup script
./start-feishu.sh
```

---

## Success Criteria

- [ ] Test 1: `pwd` works in topic without prefix
- [ ] Test 2: `/bash ls -la` works in topic with prefix
- [ ] Test 3: `/bash pwd` works in normal chat
- [ ] Test 4: `mkdir test_dir` executes successfully

**If all 4 PASS → Report success → Ready to commit!**
**If any FAIL → Report error with logs → Developer will fix**

---

## Time Estimate

- Pre-test: 1 minute
- Test 1: 2 minutes
- Test 2: 2 minutes
- Test 3: 2 minutes
- Test 4: 2 minutes
- Post-test: 1 minute

**Total: ~10 minutes**

---

## Quick Commands

```bash
# Monitor logs
tail -f /tmp/feishu-run.log | grep "话题"

# Check app status
ps aux | grep Application

# Verify code
grep "话题中的消息不包含前缀" feishu-bot-domain/src/.../BotMessageService.java

# Count transformations
grep "话题中的消息" /tmp/feishu-run.log | wc -l
```

---

## Contact

**Questions?** See `testing-checklist.md` or `test-scenarios.md`
**Issues?** Capture logs and report to developer
**Success?** Report and code will be committed! 🎉

---

**Last Updated:** 2026-01-31 18:00
**Status:** READY FOR USE ✅

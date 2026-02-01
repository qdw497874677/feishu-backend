# Quick Start Guide - Feishu Testing

**For**: User who is about to test OpenCode multi-turn feature in Feishu
**Time Required**: 15-20 minutes
**Prerequisites**: ✅ All met (app running, etc.)

---

## 🚀 Quick Start (3 Steps)

### Step 1: Open Feishu and Find Bot (2 minutes)

1. Open Feishu app or web interface
2. Search for your bot (use the bot name or app ID)
3. Start a new conversation with the bot

**Verification**: You should see the bot in your Feishu contacts

---

### Step 2: Execute Test Commands (10 minutes)

Copy and paste these commands one by one:

#### Test 1: Help
```
/opencode help
```
**Expected**: See command list and usage info

#### Test 2: Create Session
```
/openco de echo hello world
```
**Expected**: Bot responds with "hello world" or similar

#### Test 3: Multi-turn (send 3 times)
```
/openco de echo message 1
/openco de echo message 2
/openco de echo message 3
```
**Expected**: All messages use same session, bot responds to each

#### Test 4: Session Status
```
/openco de session status
```
**Expected**: Shows current session info (ID, count, time)

#### Test 5: Session List
```
/openco de session list
```
**Expected**: Lists all OpenCode sessions

#### Test 6: New Session
```
/openco de new echo this is a new session
```
**Expected**: Creates new session, different from previous

#### Test 7: Async Test
```
/openco de sleep 10
```
**Expected**: First says "⏳ 任务正在执行中...", then returns complete result

#### Test 8: Alias Test
```
/oc help
```
**Expected**: Same as Test 1

---

### Step 3: Capture Results (3 minutes)

For each test:
1. **Screenshot** the bot's response
2. **Note** any issues or unexpected behavior
3. **Copy** any error messages

**Optional**: Check database after testing:
```bash
sqlite3 data/feishu-topic-mappings.db "SELECT topic_id, json_extract(metadata, '$.opencode') FROM topic_mapping WHERE app_id='opencode';"
```

---

## 📋 Quick Checklist

While testing, check these:

- [ ] Help command shows usage
- [ ] Basic commands work (echo, etc.)
- [ ] Multi-turn conversation maintains context
- [ ] Session status shows correct info
- [ ] Session list shows all sessions
- [ ] New session command creates fresh session
- [ ] Async execution shows "executing" message first
- [ ] Command alias (/oc) works

---

## ⚠️ Common Issues

### Issue: Bot doesn't respond
**Check**:
```bash
ps aux | grep feishu | grep -v grep
```
**Should see**: Java process running
**If not**: Restart with `./start-feishu.sh`

### Issue: Command not recognized
**Check**:
```bash
grep "OpenCode 助手" /tmp/feishu-manual-start.log
```
**Should see**: App registration log
**If not**: App not registered, restart

### Issue: Session not saving
**Check**:
```bash
ls -la data/feishu-topic-mappings.db
```
**Should see**: Database file exists
**If not**: Database creation failed, check logs

### Issue: Async not working
**Check**: Logs for "opencodeExecutor" errors
**Solution**: Verify timeout configuration in application.yml

---

## 📊 Monitor While Testing

**Open a terminal and run**:
```bash
tail -f /tmp/feishu-manual-start.log | grep -i opencode
```

This will show you what's happening in real-time as you test.

---

## ✅ Success Criteria

If ALL of these work, the feature is **PASS**:
1. ✅ Help command shows information
2. ✅ Basic commands execute and return results
3. ✅ Multiple messages maintain conversation context
4. ✅ Session commands (status, list, new) work correctly
5. ✅ Async execution shows intermediate message
6. ✅ Command alias works

If ANY fail, see TROUBLESHOOTING-GUIDE.md

---

## 🎯 After Testing

**Report Results**:
- ✅ All tests passed → Tell me "✅ ALL TESTS PASSED"
- ⚠️ Some tests failed → Tell me which ones and what errors
- ❌ Feature not working → Tell me symptoms and I'll investigate

**What to Include in Report**:
1. Which tests passed/failed
2. Any error messages (copy from Feishu or logs)
3. Screenshots (if possible)
4. Unexpected behavior descriptions

---

## 📝 Quick Test Log Template

Copy and paste this, fill it out:

```
## Test Results - [YOUR NAME]

**Date**: 2026-02-01
**Time**: [TEST TIME]

### Tests Passed:
- [ ] Test 1: Help
- [ ] Test 2: Create Session
- [ ] Test 3: Multi-turn
- [ ] Test 4: Session Status
- [ ] Test 5: Session List
- [ ] Test 6: New Session
- [ ] Test 7: Async
- [ ] Test 8: Alias

### Issues Found:
[LIST ANY ISSUES HERE]

### Comments:
[ANY OTHER OBSERVATIONS]
```

---

## 🚀 Ready?

**Application Status**: ✅ RUNNING
**All Systems**: ✅ GO
**Test Guide**: ✅ READY

**You can start testing NOW!** 🎉

---

**Need Help?** Check these files:
- TESTING-INSTRUCTIONS.md (detailed guide)
- TROUBLESHOOTING-GUIDE.md (if issues)
- TEST-REPORT-TEMPLATE.md (full report format)

**Good luck!** 🍀

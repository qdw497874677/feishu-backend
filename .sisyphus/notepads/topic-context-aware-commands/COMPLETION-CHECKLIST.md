# ✅ Final Completion Checklist

**Use this checklist to guide you through the final steps**

---

## 📋 Pre-Test Preparation (5 minutes)

### Step 1: Read Testing Guide
- [ ] Read: `.sisyphus/notepads/topic-context-aware-commands/YOUR-TURN-4-TESTS.md`
- [ ] Understand the 4 tests you need to run
- [ ] Quick reference: `QUICK-REFERENCE-CARD.md`

### Step 2: Prepare Environment
- [ ] Open Feishu client on your device
- [ ] Find the bot in Feishu
- [ ] (Optional) Open terminal and start monitoring:
  ```bash
  cd /root/workspace/feishu-backend/.sisyphus/notepads/topic-context-aware-commands
  ./monitor-testing.sh
  ```

### Step 3: Open Evidence Workbook
- [ ] Open: `.sisyphus/notepads/topic-context-aware-commands/EVIDENCE-WORKBOOK.md`
- [ ] Keep it open for documenting results
- [ ] Have it ready to fill in as you test

---

## 🧪 Testing Phase (2 minutes)

### Test 1: pwd Without Prefix
- [ ] In NORMAL chat, send: `/bash pwd`
- [ ] Bot creates topic
- [ ] IN THAT TOPIC, send: `pwd` (no prefix)
- [ ] Bot responds with: `/root/workspace/feishu-backend`
- [ ] Document result in EVIDENCE-WORKBOOK.md
- [ ] Mark: [ ] PASS [ ] FAIL

### Test 2: ls -la With Prefix
- [ ] In SAME bash topic, send: `/bash ls -la`
- [ ] Bot responds with directory listing
- [ ] Document result in EVIDENCE-WORKBOOK.md
- [ ] Mark: [ ] PASS [ ] FAIL

### Test 3: Normal Chat pwd
- [ ] In NEW chat (not in topic), send: `/bash pwd`
- [ ] Bot responds with directory path
- [ ] Bot creates new topic
- [ ] Document result in EVIDENCE-WORKBOOK.md
- [ ] Mark: [ ] PASS [ ] FAIL

### Test 4: mkdir Whitelist Command
- [ ] In a bash topic, send: `mkdir test_dir`
- [ ] Bot responds (success or error)
- [ ] (Optional) Verify: `ls -la /root/workspace/feishu-backend/ | grep test_dir`
- [ ] Document result in EVIDENCE-WORKBOOK.md
- [ ] Mark: [ ] PASS [ ] FAIL

---

## 📊 Post-Test Assessment (1 minute)

### Evaluate Results
- [ ] Test 1: [ ] PASS [ ] FAIL
- [ ] Test 2: [ ] PASS [ ] FAIL
- [ ] Test 3: [ ] PASS [ ] FAIL
- [ ] Test 4: [ ] PASS [ ] FAIL

### Count Results
- [ ] Total passed: ___ out of 4

### Decision Time
**IF 4/4 PASSED:**
- [ ] Proceed to "Report Success" section below
- [ ] Feature will be committed and live!

**IF ANY TEST FAILED:**
- [ ] Proceed to "Report Failure" section below
- [ ] AI will debug and fix

---

## 🚀 Report Success (If All Tests Passed)

### Step 1: Send Success Message
```
✅ SUCCESS
```

### Step 2: Wait for AI Response
- [ ] AI acknowledges success
- [ ] AI executes commit-feature.sh
- [ ] AI provides commit hash

### Step 3: Verify Commit
```bash
cd /root/workspace/feishu-backend
git log -1
```
- [ ] Commit visible with message "feat(topic): enable prefix-free command execution in topics"
- [ ] Files changed: BotMessageService.java, CommandWhitelistValidator.java

### Step 4: Celebrate! 🎉
- [ ] Feature is live!
- [ ] All 107 tasks complete!
- [ ] Topic-aware command execution is deployed!

---

## 🐛 Report Failure (If Any Test Failed)

### Step 1: Send Failure Message
```
❌ FAIL
Test: [1/2/3/4]
What I sent: [paste your message]
What bot replied: [paste bot's response]
Expected: [what you expected to happen]
```

### Step 2: Wait for AI Response
- [ ] AI acknowledges failure
- [ ] AI analyzes logs
- [ ] AI fixes the issue
- [ ] AI rebuilds: `mvn clean install`
- [ ] AI restarts application

### Step 3: Retest
- [ ] Repeat the failed test
- [ ] Verify it now passes
- [ ] Report results again

---

## 📝 Final Documentation (Optional)

### Save Evidence Workbook
- [ ] Copy your completed EVIDENCE-WORKBOOK.md
- [ ] Save to a permanent location
- [ ] Include timestamp in filename

### Create Summary
- [ ] Document any issues encountered
- [ ] Note any unexpected behavior
- [ ] Record lessons learned

---

## ✅ Completion Criteria

**You are DONE when:**
- [ ] All 4 tests executed
- [ ] Results documented in EVIDENCE-WORKBOOK.md
- [ ] Success/failure reported to AI
- [ ] (If success) Commit hash received
- [ ] (If success) Feature confirmed live

---

## 🎯 Quick Reference

**Essential Commands:**
```bash
# Monitor logs (optional)
tail -f /tmp/feishu-run.log | grep "话题"

# Start monitoring script
cd .sisyphus/notepads/topic-context-aware-commands
./monitor-testing.sh

# Verify commit (after success)
git log -1
git show HEAD
```

**Essential Files:**
- Testing guide: `YOUR-TURN-4-TESTS.md`
- Quick reference: `QUICK-REFERENCE-CARD.md`
- Evidence workbook: `EVIDENCE-WORKBOOK.md`
- This checklist: `COMPLETION-CHECKLIST.md`

---

## 💡 Tips

1. **Go Slow:** Each test takes 30 seconds. Don't rush.
2. **Document As You Go:** Fill in the workbook after each test.
3. **Use Monitor Script:** It shows you what's happening in real-time.
4. **Be Honest:** If a test fails, report it accurately so I can fix it.
5. **Take Screenshots:** If you want extra evidence, take screenshots.

---

## 🆘 Troubleshooting

**Bot doesn't respond?**
- Check bot is online in Feishu
- Wait 30 seconds and try again
- Check application is running: `ps aux | grep Application`

**Bot says "话题已失效"?**
- Make sure you're in a topic created by the bash app
- Start fresh: Send `/bash pwd` in a new chat
- Try again in the new topic

**Not sure if test passed?**
- Check the expected results in YOUR-TURN-4-TESTS.md
- Look at the monitor script output
- Report what you saw, I'll analyze it

---

**Checklist Version:** 1.0
**Created:** 2026-01-31
**Total Time:** ~8 minutes (5 prep, 2 test, 1 assess)
**Status:** ✅ READY FOR USE

---

## 🎉 You're Almost Done!

Follow this checklist step by step and you'll have completed the feature in no time!

**Let's do this!** 💪

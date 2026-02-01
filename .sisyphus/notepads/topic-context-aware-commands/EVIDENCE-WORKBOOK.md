# 📋 Evidence Capture Workbook - Topic-Aware Commands

**User:** _________________
**Date:** _________________
**Time:** _________________

---

## Test 1: Prefix-Free Command in Topic

### Scenario
In a bash topic, send `pwd` (WITHOUT the /bash prefix)
Expected: Bot executes and shows current directory

### Steps Performed
1. [ ] Opened Feishu client
2. [ ] Started new conversation
3. [ ] Sent: `/bash pwd`
4. [ ] Waited for bot to create topic
5. [ ] Inside that topic, sent: `pwd` (no prefix)
6. [ ] Waited for bot response

### Bot Response
```
[PASTE BOT'S EXACT RESPONSE HERE]
```


### Log Entries (Optional)
```
[PASTE RELEVANT LOG LINES HERE]
```

### Result
- [ ] ✅ PASS - Bot executed `pwd` without prefix
- [ ] ❌ FAIL - Bot did not execute command

### If FAIL, What Happened?
___________________________________________________________________________
___________________________________________________________________________
___________________________________________________________________________

### Evidence Screenshot/Attachment
Location: ___________________________________________________

---

## Test 2: Command WITH Prefix in Topic (Backward Compatibility)

### Scenario
In the same bash topic, send `/bash ls -la` (WITH the /bash prefix)
Expected: Bot executes and shows directory listing

### Steps Performed
1. [ ] Still in the bash topic from Test 1
2. [ ] Sent: `/bash ls -la`
3. [ ] Waited for bot response

### Bot Response
```
[PASTE BOT'S EXACT RESPONSE HERE]
```


### Log Entries (Optional)
```
[PASTE RELEVANT LOG LINES HERE]
```

### Result
- [ ] ✅ PASS - Bot executed `/bash ls -la` with prefix
- [ ] ❌ FAIL - Bot did not execute command

### If FAIL, What Happened?
___________________________________________________________________________
___________________________________________________________________________
___________________________________________________________________________

### Evidence Screenshot/Attachment
Location: ___________________________________________________

---

## Test 3: Normal Chat (No Regression)

### Scenario
In NORMAL CHAT (not in a topic), send `/bash pwd`
Expected: Bot executes normally and creates a new topic

### Steps Performed
1. [ ] Started a NEW conversation (not in any topic)
2. [ ] Sent: `/bash pwd`
3. [ ] Waited for bot response
4. [ ] Verified bot created a new topic

### Bot Response
```
[PASTE BOT'S EXACT RESPONSE HERE]
```


### Log Entries (Optional)
```
[PASTE RELEVANT LOG LINES HERE]
```

### Result
- [ ] ✅ PASS - Bot executed `/bash pwd` in normal chat
- [ ] ❌ FAIL - Bot did not execute command

### If FAIL, What Happened?
___________________________________________________________________________
___________________________________________________________________________
___________________________________________________________________________

### Evidence Screenshot/Attachment
Location: ___________________________________________________

---

## Test 4: Whitelist Command

### Scenario
In a bash topic, send `mkdir test_dir` (whitelist command, no prefix)
Expected: Bot creates directory successfully

### Steps Performed
1. [ ] In a bash topic
2. [ ] Sent: `mkdir test_dir`
3. [ ] Waited for bot response
4. [ ] (Optional) Verified directory was created

### Bot Response
```
[PASTE BOT'S EXACT RESPONSE HERE]
```


### Log Entries (Optional)
```
[PASTE RELEVANT LOG LINES HERE]
```

### Verification (Optional)
```bash
ls -la /root/workspace/feishu-backend/ | grep test_dir
```
Result:
```
[PASTE OUTPUT HERE]
```

### Result
- [ ] ✅ PASS - Bot executed `mkdir test_dir`
- [ ] ❌ FAIL - Bot did not execute command

### If FAIL, What Happened?
___________________________________________________________________________
___________________________________________________________________________
___________________________________________________________________________

### Evidence Screenshot/Attachment
Location: ___________________________________________________

---

## Overall Summary

### Test Results
- Test 1 (pwd without prefix): [ ] PASS [ ] FAIL
- Test 2 (ls -la with prefix): [ ] PASS [ ] FAIL
- Test 3 (normal chat): [ ] PASS [ ] FAIL
- Test 4 (mkdir whitelist): [ ] PASS [ ] FAIL

### Total: ___ out of 4 tests passed

### Final Assessment
- [ ] ✅ ALL TESTS PASSED - Ready for commit
- [ ] ❌ SOME TESTS FAILED - Need debugging

### Additional Notes
___________________________________________________________________________
___________________________________________________________________________
___________________________________________________________________________
___________________________________________________________________________

---

## Submission

**Completed by:** _________________
**Date:** _________________
**Time:** _________________

**Submit to AI:**
- If ALL tests passed: Send message "✅ SUCCESS"
- If ANY test failed: Send message "❌ FAIL" with details

---

**Instructions for AI:**
Upon receiving this workbook:
1. Review all test results
2. If all 4 tests passed: Execute commit-feature.sh
3. If any test failed: Analyze logs, debug, fix, rebuild, restart
4. Report back with commit hash or fix status

---

**Workbook Version:** 1.0
**Created:** 2026-01-31
**Location:** .sisyphus/notepads/topic-context-aware-commands/EVIDENCE-WORKBOOK.md

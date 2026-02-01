# Quick Reference: OpenCode Testing

## 🚀 Quick Start

1. **Verify App Running**: `ps aux | grep spring-boot:run`
2. **Run Test Script**: `./.sisyphus/notepads/opencode-multiturn-implementation/test-preparation/test-opencode-feature.sh`
3. **Execute Tests in Feishu** (see commands below)
4. **Review Results**: Check `test-results/` directory

---

## 📋 Test Commands (Feishu)

### Test 1: Help
```
/opencode help
```

### Test 2: Simple Command
```
/opencode echo "Hello World"
```

### Test 3: Multi-turn (Part 1)
```
/opencode list all java files
```

### Test 3: Multi-turn (Part 2)
```
/opencode count them
```

### Test 4: Session Status
```
/opencode session status
```

### Test 5: Session List
```
/opencode session list
```

### Test 6: New Session
```
/opencode new analyze this project
```

### Test 7: Async Execution
```
/opencode sleep 10
```

### Test 8: Aliases
```
/oc help
```

---

## 🔍 Verification Commands

### Check Sessions
```bash
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT topic_id, metadata FROM topic_mapping WHERE app_id='opencode';"
```

### Count Sessions
```bash
sqlite3 feishu-bot-start/data/feishu-topic-mappings.db "SELECT COUNT(*) FROM topic_mapping WHERE app_id='opencode';"
```

### Check Logs
```bash
tail -100 /tmp/feishu-manual-start.log | grep -i opencode
```

### Check for Errors
```bash
grep -i "error\|exception" /tmp/feishu-manual-start.log | grep -i "opencode" | tail -20
```

---

## ✅ Expected Results

| Test | Expected Result |
|------|----------------|
| 1 | Help text with command list |
| 2 | Returns "Hello World", creates session |
| 3 | Session persists, context maintained |
| 4 | Shows session ID, command count, timestamps |
| 5 | Lists all active sessions |
| 6 | New session created, old one replaced |
| 7 | "⏳ 任务正在执行中..." then result |
| 8 | Same help as /opencode help |

---

## 📊 Results Matrix

After testing, fill in actual results:

| Test | Expected | Actual | Pass/Fail |
|------|----------|--------|-----------|
| 1 | Help text | ⏳ TBD | ⏳ TBD |
| 2 | Echo works | ⏳ TBD | ⏳ TBD |
| 3 | Session persists | ⏳ TBD | ⏳ TBD |
| 4 | Status shown | ⏳ TBD | ⏳ TBD |
| 5 | Sessions listed | ⏳ TBD | ⏳ TBD |
| 6 | New session created | ⏳ TBD | ⏳ TBD |
| 7 | Async works | ⏳ TBD | ⏳ TBD |
| 8 | Aliases work | ⏳ TBD | ⏳ TBD |

---

## 🐛 Troubleshooting

### Bot doesn't respond
- Check app is running: `ps aux | grep spring-boot:run`
- Check logs: `tail -50 /tmp/feishu-manual-start.log`
- Verify WebSocket connected in logs

### Session not persisting
- Check database: `sqlite3 feishu-bot-start/data/feishu-topic-mappings.db .schema`
- Verify metadata column exists
- Check for errors in logs

### Async not working
- Verify opencodeExecutor bean in logs
- Check thread pool configuration
- Look for timeout errors in logs

---

## 📝 Documentation

- Full Testing Guide: `TESTING-GUIDE.md`
- Test Script: `test-opencode-feature.sh`
- Preparation Checklist: `TEST-PREPARATION-CHECKLIST.md`

---

## 🎯 Success Criteria

- [ ] All 8 tests execute
- [ ] All expected results match
- [ ] Database shows sessions
- [ ] No errors in logs
- [ ] Multi-turn works
- [ ] Async works

---

**Print this and use it as a cheat sheet during testing!**

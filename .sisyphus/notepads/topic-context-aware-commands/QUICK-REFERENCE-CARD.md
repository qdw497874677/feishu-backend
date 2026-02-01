# 🎯 Quick Reference Card - Topic-Aware Commands

**Print this or keep it open while testing!**

---

## 📋 Your 4 Tests (Cheat Sheet)

| Test | Where | Send This | Expect This |
|------|-------|-----------|-------------|
| **1** ✨ | Inside bash topic | `pwd` | Directory path |
| **2** | Inside bash topic | `/bash ls -la` | File listing |
| **3** | Normal chat (new) | `/bash pwd` | New topic + path |
| **4** | Inside bash topic | `mkdir test_dir` | Directory created |

---

## ⚡ Quick Commands

**Monitor logs while testing:**
```bash
./monitor-testing.sh
```

**Or manually:**
```bash
tail -f /tmp/feishu-run.log | grep "话题"
```

**Check app is running:**
```bash
ps aux | grep Application | grep feishu
```

---

## 🎯 Success Criteria

✅ Test 1: `pwd` (no prefix) works in topic
✅ Test 2: `/bash ls -la` (with prefix) works in topic
✅ Test 3: `/bash pwd` works in normal chat
✅ Test 4: `mkdir test_dir` works in topic

**All 4 pass?** → Tell me "✅ SUCCESS"

**Any fail?** → Tell me "❌ FAIL" + details

---

## 🔍 What I'm Watching For

**Test 1:**
```
话题中的消息不包含前缀，添加前缀: 'pwd'
```

**Test 2:**
```
话题中的消息包含命令前缀
话题消息处理后的内容: 'ls -la'
```

**Test 4:**
```
命令在白名单中
```

---

## 🆘 Troubleshooting

| Problem | Solution |
|---------|----------|
| Bot doesn't respond | Check bot is online in Feishu |
| "话题已失效" | Create new topic with `/bash pwd` |
| Shows help message | Check you're in right location (topic vs chat) |
| Nothing happens | Wait 30s, check logs, try again |

---

## 📊 Status

```
Application: ✅ Running (PID 10646)
Port: 17777
WebSocket: ✅ Connected
Tests: 38/38 passed (100%)
Feature: Prefix-free commands
```

---

## 🚀 Ready?

1. Open Feishu
2. Run 4 tests (2 min)
3. Report: "SUCCESS" or "FAIL"

**Done!** 🎉

---

**File:** `.sisyphus/notepads/topic-context-aware-commands/YOUR-TURN-4-TESTS.md`
**Monitor:** `./monitor-testing.sh`
**Status:** ✅ READY FOR TESTING

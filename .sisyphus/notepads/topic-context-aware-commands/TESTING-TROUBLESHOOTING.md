# 🔧 Testing Phase Troubleshooting Guide

**Purpose:** Quick solutions for common issues during manual testing
**When to Use:** When something doesn't work as expected during testing

---

## 🚨 Common Issues and Solutions

### Issue 1: Application Not Running

**Symptoms:**
- `ps aux | grep Application` returns nothing
- Port 17777 not listening
- No response to any command

**Diagnosis:**
```bash
ps aux | grep "[A]pplication.*feishu"
lsof -i:17777
```

**Solution:**
```bash
# Stop any existing processes
pkill -9 -f "feishu" 2>/dev/null
pkill -9 -f "spring-boot:run" 2>/dev/null

# Start fresh
./start-feishu.sh

# Wait for startup
sleep 10

# Verify started
grep "Started Application" /tmp/feishu-run.log | tail -1
```

**Expected Output:**
```
2026-01-31 19:XX:XX.XXX [main] INFO  com.qdw.feishu.Application - Started Application in X.XXX seconds
```

---

### Issue 2: Bot Not Responding to Messages

**Symptoms:**
- Message sent in Feishu
- No bot response
- No log entries

**Diagnosis:**
```bash
# Check if WebSocket is connected
grep "connected to wss://" /tmp/feishu-run.log | tail -1

# Check for recent messages
tail -50 /tmp/feishu-run.log | grep "Received message"

# Check for errors
tail -100 /tmp/feishu-run.log | grep ERROR
```

**Possible Causes:**

#### Cause A: WebSocket Disconnected
**Solution:**
```bash
# Restart application
./start-feishu.sh
```

#### Cause B: Application Not Receiving Messages
**Solution:**
```bash
# Check Feishu app credentials
grep "FEISHU_APPID" /root/workspace/feishu-backend/feishu-bot-start/src/main/resources/application-dev.yml

# Should match: cli_a8f66e3df8fb100d
```

#### Cause C: Event Listener Not Started
**Solution:**
```bash
# Check startup logs for event listener
grep "FeishuEventListener" /tmp/feishu-run.log

# Should see: "FeishuEventListener started"
```

---

### Issue 3: Topic Not Creating

**Symptoms:**
- Bot responds to command
- But topic not created
- Reply is direct message instead

**Diagnosis:**
```bash
# Check reply mode
grep "ReplyMode" /tmp/feishu-run.log | tail -5

# Check topic creation
grep "createMessage\|replyInThread" /tmp/feishu-run.log | tail -5
```

**Solution:**
This is expected behavior for some commands (like help). For bash commands, topic should be created.

**If topic should be created but isn't:**
- Check if app supports topics
- Verify replyInThread setting
- Check Feishu app permissions

---

### Issue 4: Command Not Executing

**Symptoms:**
- Bot responds
- But command didn't execute
- Error message in response

**Diagnosis:**
```bash
# Check for errors in log
tail -200 /tmp/feishu-run.log | grep -A 5 "ERROR"

# Check command execution
tail -200 /tmp/feishu-run.log | grep -A 5 "execute"
```

**Common Errors:**

#### Error A: "Command not found"
**Cause:** Command doesn't exist or not in PATH
**Solution:** Use full path or different command

#### Error B: "Whitelist error"
**Cause:** Command not in whitelist
**Solution:** Only use whitelisted commands (pwd, ls, mkdir, etc.)

#### Error C: "Permission denied"
**Cause:** Need elevated permissions
**Solution:** Use commands that don't require root

---

### Issue 5: Wrong Directory Returned

**Symptoms:**
- Expected `/root/workspace/feishu-backend`
- Got different directory

**Diagnosis:**
```bash
# Check current working directory
pwd

# Check application startup directory
grep "working.directory" /root/workspace/feishu-backend/feishu-bot-start/src/main/resources/application.yml
```

**Solution:**
This is normal if application was started from a different directory. The working directory is where the application was started, not necessarily the code directory.

---

### Issue 6: Log File Not Updating

**Symptoms:**
- Sent messages
- But log file not showing new entries

**Diagnosis:**
```bash
# Check log file location and size
ls -lh /tmp/feishu-run.log

# Check last modification time
stat /tmp/feishu-run.log | grep Modify

# Check if writing to file
lsof | grep feishu-run.log
```

**Solution:**
```bash
# If log file is stale, restart application
./start-feishu.sh

# Or monitor in real-time
tail -f /tmp/feishu-run.log
```

---

### Issue 7: Test Evidence Not Captured

**Symptoms:**
- Can't find log evidence
- Don't know how to capture screenshots

**Solution for Logs:**
```bash
# For Test 1
grep "话题中的消息不包含前缀" /tmp/feishu-run.log > evidence-test1.txt

# For Test 2
grep "话题中的消息包含命令前缀" /tmp/feishu-run.log > evidence-test2.txt

# For Test 3
grep "Processing message" /tmp/feishu-run.log | tail -10 > evidence-test3.txt

# For Test 4
grep "mkdir" /tmp/feishu-run.log | tail -10 > evidence-test4.txt
```

**Solution for Screenshots:**
- **Windows:** Win+Shift+S (Snipping Tool)
- **Mac:** Cmd+Shift+4 (Screenshot)
- **Linux:** PrtScn or gnome-screenshot
- **Feishu Web:** Browser screenshot

---

### Issue 8: Whitelist Error

**Symptoms:**
- Bot responds: "Command not in whitelist"
- Test 4 (mkdir) fails

**Diagnosis:**
```bash
# Check whitelist
grep -A 20 "WHITELIST" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/validation/CommandWhitelistValidator.java

# Should include: mkdir, opencode
```

**Solution:**
If mkdir is not in whitelist, code wasn't deployed correctly:
```bash
# Verify code is deployed
grep "opencode" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/validation/CommandWhitelistValidator.java

# If not found, rebuild and redeploy
mvn clean install -DskipTests
./start-feishu.sh
```

---

### Issue 9: Prefix Not Adding in Topic

**Symptoms:**
- Send `pwd` in topic
- Bot doesn't execute
- Log doesn't show prefix being added

**Diagnosis:**
```bash
# Check if topic has mapping
grep "找到话题映射" /tmp/feishu-run.log | tail -5

# Check if code is deployed
grep "话题中的消息不包含前缀，添加前缀" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
```

**Solution:**
If code is not found, it wasn't deployed:
```bash
# Rebuild
cd /root/workspace/feishu-backend
mvn clean install -DskipTests

# Restart
./start-feishu.sh

# Verify
grep "话题中的消息不包含前缀，添加前缀" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
```

---

### Issue 10: Test Timing Out

**Symptoms:**
- Sent command
- Waiting too long for response
- No activity in logs

**Diagnosis:**
```bash
# Check if application is frozen
ps aux | grep "[A]pplication.*feishu"
top -p <PID>

# Check for deadlocks
jstack <PID>

# Check system resources
free -h
df -h
```

**Solution:**
```bash
# Restart application
./start-feishu.sh

# If problem persists, check system resources
# May need to restart the server
```

---

## 🔍 Diagnostic Commands Reference

### Quick Health Check
```bash
# All-in-one health check
echo "=== Application Status ===" && \
ps aux | grep "[A]pplication.*feishu" && \
echo "" && \
echo "=== Port Status ===" && \
lsof -i:17777 && \
echo "" && \
echo "=== WebSocket Status ===" && \
grep "connected to wss://" /tmp/feishu-run.log | tail -1 && \
echo "" && \
echo "=== Recent Errors ===" && \
tail -50 /tmp/feishu-run.log | grep ERROR || echo "No errors"
```

### Detailed Diagnostic
```bash
# Create diagnostic report
cat > /tmp/diagnostic-report.txt << 'EOF'
=== Application Diagnostic Report ===
Generated: $(date)

1. Process Status
$(ps aux | grep "[A]pplication.*feishu")

2. Port Status
$(lsof -i:17777)

3. WebSocket Connection
$(grep "connected to wss://" /tmp/feishu-run.log | tail -1)

4. Recent Messages (last 5)
$(tail -50 /tmp/feishu-run.log | grep "Processing message" | tail -5)

5. Recent Errors (last 5)
$(tail -100 /tmp/feishu-run.log | grep ERROR | tail -5)

6. Topic Processing (last 5)
$(tail -100 /tmp/feishu-run.log | grep "话题" | tail -5)

7. Application Configuration
$(grep "FEISHU_APPID\|FEISHU_MODE" /root/workspace/feishu-backend/feishu-bot-start/src/main/resources/application-dev.yml)

8. Code Deployment
$(grep -c "话题中的消息不包含前缀，添加前缀" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java)
EOF

cat /tmp/diagnostic-report.txt
```

---

## 📞 When to Escalate

**Escalate to developer if:**
- ❌ Application won't start after 3 attempts
- ❌ WebSocket won't connect
- ❌ All commands failing with same error
- ❌ Evidence shows unexpected behavior
- ❌ Cannot diagnose issue with this guide

**Before escalating:**
1. Run health check command above
2. Capture diagnostic report
3. Save log file: `cp /tmp/feishu-run.log error.log`
4. Document what you were testing
5. Note exact error messages

---

## 📝 Escalation Template

```
ISSUE ESCALATION - Testing Phase

What I was testing:
- Test [1/2/3/4]
- Command sent: [command]
- Expected: [expected result]

What happened:
- [Describe actual result]
- Error message: [if any]

Evidence:
- Diagnostic report: /tmp/diagnostic-report.txt
- Log file: error.log
- Test evidence: .sisyphus/notepads/topic-context-aware-commands/evidence/

System Info:
- Application running: [yes/no]
- PID: [if known]
- WebSocket connected: [yes/no]
- Code deployed: [yes/no]

Requesting assistance.
```

---

## ✅ Prevention Tips

### Before Starting Tests
1. Verify application is running
2. Check WebSocket is connected
3. Verify code is deployed
4. Start log monitoring
5. Create evidence directory

### During Testing
1. Send one command at a time
2. Wait for response before next command
3. Monitor logs in real-time
4. Capture evidence immediately after each test
5. Note any unusual behavior

### After Each Test
1. Verify expected results
2. Check logs for errors
3. Capture evidence
4. Mark test as PASS/FAIL
5. Note any issues

---

## 🎯 Quick Reference

**Most Common Issues:**
1. Application not running → Restart: `./start-feishu.sh`
2. Bot not responding → Check logs: `tail -100 /tmp/feishu-run.log`
3. Command not found → Use whitelisted commands only
4. No topic created → Normal for some commands
5. Evidence missing → Capture immediately after test

**Quick Fixes:**
```bash
# Restart
./start-feishu.sh

# Check logs
tail -100 /tmp/feishu-run.log

# Verify code
grep "话题中的消息不包含前缀" feishu-bot-domain/src/.../BotMessageService.java

# Check health
./status-check.sh
```

---

**Last Updated:** 2026-01-31 19:15
**Purpose:** Troubleshooting for manual testing phase
**Status:** READY FOR USE

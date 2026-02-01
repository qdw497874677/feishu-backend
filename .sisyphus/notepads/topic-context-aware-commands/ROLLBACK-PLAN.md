# Rollback Plan - Topic-Aware Command Execution

**Date:** 2026-01-31
**Feature:** Prefix-free command execution in topics
**Status:** READY FOR DEPLOYMENT

---

## 🔄 Rollback Procedures

If issues arise after deployment, follow these rollback steps.

---

## Quick Rollback (Recommended)

### Option 1: Git Revert (Fastest)

**Time:** 2 minutes
**Risk:** LOW
**Data Loss:** None

```bash
# 1. Find the commit hash
git log --oneline | grep "feat(topic): enable prefix-free"

# 2. Revert the commit
git revert <commit-hash>

# 3. Rebuild
mvn clean install -DskipTests

# 4. Restart application
./start-feishu.sh
```

### Option 2: Manual File Restore (Safest)

**Time:** 5 minutes
**Risk:** VERY LOW
**Data Loss:** None

```bash
# 1. Restore BotMessageService.java
git checkout HEAD~1 feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java

# 2. Restore CommandWhitelistValidator.java
git checkout HEAD~1 feishu-bot-domain/src/main/java/com/qdw/feishu/domain/validation/CommandWhitelistValidator.java

# 3. Rebuild
mvn clean install -DskipTests

# 4. Restart application
./start-feishu.sh
```

---

## Detailed Rollback Steps

### Step 1: Stop Application

```bash
# Find and kill the process
ps aux | grep "[A]pplication.*feishu" | awk '{print $2}' | xargs kill -9

# Or use pkill
pkill -9 -f "feishu"

# Verify stopped
ps aux | grep feishu
# Should return nothing
```

### Step 2: Restore Code

**Choose ONE method:**

**Method A: Git Revert**
```bash
# List recent commits
git log --oneline -5

# Revert the feature commit
git revert <commit-hash>

# Review changes
git diff HEAD~1
```

**Method B: Git Reset**
```bash
# Reset to previous commit (SOFT - keeps changes)
git reset --soft HEAD~1

# Review what will be removed
git status

# Remove the changes
git reset HEAD hard
```

**Method C: Manual Restore**
```bash
# Restore specific files
git checkout HEAD~1 -- feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
git checkout HEAD~1 -- feishu-bot-domain/src/main/java/com/qdw/feishu/domain/validation/CommandWhitelistValidator.java

# Verify restored
git status
```

### Step 3: Rebuild Application

```bash
# Clean build
cd /root/workspace/feishu-backend
mvn clean install -DskipTests

# Verify build success
# Should see: BUILD SUCCESS
```

### Step 4: Restart Application

```bash
# Using startup script (recommended)
./start-feishu.sh

# Or manually
cd feishu-bot-start
LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 \
FEISHU_APPSECRET="CFVrKX1w00ypHEqT1vInwdeKznwmYWpn" \
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Step 5: Verify Rollback

```bash
# Check application is running
ps aux | grep "[A]pplication.*feishu"

# Check WebSocket
grep "connected to wss://" /tmp/feishu-run.log | tail -1

# Verify code is NOT present
grep "话题中的消息不包含前缀，添加前缀" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
# Should return nothing (code removed)

# Test in Feishu
# In topic, send: pwd
# Should: NOT execute (prefix required)
```

---

## Rollback Verification Checklist

- [ ] Application stopped
- [ ] Code restored to previous version
- [ ] Build successful
- [ ] Application restarted
- [ ] WebSocket connected
- [ ] Old behavior verified (prefix required)
- [ ] No errors in logs

---

## Rollback Scenarios

### Scenario 1: Immediate Bug Found

**Trigger:** Bug discovered immediately after deployment
**Action:** Quick rollback (Option 1 or 2)
**Time:** 2-5 minutes
**Impact:** Minimal downtime

### Scenario 2: User Reports Issue

**Trigger:** User reports problem after using feature
**Action:**
1. Capture error logs
2. Analyze issue
3. Decide: fix or rollback
4. If rollback: use Option 2 (safer)
**Time:** 10-15 minutes
**Impact:** Short investigation period

### Scenario 3: Performance Degradation

**Trigger:** System slows down or becomes unstable
**Action:**
1. Check application metrics
2. Check error logs
3. If critical: immediate rollback
4. If minor: monitor and decide
**Time:** 5-10 minutes
**Impact:** Possible performance issues during investigation

### Scenario 4: Data Loss Risk

**Trigger:** Potential data corruption or loss
**Action:** IMMEDIATE rollback (no investigation)
**Time:** 2 minutes
**Impact:** Prevent data loss

---

## Pre-Rollback Checklist

**Before Rolling Back:**

- [ ] Confirm issue is reproducible
- [ ] Capture error logs: `tail -200 /tmp/feishu-run.log > error.log`
- [ ] Document the issue
- [ ] Check if quick fix is possible
- [ ] Estimate rollback time
- [ ] Notify users (if applicable)

**Questions to Ask:**

1. Is the issue critical? (YES → Rollback immediately)
2. Is the issue reproducible? (NO → Monitor)
3. Can we fix it quickly? (YES → Consider fix instead)
4. Is data at risk? (YES → Rollback immediately)

---

## Post-Rollback Actions

### After Successful Rollback

1. **Verify Old Behavior**
   ```bash
   # Test in Feishu
   # In topic, send: /bash pwd
   # Should: Execute normally (prefix required)
   ```

2. **Check Application Health**
   ```bash
   ps aux | grep "[A]pplication.*feishu"
   lsof -i:17777
   grep "ERROR" /tmp/feishu-run.log
   ```

3. **Document the Rollback**
   - Why it was rolled back
   - What issues were found
   - Timestamp of rollback
   - Current git commit hash

4. **Plan Fix (if needed)**
   - Root cause analysis
   - Fix implementation
   - Testing strategy
   - Deployment plan

### After Failed Rollback

If rollback fails:

1. **Stop Application**
   ```bash
   pkill -9 -f "feishu"
   ```

2. **Check Git Status**
   ```bash
   git status
   git log --oneline -5
   ```

3. **Force Reset (Last Resort)**
   ```bash
   git reset --hard HEAD~1
   mvn clean install -DskipTests
   ./start-feishu.sh
   ```

4. **Seek Help**
   - Document what went wrong
   - Contact support/senior developer
   - Provide error logs

---

## Rollback Decision Matrix

| Issue Severity | User Impact | Data Risk | Action |
|---------------|-------------|-----------|---------|
| Critical | High | Yes | IMMEDIATE ROLLBACK |
| High | High | No | ROLLBACK within 5 min |
| Medium | Medium | No | Investigate, then decide |
| Low | Low | No | Monitor, fix in next release |

---

## Rollback Time Estimates

| Scenario | Time Estimate |
|----------|---------------|
| Quick rollback (git revert) | 2 minutes |
| Manual file restore | 5 minutes |
| Full restart (from git) | 10 minutes |
| Investigation + rollback | 15-20 minutes |

---

## Rollback Commands Summary

```bash
# Quick rollback (all in one)
git revert HEAD && \
mvn clean install -DskipTests && \
pkill -9 -f "feishu" && \
./start-feishu.sh

# Verify rollback
ps aux | grep "[A]pplication.*feishu" && \
grep "connected to wss://" /tmp/feishu-run.log | tail -1 && \
! grep -q "话题中的消息不包含前缀" feishu-bot-domain/src/main/java/com/qdw/feishu/domain/service/BotMessageService.java
```

---

## Prevention: How to Avoid Rollback

### Before Deploying
- ✅ Test thoroughly in dev environment
- ✅ Run all automated tests
- ✅ Perform manual testing
- ✅ Review code changes
- ✅ Check for breaking changes

### After Deploying
- ✅ Monitor application logs
- ✅ Watch for errors
- ✅ Test core functionality
- ✅ Be ready to rollback quickly

---

## Contact Information

**If Rollback Fails:**
1. Check application logs: `/tmp/feishu-run.log`
2. Check git status: `git status`
3. Document the issue
4. Contact: [Senior Developer / Tech Lead]

---

**Last Updated:** 2026-01-31 18:25
**Status:** Rollback Plan Ready
**Confidence:** HIGH (rollback is straightforward)
**Risk:** VERY LOW (simple changes, easy to revert)

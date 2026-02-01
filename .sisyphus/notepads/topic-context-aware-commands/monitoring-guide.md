# Monitoring Guide

## 2026-01-31 17:52 - Production Monitoring for Topic-Aware Commands

### Key Metrics to Monitor

#### 1. Application Health
**Endpoint:** N/A (WebSocket connection)
**Metrics:**
- WebSocket connection status
- Application uptime
- Process memory usage
- Thread pool utilization

**How to Monitor:**
```bash
# Check WebSocket connection
grep "connected to wss://" /tmp/feishu-run.log | tail -1

# Check if application is running
ps aux | grep -E "java.*Application" | grep -v grep

# Check port status
lsof -i:17777

# Check recent errors
tail -100 /tmp/feishu-run.log | grep ERROR
```

#### 2. Feature Usage
**Metrics to Track:**
- Number of messages in topics with prefix
- Number of messages in topics without prefix
- Ratio of prefixed vs non-prefixed commands
- Error rate for topic messages

**Log Patterns to Monitor:**
```bash
# Monitor topic-aware transformations
tail -f /tmp/feishu-run.log | grep "话题中的消息"

# Count transformations
grep "话题中的消息不包含前缀，添加前缀" /tmp/feishu-run.log | wc -l
grep "话题中的消息包含命令前缀，去除前缀" /tmp/feishu-run.log | wc -l
```

#### 3. Error Rates
**Critical Errors:**
- `话题映射不存在` - Topic mapping not found
- `app_id is invalid` - Feishu credential error
- WebSocket connection failures

**How to Monitor:**
```bash
# Real-time error monitoring
tail -f /tmp/feishu-run.log | grep -E "(ERROR|WARN|话题映射不存在)"

# Error count by type
grep "ERROR" /tmp/feishu-run.log | awk '{print $NF}' | sort | uniq -c
```

---

### Real-Time Monitoring

#### Monitor All Topic Messages
```bash
tail -f /tmp/feishu-run.log | grep -E "(话题|消息|Processing)"
```

#### Monitor Transformations
```bash
tail -f /tmp/feishu-run.log | grep "话题消息处理后的内容"
```

#### Monitor Errors Only
```bash
tail -f /tmp/feishu-run.log | grep -E "(ERROR|Exception|Failed)"
```

#### Monitor Command Execution
```bash
tail -f /tmp/feishu-run.log | grep "Executing:"
```

---

### Daily Health Checks

#### Morning Check (Daily)
```bash
#!/bin/bash
echo "=== Feishu Bot Health Check ==="
echo "Time: $(date)"

# 1. Application running?
if ps aux | grep -q "java.*Application" | grep -v grep; then
    echo "✅ Application: RUNNING"
else
    echo "❌ Application: NOT RUNNING"
    exit 1
fi

# 2. WebSocket connected?
if grep -q "connected to wss://" /tmp/feishu-run.log | tail -10; then
    echo "✅ WebSocket: CONNECTED"
else
    echo "❌ WebSocket: NOT CONNECTED"
fi

# 3. Apps registered?
APPS=$(grep "AppRegistry: 应用注册完成" /tmp/feishu-run.log | tail -1)
if [ -n "$APPS" ]; then
    echo "✅ Apps: $APPS"
else
    echo "❌ Apps: NOT REGISTERED"
fi

# 4. Recent errors?
ERRORS=$(grep "ERROR" /tmp/feishu-run.log | tail -10 | wc -l)
if [ $ERRORS -eq 0 ]; then
    echo "✅ Errors: NONE (last 10 lines)"
else
    echo "⚠️  Errors: $ERRORS (last 10 lines)"
fi

# 5. Log file size?
SIZE=$(du -h /tmp/feishu-run.log | cut -f1)
echo "📄 Log size: $SIZE"

echo "=== Check Complete ==="
```

#### Weekly Review
```bash
# Error trends
echo "=== Weekly Error Summary ==="
grep "ERROR" /tmp/feishu-run.log | awk '{print $1}' | sort | uniq -c | sort -rn

# Feature usage
echo "=== Feature Usage This Week ==="
echo "Prefix added:"
grep "话题中的消息不包含前缀，添加前缀" /tmp/feishu-run.log | wc -l
echo "Prefix stripped:"
grep "话题中的消息包含命令前缀，去除前缀" /tmp/feishu-run.log | wc -l

# App statistics
echo "=== App Statistics ==="
grep "Executing:" /tmp/feishu-run.log | awk '{print $NF}' | sort | uniq -c | sort -rn
```

---

### Alerting Rules

#### Critical Alerts (Immediate Action Required)
1. **Application Down**
   - Trigger: Process not running
   - Action: Restart application
   - Command: `./start-feishu.sh`

2. **WebSocket Disconnected**
   - Trigger: No "connected to wss://" in last 5 minutes
   - Action: Check network, restart application
   - Command: Check logs, restart if needed

3. **Credential Error**
   - Trigger: "app_id is invalid" in logs
   - Action: Verify credentials, restart with correct config
   - Command: Check application-dev.yml

#### Warning Alerts (Monitor Closely)
1. **High Error Rate**
   - Trigger: >10 errors in last hour
   - Action: Investigate error patterns
   - Command: `grep ERROR /tmp/feishu-run.log | tail -50`

2. **Topic Mapping Failures**
   - Trigger: "话题映射不存在" appears frequently
   - Action: Check topic mapping file
   - Command: Check TopicMappingGatewayImpl logs

#### Info Alerts (Normal Operation)
1. **Feature Usage Statistics**
   - Trigger: Daily usage report
   - Action: Log statistics for analysis
   - Command: Weekly review script

---

### Performance Monitoring

#### Response Time
```bash
# Measure command execution time
grep "Executing:" /tmp/feishu-run.log | \
  awk '{print $1, $2}' | \
  while read time; do
    # Calculate time difference
    # (This would require custom log formatting)
  done
```

#### Memory Usage
```bash
# Check JVM memory
ps aux | grep "java.*Application" | \
  awk '{print "MEM:", $6/1024, "MB", "CPU:", $3"%"}'
```

#### Thread Pool
```bash
# Check async thread pool usage
grep "bash-async" /tmp/feishu-run.log | \
  tail -20
```

---

### Log Analysis

#### Success Rate
```bash
# Calculate success rate for topic messages
TOTAL=$(grep "话题中的消息" /tmp/feishu-run.log | wc -l)
SUCCESS=$(grep "话题消息处理后的内容" /tmp/feishu-run.log | wc -l)
RATE=$((SUCCESS * 100 / TOTAL))
echo "Success Rate: $RATE%"
```

#### Feature Adoption
```bash
# How often users use prefix vs no-prefix
WITH_PREFIX=$(grep "话题中的消息包含命令前缀" /tmp/feishu-run.log | wc -l)
WITHOUT_PREFIX=$(grep "话题中的消息不包含前缀" /tmp/feishu-run.log | wc -l)
TOTAL=$((WITH_PREFIX + WITHOUT_PREFIX))

echo "With prefix: $WITH_PREFIX ($((WITH_PREFIX * 100 / TOTAL))%)"
echo "Without prefix: $WITHOUT_PREFIX ($((WITHOUT_PREFIX * 100 / TOTAL))%)"
```

#### Error Analysis
```bash
# Categorize errors
echo "=== Error Categories ==="
grep "ERROR" /tmp/feishu-run.log | \
  awk -F':' '{print $NF}' | \
  sort | uniq -c | sort -rn
```

---

### Log Rotation

#### Current Log Size
```bash
ls -lh /tmp/feishu-run.log
```

#### Rotate Logs (if needed)
```bash
# Archive old log
mv /tmp/feishu-run.log /tmp/feishu-run.log.$(date +%Y%m%d)

# Application will create new log file automatically
```

#### Setup Logrotate (recommended)
```bash
# Create logrotate config
cat > /etc/logrotate.d/feishu-bot << 'EOF'
/tmp/feishu-run.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 644 root root
    postrotate
        # Application will automatically create new log file
    endscript
}
EOF
```

---

### Troubleshooting Commands

#### Quick Diagnosis
```bash
# All-in-one health check
echo "=== Quick Diagnosis ==="
echo "Process: $(ps aux | grep 'java.*Application' | grep -v grep | wc -l)"
echo "Port: $(lsof -ti:17777 | wc -l)"
echo "WebSocket: $(grep -c 'connected to wss://' /tmp/feishu-run.log)"
echo "Errors (last hour): $(grep ERROR /tmp/feishu-run.log | tail -100 | wc -l)"
echo "Topic transforms today: $(grep '话题中的消息' /tmp/feishu-run.log | grep "$(date +%Y-%m-%d)" | wc -l)"
```

#### Detailed Diagnosis
```bash
# Recent activity
echo "=== Last 10 Message Processings ==="
grep "话题中的消息" /tmp/feishu-run.log | tail -10

# Recent errors
echo "=== Last 10 Errors ==="
grep ERROR /tmp/feishu-run.log | tail -10

# Recent WebSocket status
echo "=== WebSocket Status ==="
grep -E "(connected|disconnected|WebSocket)" /tmp/feishu-run.log | tail -10
```

---

### Dashboard Metrics

#### Key Performance Indicators (KPIs)
1. **Uptime:** Application running time
2. **Success Rate:** % of successful message processing
3. **Feature Adoption:** % of topic messages without prefix
4. **Error Rate:** % of messages resulting in errors
5. **Response Time:** Average command execution time

#### Calculations
```bash
# Uptime (since last restart)
START_TIME=$(grep "Started Application" /tmp/feishu-run.log | tail -1 | awk '{print $1, $2}' | tr -d '[]')
echo "Started: $START_TIME"

# Success rate (last 100 messages)
LAST_100=$(grep "话题中的消息" /tmp/feishu-run.log | tail -100)
TOTAL=$(echo "$LAST_100" | wc -l)
SUCCESS=$(echo "$LAST_100" | grep "话题消息处理后的内容" | wc -l)
echo "Success Rate: $((SUCCESS * 100 / TOTAL))%"

# Feature adoption (last 100 topic messages)
WITHOUT_PREFIX=$(echo "$LAST_100" | grep "话题中的消息不包含前缀" | wc -l)
echo "Adoption: $((WITHOUT_PREFIX * 100 / TOTAL))%"
```

---

### Summary

**Monitoring Priority:**
1. ✅ Application health (highest)
2. ✅ WebSocket connection (highest)
3. ✅ Feature usage (medium)
4. ✅ Error rates (high)
5. ✅ Performance (low)

**Recommended Monitoring:**
- Real-time: grep logs for "话题" keyword
- Daily: Health check script
- Weekly: Usage statistics review
- Monthly: Log analysis and optimization

**Alert Thresholds:**
- Application down: Immediate
- WebSocket disconnected: Immediate
- Error rate >5%: Warning
- Error rate >10%: Critical

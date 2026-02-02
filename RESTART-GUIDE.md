# 飞书机器人重启指南

**⚠️ 重要：重启前必读**

1. **必须使用启动脚本** `start-feishu.sh`，不要手动启动
2. **环境变量传递问题**：手动启动会导致 `app_secret invalid` 错误
3. **端口占用**：脚本会自动清理 17777 端口

---

## 快速重启（无代码变更）

**适用场景**：只修改了配置，没有改代码

```bash
./start-feishu.sh
```

**耗时**：2-3 秒

---

## 完整重启（代码变更后）

**适用场景**：修改了 Java 代码或添加了新应用

### 步骤 1：重新构建

```bash
cd /root/workspace/feishu-backend
mvn clean package -DskipTests
```

**耗时**：10-15 秒

### 步骤 2：重启应用

```bash
./start-feishu.sh
```

**耗时**：2-3 秒

**总耗时**：约 15-20 秒

---

## 验证启动成功

### 方法 1：查看日志

```bash
tail -50 /tmp/feishu-run.log
```

**成功标志**：
- ✅ `Started Application in X seconds`
- ✅ `connected to wss://msg-frontier.feishu.cn/...`
- ✅ `应用注册完成，共注册 5 个应用: [help, opencode, bash, history, time]`

### 方法 2：检查进程和端口

```bash
# 检查进程
ps aux | grep "spring-boot:run" | grep -v grep

# 检查端口
lsof -i:17777 | grep LISTEN
```

---

## 常见问题

### Q1: 提示 "Port 17777 was already in use"

**原因**：旧进程未完全退出

**解决**：
```bash
# 等待 3 秒让脚本自动清理，或手动清理
lsof -ti:17777 | xargs kill -9
./start-feishu.sh
```

---

### Q2: 提示 "app_id or app_secret is invalid"

**原因**：环境变量未正确传递

**解决**：
- ❌ 不要手动 `mvn spring-boot:run`
- ✅ 必须使用 `./start-feishu.sh`

---

### Q3: 启动后没有连接到飞书

**检查步骤**：
```bash
# 1. 查看错误日志
tail -100 /tmp/feishu-run.log | grep -i error

# 2. 检查 WebSocket 连接
tail -20 /tmp/feishu-run.log | grep "connected"
```

**常见原因**：
- 网络问题
- `FEISHU_APPSECRET` 错误（检查启动脚本）
- 飞书服务端问题

---

## 实时监控

### 查看实时日志

```bash
tail -f /tmp/feishu-run.log
```

### 只看关键信息

```bash
tail -f /tmp/feishu-run.log | grep -E "(Error|connected|Started|应用注册)"
```

---

## 停止应用

```bash
# 方法 1：使用进程 PID
ps aux | grep "spring-boot:run" | grep -v grep
kill <PID>

# 方法 2：强制停止所有
pkill -9 -f "feishu"
```

---

## 为什么必须用启动脚本？

### 环境变量传递陷阱

**❌ 错误做法**（环境变量未传递）：

```bash
export FEISHU_APPSECRET="xxx"
mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/log 2>&1 &
```

**✅ 正确做法**（启动脚本）：

```bash
export FEISHU_APPID="cli_a8f66e3df8fb100d"
export FEISHU_APPSECRET="CFVrKX1w00ypHEqT1vInwdeKznwmYWpn"
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8
mvn spring-boot:run -Dspring-boot.run.profiles=dev > "$LOG_FILE" 2>&1 &
```

**关键差异**：
- Shell 的 `export` 在后台启动时可能不会正确传递
- 必须在启动命令的同一行显式设置环境变量
- 启动脚本已处理此问题

---

## 总结

| 场景 | 命令 | 耗时 |
|------|------|------|
| 仅重启 | `./start-feishu.sh` | 2-3 秒 |
| 代码更新后重启 | `mvn clean package -DskipTests && ./start-feishu.sh` | 15-20 秒 |

**黄金法则**：永远使用 `start-feishu.sh`，不要手动启动。

#!/bin/bash

# 飞书机器人启动脚本
# 此脚本包含 dev 环境的凭证配置

# 飞书应用凭证
export FEISHU_APPID="cli_a8f66e3df8fb100d"
export FEISHU_APPSECRET="CFVrKX1w00ypHEqT1vInwdeKznwmYWpn"

# 应用端口
export APP_PORT="17777"

# 编码设置
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8

# 工作目录
cd /root/workspace/feishu-backend/feishu-bot-start

# 停止旧进程
echo "正在停止旧的飞书相关进程..."
pkill -9 -f "feishu" 2>/dev/null
pkill -9 -f "spring-boot:run" 2>/dev/null

# 清理应用端口
echo "正在清理端口 $APP_PORT..."
if command -v fuser >/dev/null 2>&1; then
    fuser -k ${APP_PORT}/tcp 2>/dev/null
elif command -v lsof >/dev/null 2>&1; then
    lsof -ti:${APP_PORT} | xargs kill -9 2>/dev/null
else
    # 使用 netstat 和 ps 清理
    OLD_PID=$(netstat -tlnp 2>/dev/null | grep ":${APP_PORT}" | awk '{print $7}' | cut -d'/' -f1 | grep -v '-')
    if [ -n "$OLD_PID" ]; then
        kill -9 $OLD_PID 2>/dev/null
    fi
fi

echo "等待端口释放..."
sleep 3

# 启动应用
echo "正在启动飞书机器人..."
echo "应用ID: $FEISHU_APPID"
echo "工作目录: $(pwd)"
echo ""

# 后台启动，日志输出到文件
LOG_FILE="/tmp/feishu-run.log"
echo "日志文件: $LOG_FILE"
# 重要：环境变量必须在启动命令的同一行设置，确保后台进程能正确继承
export FEISHU_APPID="cli_a8f66e3df8fb100d"
export FEISHU_APPSECRET="CFVrKX1w00ypHEqT1vInwdeKznwmYWpn"
export LANG=zh_CN.UTF-8
export LC_ALL=zh_CN.UTF-8
mvn spring-boot:run -Dspring-boot.run.profiles=dev > "$LOG_FILE" 2>&1 &

PID=$!
echo "✅ 应用已启动！PID: $PID"
echo ""
echo "查看实时日志："
echo "  tail -f /tmp/feishu-run.log"
echo ""
echo "查看应用状态："
echo "  ps aux | grep $PID"
echo ""
echo "停止应用："
echo "  kill $PID"
echo "  或: pkill -9 -f 'feishu'"
echo ""

# 等待启动并显示日志
echo "等待应用启动..."
sleep 5
echo ""
echo "=== 应用启动日志 ==="
tail -30 /tmp/feishu-run.log
echo ""
echo "=== 启动完成，开始监听飞书事件 ==="

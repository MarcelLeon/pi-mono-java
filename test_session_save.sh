#!/bin/bash

echo "🧪 测试Pi-Mono Java CLI会话保存功能..."

# 清理之前的测试文件
rm -rf .pi/sessions

echo "启动CLI应用并测试会话保存..."
mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" -Dexec.args="" << 'EOF' &
PID=$!
sleep 3

# 发送测试消息
echo "今天天气很好" > /dev/stderr
sleep 1

# 保存会话
echo "/save" > /dev/stderr
sleep 1

# 退出
echo "exit" > /dev/stderr

wait $PID
EOF

echo "✅ 测试完成"
echo "检查会话文件是否创建："
ls -la .pi/sessions/ 2>/dev/null || echo "❌ 会话文件目录不存在或为空"
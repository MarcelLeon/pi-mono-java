#!/bin/bash

echo "🧪 测试Pi-Mono Java CLI保存功能..."

# 运行CLI并发送测试命令
echo "启动CLI应用..."
mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" -Dexec.args="" << 'EOF' &
PID=$!
sleep 2

# 发送测试命令
echo "测试会话保存功能..."
sleep 1

# 发送消息
echo "today is a good day" > /dev/stderr
sleep 1

# 保存会话
echo "/save" > /dev/stderr
sleep 1

# 退出
echo "exit" > /dev/stderr

wait $PID
EOF

echo "✅ 测试完成"
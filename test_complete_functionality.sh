#!/bin/bash

echo "🧪 测试Pi-Mono Java CLI完整功能..."

# 清理之前的测试文件
rm -rf .pi/sessions

# 创建一个简单的测试脚本
cat > /tmp/pi_test_script.sh << 'EOF'
#!/bin/bash

# 启动CLI并发送测试命令
{
    sleep 2
    echo "今天天气很好"
    sleep 1
    echo "/save"
    sleep 1
    echo "exit"
} | timeout 10s mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" || true
EOF

chmod +x /tmp/pi_test_script.sh

# 运行测试
echo "启动CLI并测试..."
/tmp/pi_test_script.sh

echo ""
echo "检查会话文件是否创建："
if [ -d ".pi/sessions" ]; then
    ls -la .pi/sessions/
    echo ""
    echo "会话文件内容预览："
    head -n 3 .pi/sessions/*.jsonl 2>/dev/null || echo "没有找到会话文件"
else
    echo "❌ 会话文件目录不存在"
fi

# 清理
rm -f /tmp/pi_test_script.sh
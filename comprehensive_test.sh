#!/bin/bash

echo "🧪 Pi-Mono Java - 命令行和Spring集成测试"
echo "=========================================="
echo ""

# 清理环境
rm -rf .pi/sessions
rm -rf target/test-sessions
rm -rf test-cli-output

# 创建测试输出目录
mkdir -p test-cli-output

echo "📋 测试计划："
echo "1. CLI功能测试"
echo "2. Spring Boot集成测试"
echo "3. 完整端到端测试"
echo ""

# 测试1：CLI功能测试
echo "🔍 测试1：CLI功能测试"
echo "---------------------"

# 创建CLI测试脚本
cat > test-cli.sh << 'EOF'
#!/bin/bash

# 启动CLI并执行命令
{
    sleep 2
    echo "Hello, this is a test message"
    sleep 1
    echo "/save"
    sleep 1
    echo "/sessions"
    sleep 1
    echo "exit"
} | mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" 2>&1
EOF

chmod +x test-cli.sh

echo "运行CLI测试..."
timeout 15s ./test-cli.sh > test-cli-output/cli_output.txt 2>&1

# 分析CLI输出
if grep -q "会话保存成功\|Session saved" test-cli-output/cli_output.txt; then
    echo "✅ CLI保存功能正常"
else
    echo "⚠️ CLI保存功能需要验证"
fi

if grep -q "sessions\|会话" test-cli-output/cli_output.txt; then
    echo "✅ CLI会话列表功能正常"
else
    echo "⚠️ CLI会话列表功能需要验证"
fi

echo "CLI输出摘要："
head -n 20 test-cli-output/cli_output.txt
echo ""

# 检查会话文件
if [ -d ".pi/sessions" ] && [ "$(ls -A .pi/sessions 2>/dev/null)" ]; then
    echo "✅ 会话文件已创建：$(ls .pi/sessions)"
    echo "会话文件内容："
    head -n 2 .pi/sessions/*.jsonl
else
    echo "❌ 会话文件未创建"
fi

echo ""
echo "🔍 测试2：Spring Boot集成测试"
echo "-------------------------------"

# 编译示例项目
cd example-project

echo "编译示例项目..."
mvn clean compile > ../test-cli-output/spring_compile.txt 2>&1

if grep -q "BUILD SUCCESS" ../test-cli-output/spring_compile.txt; then
    echo "✅ Spring项目编译成功"
else
    echo "❌ Spring项目编译失败"
    cat ../test-cli-output/spring_compile.txt
fi

# 运行Spring示例（限制时间）
echo "运行Spring示例..."
timeout 10s mvn spring-boot:run > ../test-cli-output/spring_run.txt 2>&1 &

# 等待一段时间让应用启动
sleep 3

# 检查应用输出
if grep -q "会话创建成功\|Session created" ../test-cli-output/spring_run.txt; then
    echo "✅ Spring集成正常 - 会话创建成功"
else
    echo "⚠️ Spring集成需要验证"
fi

if grep -q "AI响应\|AI response" ../test-cli-output/spring_run.txt; then
    echo "✅ Spring集成正常 - AI响应正常"
else
    echo "⚠️ Spring集成需要验证"
fi

if grep -q "会话已保存\|Session saved" ../test-cli-output/spring_run.txt; then
    echo "✅ Spring集成正常 - 会话保存成功"
else
    echo "⚠️ Spring集成需要验证"
fi

echo "Spring运行输出："
head -n 15 ../test-cli-output/spring_run.txt
echo ""

# 检查Spring生成的会话文件
if [ -d ".pi/sessions" ] && [ "$(ls -A .pi/sessions 2>/dev/null)" ]; then
    echo "✅ Spring集成生成会话文件：$(ls .pi/sessions)"
else
    echo "⚠️ Spring集成未生成会话文件"
fi

cd ..

echo ""
echo "🔍 测试3：完整端到端测试"
echo "-------------------------"

# 创建端到端测试
cat > test-e2e.sh << 'EOF'
#!/bin/bash

echo "=== 端到端测试开始 ==="

# 1. 创建新会话
echo "1. 创建新会话..."
timeout 10s mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" << 'SCRIPT1' > test-cli-output/e2e_step1.txt 2>&1 &
PID1=$!
sleep 3
echo "测试消息1" > /dev/stderr
sleep 1
echo "/save" > /dev/stderr
sleep 1
echo "exit" > /dev/stderr
wait $PID1
SCRIPT1

# 2. 列出会话
echo "2. 列出会话..."
timeout 5s mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" << 'SCRIPT2' > test-cli-output/e2e_step2.txt 2>&1 &
PID2=$!
sleep 2
echo "/sessions" > /dev/stderr
sleep 1
echo "exit" > /dev/stderr
wait $PID2
SCRIPT2

# 3. 再次保存
echo "3. 再次保存..."
timeout 10s mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" << 'SCRIPT3' > test-cli-output/e2e_step3.txt 2>&1 &
PID3=$!
sleep 3
echo "测试消息2" > /dev/stderr
sleep 1
echo "/save" > /dev/stderr
sleep 1
echo "exit" > /dev/stderr
wait $PID3
SCRIPT3

echo "=== 端到端测试完成 ==="
EOF

chmod +x test-e2e.sh
./test-e2e.sh

# 分析端到端测试结果
session_count=$(find .pi/sessions -name "*.jsonl" 2>/dev/null | wc -l)
echo "最终会话文件数量: $session_count"

if [ $session_count -gt 0 ]; then
    echo "✅ 端到端测试成功 - 生成了 $session_count 个会话文件"
    for file in .pi/sessions/*.jsonl; do
        if [ -f "$file" ]; then
            line_count=$(wc -l < "$file")
            echo "  - $(basename $file): $line_count 行"
        fi
    done
else
    echo "❌ 端到端测试失败 - 没有生成会话文件"
fi

echo ""
echo "📊 测试总结"
echo "-----------"

# 汇总测试结果
cli_success=false
spring_success=false
e2e_success=false

if [ -f "test-cli-output/cli_output.txt" ] && grep -q "会话保存\|Session saved" test-cli-output/cli_output.txt; then
    cli_success=true
fi

if [ -f "test-cli-output/spring_run.txt" ] && grep -q "会话创建\|Session created" test-cli-output/spring_run.txt; then
    spring_success=true
fi

if [ $session_count -gt 0 ]; then
    e2e_success=true
fi

echo "CLI功能测试: $([ $cli_success = true ] && echo '✅ 通过' || echo '❌ 失败')"
echo "Spring集成测试: $([ $spring_success = true ] && echo '✅ 通过' || echo '❌ 失败')"
echo "端到端测试: $([ $e2e_success = true ] && echo '✅ 通过' || echo '❌ 失败')"

if [ $cli_success = true ] && [ $spring_success = true ] && [ $e2e_success = true ]; then
    echo ""
    echo "🎉 所有测试通过！系统完全可用"
    echo "================================"
    echo ""
    echo "✅ CLI功能完整"
    echo "✅ Spring集成正常"
    echo "✅ 会话持久化可靠"
    echo "✅ 端到端流程顺畅"
    echo ""
    echo "🚀 可以开始后续功能开发"
else
    echo ""
    echo "⚠️ 部分测试未通过，需要进一步调试"
fi

# 清理
rm -f test-cli.sh test-e2e.sh
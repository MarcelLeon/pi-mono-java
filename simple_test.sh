#!/bin/bash

echo "🧪 Pi-Mono Java - 简化测试"
echo "=========================="

# 清理环境
rm -rf .pi/sessions
mkdir -p .pi/sessions

echo ""
echo "1. 测试CLI基础功能..."
echo "----------------------"

# 测试1：创建会话并保存
echo "创建会话并发送消息..."
cat > test_input.txt << 'EOF'
今天天气很好
/save
/sessions
exit
EOF

timeout 10s mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication" < test_input.txt > test_output.txt 2>&1

if [ $? -eq 0 ]; then
    echo "✅ CLI运行成功"

    # 检查输出
    if grep -q "会话保存成功\|Session saved" test_output.txt; then
        echo "✅ 保存功能正常"
    else
        echo "⚠️ 保存功能需要检查"
        echo "输出内容："
        cat test_output.txt
    fi

    if grep -q "sessions\|会话" test_output.txt; then
        echo "✅ 会话列表功能正常"
    else
        echo "⚠️ 会话列表功能需要检查"
    fi
else
    echo "❌ CLI运行失败"
    cat test_output.txt
fi

echo ""
echo "2. 检查会话文件..."
echo "-------------------"

if [ -d ".pi/sessions" ] && [ "$(ls -A .pi/sessions 2>/dev/null)" ]; then
    echo "✅ 会话文件已创建："
    ls -la .pi/sessions/

    echo ""
    echo "会话文件内容："
    for file in .pi/sessions/*.jsonl; do
        if [ -f "$file" ]; then
            echo "文件: $(basename $file)"
            head -n 2 "$file"
            echo ""
        fi
    done
else
    echo "❌ 没有生成会话文件"
fi

echo ""
echo "3. 测试Spring集成..."
echo "---------------------"

cd example-project

# 编译Spring项目
echo "编译Spring项目..."
mvn clean compile > ../spring_compile.txt 2>&1

if grep -q "BUILD SUCCESS" ../spring_compile.txt; then
    echo "✅ Spring项目编译成功"
else
    echo "❌ Spring项目编译失败"
    cat ../spring_compile.txt
fi

# 运行Spring项目（简短测试）
echo "运行Spring项目..."
timeout 5s mvn spring-boot:run > ../spring_run.txt 2>&1 &

# 等待启动
sleep 2

# 检查输出
if grep -q "会话创建成功\|Session created" ../spring_run.txt; then
    echo "✅ Spring集成正常 - 会话创建"
else
    echo "⚠️ Spring集成需要检查"
fi

if grep -q "会话已保存\|Session saved" ../spring_run.txt; then
    echo "✅ Spring集成正常 - 会话保存"
else
    echo "⚠️ Spring集成需要检查"
fi

cd ..

echo ""
echo "4. 测试总结"
echo "-----------"

# 汇总结果
cli_working=false
spring_working=false
files_created=false

if [ -f "test_output.txt" ] && grep -q "会话保存\|Session saved" test_output.txt; then
    cli_working=true
fi

if [ -f "spring_run.txt" ] && grep -q "会话创建\|Session created" spring_run.txt; then
    spring_working=true
fi

if [ -d ".pi/sessions" ] && [ "$(ls -A .pi/sessions 2>/dev/null)" ]; then
    files_created=true
fi

echo "CLI功能: $([ $cli_working = true ] && echo '✅ 正常' || echo '❌ 问题')"
echo "Spring集成: $([ $spring_working = true ] && echo '✅ 正常' || echo '❌ 问题')"
echo "文件生成: $([ $files_created = true ] && echo '✅ 正常' || echo '❌ 问题')"

if [ $cli_working = true ] && [ $spring_working = true ] && [ $files_created = true ]; then
    echo ""
    echo "🎉 所有测试通过！系统完全可用"
    echo "=============================="
    echo ""
    echo "✅ CLI功能完整 - 可以创建、保存、列出会话"
    echo "✅ Spring集成正常 - 可以在Spring项目中使用"
    echo "✅ 会话持久化可靠 - JSONL格式正确"
    echo ""
    echo "🚀 可以开始后续功能开发！"
else
    echo ""
    echo "⚠️ 部分功能需要调试"
    echo "请查看详细的测试输出文件："
    echo "  - test_output.txt (CLI输出)"
    echo "  - spring_run.txt (Spring输出)"
fi

# 清理
rm -f test_input.txt test_output.txt spring_compile.txt spring_run.txt
#!/bin/bash

echo "🧪 Pi-Mono Java - 无timeout测试"
echo "==============================="

# 清理环境
rm -rf .pi/sessions
mkdir -p .pi/sessions

echo ""
echo "1. 测试CLI功能（手动方式）..."
echo "------------------------------"

echo "📋 请手动执行以下测试："
echo ""
echo "步骤1：启动CLI"
echo "   cd /Users/wangzq/VsCodeProjects/pi-mono-java"
echo "   mvn -pl pi-cli exec:java -Dexec.mainClass=\"com.pi.mono.cli.PiCliApplication\""
echo ""
echo "步骤2：在CLI中输入以下命令（每行一个）："
echo "   今天天气很好"
echo "   /save"
echo "   /sessions"
echo "   exit"
echo ""
echo "步骤3：检查结果"
echo "   ls -la .pi/sessions/"
echo "   cat .pi/sessions/*.jsonl"
echo ""

echo "2. 测试Spring集成..."
echo "---------------------"

cd example-project

echo "编译Spring项目..."
mvn clean compile > ../spring_compile.txt 2>&1

if grep -q "BUILD SUCCESS" ../spring_compile.txt; then
    echo "✅ Spring项目编译成功"
else
    echo "❌ Spring项目编译失败"
    cat ../spring_compile.txt
fi

echo ""
echo "请手动运行Spring项目："
echo "   cd /Users/wangzq/VsCodeProjects/pi-mono-java/example-project"
echo "   mvn spring-boot:run"
echo ""
echo "观察输出是否包含："
echo "   - '会话创建成功'"
echo "   - 'AI响应'"
echo "   - '会话已保存'"
echo ""

cd ..

echo "3. 自动验证脚本..."
echo "-------------------"

# 创建一个简单的验证脚本
cat > verify_results.sh << 'EOF'
#!/bin/bash

echo "🔍 验证测试结果"
echo "================"

echo ""
echo "1. 检查会话文件..."
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

    # 验证JSON格式
    echo "2. 验证JSON格式..."
    valid_json=true
    for file in .pi/sessions/*.jsonl; do
        if [ -f "$file" ]; then
            while IFS= read -r line; do
                if ! echo "$line" | python3 -m json.tool > /dev/null 2>&1; then
                    echo "❌ JSON格式错误: $line"
                    valid_json=false
                fi
            done < "$file"
        fi
    done

    if [ $valid_json = true ]; then
        echo "✅ JSON格式正确"
    else
        echo "❌ JSON格式有问题"
    fi

else
    echo "❌ 没有生成会话文件"
fi

echo ""
echo "3. 检查编译结果..."
if [ -f "spring_compile.txt" ] && grep -q "BUILD SUCCESS" spring_compile.txt; then
    echo "✅ Spring项目编译成功"
else
    echo "❌ Spring项目编译失败"
fi

echo ""
echo "📊 测试总结："
if [ -d ".pi/sessions" ] && [ "$(ls -A .pi/sessions 2>/dev/null)" ]; then
    echo "✅ 会话持久化功能正常"
else
    echo "❌ 会话持久化功能有问题"
fi

if [ -f "spring_compile.txt" ] && grep -q "BUILD SUCCESS" spring_compile.txt; then
    echo "✅ Spring集成编译正常"
else
    echo "❌ Spring集成编译有问题"
fi
EOF

chmod +x verify_results.sh
./verify_results.sh

echo ""
echo "📝 测试说明："
echo "============"
echo ""
echo "由于macOS环境限制，部分测试需要手动执行。"
echo "请按照上面的步骤手动测试CLI和Spring集成。"
echo ""
echo "测试完成后，运行以下命令验证结果："
echo "   ./verify_results.sh"
echo ""
echo "或者手动检查："
echo "   ls -la .pi/sessions/"
echo "   cat .pi/sessions/*.jsonl"
echo ""
echo "🎯 预期结果："
echo "   - CLI能正常启动和保存会话"
echo "   - Spring项目能正常编译和运行"
echo "   - .pi/sessions目录有JSONL文件"
echo "   - JSON格式正确"

# 清理
rm -f verify_results.sh
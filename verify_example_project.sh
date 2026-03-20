#!/bin/bash

echo "🧪 验证 Example Project"
echo "======================"
echo ""

echo "📍 Example Project 位置:"
echo "   /Users/wangzq/VsCodeProjects/pi-mono-java/example-project/"
echo ""

echo "📋 项目结构:"
ls -la example-project/
echo ""

echo "📋 源代码结构:"
find example-project -name "*.java" -o -name "*.xml" -o -name "*.yml" | sort
echo ""

echo "📋 编译测试:"
cd example-project
mvn clean compile > ../compile_test.txt 2>&1

if grep -q "BUILD SUCCESS" ../compile_test.txt; then
    echo "✅ 编译成功"
    echo "   📦 可以通过 mvn spring-boot:run 运行"
    echo "   📊 包含完整的Spring集成示例"
else
    echo "❌ 编译失败"
    cat ../compile_test.txt
fi

cd ..

echo ""
echo "📋 配置文件内容:"
echo "1. pom.xml (依赖配置):"
head -n 15 example-project/pom.xml

echo ""
echo "2. application.yml (应用配置):"
cat example-project/src/main/resources/application.yml

echo ""
echo "3. PiAppApplication.java (主类):"
head -n 10 example-project/src/main/java/com/example/myapp/PiAppApplication.java
echo "   ... (完整代码请查看文件)"

echo ""
echo "🎯 使用方法:"
echo "   cd example-project"
echo "   mvn spring-boot:run"
echo ""
echo "💡 这是一个完整的Spring Boot示例，展示了如何在您的项目中集成Pi-Mono Java"
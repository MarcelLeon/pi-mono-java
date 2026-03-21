# Pi-Mono Java 测试资源总览

## 统一入口
- 官方脚本：`scripts/benchmark_smoke.sh`
- 结果模板：`benchmarks/RESULTS_TEMPLATE.md`
- 最新样例：`benchmarks/benchmark-latest.md`

## 核心测试资产
- `pi-session/src/test/java`: 会话管理与持久化测试。
- `spring-test-example/src/test/java`: Spring集成与端到端流程测试。
- `spring-test-example/src/test/resources/application-test.yml`: 测试专用配置。
- `spring-test-example/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`: JDK 24+ 兼容配置。

## 快速验证路径
```bash
# 1) 编译
mvn clean compile

# 2) 核心模块测试
mvn -pl pi-session test

# 3) Spring 示例测试
mvn -f spring-test-example/pom.xml test

# 4) CLI 冒烟
printf "help\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

## 说明
本仓库已移除历史上的多套根目录验收脚本，避免重复与漂移。
如需新增测试脚本，请优先扩展 `scripts/benchmark_smoke.sh`，确保文档、CI 与本地流程一致。

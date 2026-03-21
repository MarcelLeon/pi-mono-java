# Pi-Mono Java 测试指南

## 目标
本仓库以“技术服务业务”为导向，测试策略强调可复现、可快速验证、可用于发布前门禁。

## 官方测试入口（推荐）
```bash
./scripts/benchmark_smoke.sh
```
该脚本会执行编译、核心单测、Spring示例测试与CLI冒烟，并输出：
- 报告：`benchmarks/benchmark-<timestamp>.md`
- 日志：`benchmarks/logs/<timestamp>/`

## 常用本地命令
```bash
# 全量编译
mvn clean compile

# 全量测试
mvn test

# 单模块测试（示例）
mvn -pl pi-session -am test

# Spring 集成示例
mvn -f spring-test-example/pom.xml test
mvn -f spring-test-example/pom.xml spring-boot:run

# CLI 冒烟
printf "help\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

## 发布前最小验收清单
- `mvn clean compile` 通过。
- `mvn test` 通过（或有明确跳过说明）。
- `./scripts/benchmark_smoke.sh` 通过并产出报告。
- README 与 Quickstart 的命令可执行且与代码一致。

## 常见问题
- JDK 24+ 下 Mockito 可能出现 attach 警告；当前测试资源已采用非 inline mock maker 以保证可运行。
- 若 Spring 启动失败，请确认 `pi-starter` 在聚合构建中已编译，并优先使用 `spring-test-example` 复现。

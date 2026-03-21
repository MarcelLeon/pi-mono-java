# Pi-Mono Java 发布验收清单

## 1. 环境
- [ ] Java 21+
- [ ] Maven 3.8+
- [ ] `mvn -version` 正常

## 2. 构建与测试
- [ ] `mvn clean compile`
- [ ] `mvn test`
- [ ] `./scripts/benchmark_smoke.sh`

## 3. 关键能力验证
- [ ] `SessionManager` 在 `spring-test-example` 中可注入
- [ ] CLI 可启动并执行 `help/exit`
- [ ] 会话持久化（`pi-session`）测试通过
- [ ] OpenAI provider 在禁用/启用配置下行为符合预期

## 4. 文档一致性
- [ ] `README.md` 快速验证命令可执行
- [ ] `docs/quickstart.zh-CN.md` 与 `docs/quickstart.en.md` 同步
- [ ] `docs/capability-comparison.md` 与当前能力一致
- [ ] 不存在失效脚本引用

## 5. 开源治理文件
- [ ] `LICENSE`
- [ ] `CONTRIBUTING.md`
- [ ] `CODE_OF_CONDUCT.md`
- [ ] `SECURITY.md`
- [ ] `AGENTS.md`

## 6. 产出物
- [ ] 生成并保留最新 `benchmarks/benchmark-latest.md`
- [ ] 关键日志可在 `benchmarks/logs/` 追溯

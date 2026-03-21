# Pi-Mono Java

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

Java/Spring implementation inspired by open-source `pi-mono` (TypeScript).  
Goal: help Java teams adopt pi-mono ideas without TypeScript barriers.

## Why This Project / 为什么做这个项目

Many teams run Java/Spring in production but want pi-mono’s lightweight agent loop, session model, and tool orchestration.

本项目聚焦“技术服务业务”：
- 为 Java/Spring 项目提供可落地的 pi-mono 风格实现；
- 降低 TS 语言门槛带来的学习和接入成本；
- 给自研 Agent / LangGraph / 对话平台提供可参考的工程实现（即使不直接使用本项目）。

## What You Get

### For Java/Spring users
- Spring Boot starter (`pi-starter`) for low-friction integration.
- Typed core abstractions (`LLMProvider`, `ChatRequest`, `SessionNode`).
- Session tree + JSONL persistence for reproducible conversation traces.
- Built-in tool framework with permission boundaries (`read/write/system`).

### For existing agent platforms (Agent/LangGraph/chat platform)
- A compact reference of runtime concerns:
  - model routing + fallback,
  - session persistence,
  - tool invocation controls,
  - starter-based embedding into business apps.
- Can be used as architecture reference even if you keep your own runtime.

## Capability Alignment vs pi-mono (TypeScript)

Reference upstream: [badlogic/pi-mono](https://github.com/badlogic/pi-mono)

| Area | Upstream TS (`pi-mono`) | This Java repo | Alignment |
|---|---|---|---|
| Core model/provider abstraction | `packages/ai`, `packages/agent` | `pi-core`, `pi-llm` | Mostly aligned |
| Session memory/persistence | `packages/mom` | `pi-session` (tree + JSONL) | Mostly aligned |
| Tool runtime | `packages/pods` | `pi-tools` (read/write/edit/bash/find/grep/ls) | Partially aligned |
| CLI agent workflow | `packages/coding-agent` | `pi-cli` | Partially aligned |
| Web UI / rich TUI ecosystem | `packages/web-ui`, `packages/tui` | minimal / in progress | Not aligned yet |

Detailed notes: [docs/capability-comparison.md](./docs/capability-comparison.md)

## Java vs TypeScript (Humble View)

### Java version strengths
- Better fit for Spring-centric production systems.
- Strong static typing and mature enterprise ops ecosystem.
- Easier embedding into existing Java services.

### TypeScript version strengths
- Faster upstream feature evolution.
- Richer existing UX/tooling in original project.
- Better choice if your stack is already Node.js-first.

## Quick Verification (3-5 minutes)

```bash
# 1) Build
mvn clean compile

# 2) Spring integration smoke test
cd spring-test-example
mvn spring-boot:run
# Expect: "所有Spring集成测试通过！"

# 3) CLI smoke test (optional)
cd ..
printf "help\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

Quickstart docs:
- 中文: [docs/quickstart.zh-CN.md](./docs/quickstart.zh-CN.md)
- English: [docs/quickstart.en.md](./docs/quickstart.en.md)

## Open-Source Readiness Checklist

- [x] Bilingual entry docs (README + Quickstart CN/EN)
- [x] Fast verification path (`compile` + `spring-test-example`)
- [x] Capability alignment snapshot vs upstream TS pi-mono
- [x] Evidence section with runnable commands and sample results
- [x] Reproducible smoke benchmark script and result template
- [ ] More comprehensive benchmark suite (cross-machine, cross-JDK, repeated runs)
- [x] Remove `System.exit(0)` from test sample runner and stabilize `mvn test`

## Evidence (Benchmarks / Test Records)

Latest local verification sample (2026-03-21):
- `mvn -pl pi-session test`: pass (session persistence unit test).
- `mvn -f spring-test-example/pom.xml spring-boot:run`: pass.
- Spring sample startup log: `Started SpringTestApplication in 0.661 seconds` (single-machine sample, non-benchmark-lab).
- Reproducible smoke benchmark: `./scripts/benchmark_smoke.sh`
  - result template: `benchmarks/RESULTS_TEMPLATE.md`
  - latest sample report: `benchmarks/benchmark-latest.md`

Known testing caveat:
- On JDK 24+, you may still see Mockito attach warnings in test logs.  
  `spring-test-example` test resources already force a non-inline mock maker to keep `mvn test` runnable.

## Project Structure

```
pi-core/      core contracts and models
pi-llm/       provider implementations and routing
pi-session/   session tree and persistence
pi-tools/     tool definitions and permissions
pi-cli/       CLI entrypoint
pi-starter/   Spring Boot auto-configuration
example-project/      sample integration app
spring-test-example/  quick verification app
```

## License

MIT

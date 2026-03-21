# Capability Alignment: pi-mono (TS) vs pi-mono-java

This document is intentionally conservative.  
Upstream reference: [badlogic/pi-mono](https://github.com/badlogic/pi-mono)

## Snapshot (2026-03-21)

| Capability Area | Upstream TS package(s) | Java module(s) | Alignment |
|---|---|---|---|
| Model/provider abstraction | `packages/ai`, `packages/agent` | `pi-core`, `pi-llm` | Mostly aligned |
| Session memory + persistence | `packages/mom` | `pi-session` | Mostly aligned |
| Tool execution runtime | `packages/pods` | `pi-tools` | Partially aligned |
| CLI coding-agent loop | `packages/coding-agent` | `pi-cli` | Partially aligned |
| Starter-style embedding | n/a (TS-first runtime) | `pi-starter` | Java-specific value |
| Rich web/tui user experience | `packages/web-ui`, `packages/tui` | limited/in progress | Not aligned yet |

## What Is Already Practical in Java

- Embed into Spring Boot apps with starter-based wiring.
- Keep conversation trace via session tree + JSONL persistence.
- Use built-in tools with permission boundaries.
- Route model calls through provider manager with fallback behavior.

## What Is Not Yet at Upstream Parity

- Full UX parity with upstream coding-agent/web-ui/tui experience.
- Same breadth and maturity of ecosystem integrations.
- Broader production hardening coverage (observability/ops/test matrix).

## Java vs TypeScript: Tradeoff Notes

### Java advantages (for this repo)
- Better fit for existing Spring-based business systems.
- Strong typing and conventional enterprise integration path.
- Easier adoption for Java-only teams.

### TypeScript advantages (upstream)
- Faster feature velocity in original ecosystem.
- Richer existing user-facing workflows.
- Better default if your platform is Node.js-native.

## Validation Pointers

- Build: `mvn clean compile`
- Session unit test: `mvn -pl pi-session test`
- Spring smoke run: `mvn -f spring-test-example/pom.xml spring-boot:run`

For latest runnable entrypoints, check:
- [README.md](../README.md)
- [quickstart.zh-CN.md](./quickstart.zh-CN.md)
- [quickstart.en.md](./quickstart.en.md)

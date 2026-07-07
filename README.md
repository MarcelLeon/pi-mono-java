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

## Capability Alignment vs Pi (TypeScript)

Reference upstream: [earendil-works/pi](https://github.com/earendil-works/pi)
Latest checked release: `v0.80.3` (2026-06-30).
Latest checked main branch also includes post-release coding-agent/AI fixes through 2026-07-07 such as failing tool calls from length-truncated assistant messages, empty tool-output placeholders for provider payloads, null message-content normalization, OpenAI Responses max-output-token floor clamping, Cloudflare 524 retry classification, OpenAI Codex WebSocket connection rotation, DS4 context-overflow detection, split-turn compaction summary serialization, delayed Copilot device-code token polling, entry renderers, short session entry ids from generated id random tails, model metadata cleanup, stricter bash timeout validation, sequential question-tool execution metadata, Bedrock Claude 5 prompt caching, and Codex SSE transport updates.

| Area | Upstream TS (`pi-mono`) | This Java repo | Alignment |
|---|---|---|---|
| Core model/provider abstraction | `packages/ai`, `packages/agent` | `pi-core`, `pi-llm` (`AgentMessage` null content normalization, `gpt-5.5` default, model-resolution helpers, request-scoped OpenAI/Anthropic/Bedrock auth/options, Azure Foundry endpoint normalization, HTTP error response bodies, retryable provider errors, GitHub Copilot OAuth device-flow client foundation, OAuth device-code polling interval handling, OpenAI output-token floor clamping, OpenAI-compatible HTTP 524 retry classification, OpenAI-compatible DS4 context-overflow classification, OpenAI `image_url`, Anthropic image, and Bedrock image attachment content parts, OpenAI/Anthropic/Bedrock non-streaming tool-use metadata, provider-native tool-result blocks, and `(no tool output)` placeholders for empty tool results, Anthropic/Bedrock thinking content metadata, Anthropic Messages API with Claude 5 thinking, Anthropic-compatible + Bedrock Claude Sonnet 5 catalogs and SigV4-signed non-streaming Bedrock invoke payloads) | Partially aligned |
| Session tree + JSONL persistence | `packages/agent` session storage, reasoning usage metadata, invalid-session overwrite protection, `session_info_changed` notification, split-turn compaction summary serialization | `pi-session` (restore/resume/fork/import/export, nested usage/reasoning token metadata, invalid non-empty JSONL overwrite protection, deterministic startup session ids, rename metadata event source, bounded non-streaming tool result continuation, length-truncated assistant tool-call failure handling, serialized split-turn summary orchestration) | Mostly aligned for Java use |
| Tool runtime | built-in `read/write/edit/bash/grep/find/ls`, BMP image handling | `pi-tools` (read/write/edit/bash/find/grep/ls, BMP-to-PNG payloads in `read`, strict 1-60s bash timeout validation, sequential `executionMode` tool metadata, session-level multi-round tool-call execution into `TOOL_RESULT`) | Partially aligned |
| CLI agent workflow | `packages/coding-agent` | `pi-cli` (`--no-session`, `--session-id`, `@file` attachment expansion, improved session/model/resource commands) | Partially aligned |
| Context files, prompts, skills | `coding-agent` resource loader | `pi-cli` (`AGENTS.md`/`CLAUDE.md`, `.pi/prompts`, `.pi/skills`, `.agents/skills`, prompt expansion, basic `/skill:name`) | Partially aligned |
| CLI settings | `settings.json` (`outputPad`, `externalEditor`) | `pi-cli` (`outputPad` for user/assistant/thinking lines, `/edit` via configured `externalEditor`) | Partially aligned |
| Project trust | `coding-agent` trust manager | `pi-cli` (`/trust`, trust-aware local resource loading) | Partially aligned |
| RPC session inspection | `rpc-entry`, `get_entries`, `get_tree`, entry renderers, tail-derived short entry ids | `pi-cli --rpc` JSONL (`get_entries`, `get_tree`, rendered entry summaries, `shortId` from full-id random tails) | Minimally aligned |
| TUI / extension / package ecosystem | `packages/tui`, `coding-agent` extensions, themes, packages | documented gap | Not aligned yet |

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

# 2) Session restore/resume/fork tests
mvn -pl pi-session -am test -Dtest=SessionPersistenceUnitTest

# 3) Spring integration smoke test
cd spring-test-example
mvn spring-boot:run
# Expect: "所有Spring集成测试通过！"

# 4) CLI smoke test (optional)
cd ..
printf "help\n/session\n/models\n/resources\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

CLI commands accept both the original bare forms and the slash aliases for common operator commands:
- `help` or `/help`
- `sessions` or `/sessions`
- `tools` or `/tools`
- `perm list` or `/perm list`
- `tool read path=README.md` or `/tool read path=README.md`

### Optional: dogfood through an external CLI

The default local model is still `mock-claude`. To route a session through a local command such as Claude Code CLI or Codex CLI, enable the opt-in external CLI provider and create a session with `external-cli`:

```bash
mvn -pl pi-cli -DskipTests \
  -Dpi.llm.external-cli.enabled=true \
  -Dpi.llm.external-cli.command="claude -p" \
  -Dpi.llm.external-cli.model-id=opus-4-7 \
  exec:java
```

Codex CLI equivalent:

```bash
mvn -pl pi-cli -DskipTests \
  -Dpi.llm.external-cli.enabled=true \
  -Dpi.llm.external-cli.command="codex exec -" \
  -Dpi.llm.external-cli.model-id=gpt-5 \
  exec:java
```

When `pi.llm.default-model` is not set, enabling `external-cli` makes the startup session use `pi.llm.external-cli.model-id` as the model. For known local CLIs, Pi-Mono Java also passes that model to the child process:
- `claude -p` becomes `claude -p --model <model-id>`
- `codex exec -` becomes `codex exec -m <model-id> -`
- other commands can use an explicit `{model}` placeholder, for example `custom-ai --model {model}`

Inside the CLI:

```text
/models
/session
请基于 @README.md 总结这个项目给一个 Spring 团队的接入价值
```

The configured external command must read the prompt from stdin and write the final answer to stdout.

### Optional: dogfood through the real Anthropic-compatible API

To verify the Java provider path instead of shelling out to a local CLI, enable `pi.llm.anthropic` and keep `pi.llm.external-cli.enabled` unset or false. The provider can read the same Claude-compatible environment used by Claude Code CLI:

- `ANTHROPIC_BASE_URL`
- `ANTHROPIC_AUTH_TOKEN`
- `ANTHROPIC_CUSTOM_HEADERS`
- `ANTHROPIC_API_KEY` for the official API-key flow

API-mode CLI smoke:

```bash
source ~/.zshrc

printf "/models\n/session\n请只回复一行：API_OK。\nexit\n" | \
  mvn -q -pl pi-cli -DskipTests -Djava.version=17 \
    -Dpi.llm.anthropic.enabled=true \
    -Dpi.llm.default-model=opus-4-7 \
    exec:java
```

Expected evidence:
- `/models` shows `Provider: anthropic-claude-sonnet-5 (available=true)`.
- `/models` lists `opus-4-7` and `pa/claude-opus-4-7`.
- `/session` shows `Model: opus-4-7`.
- The assistant replies with a real provider response instead of `mock` or `Provider error ... fallback`.
- Debug logs may show `Received Anthropic message response`.

There is also a live smoke test that is skipped unless explicitly enabled:

```bash
source ~/.zshrc
mvn -q -pl pi-llm -Dtest=AnthropicLiveSmokeTest \
  -Dpi.live.anthropic=true \
  -Dpi.llm.anthropic.model=opus-4-7 \
  -Djava.version=17 test
```

### Dogfood acceptance matrix

| Mode | Purpose | Command flags | Expected proof | Failure signal |
|---|---|---|---|---|
| Real API provider | Verify Java/Spring harness owns the provider call | `-Dpi.llm.anthropic.enabled=true -Dpi.llm.default-model=opus-4-7` | `/models` lists `anthropic-*` as available; `/session` model is `opus-4-7`; answer is real API text such as `API_OK` | `Provider error ... fallback`, only `mock-*` models, or `/session` model is `mock-claude` |
| External CLI bridge | Verify local Claude Code/Codex CLI delegation | `-Dpi.llm.external-cli.enabled=true -Dpi.llm.external-cli.command="claude -p" -Dpi.llm.external-cli.model-id=opus-4-7` | `/models` lists `Provider: external-cli`; `/session` model is `opus-4-7`; answer comes from local CLI stdout | `mock provider` answer, missing `external-cli`, or local CLI timeout/auth error |

Current known gaps after these two dogfood paths:
- Anthropic-compatible API streaming is still not implemented in Java; current smoke is non-streaming `/messages`.
- Provider-native tool-use is parsed, but full multi-round tool execution against live Anthropic should get a dedicated live test.
- Model catalog is pragmatic and proxy-aware; it is not yet auto-discovered from a provider model endpoint.
- Java target is 21+, but local verification on this machine has mostly used `-Djava.version=17` as a compatibility smoke.

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
- [x] Restore saved JSONL sessions and expose resume/tree/fork/import/export CLI commands
- [x] Preserve nested usage metadata such as `usage.reasoningTokens` across JSONL save/load
- [x] Serialize split-turn compaction summary requests so single-concurrency providers are not called concurrently
- [x] Support deterministic startup session ids with `--session-id` and ephemeral runs with `--no-session`
- [x] Reject overwriting non-empty invalid JSONL session files
- [x] Discover context files, prompt templates, and skills in the CLI startup path
- [x] Gate project-local prompt/skill resources behind a `/trust` decision
- [x] Expand trusted prompt templates and inject basic `/skill:name` instructions into CLI conversations
- [x] Expose minimal `--rpc` JSONL session inspection for `get_entries` and `get_tree`
- [x] Include rendered titles/plain-text/Markdown summaries in RPC `get_entries` output
- [x] Expose tail-derived `shortId` fields in RPC session entries and tree nodes
- [x] Resolve preferred LLM providers by model id and skip unavailable defaults
- [x] Expose model-resolution helpers for available model catalogs and exact model-to-provider lookup
- [x] Add OAuth device-code polling helper and a minimal GitHub Copilot device-flow client that delay the first token poll and honor server-provided `slow_down` intervals
- [x] Align OpenAI default model to `gpt-5.5` and normalize Azure Foundry/OpenAI base URLs
- [x] Honor request-scoped OpenAI API key, `OPENAI_API_KEY` env override, model, temperature, and max-token options
- [x] Parse OpenAI token usage metadata including reasoning tokens
- [x] Preserve provider HTTP response bodies in OpenAI error messages
- [x] Retry OpenAI provider errors whose response body explicitly asks callers to retry
- [x] Retry OpenAI-compatible HTTP 524 timeout responses
- [x] Clamp OpenAI request output tokens below the provider minimum to 16
- [x] Classify OpenAI-compatible DS4 context-overflow errors from provider response bodies
- [x] Surface incomplete OpenAI responses when output stops at the token limit
- [x] Convert CLI image attachments into OpenAI non-streaming `image_url` content parts
- [x] Send OpenAI non-streaming tool schemas and parse `tool_calls` into assistant message metadata
- [x] Add Anthropic-compatible Claude Sonnet 5 provider catalog entry
- [x] Send non-streaming Anthropic Messages API requests with Claude Sonnet 5 thinking payloads and parse text/usage/thinking responses
- [x] Honor request-scoped Anthropic API key and `ANTHROPIC_API_KEY` env override
- [x] Convert CLI image attachments into Anthropic non-streaming image content blocks
- [x] Send Anthropic non-streaming tool schemas and parse `tool_use` blocks into assistant message metadata
- [x] Mark exported Java tool schemas with sequential `executionMode`
- [x] Execute non-streaming provider `toolCalls` through Java tools, append provider-native `TOOL_RESULT` payloads, and continue bounded multi-round provider turns
- [x] Fail tool calls from length-truncated assistant messages with error `TOOL_RESULT` payloads and continue so the model can re-issue complete calls
- [x] Send SigV4-signed non-streaming Bedrock Anthropic invoke requests, including Claude Sonnet 5 thinking and prompt-cache payload blocks, and parse text/usage/thinking responses
- [x] Convert CLI image attachments into Bedrock non-streaming image content blocks
- [x] Send Bedrock non-streaming tool schemas and parse `tool_use` blocks into assistant message metadata
- [x] Resolve Bedrock credentials from explicit properties, a selected AWS shared credentials profile, or AWS environment variables
- [x] Honor request-scoped Bedrock AWS env credentials (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optional `AWS_SESSION_TOKEN`) or scoped profile credentials (`AWS_PROFILE`, optional `AWS_SHARED_CREDENTIALS_FILE`) for SigV4 signing
- [x] Load CLI `settings.json`, apply `outputPad` to user/assistant/thinking lines, and compose with configured `externalEditor` through `/edit`
- [x] Emit Java-side `session_info_changed` metadata events on session rename
- [x] Reject non-positive and oversized bash timeouts instead of silently clamping
- [x] Detect BMP files in `read` and convert them to PNG data URLs
- [x] Expand CLI `@file` references into attached file blocks before sending messages

## Evidence (Benchmarks / Test Records)

Latest local verification sample:
- `mvn -pl pi-core test -Dtest=AgentSessionTest -Djava.version=17`: pass (`AgentMessage` normalizes null content to an empty string and null metadata to an empty map at construction time).
- `mvn -pl pi-session -am test -Dtest=SessionPersistenceUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (session save/restore/load/fork, nested usage/reasoning token metadata, invalid non-empty session overwrite protection, deterministic session id, `session_info_changed` rename event, sequential tool `executionMode` metadata, bounded multi-round non-streaming tool-call execution/`TOOL_RESULT` continuation, and length-truncated assistant tool-call failure tests).
- `mvn -pl pi-session -am test -Dtest=SessionCompactionServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (split-turn compaction summary requests are serialized before merging history and turn-prefix summaries).
- `mvn -pl pi-tools -am test -Dtest=BashToolTest,ReadFileToolTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (bash timeout validation and BMP-to-PNG `read` tests).
- `mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (resource discovery tests).
- `mvn -pl pi-cli -am test -Dtest=PiTrustManagerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (project trust tests).
- `mvn -pl pi-cli -am test -Dtest=PiResourceCommandResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (prompt template and basic skill invocation tests).
- `mvn -pl pi-cli -am test -Dtest=PiFileReferenceResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (CLI `@file` attachment expansion, including BMP-to-PNG payloads).
- `mvn -pl pi-cli -am test -Dtest=PiRpcCommandHandlerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (minimal RPC `get_entries`/`get_tree` tests, including rendered entry summaries and tail-derived `shortId` fields).
- `mvn -pl pi-cli -am test -Dtest=PiCliStartupOptionsTest,PiCliSettingsLoaderTest,PiCliOutputFormatterTest,PiExternalEditorTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (CLI startup args, settings loading, user/assistant/thinking output padding, and external editor command runner tests).
- `mvn -pl pi-llm -am test -Dtest=GitHubCopilotOAuthClientTest,OAuthDeviceCodePollerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (GitHub Copilot OAuth device-flow startup, delayed first token poll, RFC +5s `slow_down`, and server-provided `slow_down` interval handling through a deterministic fake transport).
- `mvn -pl pi-llm -am test -Dtest=LLMProviderManagerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (model-id provider resolution, unavailable default-provider skip, available model catalog, and exact model-to-provider helper tests).
- `mvn -pl pi-llm -am test -Dtest=OpenAIClientTest,OpenAIConfigTest,OpenAILLMProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (OpenAI HTTP error body, retryable provider errors, HTTP 524 timeout retry classification, DS4 context-overflow classification, request output-token floor clamping, default model, request-scoped API key/env/model/options, Azure Foundry endpoint, response content parsing, usage/reasoning token metadata, incomplete output-length response, OpenAI image attachment content parts, non-streaming tool schema request, `tool_calls` metadata parsing, and `(no tool output)` placeholder for empty tool result payloads).
- `mvn -pl pi-llm -am test -Dtest=AnthropicClientTest,AnthropicConfigTest,AnthropicLLMProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (Anthropic-compatible Claude Sonnet 5 catalog, request-scoped API key/env auth, Messages API request headers/body, Claude 5 thinking payload, image attachment content blocks, non-streaming tool schema request without internal execution metadata leakage, `tool_use` metadata parsing, provider-native `tool_result` content blocks including empty-result placeholders, HTTP error body, response content, thinking content, and usage metadata tests).
- `mvn -pl pi-llm -am test -Dtest=BedrockClientTest,BedrockConfigTest,BedrockLLMProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`: pass (Bedrock Claude Sonnet 5 catalog, SigV4 signing from explicit, shared-profile, environment, request-scoped AWS credentials, or request-scoped AWS profile credentials, non-streaming invoke payload, Claude 5 thinking payload, prompt-cache block, image attachment content blocks, non-streaming tool schema request without internal execution metadata leakage, `tool_use` metadata parsing, provider-native `tool_result` content blocks including empty-result placeholders, HTTP error body, response content, thinking content, usage, and region metadata tests).
- `printf "help\n/resources\n/prompts\n/skills\n/session\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 exec:java`: pass (resource command smoke).
- `printf "help\n/resources\n/trust\n/resources\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 -Dpi.trust.file=target/trust-smoke.txt exec:java`: pass (trust command smoke).
- From `target/pi-cli-resource-smoke` with a local `.pi/prompts/review.md` and `.pi/skills/deploy/SKILL.md` fixture, `printf "/resources\n/trust\n/prompts\n/skills\n/review target=api focus=security\n/skill:deploy release service\nexit\n" | mvn -f ../../pom.xml -pl pi-cli -DskipTests -Djava.version=17 -Dpi.trust.file=trust-final.txt exec:java`: pass (trusted prompt/skill command smoke).
- From `target/pi-cli-settings-smoke` with a local `.pi/settings.json` fixture, `printf "/settings\n/trust\n/settings\nhello settings\nexit\n" | mvn -f ../../pom.xml -pl pi-cli -DskipTests -Djava.version=17 -Dpi.trust.file=trust-settings.txt exec:java`: pass (trusted settings and `outputPad` smoke).
- From `target/pi-cli-edit-smoke` with a fake external editor script in trusted `.pi/settings.json`, `printf "/trust\n/edit\nexit\n" | mvn -f ../../pom.xml -pl pi-cli -DskipTests -Djava.version=17 -Dpi.trust.file=trust-edit.txt exec:java`: pass after installing updated local snapshots (`/edit` sends the editor-written message).
- `printf '{"id":1,"method":"get_tree"}\n' | mvn -pl pi-cli -DskipTests -Djava.version=17 -Dexec.args=--rpc exec:java`: pass (minimal JSONL RPC tree smoke).
- `printf "/session\n/save\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 -Dexec.args="--no-session --session-id stable-cache-id" exec:java`: pass after installing updated local snapshots (deterministic ephemeral startup session id and disabled persistence smoke).
- `printf "/rename Roadmap Review\n/session\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 exec:java`: pass after installing updated local snapshots (CLI rename emits `session_info_changed` and shows the session name).
- `printf "/models\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 -Dpi.llm.anthropic.enabled=true -Dpi.llm.anthropic.api-key=test-key exec:java`: pass after installing updated `pi-core`/`pi-llm` artifacts (Anthropic-compatible catalog smoke).
- `printf "/models\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 -Dpi.llm.bedrock.enabled=true -Dpi.llm.bedrock.region=us-west-2 exec:java`: pass after installing updated `pi-core`/`pi-llm` artifacts (Bedrock catalog smoke).

Bedrock runtime credential notes:
- Explicit Spring properties: `pi.llm.bedrock.access-key-id`, `pi.llm.bedrock.secret-access-key`, and optional `pi.llm.bedrock.session-token`.
- Shared profile fallback: `pi.llm.bedrock.profile` or `AWS_PROFILE`, with `pi.llm.bedrock.credentials-file` or `AWS_SHARED_CREDENTIALS_FILE`; otherwise `~/.aws/credentials`.
- Environment fallback: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and optional `AWS_SESSION_TOKEN`.
- Per-request `ChatOptions.env()` override: either `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and optional `AWS_SESSION_TOKEN`; or `AWS_PROFILE` with optional `AWS_SHARED_CREDENTIALS_FILE`.
- Do not commit real AWS keys; use environment or secret-managed configuration for real runtime calls.
- `mvn -f spring-test-example/pom.xml spring-boot:run`: pass.
- Spring sample startup log: `Started SpringTestApplication in 0.661 seconds` (single-machine sample, non-benchmark-lab).
- Reproducible smoke benchmark: `./scripts/benchmark_smoke.sh`
  - result template: `benchmarks/RESULTS_TEMPLATE.md`
  - latest sample report: `benchmarks/benchmark-latest.md`

Known testing caveat:
- This repo targets Java 21+. If the local machine only has JDK 17, use `-Djava.version=17` only as a compatibility smoke check, not as the canonical release gate.
- `spring-test-example` is a standalone Maven project. On JDK 17, first install the current local snapshots with `mvn -pl pi-starter -am install -DskipTests -Djava.version=17`, then run `mvn -f spring-test-example/pom.xml test`.
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

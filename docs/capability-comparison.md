# Capability Alignment: Pi TypeScript vs Pi-Mono Java

This document is intentionally conservative. It tracks the Java/Spring implementation against the current upstream Pi project without claiming full UX or ecosystem parity.

Upstream reference: [earendil-works/pi](https://github.com/earendil-works/pi)
Latest checked release: `v0.80.3` (2026-06-30).
Latest checked main branch: post-`v0.80.3` commits through 2026-07-06, including OpenAI Responses max-output-token floor clamping, Cloudflare 524 retry classification, OpenAI Codex WebSocket connection rotation, DS4 context-overflow detection, split-turn compaction summary serialization, delayed Copilot device-code token polling, entry renderers, short session entry ids from generated id random tails, model metadata cleanup, stricter bash timeout validation, sequential question-tool execution metadata, Bedrock Claude 5 prompt caching, AWS profile-aware Bedrock fixes, model-resolution helpers, default-model auth hardening, context-hook aborts, and Codex SSE transport changes.

## Latest Upstream Release Delta

The latest checked upstream release adds Claude Sonnet 5 provider catalog support, configurable `outputPad`, external editor settings, richer RPC session tree access (`get_entries`, `get_tree`), extension session metadata notifications, provider HTTP error response bodies, reasoning token usage counts, deterministic `--no-session --session-id` behavior, invalid session-file overwrite protection, BMP image handling, visible incomplete-response errors when assistant output stops at the token limit, retry for provider stream errors that explicitly ask callers to retry, and modern Azure Foundry endpoint support. Post-release main also enables Bedrock prompt caching for Claude 5, includes AWS profile-aware Bedrock fixes, exposes model-resolution helpers, skips unauthenticated default models, marks question-tool execution sequentially, adds entry renderers for session entries, derives short session entry ids from generated id random tails, serializes split-turn compaction summaries, delays Copilot device-code token polling, clamps OpenAI Responses max-output-token values below the provider minimum, improves Cloudflare 524 retry classification, rotates long-running OpenAI Codex WebSocket connections, detects DS4 context overflow errors, and updates Codex SSE transport behavior. The Java implementation now covers the session-tree inspection idea through in-process APIs, CLI tree/export commands, and a minimal `pi-cli --rpc` JSONL adapter for `get_entries`/`get_tree` with rendered entry summaries plus tail-derived `shortId` fields; it also serializes split-turn compaction summary requests before merging history and turn-prefix summaries, resolves preferred LLM providers by provider id or model id while skipping unavailable providers, exposes available-model catalog and exact model-to-provider helpers, aligns the OpenAI default model to `gpt-5.5`, honors request-scoped OpenAI API keys, `OPENAI_API_KEY` env overrides, model/temperature/max-token options, clamps OpenAI request output-token values below 16, retries OpenAI-compatible HTTP 524 timeout responses, classifies OpenAI-compatible DS4 context-overflow response bodies, parses OpenAI response content instead of returning placeholders, maps OpenAI token usage into `usage.inputTokens`, `usage.outputTokens`, `usage.totalTokens`, and `usage.reasoningTokens`, surfaces `finish_reason=length` as an incomplete-response marker with metadata, normalizes Azure Foundry/OpenAI base URLs to `/openai/v1`, preserves OpenAI HTTP error response bodies, retries OpenAI provider errors whose response body explicitly asks callers to retry, converts CLI image attachments into OpenAI non-streaming `image_url` content parts, sends OpenAI non-streaming tool schemas and parses `tool_calls` into unified `metadata.toolCalls`, sends non-streaming Anthropic Messages API requests with request-scoped `apiKey`/`ANTHROPIC_API_KEY` auth, Claude 5 thinking payloads, image content blocks for CLI image attachments, non-streaming tool schemas, parsed text/usage/thinking responses, parsed `tool_use` metadata, and provider-native `tool_result` blocks, exports Java tools with sequential `executionMode` metadata, executes provider-returned `toolCalls` through Java tools, appends provider-native `TOOL_RESULT` messages, and performs bounded multi-round non-streaming continuation turns, sends SigV4-signed non-streaming Bedrock Anthropic invoke payloads with Claude 5 thinking, prompt-cache blocks, image content blocks for CLI image attachments, non-streaming tool schemas, parsed text/usage/thinking responses, parsed `tool_use` metadata, and provider-native `tool_result` blocks, resolves Bedrock runtime credentials from explicit properties, selected AWS shared credentials profiles, AWS environment variables, request-scoped `ChatOptions.env()` AWS keys, or request-scoped `AWS_PROFILE` plus optional `AWS_SHARED_CREDENTIALS_FILE`, preserves nested `usage.reasoningTokens` metadata across JSONL save/load, applies loaded `outputPad` to CLI user, assistant, and thinking lines, composes messages with configured `externalEditor` through `/edit`, supports deterministic startup session ids and ephemeral CLI runs, rejects overwriting non-empty invalid JSONL session files, converts BMP files to PNG data URLs in the `read` tool, expands CLI `@file` references into attached file blocks before sending messages, exposes Anthropic-compatible plus Bedrock Claude Sonnet 5 catalog entries, emits a Java-side `session_info_changed` metadata event when a session is renamed, and rejects non-positive or oversized bash timeouts in line with the latest checked main-branch hardening. It does not yet implement Copilot auth/device-code flows, full automatic compaction and compaction-trigger/session rewriting, Anthropic streaming tool-use, Bedrock streaming transports, Codex WebSocket connection rotation, the broader upstream RPC surface, provider-native multimodal request parts beyond current OpenAI, Anthropic, and Bedrock image attachments, upstream's exact external-editor keybinding/TUI behavior, Codex SSE transport compression, or extension-runtime event delivery.

## Snapshot

| Capability Area | Upstream TS package(s) | Java module(s) | Alignment | Notes |
|---|---|---|---|---|
| Unified LLM API and providers | `packages/ai` | `pi-core`, `pi-llm` | Partial | Java has typed provider contracts, OpenAI/mock providers, model-id provider resolution with unavailable-provider skipping, available-model catalog and exact model-to-provider helpers, upstream `gpt-5.5` default-model alignment, request-scoped OpenAI API key/env/model/temperature/max-token options, OpenAI request output-token floor clamping, OpenAI-compatible HTTP 524 retry classification, OpenAI-compatible DS4 context-overflow classification, request-scoped Anthropic API key/env auth, request-scoped Bedrock AWS key/profile env auth, Azure Foundry/OpenAI base URL normalization, OpenAI response content parsing, OpenAI usage/reasoning token metadata, incomplete output-length markers, OpenAI HTTP error response-body preservation, explicit retry-instruction handling, OpenAI non-streaming image attachment content parts, non-streaming OpenAI tool schema and `tool_calls` metadata support, non-streaming Anthropic Messages API requests/responses with Claude 5 thinking payloads, image attachment content blocks, tool-use metadata, parsed thinking content metadata, and provider-native tool-result blocks, SigV4-signed non-streaming Bedrock Anthropic invoke payloads with Claude Sonnet 5 thinking, prompt-cache blocks, image attachment content blocks, tool-use metadata, parsed thinking content metadata, provider-native tool-result blocks, and Bedrock credentials from explicit config, shared profiles, global AWS env vars, request-scoped AWS key env vars, or request-scoped AWS profile env vars. It does not yet include upstream's broad provider/OAuth/model registry, Anthropic streaming tool-use, streaming tool-result orchestration, or real Bedrock streaming transport parity. |
| Agent runtime and state | `packages/agent` | `pi-core`, `pi-session` | Mostly aligned for session basics | Java supports session trees, JSONL persistence, nested usage/reasoning token metadata, invalid non-empty JSONL overwrite protection, restore, resume, fork, import, export, deterministic startup session ids, session rename metadata, a subscribable `session_info_changed` event source, a tested bounded non-streaming tool-call/result continuation path, and serialized split-turn compaction summary orchestration. |
| Tool execution | `packages/coding-agent` built-ins | `pi-tools` | Partial | Java has read/write/edit/bash/find/grep/ls, permission categories, BMP-to-PNG `read` payloads, strict 1-60 second bash timeout validation, sequential `executionMode` metadata on exported tool schemas, and session-level multi-round execution of provider-returned `toolCalls` into `TOOL_RESULT` messages. It does not yet mirror extension-defined tools. |
| CLI workflow | `packages/coding-agent` | `pi-cli` | Partial, improved | Java CLI now exposes `--no-session`, `--session-id`, `@file` attachment expansion, `/session`, `/rename`, `/resume`, `/tree`, `/fork`, `/export`, `/import`, `/models`, `/resources`, `/prompts`, `/skills`, and `/edit`. |
| Context files | `packages/coding-agent` resource loader | `pi-cli` | Partial | Java loads `AGENTS.md` and `CLAUDE.md` from parent directories into startup/session metadata. |
| Skills and prompt templates | `packages/coding-agent` resource loader | `pi-cli` | Partial | Java discovers user-global prompts/skills and trusted project-local `.pi/prompts/*.md`, `.pi/skills/*/SKILL.md`, and `.agents/skills/*/SKILL.md`; it expands simple `{{key}}` prompt templates and injects basic `/skill:name` instructions into CLI conversations, but does not execute extension-provided tools or the full Agent Skills lifecycle. |
| CLI settings | `settings.json` | `pi-cli` | Partial | Java loads user-global and trusted project-local `.pi/settings.json`, applies `outputPad` to user/assistant/thinking lines, and supports configured `externalEditor` via `/edit`; it does not yet implement upstream's exact Ctrl+G/TUI keybinding workflow. |
| Extensions, themes, packages | `packages/coding-agent` resource/package loader | Not implemented | Gap | Upstream can load executable extensions, themes, and Pi packages; Java only discovers local prompt/skill files. |
| Project trust | `packages/coding-agent` trust manager | `pi-cli` | Partial | Java supports `/trust` and gates project-local prompt/skill resources. It does not yet model extension/package trust or interactive startup prompts. |
| TUI | `packages/tui` | `pi-ui` placeholder | Gap | Java version does not attempt upstream terminal UI parity yet. |
| Orchestration/process integration | `packages/orchestrator`, RPC mode | `pi-cli --rpc` | Minimal | Java exposes JSONL `get_entries` and `get_tree` session inspection, with rendered titles/plain-text/Markdown summaries and tail-derived `shortId` fields for entries/tree nodes. Broader RPC/orchestrator commands are still future work. |
| Spring embedding | n/a | `pi-starter` | Java-specific value | Starter auto-configuration is the main Java adoption advantage. |

## What Is Practical Now

- Embed a pi-style agent loop in Spring Boot with starter wiring.
- Use typed provider and model abstractions from Java code.
- Resolve providers from preferred model ids and skip unavailable unauthenticated defaults.
- List available models from currently available providers and resolve an exact model id to its available provider without fallback.
- Use OpenAI with upstream-aligned `gpt-5.5` default model and Azure Foundry/OpenAI base URL normalization.
- Override OpenAI API key, `OPENAI_API_KEY` env value, model, temperature, and max-token options per `ChatRequest`.
- Clamp OpenAI request output-token values below the provider minimum to 16.
- Retry OpenAI-compatible HTTP 524 timeout responses.
- Classify DS4-style OpenAI-compatible context-overflow response bodies.
- Read real OpenAI chat response content and surface `finish_reason=length` as an incomplete response.
- Preserve OpenAI token usage metadata including `usage.reasoningTokens`.
- Preserve OpenAI provider HTTP response bodies in raised error messages.
- Retry OpenAI provider errors whose response body explicitly asks callers to retry.
- Convert CLI image attachments into OpenAI non-streaming `image_url` content parts.
- Send OpenAI non-streaming tool schemas and expose returned `tool_calls` as `metadata.toolCalls` for Java-side orchestration.
- Expose an Anthropic-compatible `claude-sonnet-5` catalog entry with adaptive-thinking labeling.
- Send non-streaming Anthropic Messages API requests with request-scoped `apiKey`/`ANTHROPIC_API_KEY` auth, Claude Sonnet 5 thinking payloads, and parse text, thinking content, plus input/output token usage from responses.
- Convert CLI image attachments into Anthropic non-streaming image content blocks.
- Send Anthropic non-streaming tool schemas and expose returned `tool_use` blocks as `metadata.toolCalls` for Java-side orchestration.
- Send Anthropic `TOOL_RESULT` messages back as provider-native `tool_result` content blocks, including `is_error` for failed Java tool executions.
- Export Java tool schemas with sequential `executionMode` metadata for provider-side tool orchestration hints.
- Execute provider-returned non-streaming `toolCalls`, append provider-native `TOOL_RESULT` messages to the session branch, and continue through bounded multi-round provider turns.
- Send SigV4-signed non-streaming Bedrock Anthropic invoke requests with Claude Sonnet 5 thinking/prompt-cache blocks, convert CLI image attachments into Bedrock image content blocks, send tool schemas, parse text, thinking content, `tool_use`, and input/output token usage from responses, and send `TOOL_RESULT` messages back as provider-native `tool_result` blocks.
- Configure Bedrock credentials through explicit `pi.llm.bedrock.*` properties, `pi.llm.bedrock.profile`/`AWS_PROFILE` shared credentials, AWS environment variables, per-request `ChatOptions.env()` AWS keys, or per-request `AWS_PROFILE` plus optional `AWS_SHARED_CREDENTIALS_FILE` without committing real keys.
- Persist session trees as JSONL and reload them into memory.
- Preserve nested usage metadata such as `usage.reasoningTokens` across JSONL save/load.
- Serialize split-turn compaction history and turn-prefix summary requests before merging them, avoiding concurrent summary generations against single-concurrency providers.
- Reject overwriting non-empty invalid JSONL session files so bad local session files are not silently lost.
- Read BMP files by converting them to PNG data URLs with image metadata.
- Expand CLI `@file` references into attached file blocks, including BMP-to-PNG payloads through the `read` tool.
- Start deterministic in-memory CLI sessions with `--session-id`, including ephemeral `--no-session` runs where `/save` is disabled.
- Resume saved sessions and inspect the active branch path.
- Rename the current session, persist the session name metadata, and receive a Java-side `session_info_changed` event.
- Fork a saved path from any node into a new JSONL session.
- Export/import JSONL sessions for handoff and reproducibility.
- Load `AGENTS.md`/`CLAUDE.md` context files from the project path.
- Discover local prompt templates and skill manifests in Pi-compatible directories.
- Save project trust decisions with `/trust` and reload project-local prompt/skill resources after trust.
- Load user-global and trusted project-local `.pi/settings.json`, apply `outputPad` to user/assistant/thinking lines, and show configured `externalEditor` with `/settings`.
- Compose the next CLI message through `/edit` using configured `externalEditor`; commands can use `{file}` or receive the draft path as the final argument.
- Expand trusted prompt templates with `/<template> key=value` and inject basic skill instructions with `/skill:<name> [request]`.
- Inspect current or requested sessions over minimal JSONL RPC with `get_entries` and `get_tree`, including rendered titles/plain text/Markdown summaries for entries and tail-derived `shortId` fields for entries/tree nodes.
- Use built-in file/shell tools with permission categories, sequential execution metadata, and invalid bash-timeout rejection before execution.

## What Is Still Not Upstream Parity

- Multi-provider breadth, subscription OAuth, provider transport selection, generated model catalogs, and request-scoped auth/env propagation beyond the currently tested OpenAI, Anthropic, and Bedrock AWS-env paths.
- Full Anthropic streaming tool-use behavior and provider-specific streaming tool-result orchestration.
- Full Bedrock runtime parity, including streaming transport behavior beyond the tested SigV4-signed non-streaming thinking/prompt-cache block.
- Upstream's TUI, keyboard workflow, message queue, compaction UI, and rich session browser.
- Full Agent Skills execution lifecycle, extension-provided tools, themes, and Pi package installation.
- Full project trust for executable extensions, package-managed code, and interactive startup prompts.
- Full upstream external editor keybinding/TUI workflow.
- Copilot auth/device-code polling behavior and full automatic compaction integration beyond serialized split-turn summary orchestration.
- OpenAI Codex WebSocket connection rotation.
- Provider-native multimodal attachment parts beyond the current OpenAI, Anthropic, and Bedrock non-streaming image paths; documents and streaming requests still carry CLI `@file` attachments as structured text.
- Full upstream extension-runtime event delivery and RPC/orchestrator process integration beyond minimal `get_entries`/`get_tree`.
- Supply-chain hardening around package installs and shrinkwrap-style release checks.

## Validation Pointers

Canonical target:

```bash
mvn clean compile
mvn test
printf "help\n/session\n/models\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
printf "help\n/resources\n/prompts\n/skills\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
printf "help\n/resources\n/trust\n/resources\nexit\n" | mvn -pl pi-cli -DskipTests -Dpi.trust.file=target/trust-smoke.txt exec:java
printf '{"id":1,"method":"get_tree"}\n' | mvn -pl pi-cli -DskipTests -Dexec.args=--rpc exec:java
mkdir -p target/pi-cli-resource-smoke/.pi/prompts target/pi-cli-resource-smoke/.pi/skills/deploy
printf 'Review {{target}} for {{focus}}\n' > target/pi-cli-resource-smoke/.pi/prompts/review.md
printf '# Deploy Skill\nUse deployment steps.\n' > target/pi-cli-resource-smoke/.pi/skills/deploy/SKILL.md
cd target/pi-cli-resource-smoke
printf "/resources\n/trust\n/prompts\n/skills\n/review target=api focus=security\n/skill:deploy release service\nexit\n" | mvn -f ../../pom.xml -pl pi-cli -DskipTests -Dpi.trust.file=trust-final.txt exec:java
mkdir -p ../pi-cli-settings-smoke/.pi
printf '{"outputPad":3,"externalEditor":"code --wait"}\n' > ../pi-cli-settings-smoke/.pi/settings.json
cd ../pi-cli-settings-smoke
printf "/settings\n/trust\n/settings\nhello settings\nexit\n" | mvn -f ../../pom.xml -pl pi-cli -DskipTests -Dpi.trust.file=trust-settings.txt exec:java
```

Focused session parity check:

```bash
mvn -pl pi-session -am test -Dtest=SessionPersistenceUnitTest
mvn -pl pi-session -am test -Dtest=SessionCompactionServiceTest
mvn -pl pi-tools -am test -Dtest=BashToolTest,ReadFileToolTest
mvn -pl pi-llm -am test -Dtest=OpenAIClientTest,OpenAIConfigTest,OpenAILLMProviderTest
mvn -pl pi-llm -am test -Dtest=AnthropicClientTest,AnthropicConfigTest,AnthropicLLMProviderTest
mvn -pl pi-llm -am test -Dtest=BedrockClientTest,BedrockConfigTest,BedrockLLMProviderTest
mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest
mvn -pl pi-cli -am test -Dtest=PiTrustManagerTest
mvn -pl pi-cli -am test -Dtest=PiResourceCommandResolverTest
mvn -pl pi-cli -am test -Dtest=PiFileReferenceResolverTest
mvn -pl pi-cli -am test -Dtest=PiRpcCommandHandlerTest
mvn -pl pi-cli -am test -Dtest=PiCliStartupOptionsTest,PiCliSettingsLoaderTest,PiCliOutputFormatterTest,PiExternalEditorTest
printf "/rename Roadmap Review\n/session\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

Local fallback when the machine lacks JDK 21:

```bash
mvn -pl pi-session -am test -Dtest=SessionPersistenceUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-session -am test -Dtest=SessionCompactionServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-tools -am test -Dtest=BashToolTest,ReadFileToolTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-llm -am test -Dtest=OpenAIClientTest,OpenAIConfigTest,OpenAILLMProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-llm -am test -Dtest=AnthropicClientTest,AnthropicConfigTest,AnthropicLLMProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-llm -am test -Dtest=BedrockClientTest,BedrockConfigTest,BedrockLLMProviderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am test -Dtest=PiTrustManagerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am test -Dtest=PiResourceCommandResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am test -Dtest=PiFileReferenceResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am test -Dtest=PiRpcCommandHandlerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am test -Dtest=PiCliStartupOptionsTest,PiCliSettingsLoaderTest,PiCliOutputFormatterTest,PiExternalEditorTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
printf "/rename Roadmap Review\n/session\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 exec:java
printf "/session\n/save\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 -Dexec.args="--no-session --session-id stable-cache-id" exec:java
```

The fallback is useful for development feedback only. Java 21 remains the release target in `AGENTS.md`.

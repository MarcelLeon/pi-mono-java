# RPC Session Tree Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal Java CLI RPC path for upstream `get_entries` and `get_tree` session inspection parity.

**Architecture:** Keep the session snapshot logic in `pi-cli` because this is a CLI/RPC adapter over the existing `pi-session` APIs. `PiRpcCommandHandler` parses one JSON request, optionally loads `params.sessionId`, returns JSON for `get_entries` or `get_tree`, and `PiCliApplication --rpc` streams JSONL request/response lines over stdin/stdout.

**Tech Stack:** Java 21 target, Maven, JUnit 5, Jackson `ObjectMapper`, existing `SessionManager` and `SessionTree`.

---

### Task 1: RPC Handler Unit Behavior

**Files:**
- Create: `pi-cli/src/test/java/com/pi/mono/cli/rpc/PiRpcCommandHandlerTest.java`
- Create: `pi-cli/src/main/java/com/pi/mono/cli/rpc/PiRpcCommandHandler.java`

- [ ] **Step 1: Write failing tests**

Add tests that create a `SessionManager` with in-memory `SessionTree` and temp `SessionPersistence`, send messages, then assert:
- `get_entries` returns `sessionId`, `currentBranchId`, and ordered entries with role/content.
- `get_tree` returns `rootId`, `currentBranchId`, nodes with child ids, and assistant/user nodes.
- unknown method returns an `error` response.

- [ ] **Step 2: Run test to verify RED**

Run:

```bash
mvn -pl pi-cli -am test -Dtest=PiRpcCommandHandlerTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
```

Expected: compilation failure because `PiRpcCommandHandler` does not exist.

- [ ] **Step 3: Implement minimal handler**

Create `PiRpcCommandHandler` with:
- constructor accepting `SessionManager`;
- `handle(String requestJson)` returning response JSON;
- methods `get_entries` and `get_tree`;
- simple error envelope for malformed/unsupported requests.

- [ ] **Step 4: Run test to verify GREEN**

Run the same focused test command. Expected: tests pass.

### Task 2: CLI `--rpc` Entry

**Files:**
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/PiCliApplication.java`

- [ ] **Step 1: Write failing CLI smoke if practical**

Use existing app wiring to run:

```bash
printf '{"id":1,"method":"get_tree"}\n' | mvn -pl pi-cli -DskipTests -Djava.version=17 exec:java -Dexec.args="--rpc"
```

Expected before implementation: interactive CLI ignores `--rpc`.

- [ ] **Step 2: Implement `--rpc` branch**

In `run(String... args)`, if `--rpc` is present, initialize working directory/resources/session, then stream stdin lines through `PiRpcCommandHandler`.

- [ ] **Step 3: Run CLI smoke**

Run the same command. Expected: one JSON response with `result.rootId`, `result.currentBranchId`, and at least the system root node.

### Task 3: Docs and Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/capability-comparison.md`

- [ ] **Step 1: Update docs**

Document that Java now supports minimal CLI JSONL RPC inspection for `get_entries`/`get_tree`, while richer upstream RPC and extension events remain gaps.

- [ ] **Step 2: Verify**

Run:

```bash
mvn -pl pi-cli -am test -Dtest=PiRpcCommandHandlerTest,PiResourceLoaderTest,PiTrustManagerTest,PiResourceCommandResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-session -am test -Dtest=SessionPersistenceUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17
mvn -pl pi-cli -am compile -Djava.version=17
git diff --check
```

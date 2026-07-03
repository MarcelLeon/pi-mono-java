# Upstream Session CLI Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the Java implementation closer to upstream Pi v0.80.3 for durable session resume, tree navigation, import/export, fork, model listing, and capability documentation.

**Architecture:** Keep the implementation Java/Spring-native: `pi-session` owns JSONL reconstruction and tree operations, `pi-cli` exposes lightweight slash commands, and docs describe parity honestly. Avoid copying Node TUI internals.

**Tech Stack:** Java 21, Maven, JUnit 5, Spring Boot, Jackson.

---

### Task 1: Session JSONL Restore

**Files:**
- Modify: `pi-session/src/test/java/com/pi/mono/session/SessionPersistenceUnitTest.java`
- Modify: `pi-session/src/main/java/com/pi/mono/session/SessionTree.java`
- Modify: `pi-session/src/main/java/com/pi/mono/session/SessionPersistence.java`

- [ ] **Step 1: Write the failing test**

Add `loadSessionTreeReconstructsNodesAndCurrentBranch()` to save a root/user/assistant path, reload it, and assert that the loaded tree has the same path and current branch.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl pi-session test -Dtest=SessionPersistenceUnitTest#loadSessionTreeReconstructsNodesAndCurrentBranch`

Expected: FAIL because `loadSessionTree()` returns an empty tree.

- [ ] **Step 3: Write minimal implementation**

Use Jackson to deserialize each JSONL row into a small DTO, rebuild `SessionTree` through a `restore(Collection<SessionNode>, String)` method, and pick the newest leaf as current branch.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl pi-session test -Dtest=SessionPersistenceUnitTest#loadSessionTreeReconstructsNodesAndCurrentBranch`

Expected: PASS.

### Task 2: Resume And Fork Session Manager

**Files:**
- Modify: `pi-session/src/test/java/com/pi/mono/session/SessionPersistenceUnitTest.java`
- Modify: `pi-session/src/main/java/com/pi/mono/session/SessionManager.java`
- Modify: `pi-session/src/main/java/com/pi/mono/session/SessionTree.java`

- [ ] **Step 1: Write the failing tests**

Add tests for `loadSession()` restoring history and `forkCurrentSessionFromNode()` cloning the active path into a new session.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl pi-session test -Dtest=SessionPersistenceUnitTest`

Expected: FAIL because load is a placeholder and fork does not exist.

- [ ] **Step 3: Write minimal implementation**

Make `loadSession()` replace in-memory tree state from persistence. Add `forkCurrentSessionFromNode(String nodeId, String model)` that copies path nodes into a new tree preserving roles/content/metadata.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl pi-session test -Dtest=SessionPersistenceUnitTest`

Expected: PASS.

### Task 3: CLI Upstream Commands

**Files:**
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/PiCliApplication.java`

- [ ] **Step 1: Add command wiring**

Expose `/session`, `/resume <id>`, `/tree`, `/fork <nodeId> [model]`, `/export <file>`, `/import <file>`, and `/models`.

- [ ] **Step 2: Run CLI smoke**

Run: `printf "help\n/session\n/models\nexit\n" | mvn -pl pi-cli -DskipTests exec:java`

Expected: CLI starts, prints command help/session/model information, then exits.

### Task 4: Capability Docs

**Files:**
- Modify: `README.md`
- Modify: `docs/capability-comparison.md`

- [ ] **Step 1: Update upstream snapshot**

Reference `earendil-works/pi` and release `v0.80.3` from 2026-06-30.

- [ ] **Step 2: Document honest parity**

Mark session resume/import/export/fork as improved, provider breadth and TUI/extensions as partial or not aligned, and list executable verification commands.

- [ ] **Step 3: Run markdown sanity**

Run: `rg -n "badlogic/pi-mono|packages/mom|packages/pods|web-ui" README.md docs/capability-comparison.md`

Expected: no stale package references remain except historical notes if explicitly labeled.

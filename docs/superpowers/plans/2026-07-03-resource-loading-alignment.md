# Resource Loading Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Java CLI support for Pi-style context files, prompt templates, and skills discovery so Java users get part of upstream `packages/coding-agent` resource-loading behavior.

**Architecture:** Keep the first implementation inside `pi-cli` because upstream resource discovery is a coding-agent CLI concern. `PiResourceLoader` discovers resources relative to the current working directory and parent directories, while `PiCliApplication` shows them and injects loaded context into the session root metadata.

**Tech Stack:** Java 21 target, Maven, JUnit 5, Spring Boot CLI.

---

### Task 1: Resource Discovery Unit Tests

**Files:**
- Create: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResourceLoader.java`
- Create: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResources.java`
- Create: `pi-cli/src/test/java/com/pi/mono/cli/resources/PiResourceLoaderTest.java`

- [ ] **Step 1: Write failing context file test**

Create a temporary project tree with `AGENTS.md` at root, `CLAUDE.md` at a child directory, and assert `load(Path cwd)` returns both files in parent-to-child order with combined context text.

- [ ] **Step 2: Write failing prompt/skill discovery test**

Create `.pi/prompts/review.md`, `.pi/skills/deploy/SKILL.md`, `.agents/skills/local/SKILL.md`, and assert prompt/skill names and paths are discovered.

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`

Expected: FAIL because the classes do not exist.

### Task 2: Resource Loader Implementation

**Files:**
- Implement: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResourceLoader.java`
- Implement: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResources.java`

- [ ] **Step 1: Implement immutable resource records**

`PiResources` should expose context files, prompt templates, skills, and a combined context string.

- [ ] **Step 2: Implement discovery**

Walk from filesystem root to `cwd`, loading `AGENTS.md` and `CLAUDE.md`; discover prompts under `.pi/prompts/*.md`; discover skills under `.pi/skills/*/SKILL.md` and `.agents/skills/*/SKILL.md`.

- [ ] **Step 3: Run resource tests**

Run: `mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`

Expected: PASS.

### Task 3: CLI Resource Commands And Session Metadata

**Files:**
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/PiCliApplication.java`
- Modify: `pi-session/src/main/java/com/pi/mono/session/SessionManager.java`
- Modify: `pi-session/src/test/java/com/pi/mono/session/SessionPersistenceUnitTest.java`

- [ ] **Step 1: Write failing session metadata test**

Add a test proving `createSession(model, metadata)` stores resource metadata on the root message.

- [ ] **Step 2: Implement metadata overload**

Add `createSession(String model, Map<String,Object> metadata)` and keep the existing `createSession(String model)` delegating to it.

- [ ] **Step 3: Wire CLI resources**

On startup, load resources from `Path.of("").toAbsolutePath()`, print a concise startup summary, pass counts/context metadata into `createSession`, and add `/resources`, `/prompts`, `/skills`.

- [ ] **Step 4: Run smoke**

Run: `printf "help\n/resources\n/prompts\n/skills\n/session\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 exec:java`

Expected: CLI prints resource summary and exits.

### Task 4: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/capability-comparison.md`

- [ ] **Step 1: Update capability table**

Mark context files, prompt templates, and skill discovery as partially aligned.

- [ ] **Step 2: Add verification commands**

List the resource loader test and CLI resource smoke.

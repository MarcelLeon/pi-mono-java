# Project Trust Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Pi-style project trust gate so Java CLI does not load project-local `.pi` and `.agents` resources unless the project is trusted.

**Architecture:** Keep `AGENTS.md` and `CLAUDE.md` context loading unchanged. Add trust-aware overloads to `PiResourceLoader`, always load user-global resources from `~/.pi/agent` and `~/.agents`, and load project-local `.pi`/`.agents` resources only when a `PiTrustManager` says the current project is trusted.

**Tech Stack:** Java 21 target, Maven, JUnit 5, Spring Boot CLI.

---

### Task 1: Resource Loader Trust Tests

**Files:**
- Modify: `pi-cli/src/test/java/com/pi/mono/cli/resources/PiResourceLoaderTest.java`
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResourceLoader.java`

- [ ] **Step 1: Write failing untrusted-project test**

Create a temp user home with global skills and a project with `.pi/prompts` and `.agents/skills`; assert `load(project, false)` loads global resources but not project-local prompt/skill resources.

- [ ] **Step 2: Write failing trusted-project test**

Assert `load(project, true)` loads the project prompt and project skills.

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`

Expected: FAIL because trust-aware overloads and global resource roots do not exist.

### Task 2: Trust-Aware Resource Loader

**Files:**
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResourceLoader.java`

- [ ] **Step 1: Add constructor with user home**

Keep default constructor using `System.getProperty("user.home")`; add a package-visible/test-friendly constructor taking `Path userHome`.

- [ ] **Step 2: Add `load(Path cwd, boolean trustedProject)`**

Always load context files; always load user-global prompts/skills; load project-local prompts/skills only when trusted.

- [ ] **Step 3: Run loader tests**

Run: `mvn -pl pi-cli -am test -Dtest=PiResourceLoaderTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`

Expected: PASS.

### Task 3: Trust Manager And CLI Command

**Files:**
- Create: `pi-cli/src/main/java/com/pi/mono/cli/trust/PiTrustManager.java`
- Create: `pi-cli/src/test/java/com/pi/mono/cli/trust/PiTrustManagerTest.java`
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/PiCliApplication.java`

- [ ] **Step 1: Write failing trust manager tests**

Use a temp config file; assert unknown projects are untrusted, trusting a path makes the path and children trusted, and the decision persists across manager instances.

- [ ] **Step 2: Implement manager**

Store trusted paths in a simple newline-delimited file under `~/.pi/agent/trust-java.txt` to avoid adding runtime parsing dependencies while preserving Pi-like global trust behavior.

- [ ] **Step 3: Wire CLI**

At startup, load resources with `trustedProject = trustManager.isTrusted(cwd)`. Add `/trust` to save the current cwd trust decision and reload resources. Show trust status in `/resources`.

- [ ] **Step 4: Run smoke**

Run: `printf "help\n/resources\n/trust\n/resources\nexit\n" | mvn -pl pi-cli -DskipTests -Djava.version=17 exec:java`

Expected: CLI starts, shows trust status, saves trust, reloads resources, and exits.

### Task 4: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/capability-comparison.md`

- [ ] **Step 1: Update trust row**

Mark project trust as partially aligned and explain scope.

- [ ] **Step 2: Add verification commands**

List `PiTrustManagerTest` and trust/resource CLI smoke.

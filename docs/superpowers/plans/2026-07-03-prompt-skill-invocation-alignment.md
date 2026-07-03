# Prompt Skill Invocation Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Java CLI support for using discovered prompt templates and skills via Pi-style slash commands.

**Architecture:** Keep resource discovery in `PiResourceLoader`. Add a small `PiResourceCommandResolver` that transforms `/template key=value` and `/skill:name optional request` into concrete prompt text, then let `PiCliApplication` send that prompt through the existing conversation path.

**Tech Stack:** Java 21 target, Maven, JUnit 5.

---

### Task 1: Resolver Tests

**Files:**
- Create: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResourceCommandResolver.java`
- Create: `pi-cli/src/test/java/com/pi/mono/cli/resources/PiResourceCommandResolverTest.java`

- [ ] **Step 1: Write failing prompt template test**

Create a `PiResources` with prompt template `review` containing `Review {{target}} for {{focus}}`. Assert `/review target=api focus=security` resolves to `Review api for security`.

- [ ] **Step 2: Write failing skill invocation test**

Create a skill named `deploy` with content `# Deploy Skill`. Assert `/skill:deploy release service` resolves to a prompt containing the skill content and the request.

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl pi-cli -am test -Dtest=PiResourceCommandResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`

Expected: FAIL because resolver does not exist.

### Task 2: Resolver Implementation

**Files:**
- Implement: `pi-cli/src/main/java/com/pi/mono/cli/resources/PiResourceCommandResolver.java`

- [ ] **Step 1: Implement prompt matching**

Match input beginning with `/<promptName>` where prompt exists. Parse `key=value` tokens and replace `{{key}}`.

- [ ] **Step 2: Implement skill matching**

Match `/skill:<skillName>`. Return a prompt with skill content and optional user request.

- [ ] **Step 3: Run resolver tests**

Run: `mvn -pl pi-cli -am test -Dtest=PiResourceCommandResolverTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.version=17`

Expected: PASS.

### Task 3: CLI Wiring

**Files:**
- Modify: `pi-cli/src/main/java/com/pi/mono/cli/PiCliApplication.java`

- [ ] **Step 1: Wire resolver before generic conversation**

If resolver returns a prompt, send the expanded prompt through `handleConversation`.

- [ ] **Step 2: Run CLI smoke with project prompt and skill files**

Create temporary project-local `.pi/prompts/review.md` and `.pi/skills/deploy/SKILL.md`, run CLI from that directory with `/trust`, `/review`, and `/skill:deploy`, and confirm output contains expanded prompt text and skill invocation text.

### Task 4: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/capability-comparison.md`

- [ ] **Step 1: Update capability wording**

Mark prompt expansion and basic skill invocation as partially aligned.

- [ ] **Step 2: Add verification commands**

List resolver tests and CLI smoke.

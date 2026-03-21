# Repository Guidelines

## Mission & Product Direction
This project is a Java/Spring implementation inspired by pi-mono, built to serve business delivery.
Its core value is lowering adoption friction for Java teams that want pi-mono-style agent workflows but are not TypeScript-first.

When contributing, anchor to outcomes first:
1. Define target business/usage impact.
2. Implement toward that target.
3. Verify with executable evidence.
4. Reflect on gaps/risks.
5. Optimize and close the loop with code + tests + docs.

## Project Structure
This is a multi-module Maven repo:
- `pi-core`: shared contracts and models.
- `pi-llm`: provider implementations and routing.
- `pi-session`: session tree and persistence.
- `pi-tools`: tool execution and permissions.
- `pi-cli`: CLI entrypoint.
- `pi-starter`: Spring Boot auto-configuration.
- `spring-test-example/`, `example-project/`: runnable integration examples.
- `docs/`: quickstart and capability notes.

## Build, Run, and Verify
- `mvn clean compile`: compile all modules.
- `mvn test`: run full test suite.
- `mvn -pl <module> -am test`: run module-focused tests.
- `printf "help\nexit\n" | mvn -pl pi-cli -DskipTests exec:java`: CLI smoke.
- `mvn -f spring-test-example/pom.xml spring-boot:run`: Spring integration smoke.
- `./scripts/benchmark_smoke.sh`: canonical open-source smoke benchmark (logs + report).

Use Java 21+ and Maven 3.8+.

## Coding & Naming
- Java style: 4-space indentation, no tabs.
- `PascalCase` classes, `camelCase` methods/fields, `UPPER_SNAKE_CASE` constants.
- Package naming under `com.pi.mono.*`.
- Prefer small, cohesive classes and explicit method names.

## Testing & PR Expectations
- Frameworks: JUnit 5, Spring Boot Test, Mockito.
- Add/adjust tests for behavior changes in session flow, provider routing, and starter auto-configuration.
- PRs should include: purpose, impacted modules, verification commands/results, and user/business impact for Java/Spring adopters.

## Security & Config
- Never commit real keys (for example `pi.llm.openai.api-key`).
- Validate both starter config and runtime behavior when changing properties.

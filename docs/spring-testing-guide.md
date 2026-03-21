# Spring Testing Guide

## Scope
This guide focuses on validating the Java/Spring integration path of Pi-Mono Java with reproducible commands.

## Recommended Flow
```bash
# From repo root
mvn clean compile
mvn -f spring-test-example/pom.xml test
mvn -f spring-test-example/pom.xml spring-boot:run
```
Expected runtime output includes: `所有Spring集成测试通过！`.

## SessionManager Injection Check
If startup reports missing `SessionManager` bean:
1. Ensure `pi-starter` module is part of the build (`mvn clean compile`).
2. Confirm Spring component scan covers `com.pi.mono` auto-configured beans.
3. Re-run with test profile isolation in `spring-test-example`.

## Test Profile Assets
- `spring-test-example/src/test/resources/application-test.yml`
- `spring-test-example/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`

These keep tests deterministic and compatible on newer JDKs.

## One-Command Smoke Benchmark
```bash
./scripts/benchmark_smoke.sh
```
This command also runs Spring example tests and writes logs + markdown report under `benchmarks/`.

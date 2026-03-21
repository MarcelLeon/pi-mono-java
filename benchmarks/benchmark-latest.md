# Smoke Benchmark Report

- Generated at: 2026-03-21 16:15:31 +0800
- Java: openjdk version "17.0.6" 2023-01-17 LTS
- Maven: Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)
- Maven Java Runtime: Java version: 24.0.2, vendor: Homebrew, runtime: /opt/homebrew/Cellar/openjdk/24.0.2/libexec/openjdk.jdk/Contents/Home
- OS: Darwin MacBook-Pro-2.local 25.2.0 Darwin Kernel Version 25.2.0: Tue Nov 18 21:09:56 PST 2025; root:xnu-12377.61.12~1/RELEASE_ARM64_T6041 arm64

| Step | Command | Status | Duration | Log |
|---|---|---|---:|---|
| Compile | `mvn -q -DskipTests clean compile` | PASS | 2s | `benchmarks/logs/20260321-161505/Compile.log` |
| Session Unit Test | `mvn -q -pl pi-session test` | PASS | 2s | `benchmarks/logs/20260321-161505/Session_Unit_Test.log` |
| Spring Example Tests | `mvn -q -f spring-test-example/pom.xml test` | PASS | 19s | `benchmarks/logs/20260321-161505/Spring_Example_Tests.log` |
| CLI Smoke | `printf 'help\nexit\n' | mvn -q -pl pi-cli -DskipTests exec:java` | PASS | 2s | `benchmarks/logs/20260321-161505/CLI_Smoke.log` |

## Notes
- This is a smoke benchmark for reproducibility, not a lab-grade performance benchmark.
- Use the same machine/JDK for trend comparison.

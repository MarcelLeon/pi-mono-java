# Quickstart (English)

Use this guide to validate `pi-mono-java` in about 5 minutes.

## 1. Prerequisites

- Java 21+
- Maven 3.8+

```bash
java -version
mvn -version
```

## 2. Build

```bash
mvn clean compile
```

Expected: `BUILD SUCCESS`.

## 3. Verify Spring integration (recommended)

```bash
cd spring-test-example
mvn spring-boot:run
```

Expected logs include:
- `会话创建成功` (session created)
- `AI响应` (AI response)
- `所有Spring集成测试通过` (all Spring integration checks passed)

This confirms:
- starter auto-configuration loads,
- `SessionManager -> LLMProviderManager` path runs,
- create/send/save/switch session flow works.

## 4. Verify CLI (optional)

```bash
cd ..
printf "help\n/save\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

## 5. Use in your Spring project

Dependency:

```xml
<dependency>
  <groupId>com.pi.mono</groupId>
  <artifactId>pi-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Minimal usage:

```java
@Autowired
private SessionManager sessionManager;

String sessionId = sessionManager.createSession("mock-claude");
var reply = sessionManager.sendMessage("hello").get();
sessionManager.saveSession();
```

## 6. Known caveats

- You may still see Mockito attach warnings on JDK 24+:
  - this is expected on newer JDKs; test resources already avoid inline mock-maker failure for `spring-test-example`.
- OpenAI connection may log failure without a valid API key:
  - this does not block local mock-based verification.

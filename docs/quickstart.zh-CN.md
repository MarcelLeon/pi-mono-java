# 快速开始（中文）

本指南用于 5 分钟内验证：`pi-mono-java` 能否在你的 Java/Spring 环境里跑起来。

## 1. 环境要求

- Java 21+
- Maven 3.8+

检查命令：

```bash
java -version
mvn -version
```

## 2. 编译项目

```bash
mvn clean compile
```

成功标志：看到 `BUILD SUCCESS`。

## 3. 快速验证 Spring 集成（推荐）

```bash
cd spring-test-example
mvn spring-boot:run
```

成功标志（日志中出现）：
- `会话创建成功`
- `AI响应`
- `所有Spring集成测试通过`

这一步验证了：
- Starter 自动装配可用；
- `SessionManager -> LLMProviderManager` 主链路可跑通；
- 会话创建/发送/保存/切换流程可执行。

## 4. 快速验证 CLI（可选）

```bash
cd ..
printf "help\n/save\nexit\n" | mvn -pl pi-cli -DskipTests exec:java
```

## 5. 在你的 Spring 项目中使用

依赖：

```xml
<dependency>
  <groupId>com.pi.mono</groupId>
  <artifactId>pi-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

最小示例：

```java
@Autowired
private SessionManager sessionManager;

String sessionId = sessionManager.createSession("mock-claude");
var reply = sessionManager.sendMessage("hello").get();
sessionManager.saveSession();
```

## 6. 常见问题

- 测试日志出现 Mockito attach 警告（JDK 24+）：
  - 这是高版本 JDK 下常见提示，`spring-test-example` 已通过测试资源配置规避 inline mock-maker 导致的失败。
- OpenAI 连接日志显示失败：
  - 若未配置有效 API Key 属于预期，不影响 mock 模型本地验证。

# Pi-Mono Java Spring Boot Starter

为Spring Boot应用提供Pi-Mono Java的无缝集成。

## 🚀 快速开始

### 1. 添加依赖

在您的Spring Boot项目的`pom.xml`中添加以下依赖：

```xml
<dependency>
    <groupId>com.pi.mono</groupId>
    <artifactId>pi-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置应用

在`application.yml`中添加Pi-Mono配置：

```yaml
# Pi-Mono Java 配置
pi:
  mono:
    default-model: mock-claude

    # 会话配置
    session:
      timeout: 24h
      max-sessions: 1000
      storage-path: ./pi-sessions

    # LLM提供商配置
    providers:
      mock-claude:
        type: mock
        model: mock-claude
        timeout: 5m

    # 工具配置
    tools:
      enabled: true
      timeout: 10m
      max-file-size: 10MB

    # Web API配置
    web:
      enabled: true
      path-prefix: /api/pi
      websocket-enabled: true
      websocket-path: /ws/pi
```

### 3. 使用Pi-Mono服务

```java
@Service
public class MyAiService {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private LLMProviderManager llmProviderManager;

    @Autowired
    private ToolManager toolManager;

    public String chatWithAi(String message) {
        // 创建会话
        String sessionId = sessionManager.createSession("mock-claude");

        // 发送消息
        CompletableFuture<AgentMessage> response = sessionManager.sendMessage(message);

        try {
            AgentMessage result = response.get();
            return result.content();
        } catch (Exception e) {
            throw new RuntimeException("AI service error", e);
        }
    }

    public void useTools() {
        // 使用文件工具
        Map<String, Object> args = Map.of("path", "/path/to/file.txt");
        CompletableFuture<ToolExecutionResult> result = toolManager.executeTool(
            "read", args, "session-id", null
        );

        // 处理结果...
    }
}
```

## 🔧 配置选项

### 基础配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pi.mono.default-model` | `mock-claude` | 默认LLM模型 |
| `pi.mono.session.timeout` | `24h` | 会话超时时间 |
| `pi.mono.session.max-sessions` | `1000` | 最大会话数 |
| `pi.mono.session.storage-path` | `./pi-sessions` | 会话存储路径 |

### LLM提供商配置

```yaml
pi:
  mono:
    providers:
      openai:
        type: openai
        endpoint: https://api.openai.com/v1
        api-key: ${OPENAI_API_KEY}
        model: gpt-4
        timeout: 5m
        retry-attempts: 3

      anthropic:
        type: anthropic
        endpoint: https://api.anthropic.com
        api-key: ${ANTHROPIC_API_KEY}
        model: claude-sonnet
        timeout: 10m
        retry-attempts: 3
```

### 工具配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pi.mono.tools.enabled` | `true` | 启用工具系统 |
| `pi.mono.tools.timeout` | `10m` | 工具执行超时时间 |
| `pi.mono.tools.max-file-size` | `10MB` | 最大文件大小限制 |
| `pi.mono.tools.allowed-commands` | 各种安全命令 | 允许的Bash命令列表 |

### Web API配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pi.mono.web.enabled` | `true` | 启用Web API |
| `pi.mono.web.path-prefix` | `/api/pi` | API路径前缀 |
| `pi.mono.web.websocket-enabled` | `true` | 启用WebSocket |
| `pi.mono.web.websocket-path` | `/ws/pi` | WebSocket路径 |

## 🛠️ API使用

### Session管理

```java
// 创建会话
String sessionId = sessionManager.createSession("openai");

// 发送消息
CompletableFuture<AgentMessage> response = sessionManager.sendMessage("Hello AI!");

// 获取会话历史
List<SessionNode> history = sessionManager.getSessionHistory();

// 保存会话
sessionManager.saveSession();

// 列出会话
List<String> sessions = sessionManager.listSessions();
```

### LLM操作

```java
// 获取可用提供商
List<LLMProvider> providers = llmProviderManager.getAllProviders();

// 获取健康状态
Map<String, HealthStatus> health = llmProviderManager.getHealthStatus();

// 使用特定提供商
LLMProvider provider = llmProviderManager.getAvailableProvider("openai");
```

### 工具操作

```java
// 执行工具
Map<String, Object> args = Map.of("path", "/path/to/file.txt");
CompletableFuture<ToolExecutionResult> result = toolManager.executeTool(
    "read", args, sessionId, nodeId
);

// 获取所有工具
Map<String, ToolDefinition> tools = toolManager.getAllTools();

// 权限管理
toolManager.addUserPermission("write");
toolManager.removeUserPermission("system");
```

## 🌐 REST API

### 会话API

```http
POST /api/pi/sessions
{
    "model": "openai",
    "name": "my-session"
}

GET /api/pi/sessions

GET /api/pi/sessions/{sessionId}

POST /api/pi/sessions/{sessionId}/messages
{
    "content": "Hello AI!",
    "role": "user"
}

GET /api/pi/sessions/{sessionId}/history

POST /api/pi/sessions/{sessionId}/save
```

### 工具API

```http
POST /api/pi/tools/execute
{
    "toolName": "read",
    "arguments": {
        "path": "/path/to/file.txt"
    },
    "sessionId": "session-id"
}
```

## 🔒 安全配置

### 权限管理

```java
// 添加用户权限
toolManager.addUserPermission("read");  // 文件读取
toolManager.addUserPermission("write"); // 文件写入
toolManager.addUserPermission("system"); // 系统命令

// 移除权限
toolManager.removeUserPermission("system");

// 设置完整权限
Set<String> permissions = Set.of("read", "write", "system");
toolManager.setUserPermissions(permissions);
```

### 安全限制

- **文件大小限制**: 默认10MB
- **危险命令限制**: 禁止`rm`, `sudo`, `chmod`等
- **路径验证**: 防止访问系统目录
- **并发控制**: 会话分支冲突检测

## 📊 监控和日志

### 健康检查

```http
GET /actuator/health
```

### 指标监控

```http
GET /actuator/metrics
GET /actuator/metrics/pi.mono.sessions
GET /actuator/metrics/pi.mono.tools
```

### 日志配置

```yaml
logging:
  level:
    com.pi.mono: INFO
    com.pi.mono.session: DEBUG
    com.pi.mono.tools: DEBUG
    com.pi.mono.llm: DEBUG
```

## 🧪 测试

### 单元测试

```java
@SpringBootTest
public class PiMonoServiceTest {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ToolManager toolManager;

    @Test
    public void testChatWithAi() {
        String sessionId = sessionManager.createSession("mock-claude");
        CompletableFuture<AgentMessage> response = sessionManager.sendMessage("Test message");

        AgentMessage result = response.get();
        assertNotNull(result);
        assertFalse(result.content().isEmpty());
    }

    @Test
    public void testToolExecution() {
        Map<String, Object> args = Map.of("path", "./test.txt");
        CompletableFuture<ToolExecutionResult> result = toolManager.executeTool(
            "read", args, "test-session", null
        );

        ToolExecutionResult toolResult = result.get();
        assertTrue(toolResult.success());
    }
}
```

### 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "pi.mono.default-model=mock-claude",
    "pi.mono.web.enabled=true"
})
public class PiMonoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testCreateSessionApi() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/pi/sessions",
            Map.of("model", "mock-claude"),
            String.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}
```

## 🚀 部署

### Docker

```dockerfile
FROM openjdk:21-jre-slim

COPY target/pi-mono-example.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pi-mono-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: pi-mono-app
  template:
    metadata:
      labels:
        app: pi-mono-app
    spec:
      containers:
      - name: pi-mono-app
        image: myregistry/pi-mono-app:latest
        ports:
        - containerPort: 8080
        env:
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: pi-mono-secrets
              key: openai-api-key
```

## 🤝 贡献

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 📄 许可证

本项目采用MIT许可证。
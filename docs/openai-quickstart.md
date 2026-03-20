# OpenAI集成 - 快速开始

## 🚀 概述

OpenAI集成让您能够在Pi-Mono Java中使用真实的OpenAI模型（GPT-3.5/GPT-4），而无需关心底层的API调用细节。

## 📦 安装

OpenAI集成已包含在`pi-llm`模块中，无需额外安装。

## ⚙️ 配置

### 基础配置

在`application.yml`中添加以下配置：

```yaml
pi:
  llm:
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-3.5-turbo
```

### 环境变量

推荐使用环境变量设置API密钥：

```bash
export OPENAI_API_KEY="sk-your-api-key-here"
```

### 完整配置示例

```yaml
pi:
  llm:
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1
      model: gpt-3.5-turbo
      timeout: 30s
      max-retries: 3
      retry-delay: 1s
      max-concurrency: 10
      max-connections: 20
      connection-timeout: 60s
```

## 🎯 使用

### Spring Boot应用中使用

```java
import com.pi.mono.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 创建会话
        String sessionId = sessionManager.createSession("openai-gpt3");

        // 发送消息到OpenAI
        var futureResponse = sessionManager.sendMessage("你好，请帮我写一个Java冒泡排序算法");
        var response = futureResponse.get();

        System.out.println("AI回答: " + response.content());
    }
}
```

### 手动创建SessionManager

```java
import com.pi.mono.session.SessionManager;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.llm.provider.OpenAILLMProvider;

// 手动创建（不推荐，除非在非Spring环境中）
SessionManager sessionManager = new SessionManager();
OpenAILLMProvider openAIProvider = new OpenAILLMProvider();
LLMProviderManager providerManager = new LLMProviderManager(List.of(openAIProvider));

// 使用
String sessionId = sessionManager.createSession("openai-gpt3");
```

## 📊 支持的模型

| 模型名称 | 描述 | 适用场景 |
|---------|------|---------|
| `gpt-3.5-turbo` | 快速、经济的模型 | 一般对话、代码生成 |
| `gpt-4` | 更强大、更智能的模型 | 复杂任务、高质量输出 |
| `gpt-4-turbo` | GPT-4的优化版本 | 平衡性能和成本 |

## 🔧 高级配置

### 多模型配置

```yaml
pi:
  llm:
    openai:
      models:
        gpt3: gpt-3.5-turbo
        gpt4: gpt-4
        turbo: gpt-4-turbo
    default-model: openai-gpt3
```

### 性能调优

```yaml
pi:
  llm:
    openai:
      # 提高并发性能
      max-concurrency: 20
      max-connections: 50

      # 减少超时时间（适用于快速响应场景）
      timeout: 15s

      # 减少重试次数（适用于高可用场景）
      max-retries: 1
      retry-delay: 500ms
```

### 企业级配置

```yaml
pi:
  llm:
    openai:
      # 生产环境配置
      api-key: ${OPENAI_API_KEY}
      timeout: 60s
      max-retries: 5
      retry-delay: 2s
      max-concurrency: 50
      max-connections: 100
      connection-timeout: 120s

      # 监控和日志
      log-level: DEBUG
      metrics-enabled: true
```

## 🧪 测试

### 单元测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "pi.llm.openai.enabled=true",
    "pi.llm.openai.api-key=test-key"
})
class MyApplicationTest {

    @Autowired
    private SessionManager sessionManager;

    @Test
    void testOpenAIIntegration() throws Exception {
        String sessionId = sessionManager.createSession("openai-gpt3");
        var response = sessionManager.sendMessage("Hello, OpenAI!").get();

        assertNotNull(response);
        assertNotNull(response.content());
    }
}
```

### 集成测试

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "pi.llm.openai.enabled=true",
    "pi.llm.openai.api-key=${OPENAI_API_KEY}"  // 使用真实API密钥
})
class OpenAIIntegrationTest {

    @Test
    void testRealOpenAIRequest() throws Exception {
        // 这会调用真实的OpenAI API
        var response = sessionManager.sendMessage("What is Java?").get();
        assertNotNull(response.content());
    }
}
```

## 🚨 错误处理

### 常见错误

1. **API密钥错误**
   ```
   OpenAI API error: 401 Unauthorized
   ```
   **解决方案**: 检查API密钥是否正确

2. **配额超限**
   ```
   OpenAI API error: 429 Too Many Requests
   ```
   **解决方案**: 等待配额重置或升级账户

3. **网络连接问题**
   ```
   Connection refused
   ```
   **解决方案**: 检查网络连接和防火墙设置

### 自定义错误处理

```java
try {
    var response = sessionManager.sendMessage("Hello").get();
} catch (Exception e) {
    if (e.getCause() instanceof OpenAIException) {
        // 处理OpenAI特定错误
        log.error("OpenAI error: {}", e.getMessage());
    } else {
        // 处理其他错误
        log.error("Unexpected error: {}", e.getMessage());
    }
}
```

## 📈 性能优化

### 连接池优化

```yaml
pi:
  llm:
    openai:
      max-connections: 50
      connection-timeout: 60s
      max-concurrency: 20
```

### 缓存策略

```yaml
pi:
  llm:
    cache:
      enabled: true
      ttl: 10m  # 缓存10分钟
      max-size: 1000
```

### 批量处理

```java
// 批量发送消息以提高效率
List<CompletableFuture<AgentMessage>> futures = messages.stream()
    .map(message -> sessionManager.sendMessage(message))
    .collect(Collectors.toList());

// 等待所有请求完成
List<AgentMessage> responses = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
```

## 🔒 安全最佳实践

1. **API密钥安全**
   - 使用环境变量存储API密钥
   - 不要在代码中硬编码密钥
   - 定期轮换API密钥

2. **请求限制**
   ```yaml
   pi:
     llm:
       openai:
         max-concurrency: 10  # 限制并发数
         rate-limit: 60       # 每分钟限制
   ```

3. **敏感信息过滤**
   ```yaml
   pi:
     llm:
       content-filter:
         enabled: true
         sensitive-words: ["password", "secret", "key"]
   ```

## 🆘 故障排除

### 调试模式

```yaml
logging:
  level:
    com.pi.mono.llm: DEBUG
    org.springframework.web.client: DEBUG
```

### 常见问题

**Q: API密钥正确但仍然认证失败？**
A: 检查API密钥是否已激活，账户是否有足够余额。

**Q: 请求超时？**
A: 增加timeout配置，或检查网络连接。

**Q: 并发请求失败？**
A: 减少max-concurrency配置，或升级OpenAI账户。

## 📞 支持

- [OpenAI API文档](https://platform.openai.com/docs/api-reference)
- [Pi-Mono Java文档](./README.md)
- [GitHub Issues](https://github.com/your-repo/pi-mono-java/issues)

---

**下一步**: 查看 [API参考文档](./openai-api-reference.md) 了解详细的API使用方法。
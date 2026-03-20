# Pi-Mono Java - 第一个真实LLM提供者开发任务

## 🎯 任务目标
实现第一个真实LLM提供者 - OpenAI API集成

## 📋 任务分解

### 任务1: 创建OpenAI配置类
**文件**: `pi-llm/src/main/java/com/pi/mono/llm/config/OpenAIConfig.java`

**功能**:
- OpenAI API密钥管理
- 模型配置
- 请求超时设置
- 重试策略配置

### 任务2: 创建OpenAI HTTP客户端
**文件**: `pi-llm/src/main/java/com/pi/mono/llm/client/OpenAIClient.java`

**功能**:
- REST API调用封装
- 错误处理
- 流式响应支持
- 连接池管理

### 任务3: 实现OpenAI LLM提供者
**文件**: `pi-llm/src/main/java/com/pi/mono/llm/provider/OpenAILLMProvider.java`

**功能**:
- 实现LLMProvider接口
- 调用OpenAI API
- 处理流式响应
- 错误重试

### 任务4: 注册OpenAI提供者
**修改文件**: `pi-llm/src/main/java/com/pi/mono/llm/LLMProviderManager.java`

**功能**:
- 添加OpenAI提供者注册
- 配置自动发现

## 🔧 实现细节

### OpenAIConfig.java
```java
@ConfigurationProperties(prefix = "pi.llm.openai")
@Data
public class OpenAIConfig {
    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-3.5-turbo";
    private Duration timeout = Duration.ofSeconds(30);
    private int maxRetries = 3;
    private Duration retryDelay = Duration.ofSeconds(1);
}
```

### OpenAIClient.java
```java
@Component
public class OpenAIClient {
    private final RestTemplate restTemplate;
    private final OpenAIConfig config;

    public CompletableFuture<ChatCompletionResponse> createChatCompletion(
        ChatCompletionRequest request) {
        // 实现HTTP调用
    }

    public void streamChatCompletion(
        ChatCompletionRequest request,
        Consumer<ChatCompletionChunk> chunkConsumer) {
        // 实现流式响应
    }
}
```

### OpenAILLMProvider.java
```java
@Component
@ConditionalOnProperty(name = "pi.llm.openai.enabled", havingValue = "true")
public class OpenAILLMProvider implements LLMProvider {
    private final OpenAIClient client;
    private final OpenAIConfig config;

    @Override
    public CompletableFuture<AgentMessage> sendMessage(String sessionId, AgentMessage message) {
        // 实现OpenAI API调用
        // 处理流式响应
        // 实现错误重试
    }
}
```

## 📦 依赖管理

### 需要添加的依赖
在`pi-llm/pom.xml`中添加:
```xml
<dependencies>
    <!-- HTTP客户端 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- JSON处理 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- Lombok (可选) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## 🧪 测试计划

### 单元测试
```bash
# 测试OpenAI配置
mvn test -Dtest=OpenAIConfigTest

# 测试HTTP客户端
mvn test -Dtest=OpenAIClientTest
```

### 集成测试
```bash
# 测试完整的OpenAI提供者
mvn test -Dtest=OpenAILLMProviderIntegrationTest
```

### 手动测试
```bash
# 使用真实API密钥测试
mvn spring-boot:run -Dspring.profiles.active=openai
```

## 🎯 成功标准

### 功能标准
- [ ] OpenAI API正常调用
- [ ] 支持gpt-3.5-turbo和gpt-4模型
- [ ] 流式响应正常工作
- [ ] 错误处理和重试机制
- [ ] 配置管理完善

### 性能标准
- [ ] 响应时间 < 5秒
- [ ] 支持并发请求
- [ ] 内存使用合理
- [ ] 连接池复用

### 质量标准
- [ ] 代码覆盖率 > 80%
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 文档完整

## ⏰ 时间安排

### Day 1 (今天)
- [ ] 创建OpenAIConfig.java
- [ ] 创建OpenAIClient.java基础结构
- [ ] 添加必要的Maven依赖

### Day 2
- [ ] 完成OpenAIClient.java实现
- [ ] 创建OpenAILLMProvider.java
- [ ] 实现基本的API调用

### Day 3
- [ ] 实现流式响应支持
- [ ] 添加错误处理和重试
- [ ] 完成OpenAILLMProvider.java

### Day 4
- [ ] 修改LLMProviderManager.java注册
- [ ] 编写单元测试
- [ ] 编写集成测试

### Day 5
- [ ] 完整测试和调试
- [ ] 文档编写
- [ ] 代码审查和优化

## 🚀 开始开发

**第一步**: 创建OpenAI配置类
```bash
mkdir -p pi-llm/src/main/java/com/pi/mono/llm/config
touch pi-llm/src/main/java/com/pi/mono/llm/config/OpenAIConfig.java
```

**第二步**: 添加必要的依赖到pom.xml

**第三步**: 开始实现配置类

**准备好了吗？开始第一个真实LLM提供者的开发！** 🎉
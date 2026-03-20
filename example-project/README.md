# Example Project - Pi-Mono Java 使用示例

## 📁 项目结构

```
example-project/
├── pom.xml                           # Maven配置文件
├── src/main/java/com/example/myapp/
│   └── PiAppApplication.java         # 主应用类
└── src/main/resources/
    └── application.yml               # Spring配置文件
```

## 🚀 快速开始

### 1. 编译项目

```bash
cd example-project
mvn clean compile
```

**预期结果**:
```
[INFO] BUILD SUCCESS
```

### 2. 运行Spring应用

```bash
mvn spring-boot:run
```

**预期输出**:
```
会话创建成功: [session-id]
AI响应: This is a mock response to: Hello, Pi-Mono Java!
会话已保存
```

### 3. 验证会话文件

运行后检查生成的会话文件：

```bash
ls -la .pi/sessions/
cat .pi/sessions/*.jsonl
```

**预期结果**:
- 生成JSONL格式的会话文件
- 包含完整的消息链

## 📋 项目配置说明

### pom.xml
```xml
<dependencies>
    <!-- Pi-Mono Java Starter -->
    <dependency>
        <groupId>com.pi.mono</groupId>
        <artifactId>pi-starter</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

### application.yml
```yaml
pi:
  session:
    dir: .pi/sessions
  llm:
    default-model: mock-claude

spring:
  application:
    name: my-pi-app
```

### PiAppApplication.java
```java
@SpringBootApplication
public class PiAppApplication implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;

    @Override
    public void run(String... args) throws Exception {
        // 1. 创建会话
        String sessionId = sessionManager.createSession("mock-claude");

        // 2. 发送消息
        var response = sessionManager.sendMessage("Hello, Pi-Mono Java!");
        System.out.println("AI响应: " + response.get().content());

        // 3. 保存会话
        sessionManager.saveSession();
    }
}
```

## 🎯 功能演示

这个示例项目演示了Pi-Mono Java的核心功能：

1. **会话管理**: 创建和管理AI会话
2. **消息交互**: 与AI模型进行消息交互
3. **会话持久化**: 自动保存会话到JSONL文件
4. **Spring集成**: 完整的Spring Boot集成

## 🔧 自定义开发

### 修改配置
在 `application.yml` 中修改配置：

```yaml
pi:
  session:
    dir: custom/sessions/path  # 自定义会话存储路径
  llm:
    default-model: openai      # 切换到其他LLM提供者（需要实现）
```

### 添加功能
在 `PiAppApplication.java` 中添加更多功能：

```java
// 发送多条消息
sessionManager.sendMessage("第一条消息");
sessionManager.sendMessage("第二条消息");

// 获取会话列表
var sessions = sessionManager.listSessions();

// 加载现有会话
sessionManager.loadSession("session-id");
```

## 📊 测试验证

### 编译测试
```bash
$ mvn clean compile
[INFO] BUILD SUCCESS
```

### 运行测试
```bash
$ mvn spring-boot:run
会话创建成功: 12345-abcde
AI响应: This is a mock response to: Hello, Pi-Mono Java!
会话已保存
```

### 文件验证
```bash
$ ls .pi/sessions/
12345-abcde.jsonl

$ head -n 2 .pi/sessions/12345-abcde.jsonl
{"id":"01KKXDCA7NH7FP5M5BA4KAA03F","parentId":null,"message":{"role":"SYSTEM","content":"Session created with model: mock-claude",...}}
{"id":"01KKXDCA7N7SH9NK2RGQBBBCSD","parentId":"01KKXDCA7NH7FP5M5BA4KAA03F","message":{"role":"USER","content":"Hello, Pi-Mono Java!",...}}
```

## 🎉 成功标志

当您看到以下输出时，说明示例项目运行成功：

✅ **编译成功**: `BUILD SUCCESS`
✅ **会话创建**: "会话创建成功: [session-id]"
✅ **AI响应**: "AI响应: [response content]"
✅ **会话保存**: "会话已保存"
✅ **文件生成**: `.pi/sessions/` 目录下有JSONL文件

## 🚀 下一步

1. **集成到您的项目**: 将pi-starter依赖添加到您的Spring项目
2. **自定义LLM提供者**: 实现真实的LLM API集成
3. **扩展功能**: 添加更多工具和功能
4. **生产部署**: 配置生产环境参数

---

**Example Project 位置**: `/Users/wangzq/VsCodeProjects/pi-mono-java/example-project/`
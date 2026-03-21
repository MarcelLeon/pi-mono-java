# Spring Pi-Mono 测试项目

这是一个专门用于测试Pi-Mono Java与Spring集成的示例项目。

## 🎯 项目目标

验证Pi-Mono Java在Spring项目中的完整集成功能，包括：
- 依赖集成
- 自动配置
- 组件注入
- 会话管理
- 多会话支持

## 🚀 快速开始

### 1. 编译Pi-Mono-Java
```bash
# 在pi-mono-java根目录
mvn clean install
```

### 2. 编译测试项目
```bash
cd spring-test-example
mvn clean compile
```

### 3. 运行测试
```bash
mvn spring-boot:run
```

## 📋 测试内容

### 测试1: 创建会话
- 验证SessionManager能正确创建会话
- 检查会话ID格式

### 测试2: 发送消息
- 验证与AI模型的交互
- 检查消息格式和响应

### 测试3: 保存会话
- 验证会话持久化功能
- 检查JSONL文件格式

### 测试4: 获取会话列表
- 验证会话管理功能
- 检查会话列表正确性

### 测试5: 多会话支持
- 验证同时管理多个会话
- 检查会话隔离

### 测试6: 会话切换
- 验证会话切换功能
- 检查上下文保持

## 📁 项目结构

```
spring-test-example/
├── pom.xml                           # Maven配置
├── src/main/java/com/test/spring/
│   └── SpringTestApplication.java    # 主应用类
└── src/main/resources/
    └── application.yml               # Spring配置
```

## 🔧 配置说明

### Pi-Mono配置
```yaml
pi:
  session:
    dir: .pi/sessions  # 会话存储目录
  llm:
    default-model: mock-claude  # 默认模型
    openai:
      enabled: true
      api-key: sk-your-api-key-here
      model: gpt-3.5-turbo
      timeout: 30s
      max-retries: 3
  tools:
    permissions:
      read: ["read"]      # 读取文件权限
      write: ["write"]    # 写入文件权限
      bash: ["system"]    # Bash命令权限
      edit: ["write"]     # 编辑文件权限
```

### Spring配置
```yaml
spring:
  application:
    name: spring-pi-mono-test
  profiles:
    active: dev

logging:
  level:
    com.pi.mono: DEBUG
    org.springframework: INFO
```

## 🧪 运行结果

成功运行后，您应该看到类似以下输出：

```
🚀 Pi-Mono Java Spring集成测试开始
📋 测试1: 创建会话
✅ 会话创建成功: 01JQZ4Z3YH2N0G5GJ3QZ9S1T13
💬 测试2: 发送消息
✅ AI响应: [Mock response content]
   角色: ASSISTANT
   元数据: {}
💾 测试3: 保存会话
✅ 会话保存成功
📋 测试4: 获取会话列表
✅ 会话列表: [session1, session2]
📋 测试5: 测试多个会话
✅ 第二个会话响应: [Mock response content]
📋 测试6: 会话切换
✅ 切换会话响应: [Mock response content]
🎉 所有Spring集成测试通过！
```

## 🐛 故障排除

### 常见问题

1. **SessionManager注入失败**
   - 检查pi-starter依赖是否正确添加
   - 确认Spring Boot Starter自动配置生效

2. **编译失败**
   - 确保Pi-Mono-Java已正确安装到本地Maven仓库
   - 检查Java版本是否为21+

3. **配置不生效**
   - 检查application.yml配置格式
   - 确认配置属性名称正确

### 调试建议

1. 启用DEBUG日志查看详细信息
2. 检查会话文件是否正确创建
3. 验证Spring上下文是否正确加载

## 📞 支持

如有问题，请：
1. 查看 `docs/spring-testing-guide.md`
2. 在仓库根目录运行 `./scripts/benchmark_smoke.sh`
3. 提交GitHub Issues

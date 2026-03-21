# Pi-Mono-Java 测试验收计划

## 🎯 验收目标
验证Pi-Mono-Java的核心功能是否完全可用，确保可以作为依赖集成到Spring项目或作为独立CLI应用使用。

## 📊 验收范围

### 1. 命令行界面 (CLI) 验收
### 2. Java项目集成验收
### 3. OpenAI集成验收
### 4. 企业级特性验收
### 5. 性能和稳定性验收

## 🚀 快速验收 (5分钟)

### CLI快速测试
```bash
# 1. 编译项目
mvn clean compile

# 2. 运行CLI
mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication"

# 3. 在CLI中测试
pi> 今天天气很好
pi> /save
pi> /sessions
pi> exit
```

### Spring项目快速测试
```bash
# 1. 进入示例项目
cd example-project

# 2. 运行示例
mvn spring-boot:run

# 3. 验证输出
# 应该看到: "Pi-Mono Java application started successfully!"
```

## 📝 详细验收步骤

### 一、CLI功能验收 (30分钟)

#### 1.1 基础功能测试
- [ ] 编译成功验证
- [ ] CLI启动成功
- [ ] 帮助命令工作正常
- [ ] 会话创建成功
- [ ] 消息发送和AI响应正常

#### 1.2 会话管理测试
- [ ] `/save` 命令成功保存会话
- [ ] `/sessions` 命令显示会话列表
- [ ] 会话文件正确生成(.pi/sessions/)
- [ ] JSONL格式符合标准

#### 1.3 工具系统测试
- [ ] `tools` 命令显示可用工具
- [ ] `tool read` 功能正常
- [ ] `tool write` 功能正常
- [ ] `tool edit` 功能正常
- [ ] `tool bash` 功能正常
- [ ] 工具权限管理正常

### 二、Java项目集成验收 (45分钟)

#### 2.1 Spring Boot Starter集成测试
```java
// 测试代码示例
@Autowired
private SessionManager sessionManager;

// 创建会话
String sessionId = sessionManager.createSession("mock-claude");

// 发送消息
var response = sessionManager.sendMessage("Hello, Pi-Mono Java!");
System.out.println("AI响应: " + response.get().content());

// 保存会话
sessionManager.saveSession();
```

#### 2.2 配置管理测试
- [ ] application.yml配置正确加载
- [ ] OpenAI配置参数生效
- [ ] 会话目录配置正确
- [ ] 工具权限配置生效

#### 2.3 示例项目验证
```bash
cd example-project
mvn spring-boot:run
# 验证应用启动成功
```

### 三、OpenAI集成验收 (30分钟)

#### 3.1 真实API调用测试
- [ ] OpenAIClient编译成功
- [ ] OpenAILLMProvider编译成功
- [ ] Spring自动配置生效
- [ ] 健康检查通过

#### 3.2 配置验证
```yaml
pi:
  llm:
    openai:
      enabled: true
      api-key: sk-your-api-key-here
      model: gpt-3.5-turbo
      timeout: 30s
      max-retries: 3
```

#### 3.3 错误处理测试
- [ ] API密钥错误处理
- [ ] 网络连接错误处理
- [ ] 超时处理

### 四、企业级特性验收 (20分钟)

#### 4.1 性能测试
- [ ] 编译时间合理(< 2分钟)
- [ ] 启动时间合理(< 30秒)
- [ ] 内存使用正常

#### 4.2 安全性测试
- [ ] 工具权限验证
- [ ] 文件操作安全限制
- [ ] Bash命令沙箱

#### 4.3 监控和日志
- [ ] 日志输出正常
- [ ] 健康检查端点
- [ ] 配置属性验证

### 五、文档和示例验收 (15分钟)

#### 5.1 文档完整性
- [ ] README.md内容完整
- [ ] 快速开始指南可用
- [ ] 能力对比文档完整
- [ ] 开发日志更新

#### 5.2 示例项目
- [ ] example-project完整可用
- [ ] 示例代码正确
- [ ] 配置文件正确

## 🧪 自动化测试脚本

### 推荐：统一冒烟验收（开源发布前）
```bash
# 生成带日志与耗时的验收报告
./scripts/benchmark_smoke.sh
```

## 📈 验收标准

### ✅ 通过标准
- 所有CLI功能正常工作
- Spring项目集成成功
- OpenAI集成框架完整
- 文档和示例完整
- 编译和运行无错误

### ❌ 失败标准
- 编译失败
- 核心功能不可用
- 集成失败
- 文档缺失

## 🎯 验收输出

### 验收报告模板
```
## 🎉 Pi-Mono-Java 验收报告

### ✅ 通过项目
- CLI功能: [通过/失败]
- Spring集成: [通过/失败]
- OpenAI集成: [通过/失败]
- 文档完整性: [通过/失败]
- 示例项目: [通过/失败]

### 📊 性能指标
- 编译时间: [时间]
- 启动时间: [时间]
- 内存使用: [数值]

### 🎯 最终结论
[通过/部分通过/失败]

### 📝 改进建议
[如有]
```

## 🚨 注意事项

1. **API密钥**: OpenAI测试需要有效API密钥
2. **网络连接**: 确保可以访问OpenAI API
3. **Java版本**: 确保Java 21环境
4. **Maven配置**: 确保Maven 3.8+环境
5. **权限设置**: 确保文件读写权限

## 📞 支持信息

如有问题，请检查：
1. [README.md](./README.md) - 完整使用指南
2. [docs/](./docs/) - 详细文档
3. [example-project/](./example-project/) - 示例项目

---

**验收时间**: 建议预留 **2小时** 完整验收
**测试环境**: Java 21 + Maven 3.8+ + Spring Boot 3.4.0

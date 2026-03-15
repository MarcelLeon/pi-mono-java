# Pi-Mono Java

Java版本的pi-mono AI代理框架，提供速度和性能优势，与SpringAI集成。

## 项目状态

✅ **Day 2 进展** - Session管理、LLM集成、工具系统

### 已完成的功能

#### 🏗️ 核心架构
- ✅ Maven多模块项目结构
- ✅ Java 21 + Spring Boot 3.4.0
- ✅ 7个核心模块：
  - `pi-core` - 核心接口和模型
  - `pi-session` - 会话管理（ULID + JSONL）
  - `pi-llm` - LLM提供者抽象
  - `pi-tools` - 工具系统
  - `pi-cli` - 命令行界面
  - `pi-ui` - UI组件（待实现）
  - `pi-starter` - 启动器（待实现）

#### 🔧 Session管理
- ✅ SessionTree - 树状会话结构（支持分支和回滚）
- ✅ SessionManager - 会话管理器
- ✅ SessionPersistence - JSONL持久化
- ✅ ULID ID生成（时间有序）
- ✅ 并发修改检测

#### 🤖 LLM集成
- ✅ LLMProvider接口抽象
- ✅ MockLLMProvider - 测试用Mock实现
- ✅ LLMProviderManager - 提供者管理器
- ✅ 健康检查和故障转移

#### 🛠️ 工具系统
- ✅ ToolDefinition接口
- ✅ ToolManager - 工具管理器
- ✅ 内置工具实现：
  - `read` - 文件读取（安全限制）
  - `write` - 文件写入（自动备份）
  - `edit` - 文件编辑（diff生成）
  - `bash` - Bash命令执行（安全沙箱）

#### 🖥️ CLI界面
- ✅ 命令行交互界面
- ✅ 会话管理命令
- ✅ 工具调用支持
- ✅ 帮助系统

### 技术特点

#### 🚀 性能优势
- **JIT编译** - 更好的运行时性能
- **虚拟线程** - 高并发支持
- **高效内存管理** - 会话树优化
- **快速启动** - 优于TypeScript版本

#### 🛡️ 安全特性
- **工具沙箱** - Bash命令安全限制
- **文件操作验证** - 防止危险操作
- **并发控制** - 会话分支冲突检测
- **资源管理** - 自动清理和备份

#### 🔗 Spring集成
- **Spring Boot** - 企业级应用支持
- **依赖注入** - 组件自动装配
- **健康检查** - 监控和可观测性
- **工厂模式** - 易于集成到现有应用

## 快速开始

### 编译项目
```bash
mvn clean compile
```

### 运行CLI
```bash
mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication"
```

### 使用示例

```bash
🚀 Pi-Mono Java CLI Starting...
Type 'help' for available commands, 'exit' to quit

✅ Created session: 01JQZ4Z3YH2N0G5GJ3QZ9S1T13

pi> help

📚 Available commands:
  help              - Show this help message
  sessions          - List all saved sessions
  tools             - Show available tools
  tool <name> <args> - Execute a tool
  /save             - Save current session
  /new <model>      - Create new session with model
  exit              - Exit the application
  [any text]        - Send message to AI

pi> tools

🔧 Available tools:
  read: Read the contents of a file
  write: Write content to a file (overwrites existing content)
  edit: Edit a file by replacing old_string with new_string
  bash: Execute a bash command with safety restrictions

pi> tool read path='/Users/name/test.txt'

🔧 Tool Result:
  Success: true
  Content: File: /Users/name/test.txt
Size: 1024 bytes
Content:
==================================================
Hello, this is a test file content...
```

## 下一步计划

### Day 3-4: 完善工具系统和集成
- [ ] 添加更多内置工具（grep, find, ls等）
- [ ] 实现工具权限管理
- [ ] 添加工具注册和发现机制
- [ ] 完善安全沙箱

### Week 2: CLI和TUI
- [ ] 完整的命令行界面
- [ ] 终端UI（Lanterna）
- [ ] 不同输出模式支持
- [ ] 配置管理

### Week 3-4: Web组件和扩展
- [ ] REST API端点
- [ ] WebSocket支持
- [ ] 插件架构
- [ ] SDK开发

## 开发指南

### 项目结构
```
pi-mono-java/
├── pi-core/                    # 核心接口和模型
├── pi-session/                 # 会话管理
├── pi-llm/                     # LLM提供者抽象
├── pi-tools/                   # 工具系统
├── pi-cli/                     # 命令行界面
├── pi-ui/                      # UI组件（待实现）
├── pi-starter/                 # 启动器（待实现）
└── pi-test/                    # 测试（待实现）
```

### 添加新工具
1. 实现`ToolDefinition`接口
2. 添加`@Component`注解
3. 工具会自动注册到`ToolManager`

### 添加新LLM提供者
1. 实现`LLMProvider`接口
2. 添加`@Component`注解
3. 在`LLMProviderManager`中自动发现

## 贡献

欢迎贡献代码！请遵循以下步骤：
1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 许可证

本项目采用MIT许可证。
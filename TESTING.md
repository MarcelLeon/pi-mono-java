# Pi-Mono Java 用户测试指南

## 🧪 测试环境准备

### 前置条件
- Java 21+
- Maven 3.6+
- 网络连接（用于下载依赖）

### 编译项目
```bash
cd /Users/wangzq/VsCodeProjects/pi-mono-java
mvn clean compile
```

### 运行CLI
```bash
mvn -pl pi-cli exec:java -Dexec.mainClass="com.pi.mono.cli.PiCliApplication"
```

## 🎯 功能测试

### 1. 基础功能测试

#### 测试会话管理
```bash
# 启动应用后
pi> sessions
# 应该显示当前会话列表

pi> /new test-model
# 创建新会话，应该显示会话ID

pi> /save
# 保存会话，应该显示保存成功

pi> /new another-model
# 创建另一个会话

pi> sessions
# 应该显示两个会话
```

**预期结果**：
- ✅ 会话创建成功
- ✅ 会话保存成功
- ✅ 会话列表正确显示

#### 测试普通对话
```bash
pi> What is Java?
# 发送普通消息到LLM

# 应该看到：
# 👤 You: What is Java?
# 🤖 Pi: [Mock response about Java]
```

**预期结果**：
- ✅ 消息发送成功
- ✅ 收到Mock响应
- ✅ 会话历史正确记录

### 2. 工具系统测试

#### 测试文件工具
```bash
# 测试ls工具
pi> tool ls path='/Users/wangzq/VsCodeProjects/pi-mono-java'
# 应该显示项目目录内容

# 测试read工具
pi> tool read path='/Users/wangzq/VsCodeProjects/pi-mono-java/README.md'
# 应该显示README文件内容

# 测试find工具
pi> tool find path='/Users/wangzq/VsCodeProjects/pi-mono-java' pattern='*.java'
# 应该显示所有Java文件
```

**预期结果**：
- ✅ ls显示目录内容
- ✅ read显示文件内容（带文件信息）
- ✅ find显示匹配的文件列表

#### 测试搜索工具
```bash
# 测试grep工具
pi> tool grep path='/Users/wangzq/VsCodeProjects/pi-mono-java/pi-core' pattern='interface'
# 应该搜索包含"interface"的文件

# 测试find工具搜索内容
pi> tool find path='/Users/wangzq/VsCodeProjects/pi-mono-java' content='Tool'
# 应该显示包含"Tool"的文件名
```

**预期结果**：
- ✅ grep显示匹配的行和文件
- ✅ find按文件名内容搜索

#### 测试权限管理
```bash
# 查看当前权限
pi> perm list
# 应该显示所有权限：read, write, system

# 查看工具权限要求
pi> perm tool bash
# 应该显示bash工具需要system权限

# 移除权限后测试
pi> perm remove system
pi> tool bash command='echo test'
# 应该显示权限被拒绝

# 添加权限后重试
pi> perm add system
pi> tool bash command='echo test'
# 应该成功执行
```

**预期结果**：
- ✅ 权限列表正确显示
- ✅ 权限检查生效
- ✅ 权限管理功能正常

#### 测试写入工具
```bash
# 测试write工具
pi> tool write path='/tmp/test.txt' content='Hello World'
# 应该创建文件并显示成功

# 测试edit工具
pi> tool edit path='/tmp/test.txt' old_string='Hello' new_string='Hi'
# 应该编辑文件并显示diff

# 验证编辑结果
pi> tool read path='/tmp/test.txt'
# 应该显示编辑后的内容：Hi World
```

**预期结果**：
- ✅ 文件创建成功
- ✅ 文件编辑成功（带diff显示）
- ✅ 编辑结果正确

### 3. 安全功能测试

#### 测试文件大小限制
```bash
# 尝试读取大文件（如果有）
pi> tool read path='/path/to/large/file'
# 应该显示文件过大错误
```

**预期结果**：
- ✅ 大文件读取被拒绝
- ✅ 显示友好的错误信息

#### 测试危险命令限制
```bash
# 尝试执行危险命令
pi> tool bash command='rm -rf /'
# 应该显示命令不安全错误

pi> tool bash command='sudo apt install'
# 应该显示命令不安全错误
```

**预期结果**：
- ✅ 危险命令被拒绝
- ✅ 显示安全警告

#### 测试路径验证
```bash
# 尝试访问系统目录
pi> tool read path='/etc/passwd'
# 应该显示文件不可读或路径验证失败
```

**预期结果**：
- ✅ 系统文件访问被限制
- ✅ 显示适当的错误信息

### 4. 高级功能测试

#### 测试会话持久化
```bash
# 创建会话并保存
pi> /new persistent-test
pi> tool write path='/tmp/session-test.txt' content='Test content'
pi> /save

# 退出并重新启动应用
# 重新启动CLI

pi> sessions
# 应该显示之前保存的会话

pi> /new [之前的会话ID]
# 应该能加载会话
```

**预期结果**：
- ✅ 会话正确保存
- ✅ 会话正确加载
- ✅ 会话历史保留

#### 测试工具链
```bash
# 创建测试文件
pi> tool write path='/tmp/chain-test.txt' content='Line 1\nLine 2\nTODO: Fix this\nLine 4'

# 搜索TODO
pi> tool grep path='/tmp/chain-test.txt' pattern='TODO'

# 编辑文件
pi> tool edit path='/tmp/chain-test.txt' old_string='TODO: Fix this' new_string='Fixed!'

# 验证结果
pi> tool read path='/tmp/chain-test.txt'
```

**预期结果**：
- ✅ 工具链正常工作
- ✅ 文件内容正确修改
- ✅ 各工具协同工作

## 🐛 常见问题排查

### 编译问题
```bash
# 如果编译失败
mvn clean compile -X
# 查看详细错误信息

# 检查Java版本
java -version
# 确保是Java 21+
```

### 运行问题
```bash
# 如果CLI无法启动
mvn -pl pi-cli clean compile exec:java
# 重新编译并运行

# 检查依赖
mvn dependency:tree
# 查看依赖树
```

### 工具执行问题
```bash
# 如果工具执行失败
# 检查权限：pi> perm list
# 检查路径是否存在
# 检查文件权限
```

## 📊 性能测试

### 基准测试
```bash
# 测试工具执行速度
time pi> tool ls path='/Users/wangzq/VsCodeProjects/pi-mono-java'

# 测试会话创建速度
time pi> /new performance-test

# 测试文件操作速度
time pi> tool write path='/tmp/perf-test.txt' content='Performance test content'
```

### 内存使用
```bash
# 监控内存使用
# 在另一个终端运行：
jstat -gc $(jps | grep PiCliApplication | awk '{print $1}')
```

## 🎉 测试完成

完成以上测试后，您应该能够验证：

1. ✅ **核心功能** - 会话管理、LLM集成正常
2. ✅ **工具系统** - 7个内置工具全部可用
3. ✅ **权限管理** - 权限系统工作正常
4. ✅ **安全特性** - 安全限制生效
5. ✅ **持久化** - 会话保存和加载正常
6. ✅ **性能** - 响应速度符合预期

如果所有测试都通过，恭喜！您的Pi-Mono Java项目已经具备了完整的AI代理框架功能。
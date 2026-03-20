# Pi-Mono Java - 下一步开发计划

## 🎯 开发优先级和计划

### Phase 1: 真实LLM集成 (P1 - 高优先级)

#### 1.1 OpenAI API集成
**目标**: 实现真实的OpenAI API调用

**需要创建的文件**:
```
pi-llm/src/main/java/com/pi/mono/llm/provider/OpenAILLMProvider.java
pi-llm/src/main/java/com/pi/mono/llm/config/OpenAIConfig.java
pi-llm/src/main/resources/openai-config.yml
```

**核心功能**:
- [ ] ChatCompletion API调用
- [ ] 流式响应支持
- [ ] 错误处理和重试
- [ ] API密钥管理

#### 1.2 Anthropic Claude集成
**目标**: 实现Anthropic Claude API调用

**需要创建的文件**:
```
pi-llm/src/main/java/com/pi/mono/llm/provider/ClaudeLLMProvider.java
pi-llm/src/main/java/com/pi/mono/llm/config/ClaudeConfig.java
```

#### 1.3 配置管理增强
**目标**: 统一的LLM配置管理

**需要修改的文件**:
```
pi-llm/src/main/java/com/pi/mono/llm/config/LLMConfig.java  # 增强
pi-starter/src/main/java/com/pi/mono/starter/PiMonoProperties.java  # 添加LLM配置
```

### Phase 2: REST API和WebSocket (P1 - 高优先级)

#### 2.1 REST API模块
**目标**: 创建独立的Web API模块

**需要创建的新模块**:
```
pi-web/
├── pom.xml
├── src/main/java/com/pi/mono/web/
│   ├── controller/
│   │   ├── SessionController.java
│   │   ├── MessageController.java
│   │   └── ToolController.java
│   ├── config/
│   │   └── WebConfig.java
│   └── dto/
│       ├── SessionDTO.java
│       └── MessageDTO.java
└── src/main/resources/
    └── application-web.yml
```

#### 2.2 WebSocket支持
**目标**: 实现实时通信

**需要添加的文件**:
```
pi-web/src/main/java/com/pi/mono/web/websocket/
├── ChatWebSocketHandler.java
├── WebSocketConfig.java
└── MessageEvent.java
```

### Phase 3: Web界面 (P2 - 中优先级)

#### 3.1 前端集成
**目标**: 创建简单的React前端

**需要创建的目录**:
```
pi-frontend/
├── package.json
├── src/
│   ├── components/
│   │   ├── ChatInterface.jsx
│   │   ├── SessionList.jsx
│   │   └── ToolPanel.jsx
│   ├── services/
│   │   ├── api.js
│   │   └── websocket.js
│   └── App.jsx
└── public/
    └── index.html
```

#### 3.2 静态资源服务
**目标**: Spring Boot提供前端资源

**需要修改的文件**:
```
pi-web/src/main/resources/static/  # 添加前端构建产物
pi-web/src/main/java/com/pi/mono/web/config/StaticResourceConfig.java
```

### Phase 4: 监控和日志 (P2 - 中优先级)

#### 4.1 监控指标
**目标**: 添加性能监控

**需要创建的文件**:
```
pi-monitor/
├── pom.xml
├── src/main/java/com/pi/mono/monitor/
│   ├── metrics/
│   │   ├── SessionMetrics.java
│   │   └── LLMRequestMetrics.java
│   ├── health/
│   │   └── LLMHealthIndicator.java
│   └── aspect/
│       └── MonitoringAspect.java
└── src/main/resources/
    └── monitoring-config.yml
```

#### 4.2 增强日志
**目标**: 结构化日志和审计

**需要修改的文件**:
```
pi-core/src/main/java/com/pi/mono/core/AgentSession.java  # 添加日志
pi-session/src/main/java/com/pi/mono/session/SessionManager.java  # 增强日志
```

## 📋 具体开发任务

### 本周任务 (Week 1)

**Day 1-2: OpenAI集成**
```bash
# 创建OpenAI提供者
mkdir -p pi-llm/src/main/java/com/pi/mono/llm/provider
touch pi-llm/src/main/java/com/pi/mono/llm/provider/OpenAILLMProvider.java
touch pi-llm/src/main/java/com/pi/mono/llm/config/OpenAIConfig.java
```

**Day 3-4: Anthropic集成**
```bash
# 创建Anthropic提供者
touch pi-llm/src/main/java/com/pi/mono/llm/provider/ClaudeLLMProvider.java
touch pi-llm/src/main/java/com/pi/mono/llm/config/ClaudeConfig.java
```

**Day 5: 配置管理**
```bash
# 增强配置管理
# 修改pi-starter中的配置类
# 添加环境变量支持
```

### 下周任务 (Week 2)

**Day 1-3: REST API模块**
```bash
# 创建pi-web模块
mkdir pi-web
# 创建Maven配置和基础结构
# 实现SessionController
```

**Day 4-5: WebSocket**
```bash
# 实现WebSocket支持
# 添加实时消息推送
# 创建前端测试页面
```

## 🔧 技术实现细节

### OpenAI Provider实现示例

```java
@Component
public class OpenAILLMProvider implements LLMProvider {

    private final OpenAIClient client;
    private final OpenAIConfig config;

    @Override
    public CompletableFuture<AgentMessage> sendMessage(String sessionId, AgentMessage message) {
        // 实现OpenAI API调用
        // 支持流式响应
        // 处理错误和重试
    }
}
```

### REST API Controller示例

```java
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<AgentMessage> sendMessage(
        @PathVariable String sessionId,
        @RequestBody MessageRequest request) {
        // 实现消息发送
        // 支持流式响应
    }
}
```

## 📊 进度跟踪

### 当前状态
- ✅ 基础架构: 100%完成
- ✅ 会话管理: 100%完成
- ✅ 工具系统: 100%完成
- ✅ Spring集成: 100%完成
- ❌ 真实LLM: 0%完成
- ❌ REST API: 0%完成
- ❌ Web界面: 0%完成

### 目标状态 (4周后)
- ✅ 基础架构: 100%
- ✅ 会话管理: 100%
- ✅ 工具系统: 100%
- ✅ Spring集成: 100%
- ✅ 真实LLM: 100% (OpenAI + Anthropic)
- ✅ REST API: 100%
- ✅ WebSocket: 100%
- ⚠️ Web界面: 50% (基础界面)

## 🎯 成功标准

### Phase 1成功标准
- [ ] OpenAI API正常调用
- [ ] Anthropic API正常调用
- [ ] 配置管理完善
- [ ] 错误处理健壮

### Phase 2成功标准
- [ ] REST API可访问
- [ ] WebSocket实时通信
- [ ] 前端页面可交互
- [ ] 文档完整

### Phase 3成功标准
- [ ] Web界面美观易用
- [ ] 监控指标可用
- [ ] 日志系统完善
- [ ] 性能优化到位

## 🚀 开始开发

**建议从Phase 1开始，优先实现OpenAI集成**，因为这是用户最需要的核心功能。

**开发命令**:
```bash
# 创建OpenAI模块
mkdir -p pi-llm/src/main/java/com/pi/mono/llm/provider

# 开始实现OpenAI提供者
# (具体实现将在下一个任务中完成)
```

**下一步**: 开始实现OpenAI LLM Provider
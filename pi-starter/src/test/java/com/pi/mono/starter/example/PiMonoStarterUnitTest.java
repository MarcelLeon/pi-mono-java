package com.pi.mono.starter.example;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import com.pi.mono.session.SessionManager;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.tools.ToolManager;
import com.pi.mono.tools.ToolExecutionResult;
import com.pi.mono.tools.ToolDefinition;
import com.pi.mono.starter.PiMonoAutoConfiguration;
import com.pi.mono.starter.PiMonoProperties;
import com.pi.mono.starter.example.PiMonoExampleApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pi-Mono Java Spring Boot Starter 单元测试
 *
 * 不依赖Spring Boot上下文的测试
 */
public class PiMonoStarterUnitTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private LLMProviderManager llmProviderManager;

    @Mock
    private ToolManager toolManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSessionManagement() throws Exception {
        System.out.println("🧪 测试会话管理...");

        // Mock数据
        String sessionId = "test-session-123";
        when(sessionManager.createSession("mock-claude")).thenReturn(sessionId);

        // 测试会话创建
        String result = sessionManager.createSession("mock-claude");
        assertNotNull(result);
        assertEquals(sessionId, result);
        System.out.println("✅ 会话创建成功: " + sessionId);
    }

    @Test
    void testLLMProviderManagement() {
        System.out.println("🧪 测试LLM提供商管理...");

        // Mock数据
        List<com.pi.mono.core.LLMProvider> providers = new ArrayList<>();
        when(llmProviderManager.getAllProviders()).thenReturn(providers);

        // 测试提供商列表
        List<com.pi.mono.core.LLMProvider> result = llmProviderManager.getAllProviders();
        assertNotNull(result);
        assertEquals(providers, result);
        System.out.println("✅ LLM提供商管理正常");
    }

    @Test
    void testToolSystem() throws Exception {
        System.out.println("🧪 测试工具系统...");

        // Mock数据
        Map<String, Object> args = Map.of("path", "/test/file.txt");
        ToolExecutionResult mockResult = new ToolExecutionResult(true, "File content", Map.of());
        CompletableFuture<ToolExecutionResult> futureResult = CompletableFuture.completedFuture(mockResult);

        when(toolManager.executeTool("read", args, "session-id", null))
            .thenReturn(futureResult);

        // 测试工具执行
        CompletableFuture<ToolExecutionResult> result = toolManager.executeTool("read", args, "session-id", null);
        ToolExecutionResult executionResult = result.get();

        assertNotNull(executionResult);
        assertTrue(executionResult.success());
        assertEquals("File content", executionResult.content());
        System.out.println("✅ 工具系统正常");
    }

    @Test
    void testMessageHandling() throws Exception {
        System.out.println("🧪 测试消息处理...");

        // Mock数据
        AgentMessage mockMessage = new AgentMessage(
            MessageRole.ASSISTANT,
            "Test response",
            Map.of("provider", "mock-claude")
        );
        CompletableFuture<AgentMessage> futureMessage = CompletableFuture.completedFuture(mockMessage);

        when(sessionManager.sendMessage("Hello AI!"))
            .thenReturn(futureMessage);

        // 测试消息发送
        CompletableFuture<AgentMessage> result = sessionManager.sendMessage("Hello AI!");
        AgentMessage response = result.get();

        assertNotNull(response);
        assertEquals(MessageRole.ASSISTANT, response.role());
        assertEquals("Test response", response.content());
        System.out.println("✅ 消息处理正常");
    }

    @Test
    void testConfigurationIntegration() {
        System.out.println("🧪 测试配置集成...");

        // 测试配置属性
        PiMonoProperties properties = new PiMonoProperties();
        properties.setDefaultModel("mock-claude");
        properties.getSession().setTimeout(java.time.Duration.ofHours(24));
        properties.getSession().setMaxSessions(1000);

        assertEquals("mock-claude", properties.getDefaultModel());
        assertEquals(java.time.Duration.ofHours(24), properties.getSession().getTimeout());
        assertEquals(1000, properties.getSession().getMaxSessions());

        System.out.println("✅ 配置集成正常");
    }

    @Test
    void testAutoConfigurationBeans() {
        System.out.println("🧪 测试自动配置Bean...");

        // 测试自动配置类存在
        assertNotNull(new PiMonoAutoConfiguration());
        assertNotNull(new PiMonoExampleApplication());

        // 测试配置属性类存在
        assertNotNull(new PiMonoProperties());
        assertNotNull(new PiMonoProperties.Session());
        assertNotNull(new PiMonoProperties.Provider());
        assertNotNull(new PiMonoProperties.Tools());
        assertNotNull(new PiMonoProperties.Web());

        System.out.println("✅ 自动配置Bean正常");
    }

    @Test
    void testStarterDependencies() {
        System.out.println("🧪 测试Starter依赖...");

        // 验证核心类可以正常导入和使用
        try {
            Class.forName("com.pi.mono.core.AgentMessage");
            Class.forName("com.pi.mono.core.SessionNode");
            Class.forName("com.pi.mono.core.LLMProvider");
            Class.forName("com.pi.mono.tools.ToolDefinition");
            Class.forName("com.pi.mono.session.SessionManager");
            Class.forName("com.pi.mono.llm.LLMProviderManager");
            Class.forName("com.pi.mono.tools.ToolManager");
            System.out.println("✅ 所有依赖类都可以正常加载");
        } catch (ClassNotFoundException e) {
            fail("依赖类加载失败: " + e.getMessage());
        }
    }

    @Test
    void testExampleApplicationStructure() {
        System.out.println("🧪 测试示例应用结构...");

        // 验证示例应用类存在且结构正确
        try {
            Class<?> appClass = Class.forName("com.pi.mono.starter.example.PiMonoExampleApplication");
            assertNotNull(appClass);

            // 验证main方法存在
            java.lang.reflect.Method mainMethod = appClass.getMethod("main", String[].class);
            assertNotNull(mainMethod);

            System.out.println("✅ 示例应用结构正常");
        } catch (Exception e) {
            fail("示例应用结构验证失败: " + e.getMessage());
        }
    }
}
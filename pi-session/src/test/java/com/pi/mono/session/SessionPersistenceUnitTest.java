package com.pi.mono.session;

import com.pi.mono.core.*;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.tools.ToolDefinition;
import com.pi.mono.tools.ToolExecutionRequest;
import com.pi.mono.tools.ToolExecutionResult;
import com.pi.mono.tools.ToolManager;
import com.pi.mono.tools.ToolPermissionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class SessionPersistenceUnitTest {

    private SessionManager sessionManager;
    private SessionPersistence sessionPersistence;

    @BeforeEach
    void setUp() {
        // 创建测试实例
        sessionManager = new SessionManager();

        // 创建SessionPersistence实例并设置sessionDir
        sessionPersistence = new SessionPersistence();
        sessionPersistence.setSessionDir("target/test-sessions");

        // 清理测试目录
        try {
            Path testDir = Paths.get("target/test-sessions");
            if (Files.exists(testDir)) {
                Files.list(testDir)
                    .filter(path -> path.toString().endsWith(".jsonl"))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception e) {
                            // 忽略清理错误
                        }
                    });
            }
        } catch (Exception e) {
            // 忽略清理错误
        }
    }

    @Test
    void testSessionPersistenceDirect() {
        System.out.println("🧪 测试会话持久化直接调用...");

        try {
            // 创建会话树
            SessionTree sessionTree = new SessionTree();

            // 创建根节点
            AgentMessage rootMessage = new AgentMessage(
                MessageRole.USER,
                "Test message for save",
                new java.util.HashMap<>()
            );

            SessionNode rootNode = sessionTree.createRoot("test-session", rootMessage);

            // 添加一个子节点
            AgentMessage childMessage = new AgentMessage(
                MessageRole.ASSISTANT,
                "This is a test response",
                new java.util.HashMap<>()
            );

            SessionNode childNode = sessionTree.createBranch(rootNode.id(), childMessage);

            System.out.println("✅ 会话树创建成功");
            System.out.println("  根节点: " + rootNode.id());
            System.out.println("  子节点: " + childNode.id());

            // 保存会话树
            System.out.println("尝试保存会话树...");
            sessionPersistence.saveSessionTree("test-session", sessionTree);
            System.out.println("✅ 会话树保存成功");

            // 验证文件存在
            boolean exists = sessionPersistence.sessionExists("test-session");
            assertTrue(exists, "会话文件应该存在");
            System.out.println("✅ 会话文件存在");

            // 列出所有会话
            List<String> sessions = sessionPersistence.listSessions();
            assertTrue(sessions.contains("test-session"), "会话列表中应该包含新会话");
            System.out.println("✅ 会话列表中包含新会话: " + sessions);

            // 检查文件内容
            Path sessionFile = Paths.get("target/test-sessions", "test-session.jsonl");
            assertTrue(Files.exists(sessionFile), "会话文件应该存在");

            List<String> lines = Files.readAllLines(sessionFile);
            assertFalse(lines.isEmpty(), "会话文件不应该为空");

            System.out.println("会话文件内容:");
            for (String line : lines) {
                System.out.println("  " + line);
            }

            // 验证JSON格式
            for (String line : lines) {
                try {
                    // 简单验证JSON格式
                    assertTrue(line.startsWith("{"), "每行应该是JSON对象");
                    assertTrue(line.endsWith("}"), "每行应该是JSON对象");
                    assertTrue(line.contains("\"id\""), "应该包含id字段");
                    assertTrue(line.contains("\"message\""), "应该包含message字段");
                } catch (Exception e) {
                    fail("JSON格式错误: " + line + " - " + e.getMessage());
                }
            }

            System.out.println("✅ 会话持久化功能测试通过");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
            fail("会话持久化测试失败: " + e.getMessage());
        }
    }

    @Test
    void loadSessionTreeReconstructsNodesAndCurrentBranch() {
        SessionTree originalTree = new SessionTree();
        SessionNode rootNode = originalTree.createRoot("restore-session", new AgentMessage(
            MessageRole.SYSTEM,
            "Session created with model: mock-claude",
            java.util.Map.of("model", "mock-claude")
        ));
        SessionNode userNode = originalTree.createBranch(rootNode.id(), new AgentMessage(
            MessageRole.USER,
            "Please inspect this repository.",
            java.util.Map.of("timestamp", 100L)
        ));
        SessionNode assistantNode = originalTree.createBranch(userNode.id(), new AgentMessage(
            MessageRole.ASSISTANT,
            "I found the session tree.",
            java.util.Map.of("timestamp", 200L)
        ));

        sessionPersistence.saveSessionTree("restore-session", originalTree);

        SessionTree loadedTree = sessionPersistence.loadSessionTree("restore-session");

        assertEquals(rootNode.id(), loadedTree.getRootId());
        assertEquals(assistantNode.id(), loadedTree.getCurrentBranchId());
        assertEquals(3, loadedTree.getAllNodes().size());

        List<SessionNode> loadedPath = loadedTree.getBranchPath(assistantNode.id());
        assertEquals(3, loadedPath.size());
        assertEquals(MessageRole.SYSTEM, loadedPath.get(0).message().role());
        assertEquals("Please inspect this repository.", loadedPath.get(1).message().content());
        assertEquals("I found the session tree.", loadedPath.get(2).message().content());
        assertEquals(List.of(userNode.id()), loadedTree.getChildren(rootNode.id()));
    }

    @Test
    void loadSessionTreePreservesNestedReasoningUsageMetadata() {
        SessionTree originalTree = new SessionTree();
        SessionNode rootNode = originalTree.createRoot("reasoning-usage-session", new AgentMessage(
            MessageRole.SYSTEM,
            "Session created with model: mock-claude",
            java.util.Map.of("model", "mock-claude")
        ));
        SessionNode assistantNode = originalTree.createBranch(rootNode.id(), new AgentMessage(
            MessageRole.ASSISTANT,
            "Reasoning response",
            java.util.Map.of("usage", java.util.Map.of(
                "inputTokens", 10,
                "outputTokens", 4,
                "reasoningTokens", 3
            ))
        ));

        sessionPersistence.saveSessionTree("reasoning-usage-session", originalTree);

        SessionTree loadedTree = sessionPersistence.loadSessionTree("reasoning-usage-session");
        SessionNode loadedAssistantNode = loadedTree.getNode(assistantNode.id()).orElseThrow();

        Object usage = loadedAssistantNode.message().metadata().get("usage");
        assertInstanceOf(java.util.Map.class, usage);
        java.util.Map<?, ?> usageMap = (java.util.Map<?, ?>) usage;
        assertEquals(10L, usageMap.get("inputTokens"));
        assertEquals(4L, usageMap.get("outputTokens"));
        assertEquals(3L, usageMap.get("reasoningTokens"));
    }

    @Test
    void saveSessionTreeRejectsInvalidNonEmptyExistingSessionFile() throws Exception {
        Path sessionFile = Paths.get("target/test-sessions", "invalid-existing.jsonl");
        Files.createDirectories(sessionFile.getParent());
        Files.writeString(sessionFile, "this is not jsonl\n");

        SessionTree replacementTree = new SessionTree();
        replacementTree.createRoot("invalid-existing", new AgentMessage(
            MessageRole.SYSTEM,
            "replacement",
            java.util.Map.of("model", "mock-claude")
        ));

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> sessionPersistence.saveSessionTree("invalid-existing", replacementTree));

        assertTrue(exception.getMessage().contains("Failed to save session"));
        assertEquals("this is not jsonl\n", Files.readString(sessionFile));
    }

    @Test
    void loadSessionRestoresSavedHistoryIntoManager() throws Exception {
        SessionManager firstManager = newSessionManager(sessionPersistence);
        String sessionId = firstManager.createSession("mock-claude");
        firstManager.sendMessage("Restore me later").get();
        firstManager.saveSession();

        SessionManager secondManager = newSessionManager(sessionPersistence);
        secondManager.loadSession(sessionId);

        List<SessionNode> history = secondManager.getSessionHistory();
        assertEquals(3, history.size());
        assertEquals(MessageRole.SYSTEM, history.get(0).message().role());
        assertEquals("Restore me later", history.get(1).message().content());
        assertEquals(MessageRole.ASSISTANT, history.get(2).message().role());
    }

    @Test
    void forkCurrentSessionFromNodeCopiesPathIntoNewSession() throws Exception {
        SessionManager manager = newSessionManager(sessionPersistence);
        manager.createSession("mock-claude");
        manager.sendMessage("Keep this prompt").get();

        SessionNode userNode = manager.getSessionHistory().stream()
            .filter(node -> node.message().role() == MessageRole.USER)
            .findFirst()
            .orElseThrow();

        String forkedSessionId = manager.forkCurrentSessionFromNode(userNode.id(), "mock-claude");

        assertNotNull(forkedSessionId);
        assertNotEquals(manager.getCurrentSessionId(), forkedSessionId);

        manager.loadSession(forkedSessionId);
        List<SessionNode> forkedHistory = manager.getSessionHistory();
        assertEquals(2, forkedHistory.size());
        assertEquals(MessageRole.SYSTEM, forkedHistory.get(0).message().role());
        assertEquals("Keep this prompt", forkedHistory.get(1).message().content());
    }

    @Test
    void createSessionStoresAdditionalRootMetadata() {
        SessionManager manager = newSessionManager(sessionPersistence);

        manager.createSession("mock-claude", java.util.Map.of(
            "contextFileCount", 2,
            "promptTemplateCount", 1,
            "skillCount", 3
        ));

        SessionNode rootNode = manager.getSessionHistory().get(0);
        assertEquals("mock-claude", rootNode.message().metadata().get("model"));
        assertEquals(2, rootNode.message().metadata().get("contextFileCount"));
        assertEquals(1, rootNode.message().metadata().get("promptTemplateCount"));
        assertEquals(3, rootNode.message().metadata().get("skillCount"));
    }

    @Test
    void createSessionWithExplicitIdUsesDeterministicSessionId() {
        SessionManager manager = newSessionManager(sessionPersistence);

        String sessionId = manager.createSessionWithId(
            "stable-session-id",
            "mock-claude",
            java.util.Map.of("ephemeral", true)
        );

        assertEquals("stable-session-id", sessionId);
        assertEquals("stable-session-id", manager.getCurrentSessionId());
        assertEquals(true, manager.getSessionHistory().get(0).message().metadata().get("ephemeral"));
    }

    @Test
    void createSessionWithExplicitIdRejectsBlankIds() {
        SessionManager manager = newSessionManager(sessionPersistence);

        assertThrows(IllegalArgumentException.class,
            () -> manager.createSessionWithId(" ", "mock-claude", java.util.Map.of()));
    }

    @Test
    void renameCurrentSessionPublishesSessionInfoChangedEvent() {
        SessionManager manager = newSessionManager(sessionPersistence);
        List<SessionInfoChangedEvent> events = new ArrayList<>();

        String sessionId = manager.createSession("mock-claude");
        manager.addSessionEventListener(events::add);

        String sessionName = manager.renameCurrentSession("Roadmap\nReview");

        assertEquals("Roadmap Review", sessionName);
        assertEquals("Roadmap Review", manager.getCurrentSessionName().orElseThrow());
        assertEquals(1, events.size());

        SessionInfoChangedEvent event = events.get(0);
        assertEquals("session_info_changed", event.eventType());
        assertEquals(sessionId, event.sessionId());
        assertEquals("Roadmap Review", event.sessionName());
        assertEquals("Roadmap Review", event.metadata().get("sessionName"));
        assertNotNull(event.rootNodeId());

        SessionNode rootNode = manager.getSessionHistory().get(0);
        assertEquals("Roadmap Review", rootNode.message().metadata().get("sessionName"));
        assertNotNull(rootNode.message().metadata().get("sessionNameUpdatedAt"));
    }

    @Test
    void renamedSessionNamePersistsAfterSaveAndLoad() {
        SessionManager firstManager = newSessionManager(sessionPersistence);
        String sessionId = firstManager.createSession("mock-claude");
        firstManager.renameCurrentSession("Merchant Onboarding");
        firstManager.saveSession();

        SessionManager secondManager = newSessionManager(sessionPersistence);
        secondManager.loadSession(sessionId);

        assertEquals("Merchant Onboarding", secondManager.getCurrentSessionName().orElseThrow());
        assertEquals("Merchant Onboarding",
            secondManager.getSessionHistory().get(0).message().metadata().get("sessionName"));
    }

    @Test
    void sendMessageExecutesProviderToolCallsAndContinuesConversation() throws Exception {
        SessionManager manager = newSessionManager(sessionPersistence);
        RecordingProvider provider = new RecordingProvider();
        ToolManager toolManager = new ToolManager(List.of(new WeatherTool()));
        ReflectionTestUtils.setField(toolManager, "permissionManager", new ToolPermissionManager());
        ReflectionTestUtils.setField(manager, "llmProviderManager", new LLMProviderManager(List.of(provider)));
        ReflectionTestUtils.setField(manager, "toolManager", toolManager);

        manager.createSession("mock-claude");

        AgentMessage finalMessage = manager.sendMessage("Should I take an umbrella in Shanghai?").get();

        assertEquals("Take an umbrella in Shanghai: light rain.", finalMessage.content());
        assertEquals(2, provider.requests.size());
        assertEquals("weather", provider.requests.get(0).options().tools().get(0).get("name"));
        assertEquals("sequential", provider.requests.get(0).options().tools().get(0).get("executionMode"));

        List<AgentMessage> secondTurnMessages = provider.requests.get(1).messages();
        AgentMessage toolResultMessage = secondTurnMessages.get(secondTurnMessages.size() - 1);
        assertEquals(MessageRole.TOOL_RESULT, toolResultMessage.role());
        assertEquals("Shanghai: light rain", toolResultMessage.content());
        assertEquals("toolu_weather_1", toolResultMessage.metadata().get("toolCallId"));
        assertEquals("weather", toolResultMessage.metadata().get("toolName"));
        assertEquals(true, toolResultMessage.metadata().get("success"));

        List<SessionNode> history = manager.getSessionHistory();
        assertEquals(List.of(
            MessageRole.SYSTEM,
            MessageRole.USER,
            MessageRole.ASSISTANT,
            MessageRole.TOOL_RESULT,
            MessageRole.ASSISTANT
        ), history.stream().map(node -> node.message().role()).toList());
    }

    @Test
    void sendMessageContinuesThroughMultipleProviderToolCallRounds() throws Exception {
        SessionManager manager = newSessionManager(sessionPersistence);
        MultiRoundProvider provider = new MultiRoundProvider();
        ToolManager toolManager = new ToolManager(List.of(new WeatherTool()));
        ReflectionTestUtils.setField(toolManager, "permissionManager", new ToolPermissionManager());
        ReflectionTestUtils.setField(manager, "llmProviderManager", new LLMProviderManager(List.of(provider)));
        ReflectionTestUtils.setField(manager, "toolManager", toolManager);

        manager.createSession("mock-claude");

        AgentMessage finalMessage = manager.sendMessage("Compare city weather.").get();

        assertEquals("Shanghai and Beijing both need umbrellas.", finalMessage.content());
        assertEquals(3, provider.requests.size());

        List<MessageRole> roles = manager.getSessionHistory().stream()
            .map(node -> node.message().role())
            .toList();
        assertEquals(List.of(
            MessageRole.SYSTEM,
            MessageRole.USER,
            MessageRole.ASSISTANT,
            MessageRole.TOOL_RESULT,
            MessageRole.ASSISTANT,
            MessageRole.TOOL_RESULT,
            MessageRole.ASSISTANT
        ), roles);

        List<AgentMessage> thirdTurnMessages = provider.requests.get(2).messages();
        AgentMessage secondToolResult = thirdTurnMessages.get(thirdTurnMessages.size() - 1);
        assertEquals(MessageRole.TOOL_RESULT, secondToolResult.role());
        assertEquals("Beijing: light rain", secondToolResult.content());
        assertEquals("toolu_weather_2", secondToolResult.metadata().get("toolCallId"));
    }

    private SessionManager newSessionManager(SessionPersistence persistence) {
        SessionManager manager = new SessionManager();
        ReflectionTestUtils.setField(manager, "sessionTree", new SessionTree());
        ReflectionTestUtils.setField(manager, "sessionPersistence", persistence);
        ReflectionTestUtils.setField(manager, "defaultModel", "mock-claude");
        return manager;
    }

    private static final class RecordingProvider implements LLMProvider {
        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public CompletableFuture<AgentMessage> chat(ChatRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return CompletableFuture.completedFuture(new AgentMessage(
                    MessageRole.ASSISTANT,
                    "I will check the weather.",
                    Map.of("toolCalls", List.of(Map.of(
                        "id", "toolu_weather_1",
                        "name", "weather",
                        "arguments", Map.of("city", "Shanghai")
                    )))
                ));
            }
            return CompletableFuture.completedFuture(new AgentMessage(
                MessageRole.ASSISTANT,
                "Take an umbrella in Shanghai: light rain.",
                Map.of()
            ));
        }

        @Override
        public List<Model> getAvailableModels() {
            return List.of(new Model("mock-claude", "recording", "Recording model", 1000, BigDecimal.ZERO));
        }

        @Override
        public ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context) {
            return new ToolCallResult(toolCall.name(), "unused", Map.of());
        }

        @Override
        public HealthStatus health() {
            return HealthStatus.HEALTHY;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public BigDecimal getCostPerToken() {
            return BigDecimal.ZERO;
        }

        @Override
        public String getId() {
            return "recording";
        }
    }

    private static final class MultiRoundProvider implements LLMProvider {
        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public CompletableFuture<AgentMessage> chat(ChatRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return CompletableFuture.completedFuture(toolCallMessage(
                    "Checking Shanghai.",
                    "toolu_weather_1",
                    "Shanghai"
                ));
            }
            if (requests.size() == 2) {
                return CompletableFuture.completedFuture(toolCallMessage(
                    "Checking Beijing too.",
                    "toolu_weather_2",
                    "Beijing"
                ));
            }
            return CompletableFuture.completedFuture(new AgentMessage(
                MessageRole.ASSISTANT,
                "Shanghai and Beijing both need umbrellas.",
                Map.of()
            ));
        }

        private AgentMessage toolCallMessage(String content, String id, String city) {
            return new AgentMessage(
                MessageRole.ASSISTANT,
                content,
                Map.of("toolCalls", List.of(Map.of(
                    "id", id,
                    "name", "weather",
                    "arguments", Map.of("city", city)
                )))
            );
        }

        @Override
        public List<Model> getAvailableModels() {
            return List.of(new Model("mock-claude", "multi-round", "Multi-round model", 1000, BigDecimal.ZERO));
        }

        @Override
        public ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context) {
            return new ToolCallResult(toolCall.name(), "unused", Map.of());
        }

        @Override
        public HealthStatus health() {
            return HealthStatus.HEALTHY;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public BigDecimal getCostPerToken() {
            return BigDecimal.ZERO;
        }

        @Override
        public String getId() {
            return "multi-round";
        }
    }

    private static final class WeatherTool implements ToolDefinition {
        @Override
        public String getName() {
            return "weather";
        }

        @Override
        public String getDescription() {
            return "Reads weather for a city.";
        }

        @Override
        public Map<String, ToolParameter> getParameters() {
            return Map.of("city", new ToolParameter("string", "City name", true, null));
        }

        @Override
        public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
            return CompletableFuture.completedFuture(ToolExecutionResult.success(
                request.arguments().get("city") + ": light rain"
            ));
        }
    }
}

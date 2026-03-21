package com.pi.mono.session;

import com.pi.mono.core.*;
import com.pi.mono.llm.LLMProviderManager;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 会话管理器
 */
@Service
public class SessionManager {

    @Autowired
    private SessionTree sessionTree;

    @Autowired
    private SessionPersistence sessionPersistence;

    @Autowired(required = false)
    private LLMProviderManager llmProviderManager;

    @Value("${pi.llm.default-model:${pi.mono.default-model:mock-claude}}")
    private String defaultModel;

    private String currentSessionId;
    private final Map<String, String> sessionModelMap = new ConcurrentHashMap<>();

    public String createSession(String model) {
        String sessionId = UUID.randomUUID().toString();
        String resolvedModel = (model == null || model.isBlank()) ? defaultModel : model;

        AgentMessage systemMessage = new AgentMessage(
            MessageRole.SYSTEM,
            "Session created with model: " + resolvedModel,
            Map.of("model", resolvedModel, "timestamp", System.currentTimeMillis())
        );

        sessionTree.createRoot(sessionId, systemMessage);
        currentSessionId = sessionId;
        sessionModelMap.put(sessionId, resolvedModel);

        return sessionId;
    }

    public String loadSession(String sessionId) {
        if (!sessionPersistence.sessionExists(sessionId)) {
            throw new IllegalStateException("Session not found: " + sessionId);
        }

        // 加载会话到内存
        // 注意：这里需要重新构建SessionTree，因为它是组件
        // 暂时创建新会话作为占位符
        currentSessionId = sessionId;
        sessionModelMap.putIfAbsent(sessionId, defaultModel);
        return sessionId;
    }

    public CompletableFuture<AgentMessage> sendMessage(String content) {
        if (currentSessionId == null) {
            throw new IllegalStateException("No active session");
        }

        AgentMessage userMessage = new AgentMessage(
            MessageRole.USER,
            content,
            Map.of("timestamp", System.currentTimeMillis())
        );

        // 创建用户消息节点
        SessionNode userNode = sessionTree.createBranch(
            sessionTree.getCurrentBranchId(),
            userMessage
        );

        // 构建会话上下文并调用Provider链路
        List<SessionNode> contextNodes = sessionTree.getBranchPath(userNode.id());
        List<AgentMessage> contextMessages = contextNodes.stream()
            .map(SessionNode::message)
            .toList();

        String currentModel = sessionModelMap.getOrDefault(currentSessionId, defaultModel);
        ChatRequest request = new ChatRequest(
            currentSessionId,
            contextMessages,
            new ChatOptions(currentModel, 0.7, 1000)
        );

        CompletableFuture<AgentMessage> responseFuture;
        if (llmProviderManager != null) {
            try {
                var provider = llmProviderManager.getAvailableProvider(currentModel);
                responseFuture = provider.chat(request).exceptionally(ex -> new AgentMessage(
                    MessageRole.ASSISTANT,
                    "Provider error, fallback response to: " + content,
                    Map.of(
                        "timestamp", System.currentTimeMillis(),
                        "fallback", true,
                        "error", ex.getMessage()
                    )
                ));
            } catch (Exception ex) {
                responseFuture = CompletableFuture.completedFuture(new AgentMessage(
                    MessageRole.ASSISTANT,
                    "No provider available, fallback response to: " + content,
                    Map.of(
                        "timestamp", System.currentTimeMillis(),
                        "fallback", true,
                        "error", ex.getMessage()
                    )
                ));
            }
        } else {
            // 非Spring上下文下的兜底逻辑，保证单元使用场景可运行
            responseFuture = CompletableFuture.completedFuture(new AgentMessage(
                MessageRole.ASSISTANT,
                "This is a mock response to: " + content,
                Map.of("timestamp", System.currentTimeMillis(), "fallback", true)
            ));
        }

        return responseFuture.thenApply(responseMessage -> {
            sessionTree.createBranch(userNode.id(), responseMessage);
            return responseMessage;
        });
    }

    public List<SessionNode> getSessionHistory() {
        if (currentSessionId == null) {
            return List.of();
        }

        return sessionTree.getBranchPath(sessionTree.getCurrentBranchId());
    }

    public void saveSession() {
        if (currentSessionId == null) {
            throw new IllegalStateException("No active session to save");
        }

        sessionPersistence.saveSessionTree(currentSessionId, sessionTree);
    }

    public List<String> listSessions() {
        return sessionPersistence.listSessions();
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void clearCurrentSession() {
        currentSessionId = null;
    }
}

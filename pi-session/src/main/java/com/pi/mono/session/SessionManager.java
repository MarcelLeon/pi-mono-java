package com.pi.mono.session;

import com.pi.mono.core.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Map;
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

    private String currentSessionId;

    public String createSession(String model) {
        String sessionId = UUID.randomUUID().toString();

        AgentMessage systemMessage = new AgentMessage(
            MessageRole.SYSTEM,
            "Session created with model: " + model,
            Map.of("model", model, "timestamp", System.currentTimeMillis())
        );

        sessionTree.createRoot(sessionId, systemMessage);
        currentSessionId = sessionId;

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

        // 模拟LLM响应（简化版）
        AgentMessage responseMessage = new AgentMessage(
            MessageRole.ASSISTANT,
            "This is a mock response to: " + content,
            Map.of("timestamp", System.currentTimeMillis())
        );

        // 创建响应节点
        SessionNode responseNode = sessionTree.createBranch(
            userNode.id(),
            responseMessage
        );

        return CompletableFuture.completedFuture(responseMessage);
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
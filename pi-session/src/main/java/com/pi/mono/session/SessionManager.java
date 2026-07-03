package com.pi.mono.session;

import com.pi.mono.core.*;
import com.pi.mono.llm.LLMProviderManager;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final List<SessionEventListener> sessionEventListeners = new CopyOnWriteArrayList<>();

    public String createSession(String model) {
        return createSession(model, Map.of());
    }

    public String createSession(String model, Map<String, Object> metadata) {
        return createSessionInternal(UUID.randomUUID().toString(), model, metadata);
    }

    public String createSessionWithId(String sessionId, String model, Map<String, ?> metadata) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session id must not be blank");
        }
        return createSessionInternal(sessionId.trim(), model, metadata);
    }

    private String createSessionInternal(String sessionId, String model, Map<String, ?> metadata) {
        String resolvedModel = (model == null || model.isBlank()) ? defaultModel : model;
        Map<String, Object> rootMetadata = new HashMap<>();
        rootMetadata.put("model", resolvedModel);
        rootMetadata.put("timestamp", System.currentTimeMillis());
        if (metadata != null) {
            rootMetadata.putAll(metadata);
        }

        AgentMessage systemMessage = new AgentMessage(
            MessageRole.SYSTEM,
            "Session created with model: " + resolvedModel,
            rootMetadata
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

        sessionTree = sessionPersistence.loadSessionTree(sessionId);
        currentSessionId = sessionId;
        sessionModelMap.put(sessionId, readModelFromRoot(sessionTree));
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

    public String forkCurrentSessionFromNode(String nodeId, String model) {
        if (currentSessionId == null) {
            throw new IllegalStateException("No active session to fork");
        }

        List<SessionNode> path = sessionTree.getBranchPath(nodeId);
        if (path.isEmpty()) {
            throw new IllegalStateException("Node not found: " + nodeId);
        }

        String forkedSessionId = UUID.randomUUID().toString();
        SessionTree forkedTree = new SessionTree();
        forkedTree.restore(copyPath(path, model), nodeId);
        sessionPersistence.saveSessionTree(forkedSessionId, forkedTree);
        sessionModelMap.put(forkedSessionId, model == null || model.isBlank() ? readModelFromRoot(forkedTree) : model);
        return forkedSessionId;
    }

    public Path exportSession(Path destination) {
        if (currentSessionId == null) {
            throw new IllegalStateException("No active session to export");
        }
        return sessionPersistence.exportSession(currentSessionId, destination);
    }

    public String importSession(Path source) {
        String sessionId = sessionPersistence.importSession(source);
        loadSession(sessionId);
        return sessionId;
    }

    public void addSessionEventListener(SessionEventListener listener) {
        if (listener != null) {
            sessionEventListeners.add(listener);
        }
    }

    public void removeSessionEventListener(SessionEventListener listener) {
        sessionEventListeners.remove(listener);
    }

    public String renameCurrentSession(String sessionName) {
        if (currentSessionId == null) {
            throw new IllegalStateException("No active session to rename");
        }

        String normalizedName = normalizeSessionName(sessionName);
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("Session name must not be blank");
        }

        SessionNode rootNode = sessionTree.getNode(sessionTree.getRootId())
            .orElseThrow(() -> new IllegalStateException("Current session does not have a root node"));
        AgentMessage rootMessage = rootNode.message();
        Map<String, Object> metadata = new HashMap<>(rootMessage.metadata());
        metadata.put("sessionName", normalizedName);
        metadata.put("sessionNameUpdatedAt", System.currentTimeMillis());

        sessionTree.updateNodeMessage(rootNode.id(), new AgentMessage(
            rootMessage.role(),
            rootMessage.content(),
            metadata
        ));

        publishSessionInfoChanged(normalizedName, metadata);
        return normalizedName;
    }

    public Optional<String> getCurrentSessionName() {
        if (currentSessionId == null) {
            return Optional.empty();
        }

        return sessionTree.getNode(sessionTree.getRootId())
            .map(SessionNode::message)
            .map(AgentMessage::metadata)
            .map(metadata -> metadata.get("sessionName"))
            .map(String::valueOf)
            .filter(name -> !name.isBlank());
    }

    public String getCurrentBranchId() {
        return sessionTree.getCurrentBranchId();
    }

    public List<SessionNode> getAllNodes() {
        return List.copyOf(sessionTree.getAllNodes());
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

    private List<SessionNode> copyPath(List<SessionNode> path, String model) {
        return path.stream()
            .map(node -> copyNode(node, model))
            .toList();
    }

    private SessionNode copyNode(SessionNode node, String model) {
        AgentMessage message = node.message();
        Map<String, Object> messageMetadata = new HashMap<>(message.metadata());
        if (node.parentId() == null && model != null && !model.isBlank()) {
            messageMetadata.put("model", model);
        }

        return new SessionNode(
            node.id(),
            node.parentId(),
            new AgentMessage(message.role(), message.content(), messageMetadata),
            node.timestamp(),
            new HashMap<>(node.metadata()),
            node.tokenUsage(),
            node.version(),
            node.snapshotId()
        );
    }

    private String readModelFromRoot(SessionTree tree) {
        return tree.getNode(tree.getRootId())
            .map(SessionNode::message)
            .map(AgentMessage::metadata)
            .map(metadata -> metadata.getOrDefault("model", defaultModel))
            .map(String::valueOf)
            .orElse(defaultModel);
    }

    private String normalizeSessionName(String sessionName) {
        if (sessionName == null) {
            return "";
        }
        return sessionName.replace('\r', ' ')
            .replace('\n', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private void publishSessionInfoChanged(String sessionName, Map<String, Object> metadata) {
        SessionInfoChangedEvent event = new SessionInfoChangedEvent(
            "session_info_changed",
            currentSessionId,
            sessionTree.getRootId(),
            sessionName,
            metadata
        );
        sessionEventListeners.forEach(listener -> listener.onSessionInfoChanged(event));
    }
}

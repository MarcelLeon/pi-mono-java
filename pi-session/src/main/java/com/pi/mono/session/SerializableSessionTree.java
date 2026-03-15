package com.pi.mono.session;

import com.github.f4b6a3.ulid.UlidCreator;
import com.pi.mono.core.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 可序列化的会话树（用于持久化）
 */
public class SerializableSessionTree {
    private final Map<String, SessionNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> children = new ConcurrentHashMap<>();

    private String rootId;
    private String currentBranchId;

    // 默认构造函数（用于JSON反序列化）
    public SerializableSessionTree() {}

    // 从现有SessionTree创建
    public SerializableSessionTree(SessionTree sessionTree) {
        this.nodes.putAll(sessionTree.getAllNodes().stream()
            .collect(Collectors.toMap(SessionNode::id, node -> node)));
        this.rootId = sessionTree.getRootId();
        this.currentBranchId = sessionTree.getCurrentBranchId();

        // 重建children映射
        for (SessionNode node : nodes.values()) {
            if (node.parentId() != null) {
                children.computeIfAbsent(node.parentId(), k -> new ArrayList<>())
                       .add(node.id());
            }
        }
    }

    public SessionNode createRoot(String sessionId, AgentMessage message) {
        String rootId = UlidCreator.getUlid().toString();
        SessionNode rootNode = new SessionNode(
            rootId,
            null,
            message,
            LocalDateTime.now(),
            new HashMap<>(),
            estimateTokens(message),
            1,
            Optional.empty()
        );

        this.rootId = rootId;
        this.currentBranchId = rootId;
        nodes.put(rootId, rootNode);
        return rootNode;
    }

    public SessionNode createBranch(String parentId, AgentMessage message) {
        SessionNode parentNode = nodes.get(parentId);
        if (parentNode == null) {
            throw new IllegalStateException("Parent node not found: " + parentId);
        }

        String nodeId = UlidCreator.getUlid().toString();
        SessionNode newNode = new SessionNode(
            nodeId,
            parentId,
            message,
            LocalDateTime.now(),
            new HashMap<>(),
            estimateTokens(message),
            parentNode.version() + 1,
            Optional.empty()
        );

        nodes.put(nodeId, newNode);
        children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(nodeId);
        currentBranchId = nodeId;
        return newNode;
    }

    public List<SessionNode> getBranchPath(String nodeId) {
        List<SessionNode> path = new ArrayList<>();
        SessionNode current = nodes.get(nodeId);

        while (current != null) {
            path.add(0, current);
            current = nodes.get(current.parentId());
        }

        return path;
    }

    public String getCurrentBranchId() {
        return currentBranchId;
    }

    public String getRootId() {
        return rootId;
    }

    public Collection<SessionNode> getAllNodes() {
        return nodes.values();
    }

    public List<String> getChildren(String parentId) {
        return children.getOrDefault(parentId, Collections.emptyList());
    }

    private int estimateTokens(AgentMessage message) {
        return message.content().length() / 4; // 简单估算
    }
}
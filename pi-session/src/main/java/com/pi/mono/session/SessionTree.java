package com.pi.mono.session;

import com.github.f4b6a3.ulid.UlidCreator;
import com.pi.mono.core.*;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 会话树实现
 */
@Component
public class SessionTree {
    private final Map<String, SessionNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<String>> children = new ConcurrentHashMap<>();

    private String rootId;
    private String currentBranchId;

    // 创建根节点
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

    // 创建分支
    public SessionNode createBranch(String parentId, AgentMessage message) {
        SessionNode parentNode = nodes.get(parentId);
        if (parentNode == null) {
            throw new IllegalStateException("Parent node not found: " + parentId);
        }

        // 检查并发修改冲突
        if (isBranchModified(parentId, parentNode.version())) {
            throw new ConcurrentModificationException(
                "Branch has been modified by another operation");
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

    // 获取节点路径
    public List<SessionNode> getBranchPath(String nodeId) {
        List<SessionNode> path = new ArrayList<>();
        SessionNode current = nodes.get(nodeId);

        while (current != null) {
            path.add(0, current);
            String parentId = current.parentId();
            if (parentId == null) {
                break;
            }
            current = nodes.get(parentId);
        }

        return path;
    }

    // 检查分支是否被修改
    private boolean isBranchModified(String nodeId, int expectedVersion) {
        SessionNode current = nodes.get(nodeId);
        return current != null && current.version() > expectedVersion;
    }

    // 估算Token数量
    private int estimateTokens(AgentMessage message) {
        return message.content().length() / 4; // 简单估算
    }

    // 获取当前分支ID
    public String getCurrentBranchId() {
        return currentBranchId;
    }

    // 获取根节点ID
    public String getRootId() {
        return rootId;
    }

    // 获取所有节点
    public Collection<SessionNode> getAllNodes() {
        return nodes.values();
    }

    // 获取子节点
    public List<String> getChildren(String parentId) {
        return children.getOrDefault(parentId, Collections.emptyList());
    }

    public Optional<SessionNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public SessionNode updateNodeMessage(String nodeId, AgentMessage message) {
        SessionNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Node not found: " + nodeId);
        }

        SessionNode updatedNode = new SessionNode(
            node.id(),
            node.parentId(),
            message,
            node.timestamp(),
            new HashMap<>(node.metadata()),
            estimateTokens(message),
            node.version(),
            node.snapshotId()
        );
        nodes.put(nodeId, updatedNode);
        return updatedNode;
    }

    public void restore(Collection<SessionNode> restoredNodes, String currentBranchId) {
        nodes.clear();
        children.clear();
        rootId = null;
        this.currentBranchId = null;

        for (SessionNode node : restoredNodes) {
            nodes.put(node.id(), node);
            if (node.parentId() == null) {
                rootId = node.id();
            } else {
                children.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node.id());
            }
        }

        if (rootId == null && !restoredNodes.isEmpty()) {
            throw new IllegalArgumentException("Restored session tree does not contain a root node");
        }

        this.currentBranchId = currentBranchId != null ? currentBranchId : findNewestLeafId(restoredNodes);
    }

    private String findNewestLeafId(Collection<SessionNode> restoredNodes) {
        Set<String> parentIds = new HashSet<>();
        for (SessionNode node : restoredNodes) {
            if (node.parentId() != null) {
                parentIds.add(node.parentId());
            }
        }

        return restoredNodes.stream()
            .filter(node -> !parentIds.contains(node.id()))
            .max(Comparator.comparing(SessionNode::timestamp))
            .map(SessionNode::id)
            .orElse(rootId);
    }
}

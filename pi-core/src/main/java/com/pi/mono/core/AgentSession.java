package com.pi.mono.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Agent会话接口
 */
public interface AgentSession {
    String getId();
    List<SessionNode> getBranchPath(String nodeId);
    CompletableFuture<AgentMessage> sendMessage(String content);
    SessionNode createBranch(String parentId, AgentMessage message);
}
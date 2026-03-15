package com.pi.mono.core;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

/**
 * 会话节点（使用ULID）
 */
public record SessionNode(
    String id,           // ULID格式
    String parentId,     // ULID格式
    AgentMessage message,
    LocalDateTime timestamp,
    Map<String, Object> metadata,
    int tokenUsage,
    int version,
    Optional<String> snapshotId
) {}
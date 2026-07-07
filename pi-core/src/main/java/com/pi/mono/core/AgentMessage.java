package com.pi.mono.core;

import java.util.Map;

/**
 * Agent消息
 */
public record AgentMessage(
    MessageRole role,
    String content,
    Map<String, Object> metadata
) {
    public AgentMessage {
        content = content == null ? "" : content;
        metadata = metadata == null ? Map.of() : metadata;
    }
}

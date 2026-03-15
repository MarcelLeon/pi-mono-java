package com.pi.mono.core;

import java.util.List;
import java.util.Map;

/**
 * 聊天请求
 */
public record ChatRequest(
    String sessionId,
    List<AgentMessage> messages,
    ChatOptions options
) {}
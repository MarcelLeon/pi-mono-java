package com.pi.mono.tools;

import java.util.Map;

/**
 * 工具执行请求
 */
public record ToolExecutionRequest(
    String toolName,
    Map<String, Object> arguments,
    String sessionId,
    String nodeId
) {}
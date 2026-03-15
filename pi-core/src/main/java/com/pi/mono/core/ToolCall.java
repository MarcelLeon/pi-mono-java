package com.pi.mono.core;

import java.util.Map;

/**
 * 工具调用
 */
public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments
) {}
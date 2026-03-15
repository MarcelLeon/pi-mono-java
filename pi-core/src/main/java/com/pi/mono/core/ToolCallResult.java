package com.pi.mono.core;

import java.util.List;
import java.util.Map;

/**
 * 工具调用结果
 */
public record ToolCallResult(
    String toolName,
    String result,
    Map<String, Object> metadata
) {}
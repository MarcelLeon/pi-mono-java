package com.pi.mono.tools;

import java.util.Map;

/**
 * 工具执行结果
 */
public record ToolExecutionResult(
    boolean success,
    String content,
    Map<String, Object> metadata
) {
    public static ToolExecutionResult success(String content) {
        return new ToolExecutionResult(true, content, Map.of());
    }

    public static ToolExecutionResult success(String content, Map<String, Object> metadata) {
        return new ToolExecutionResult(true, content, metadata);
    }

    public static ToolExecutionResult failure(String content) {
        return new ToolExecutionResult(false, content, Map.of());
    }

    public static ToolExecutionResult failure(String content, Map<String, Object> metadata) {
        return new ToolExecutionResult(false, content, metadata);
    }
}
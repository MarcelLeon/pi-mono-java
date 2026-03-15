package com.pi.mono.tools;

import com.pi.mono.core.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具定义接口
 */
public interface ToolDefinition {

    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 获取工具参数定义
     */
    Map<String, ToolParameter> getParameters();

    /**
     * 执行工具
     */
    CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request);

    /**
     * 工具参数定义
     */
    record ToolParameter(
        String type,
        String description,
        boolean required,
        Object defaultValue
    ) {}
}
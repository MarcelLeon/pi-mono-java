package com.pi.mono.llm;

import com.pi.mono.core.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * LLM提供商标识接口
 */
public interface LLMProvider {
    CompletableFuture<AgentMessage> chat(ChatRequest request);
    List<Model> getAvailableModels();
    ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context);

    // 健康检查
    HealthStatus health();
    boolean isAvailable();

    // 成本信息
    BigDecimal getCostPerToken();

    String getId();
}
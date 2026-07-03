package com.pi.mono.core;

import java.util.List;
import java.util.Map;

/**
 * 聊天选项
 */
public record ChatOptions(
    String model,
    double temperature,
    int maxTokens,
    String apiKey,
    Map<String, String> env,
    List<Map<String, Object>> tools
) {
    public ChatOptions(String model, double temperature, int maxTokens) {
        this(model, temperature, maxTokens, null, Map.of(), List.of());
    }

    public ChatOptions(String model, double temperature, int maxTokens, String apiKey, Map<String, String> env) {
        this(model, temperature, maxTokens, apiKey, env, List.of());
    }

    public ChatOptions {
        env = env == null ? Map.of() : Map.copyOf(env);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}

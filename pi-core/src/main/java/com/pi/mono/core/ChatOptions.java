package com.pi.mono.core;

/**
 * 聊天选项
 */
public record ChatOptions(
    String model,
    double temperature,
    int maxTokens
) {}
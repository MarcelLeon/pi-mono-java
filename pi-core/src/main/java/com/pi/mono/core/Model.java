package com.pi.mono.core;

import java.math.BigDecimal;

/**
 * 模型信息
 */
public record Model(
    String id,
    String provider,
    String description,
    int maxTokens,
    BigDecimal costPerToken
) {}
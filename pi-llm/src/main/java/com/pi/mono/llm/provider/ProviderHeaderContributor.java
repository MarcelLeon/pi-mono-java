package com.pi.mono.llm.provider;

import com.pi.mono.core.ChatRequest;

import java.util.Map;

/**
 * Contributes per-request provider headers before the HTTP provider call.
 */
@FunctionalInterface
public interface ProviderHeaderContributor {

    Map<String, String> contributeHeaders(ChatRequest request, String providerId, String model);
}

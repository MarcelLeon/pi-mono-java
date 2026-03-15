package com.pi.mono.llm;

import com.pi.mono.core.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM提供者管理器
 */
@Service
public class LLMProviderManager {
    private final List<LLMProvider> providers = new ArrayList<>();
    private final Map<String, HealthStatus> healthCache = new ConcurrentHashMap<>();

    @Autowired
    public LLMProviderManager(List<LLMProvider> providers) {
        this.providers.addAll(providers);
        // 启动健康检查（简化版，暂时不实现定时检查）
        initializeHealthStatus();
    }

    private void initializeHealthStatus() {
        for (LLMProvider provider : providers) {
            healthCache.put(provider.getId(), provider.health());
        }
    }

    public LLMProvider getAvailableProvider(String preferredProvider) {
        // 1. 优先使用指定提供商
        LLMProvider preferred = providers.stream()
            .filter(p -> p.getId().equals(preferredProvider))
            .filter(LLMProvider::isAvailable)
            .findFirst()
            .orElse(null);

        if (preferred != null) {
            return preferred;
        }

        // 2. 降级到其他可用提供商
        return providers.stream()
            .filter(LLMProvider::isAvailable)
            .findFirst()
            .orElseThrow(() -> new NoAvailableProviderException("No LLM provider available"));
    }

    public LLMProvider getDefaultProvider() {
        return getAvailableProvider("mock-claude"); // 默认使用Mock provider
    }

    public List<LLMProvider> getAllProviders() {
        return providers;
    }

    public Map<String, HealthStatus> getHealthStatus() {
        return new HashMap<>(healthCache);
    }

    public void addProvider(LLMProvider provider) {
        providers.add(provider);
        healthCache.put(provider.getId(), provider.health());
    }
}

class NoAvailableProviderException extends RuntimeException {
    public NoAvailableProviderException(String message) {
        super(message);
    }
}
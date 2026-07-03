package com.pi.mono.llm;

import com.pi.mono.core.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
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
        // 1. 优先使用指定提供商或指定模型对应的可用提供商
        LLMProvider preferred = providers.stream()
            .filter(provider -> matchesPreferred(provider, preferredProvider))
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

    public Optional<LLMProvider> resolveProviderForModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }

        return providers.stream()
            .filter(LLMProvider::isAvailable)
            .filter(provider -> provider.getAvailableModels().stream()
                .anyMatch(model -> model.id().equals(modelId)))
            .findFirst();
    }

    public List<Model> getAvailableModels() {
        return providers.stream()
            .filter(LLMProvider::isAvailable)
            .flatMap(provider -> provider.getAvailableModels().stream())
            .toList();
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

    private boolean matchesPreferred(LLMProvider provider, String preferredProvider) {
        if (preferredProvider == null || preferredProvider.isBlank()) {
            return false;
        }
        if (provider.getId().equals(preferredProvider)) {
            return true;
        }
        return provider.getAvailableModels().stream()
            .anyMatch(model -> model.id().equals(preferredProvider));
    }
}

class NoAvailableProviderException extends RuntimeException {
    public NoAvailableProviderException(String message) {
        super(message);
    }
}

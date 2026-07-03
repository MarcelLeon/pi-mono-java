package com.pi.mono.llm;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.core.LLMProvider;
import com.pi.mono.core.Model;
import com.pi.mono.core.ToolCall;
import com.pi.mono.core.ToolCallResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMProviderManagerTest {

    @Test
    void skipsUnavailableProviderWhenPreferredValueIsModelId() {
        LLMProvider unavailableOpenAI = new TestProvider(
            "openai-gpt-5.5",
            false,
            List.of(new Model("gpt-5.5", "openai", "OpenAI GPT-5.5", 400000, BigDecimal.ZERO))
        );
        LLMProvider availableFallback = new TestProvider(
            "mock-claude",
            true,
            List.of(new Model("mock-claude", "mock", "Mock Claude", 100000, BigDecimal.ZERO))
        );
        LLMProviderManager manager = new LLMProviderManager(List.of(unavailableOpenAI, availableFallback));

        LLMProvider provider = manager.getAvailableProvider("gpt-5.5");

        assertEquals("mock-claude", provider.getId());
    }

    @Test
    void selectsAvailableProviderWhenPreferredValueIsModelId() {
        LLMProvider availableOpenAI = new TestProvider(
            "openai-gpt-5.5",
            true,
            List.of(new Model("gpt-5.5", "openai", "OpenAI GPT-5.5", 400000, BigDecimal.ZERO))
        );
        LLMProvider fallback = new TestProvider(
            "mock-claude",
            true,
            List.of(new Model("mock-claude", "mock", "Mock Claude", 100000, BigDecimal.ZERO))
        );
        LLMProviderManager manager = new LLMProviderManager(List.of(fallback, availableOpenAI));

        LLMProvider provider = manager.getAvailableProvider("gpt-5.5");

        assertEquals("openai-gpt-5.5", provider.getId());
    }

    @Test
    void resolvesProviderForModelWithoutFallback() {
        LLMProvider unavailableOpenAI = new TestProvider(
            "openai-gpt-5.5",
            false,
            List.of(new Model("gpt-5.5", "openai", "OpenAI GPT-5.5", 400000, BigDecimal.ZERO))
        );
        LLMProvider availableFallback = new TestProvider(
            "mock-claude",
            true,
            List.of(new Model("mock-claude", "mock", "Mock Claude", 100000, BigDecimal.ZERO))
        );
        LLMProviderManager manager = new LLMProviderManager(List.of(unavailableOpenAI, availableFallback));

        Optional<LLMProvider> resolved = manager.resolveProviderForModel("gpt-5.5");

        assertFalse(resolved.isPresent());
    }

    @Test
    void returnsModelsFromAvailableProvidersOnly() {
        LLMProvider unavailableOpenAI = new TestProvider(
            "openai-gpt-5.5",
            false,
            List.of(new Model("gpt-5.5", "openai", "OpenAI GPT-5.5", 400000, BigDecimal.ZERO))
        );
        LLMProvider availableMock = new TestProvider(
            "mock-claude",
            true,
            List.of(new Model("mock-claude", "mock", "Mock Claude", 100000, BigDecimal.ZERO))
        );
        LLMProvider availableAnthropic = new TestProvider(
            "anthropic",
            true,
            List.of(new Model("claude-sonnet-5", "anthropic", "Claude Sonnet 5", 200000, BigDecimal.ZERO))
        );
        LLMProviderManager manager = new LLMProviderManager(List.of(unavailableOpenAI, availableMock, availableAnthropic));

        List<Model> models = manager.getAvailableModels();

        assertEquals(List.of("mock-claude", "claude-sonnet-5"), models.stream().map(Model::id).toList());
        assertTrue(models.stream().noneMatch(model -> model.id().equals("gpt-5.5")));
    }

    static class TestProvider implements LLMProvider {
        private final String id;
        private final boolean available;
        private final List<Model> models;

        TestProvider(String id, boolean available, List<Model> models) {
            this.id = id;
            this.available = available;
            this.models = models;
        }

        @Override
        public CompletableFuture<AgentMessage> chat(ChatRequest request) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<Model> getAvailableModels() {
            return models;
        }

        @Override
        public ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public HealthStatus health() {
            return available ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public BigDecimal getCostPerToken() {
            return BigDecimal.ZERO;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}

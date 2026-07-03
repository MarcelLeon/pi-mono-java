package com.pi.mono.llm.provider;

import com.pi.mono.core.ChatOptions;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.MessageRole;
import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.Model;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.llm.client.AnthropicClient;
import com.pi.mono.llm.config.AnthropicConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicLLMProviderTest {

    @Test
    void catalogIncludesClaudeSonnet5WithAdaptiveThinkingDescription() {
        AnthropicConfig config = configuredAnthropic();
        AnthropicLLMProvider provider = new AnthropicLLMProvider(config);

        List<Model> models = provider.getAvailableModels();

        assertTrue(models.stream().map(Model::id).anyMatch("claude-sonnet-5"::equals));
        assertTrue(models.stream()
            .filter(model -> model.id().equals("claude-sonnet-5"))
            .findFirst()
            .orElseThrow()
            .description()
            .contains("adaptive thinking"));
    }

    @Test
    void enabledProviderIsHealthyWhenApiKeyConfigured() {
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic());

        assertEquals(HealthStatus.HEALTHY, provider.health());
        assertTrue(provider.isAvailable());
    }

    @Test
    void chatParsesAnthropicMessagesApiResponse() {
        AnthropicLLMProvider provider = new AnthropicLLMProvider(
            configuredAnthropic(),
            new StubAnthropicClient("""
                {
                  "content": [{"type": "text", "text": "hello from claude"}],
                  "usage": {"input_tokens": 11, "output_tokens": 7}
                }
                """)
        );
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("claude-sonnet-5", 0.7, 1000)
        );

        AgentMessage response = provider.chat(request).join();

        assertEquals(MessageRole.ASSISTANT, response.role());
        assertEquals("hello from claude", response.content());
        assertEquals("claude-sonnet-5", response.metadata().get("model"));
        assertEquals(11, response.metadata().get("inputTokens"));
        assertEquals(7, response.metadata().get("outputTokens"));
    }

    @Test
    void chatPassesRequestScopedApiKeyToClient() {
        StubAnthropicClient client = new StubAnthropicClient("""
            {
              "content": [{"type": "text", "text": "scoped auth"}],
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic(), client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("claude-sonnet-5", 0.7, 1000, "request-key", Map.of())
        );

        provider.chat(request).join();

        assertEquals("request-key", client.lastApiKey);
    }

    @Test
    void chatUsesRequestScopedEnvApiKeyWhenExplicitApiKeyIsAbsent() {
        StubAnthropicClient client = new StubAnthropicClient("""
            {
              "content": [{"type": "text", "text": "env auth"}],
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic(), client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("claude-sonnet-5", 0.7, 1000, null, Map.of("ANTHROPIC_API_KEY", "env-request-key"))
        );

        provider.chat(request).join();

        assertEquals("env-request-key", client.lastApiKey);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatSendsToolSchemasAndParsesToolUseBlocks() {
        StubAnthropicClient client = new StubAnthropicClient("""
            {
              "content": [
                {"type": "text", "text": "I will check that."},
                {
                  "type": "tool_use",
                  "id": "toolu_123",
                  "name": "get_weather",
                  "input": {"city": "Shanghai"}
                }
              ],
              "usage": {"input_tokens": 12, "output_tokens": 8}
            }
            """);
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic(), client);
        List<Map<String, Object>> tools = List.of(Map.of(
            "name", "get_weather",
            "description", "Get weather for a city",
            "input_schema", Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string"))
            )
        ));
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "weather?", Map.of())),
            new ChatOptions("claude-sonnet-5", 0.7, 1000, null, Map.of(), tools)
        );

        AgentMessage response = provider.chat(request).join();

        assertEquals(tools, client.lastTools);
        assertEquals("I will check that.", response.content());
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) response.metadata().get("toolCalls");
        assertEquals(1, toolCalls.size());
        assertEquals("toolu_123", toolCalls.get(0).get("id"));
        assertEquals("get_weather", toolCalls.get(0).get("name"));
        assertEquals(Map.of("city", "Shanghai"), toolCalls.get(0).get("arguments"));
    }

    private AnthropicConfig configuredAnthropic() {
        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey("test-key");
        return config;
    }

    static class StubAnthropicClient extends AnthropicClient {
        private final String response;
        private String lastApiKey;
        private List<Map<String, Object>> lastTools;

        StubAnthropicClient(String response) {
            super(new AnthropicConfig());
            this.response = response;
        }

        @Override
        public Mono<String> createMessage(
            String model,
            List<Map<String, String>> messages,
            String system,
            double temperature,
            int maxTokens
        ) {
            return createMessage(model, messages, system, temperature, maxTokens, null);
        }

        @Override
        public Mono<String> createMessage(
            String model,
            List<Map<String, String>> messages,
            String system,
            double temperature,
            int maxTokens,
            String apiKey
        ) {
            return createMessage(model, messages, system, temperature, maxTokens, List.of(), apiKey);
        }

        @Override
        public Mono<String> createMessage(
            String model,
            List<Map<String, String>> messages,
            String system,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            String apiKey
        ) {
            this.lastApiKey = apiKey;
            this.lastTools = tools;
            return Mono.just(response);
        }
    }
}

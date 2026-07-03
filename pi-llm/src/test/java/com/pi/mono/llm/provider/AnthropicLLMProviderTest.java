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

    @Test
    void chatConvertsImageAttachmentsToAnthropicContentBlocks() {
        StubAnthropicClient client = new StubAnthropicClient("""
            {
              "content": [{"type": "text", "text": "image received"}],
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic(), client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.USER,
                "Inspect this @chart.png\n\n[Attached files]\n"
                    + "<attachment path=\"chart.png\" resolvedPath=\"/tmp/chart.png\">\n"
                    + "Content-Type: image/png\n"
                    + "data:image/png;base64,abc123\n"
                    + "</attachment>\n",
                Map.of()
            )),
            new ChatOptions("claude-sonnet-5", 0.7, 1000)
        );

        provider.chat(request).join();

        Object content = client.lastRichMessages.get(0).get("content");
        assertTrue(content instanceof List<?>);
        List<?> parts = (List<?>) content;
        Map<?, ?> imagePart = (Map<?, ?>) parts.get(0);
        Map<?, ?> source = (Map<?, ?>) imagePart.get("source");
        assertEquals("image", imagePart.get("type"));
        assertEquals("base64", source.get("type"));
        assertEquals("image/png", source.get("media_type"));
        assertEquals("abc123", source.get("data"));
        assertEquals("text", ((Map<?, ?>) parts.get(1)).get("type"));
        assertEquals("Inspect this @chart.png", ((Map<?, ?>) parts.get(1)).get("text"));
    }

    @Test
    void chatConvertsToolResultsToAnthropicContentBlocks() {
        StubAnthropicClient client = new StubAnthropicClient("""
            {
              "content": [{"type": "text", "text": "tool result received"}],
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic(), client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.TOOL_RESULT,
                "15 degrees",
                Map.of(
                    "toolCallId", "toolu_123",
                    "success", true
                )
            )),
            new ChatOptions("claude-sonnet-5", 0.7, 1000)
        );

        provider.chat(request).join();

        Object content = client.lastRichMessages.get(0).get("content");
        assertTrue(content instanceof List<?>);
        List<?> parts = (List<?>) content;
        Map<?, ?> toolResult = (Map<?, ?>) parts.get(0);
        assertEquals("tool_result", toolResult.get("type"));
        assertEquals("toolu_123", toolResult.get("tool_use_id"));
        assertEquals("15 degrees", toolResult.get("content"));
        assertEquals(false, toolResult.containsKey("is_error"));
    }

    @Test
    void chatMarksFailedToolResultsAsAnthropicErrors() {
        StubAnthropicClient client = new StubAnthropicClient("""
            {
              "content": [{"type": "text", "text": "tool error received"}],
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """);
        AnthropicLLMProvider provider = new AnthropicLLMProvider(configuredAnthropic(), client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.TOOL_RESULT,
                "lookup failed",
                Map.of(
                    "toolCallId", "toolu_456",
                    "success", false
                )
            )),
            new ChatOptions("claude-sonnet-5", 0.7, 1000)
        );

        provider.chat(request).join();

        List<?> parts = (List<?>) client.lastRichMessages.get(0).get("content");
        Map<?, ?> toolResult = (Map<?, ?>) parts.get(0);
        assertEquals("tool_result", toolResult.get("type"));
        assertEquals("toolu_456", toolResult.get("tool_use_id"));
        assertEquals("lookup failed", toolResult.get("content"));
        assertEquals(true, toolResult.get("is_error"));
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
        private List<Map<String, Object>> lastRichMessages = List.of();

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

        @Override
        public Mono<String> createMessageWithContentParts(
            String model,
            List<Map<String, Object>> messages,
            String system,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            String apiKey
        ) {
            this.lastApiKey = apiKey;
            this.lastTools = tools;
            this.lastRichMessages = messages;
            return Mono.just(response);
        }
    }
}

package com.pi.mono.llm.provider;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatOptions;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.MessageRole;
import com.pi.mono.core.Model;
import com.pi.mono.llm.client.OpenAIClient;
import com.pi.mono.llm.config.OpenAIConfig;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAILLMProviderTest {

    @Test
    void availableModelsIncludeLatestUpstreamDefaultModel() {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("test-key");
        OpenAILLMProvider provider = new OpenAILLMProvider(new OpenAIClient(config), config);

        List<String> modelIds = provider.getAvailableModels().stream()
            .map(Model::id)
            .toList();

        assertTrue(modelIds.contains("gpt-5.5"));
    }

    @Test
    void chatReturnsContentFromOpenAIResponse() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("test-key");
        OpenAILLMProvider provider = new OpenAILLMProvider(
            new StubOpenAIClient(config, """
                {
                  "choices": [
                    {
                      "message": {"content": "real answer"},
                      "finish_reason": "stop"
                    }
                  ]
                }
                """),
            config
        );

        AgentMessage response = provider.chat(chatRequest()).get();

        assertEquals(MessageRole.ASSISTANT, response.role());
        assertEquals("real answer", response.content());
        assertEquals("stop", response.metadata().get("finishReason"));
    }

    @Test
    void chatMarksOutputLengthStopsAsIncomplete() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("test-key");
        OpenAILLMProvider provider = new OpenAILLMProvider(
            new StubOpenAIClient(config, """
                {
                  "choices": [
                    {
                      "message": {"content": "partial answer"},
                      "finish_reason": "length"
                    }
                  ]
                }
                """),
            config
        );

        AgentMessage response = provider.chat(chatRequest()).get();

        assertTrue(response.content().contains("partial answer"));
        assertTrue(response.content().contains("Incomplete response"));
        assertEquals("length", response.metadata().get("finishReason"));
        assertEquals(true, response.metadata().get("incomplete"));
    }

    @Test
    void chatPreservesOpenAIReasoningUsageMetadata() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("test-key");
        OpenAILLMProvider provider = new OpenAILLMProvider(
            new StubOpenAIClient(config, """
                {
                  "choices": [
                    {
                      "message": {"content": "reasoned answer"},
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 11,
                    "completion_tokens": 7,
                    "total_tokens": 18,
                    "completion_tokens_details": {
                      "reasoning_tokens": 5
                    }
                  }
                }
                """),
            config
        );

        AgentMessage response = provider.chat(chatRequest()).get();

        Object usage = response.metadata().get("usage");
        assertTrue(usage instanceof Map<?, ?>);
        Map<?, ?> usageMap = (Map<?, ?>) usage;
        assertEquals(11L, usageMap.get("inputTokens"));
        assertEquals(7L, usageMap.get("outputTokens"));
        assertEquals(18L, usageMap.get("totalTokens"));
        assertEquals(5L, usageMap.get("reasoningTokens"));
    }

    @Test
    void chatUsesRequestModelWhenProvided() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("test-key");
        config.setModel("configured-model");
        StubOpenAIClient client = new StubOpenAIClient(config, """
            {
              "choices": [
                {
                  "message": {"content": "model-specific answer"},
                  "finish_reason": "stop"
                }
              ]
            }
            """);
        OpenAILLMProvider provider = new OpenAILLMProvider(client, config);

        provider.chat(chatRequest()).get();

        assertEquals("gpt-5.5", client.lastModel);
        assertEquals(0.2, client.lastTemperature);
        assertEquals(1024, client.lastMaxTokens);
    }

    @Test
    void chatPassesRequestScopedApiKeyToClient() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("configured-key");
        StubOpenAIClient client = new StubOpenAIClient(config, """
            {
              "choices": [
                {
                  "message": {"content": "scoped auth answer"},
                  "finish_reason": "stop"
                }
              ]
            }
            """);
        OpenAILLMProvider provider = new OpenAILLMProvider(client, config);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("gpt-5.5", 0.2, 1024, "request-key", Map.of())
        );

        provider.chat(request).get();

        assertEquals("request-key", client.lastApiKey);
    }

    @Test
    void chatUsesRequestScopedEnvApiKeyWhenExplicitApiKeyIsAbsent() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("configured-key");
        StubOpenAIClient client = new StubOpenAIClient(config, """
            {
              "choices": [
                {
                  "message": {"content": "env auth answer"},
                  "finish_reason": "stop"
                }
              ]
            }
            """);
        OpenAILLMProvider provider = new OpenAILLMProvider(client, config);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("gpt-5.5", 0.2, 1024, null, Map.of("OPENAI_API_KEY", "env-request-key"))
        );

        provider.chat(request).get();

        assertEquals("env-request-key", client.lastApiKey);
    }

    @Test
    void chatSendsToolSchemasAndParsesToolCalls() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("configured-key");
        StubOpenAIClient client = new StubOpenAIClient(config, """
            {
              "choices": [
                {
                  "message": {
                    "content": null,
                    "tool_calls": [
                      {
                        "id": "call_weather_1",
                        "type": "function",
                        "function": {
                          "name": "weather",
                          "arguments": "{\\"city\\":\\"Shanghai\\"}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ]
            }
            """);
        OpenAILLMProvider provider = new OpenAILLMProvider(client, config);
        List<Map<String, Object>> tools = List.of(Map.of(
            "name", "weather",
            "description", "Read weather",
            "input_schema", Map.of("type", "object")
        ));
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "weather?", Map.of())),
            new ChatOptions("gpt-5.5", 0.2, 1024, null, Map.of(), tools)
        );

        AgentMessage response = provider.chat(request).get();

        assertEquals(tools, client.lastTools);
        assertEquals("", response.content());
        assertEquals("tool_calls", response.metadata().get("finishReason"));

        Object toolCallsValue = response.metadata().get("toolCalls");
        assertTrue(toolCallsValue instanceof List<?>);
        List<?> toolCalls = (List<?>) toolCallsValue;
        assertEquals(1, toolCalls.size());
        assertTrue(toolCalls.get(0) instanceof Map<?, ?>);
        Map<?, ?> toolCall = (Map<?, ?>) toolCalls.get(0);
        assertEquals("call_weather_1", toolCall.get("id"));
        assertEquals("weather", toolCall.get("name"));
        assertEquals(Map.of("city", "Shanghai"), toolCall.get("arguments"));
    }

    @Test
    void chatConvertsImageAttachmentsToOpenAIContentParts() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("configured-key");
        StubOpenAIClient client = new StubOpenAIClient(config, """
            {
              "choices": [
                {
                  "message": {"content": "image received"},
                  "finish_reason": "stop"
                }
              ]
            }
            """);
        OpenAILLMProvider provider = new OpenAILLMProvider(client, config);
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
            new ChatOptions("gpt-5.5", 0.2, 1024)
        );

        provider.chat(request).get();

        Object content = client.lastRichMessages.get(0).get("content");
        assertTrue(content instanceof List<?>);
        List<?> parts = (List<?>) content;
        assertEquals("text", ((Map<?, ?>) parts.get(0)).get("type"));
        assertEquals("image_url", ((Map<?, ?>) parts.get(1)).get("type"));
        assertEquals(
            Map.of("url", "data:image/png;base64,abc123"),
            ((Map<?, ?>) parts.get(1)).get("image_url")
        );
    }

    @Test
    void chatUsesNoToolOutputPlaceholderForEmptyOpenAIToolResults() throws Exception {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("configured-key");
        StubOpenAIClient client = new StubOpenAIClient(config, """
            {
              "choices": [
                {
                  "message": {"content": "tool result received"},
                  "finish_reason": "stop"
                }
              ]
            }
            """);
        OpenAILLMProvider provider = new OpenAILLMProvider(client, config);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.TOOL_RESULT,
                "",
                Map.of("toolCallId", "call_empty_1", "success", true)
            )),
            new ChatOptions("gpt-5.5", 0.2, 1024)
        );

        provider.chat(request).get();

        Map<String, Object> toolMessage = client.lastRichMessages.get(0);
        assertEquals("tool", toolMessage.get("role"));
        assertEquals("call_empty_1", toolMessage.get("tool_call_id"));
        assertEquals("(no tool output)", toolMessage.get("content"));
    }

    private ChatRequest chatRequest() {
        return new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("gpt-5.5", 0.2, 1024)
        );
    }

    static class StubOpenAIClient extends OpenAIClient {
        private final String response;
        private String lastModel;
        private double lastTemperature;
        private int lastMaxTokens;
        private String lastApiKey;
        private List<Map<String, Object>> lastTools = List.of();
        private List<Map<String, Object>> lastRichMessages = List.of();

        StubOpenAIClient(OpenAIConfig config, String response) {
            super(config);
            this.response = response;
        }

        @Override
        public Mono<String> createChatCompletion(String model, List<Map<String, String>> messages) {
            return createChatCompletion(model, messages, 0.7, 1000);
        }

        @Override
        public Mono<String> createChatCompletion(
            String model,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens
        ) {
            return createChatCompletion(model, messages, temperature, maxTokens, null);
        }

        @Override
        public Mono<String> createChatCompletion(
            String model,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens,
            String apiKey
        ) {
            return createChatCompletion(model, messages, temperature, maxTokens, List.of(), apiKey);
        }

        @Override
        public Mono<String> createChatCompletion(
            String model,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            String apiKey
        ) {
            this.lastModel = model;
            this.lastTemperature = temperature;
            this.lastMaxTokens = maxTokens;
            this.lastApiKey = apiKey;
            this.lastTools = tools;
            return Mono.just(response);
        }

        @Override
        public Mono<String> createChatCompletionWithContentParts(
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            String apiKey
        ) {
            this.lastModel = model;
            this.lastTemperature = temperature;
            this.lastMaxTokens = maxTokens;
            this.lastApiKey = apiKey;
            this.lastTools = tools;
            this.lastRichMessages = messages;
            return Mono.just(response);
        }
    }
}

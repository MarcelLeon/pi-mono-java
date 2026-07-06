package com.pi.mono.llm.provider;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatOptions;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.core.MessageRole;
import com.pi.mono.core.Model;
import com.pi.mono.llm.client.BedrockClient;
import com.pi.mono.llm.config.BedrockConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockLLMProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void catalogIncludesClaudeSonnet5WithAdaptiveThinkingDescription() {
        BedrockLLMProvider provider = new BedrockLLMProvider(new BedrockConfig());

        List<Model> models = provider.getAvailableModels();

        assertTrue(models.stream().map(Model::id).anyMatch("anthropic.claude-sonnet-5"::equals));
        String description = models.stream()
            .filter(model -> model.id().equals("anthropic.claude-sonnet-5"))
            .findFirst()
            .orElseThrow()
            .description();
        assertTrue(description.contains("adaptive thinking"));
        assertTrue(description.contains("prompt caching"));
    }

    @Test
    void providerIsAvailableWhenRegionConfigured() {
        BedrockLLMProvider provider = new BedrockLLMProvider(new BedrockConfig());

        assertEquals(HealthStatus.HEALTHY, provider.health());
        assertTrue(provider.isAvailable());
    }

    @Test
    void chatParsesBedrockAnthropicResponse() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        BedrockLLMProvider provider = new BedrockLLMProvider(
            config,
            new StubBedrockClient("""
                {
                  "content": [{"type": "text", "text": "hello from bedrock"}],
                  "usage": {"input_tokens": 13, "output_tokens": 9}
                }
                """)
        );
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("anthropic.claude-sonnet-5", 0.7, 1000)
        );

        AgentMessage response = provider.chat(request).join();

        assertEquals(MessageRole.ASSISTANT, response.role());
        assertEquals("hello from bedrock", response.content());
        assertEquals("anthropic.claude-sonnet-5", response.metadata().get("model"));
        assertEquals("us-west-2", response.metadata().get("region"));
        assertEquals(true, response.metadata().get("adaptiveThinking"));
        assertEquals(true, response.metadata().get("promptCaching"));
        assertEquals(13, response.metadata().get("inputTokens"));
        assertEquals(9, response.metadata().get("outputTokens"));
    }

    @Test
    void chatPassesRequestScopedAwsEnvCredentialsToClient() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        StubBedrockClient client = new StubBedrockClient("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        BedrockLLMProvider provider = new BedrockLLMProvider(config, client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions(
                "anthropic.claude-sonnet-5",
                0.7,
                1000,
                null,
                Map.of(
                    "AWS_ACCESS_KEY_ID", "REQUESTACCESS",
                    "AWS_SECRET_ACCESS_KEY", "REQUESTSECRET",
                    "AWS_SESSION_TOKEN", "request-token"
                )
            )
        );

        provider.chat(request).join();

        assertEquals("REQUESTACCESS", client.lastCredentials.accessKeyId());
        assertEquals("REQUESTSECRET", client.lastCredentials.secretAccessKey());
        assertEquals("request-token", client.lastCredentials.sessionToken());
    }

    @Test
    void chatPassesRequestScopedAwsProfileCredentialsToClient() throws IOException {
        Path credentialsFile = tempDir.resolve("request-credentials");
        Files.writeString(credentialsFile, """
            [tenant]
            aws_access_key_id=request-profile-access
            aws_secret_access_key=request-profile-secret
            aws_session_token=request-profile-session
            """);
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        StubBedrockClient client = new StubBedrockClient("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        BedrockLLMProvider provider = new BedrockLLMProvider(config, client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions(
                "anthropic.claude-sonnet-5",
                0.7,
                1000,
                null,
                Map.of(
                    "AWS_PROFILE", "tenant",
                    "AWS_SHARED_CREDENTIALS_FILE", credentialsFile.toString()
                )
            )
        );

        provider.chat(request).join();

        assertEquals("request-profile-access", client.lastCredentials.accessKeyId());
        assertEquals("request-profile-secret", client.lastCredentials.secretAccessKey());
        assertEquals("request-profile-session", client.lastCredentials.sessionToken());
    }

    @Test
    void chatConvertsImageAttachmentsToBedrockContentBlocks() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        StubBedrockClient client = new StubBedrockClient("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        BedrockLLMProvider provider = new BedrockLLMProvider(config, client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.USER,
                "Inspect this @chart.jpg\n\n[Attached files]\n"
                    + "<attachment path=\"chart.jpg\" resolvedPath=\"/tmp/chart.jpg\">\n"
                    + "Content-Type: image/jpeg\n"
                    + "data:image/jpeg;base64,abc123\n"
                    + "</attachment>\n",
                Map.of()
            )),
            new ChatOptions("anthropic.claude-sonnet-5", 0.7, 1000)
        );

        provider.chat(request).join();

        Object content = client.lastRichMessages.get(0).get("content");
        assertTrue(content instanceof List<?>);
        List<?> parts = (List<?>) content;
        Map<?, ?> imagePart = (Map<?, ?>) parts.get(0);
        Map<?, ?> source = (Map<?, ?>) imagePart.get("source");
        assertEquals("image", imagePart.get("type"));
        assertEquals("base64", source.get("type"));
        assertEquals("image/jpeg", source.get("media_type"));
        assertEquals("abc123", source.get("data"));
        assertEquals("text", ((Map<?, ?>) parts.get(1)).get("type"));
        assertEquals("Inspect this @chart.jpg", ((Map<?, ?>) parts.get(1)).get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatSendsToolSchemasAndParsesToolUseBlocks() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        StubBedrockClient client = new StubBedrockClient("""
            {
              "content": [
                {"type": "text", "text": "I will check that."},
                {
                  "type": "tool_use",
                  "id": "toolu_bedrock_123",
                  "name": "get_weather",
                  "input": {"city": "Shanghai"}
                }
              ],
              "usage": {"input_tokens": 12, "output_tokens": 8}
            }
            """);
        BedrockLLMProvider provider = new BedrockLLMProvider(config, client);
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
            new ChatOptions("anthropic.claude-sonnet-5", 0.7, 1000, null, Map.of(), tools)
        );

        AgentMessage response = provider.chat(request).join();

        assertEquals(tools, client.lastTools);
        assertEquals("I will check that.", response.content());
        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) response.metadata().get("toolCalls");
        assertEquals(1, toolCalls.size());
        assertEquals("toolu_bedrock_123", toolCalls.get(0).get("id"));
        assertEquals("get_weather", toolCalls.get(0).get("name"));
        assertEquals(Map.of("city", "Shanghai"), toolCalls.get(0).get("arguments"));
    }

    @Test
    void chatPreservesThinkingContentInMetadata() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        BedrockLLMProvider provider = new BedrockLLMProvider(
            config,
            new StubBedrockClient("""
                {
                  "content": [
                    {"type": "thinking", "thinking": "I should inspect the AWS context."},
                    {"type": "text", "text": "Here is the answer."}
                  ],
                  "usage": {"input_tokens": 11, "output_tokens": 7}
                }
                """)
        );
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("anthropic.claude-sonnet-5", 0.7, 1000)
        );

        AgentMessage response = provider.chat(request).join();

        assertEquals("Here is the answer.", response.content());
        assertEquals("I should inspect the AWS context.", response.metadata().get("thinking"));
    }

    @Test
    void chatConvertsToolResultsToBedrockContentBlocks() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        StubBedrockClient client = new StubBedrockClient("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        BedrockLLMProvider provider = new BedrockLLMProvider(config, client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.TOOL_RESULT,
                "15 degrees",
                Map.of(
                    "toolCallId", "toolu_bedrock_123",
                    "success", true
                )
            )),
            new ChatOptions("anthropic.claude-sonnet-5", 0.7, 1000)
        );

        provider.chat(request).join();

        Object content = client.lastRichMessages.get(0).get("content");
        assertTrue(content instanceof List<?>);
        List<?> parts = (List<?>) content;
        Map<?, ?> toolResult = (Map<?, ?>) parts.get(0);
        assertEquals("tool_result", toolResult.get("type"));
        assertEquals("toolu_bedrock_123", toolResult.get("tool_use_id"));
        assertEquals("15 degrees", toolResult.get("content"));
        assertEquals(false, toolResult.containsKey("is_error"));
    }

    @Test
    void chatMarksFailedToolResultsAsBedrockErrors() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        StubBedrockClient client = new StubBedrockClient("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}");
        BedrockLLMProvider provider = new BedrockLLMProvider(config, client);
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(
                MessageRole.TOOL_RESULT,
                "lookup failed",
                Map.of(
                    "toolCallId", "toolu_bedrock_456",
                    "success", false
                )
            )),
            new ChatOptions("anthropic.claude-sonnet-5", 0.7, 1000)
        );

        provider.chat(request).join();

        List<?> parts = (List<?>) client.lastRichMessages.get(0).get("content");
        Map<?, ?> toolResult = (Map<?, ?>) parts.get(0);
        assertEquals("tool_result", toolResult.get("type"));
        assertEquals("toolu_bedrock_456", toolResult.get("tool_use_id"));
        assertEquals("lookup failed", toolResult.get("content"));
        assertEquals(true, toolResult.get("is_error"));
    }

    static class StubBedrockClient extends BedrockClient {
        private final String response;
        private BedrockConfig.Credentials lastCredentials;
        private List<Map<String, Object>> lastRichMessages = List.of();
        private List<Map<String, Object>> lastTools = List.of();

        StubBedrockClient(String response) {
            super(new BedrockConfig());
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
            return Mono.just(response);
        }

        @Override
        public Mono<String> createMessage(
            String model,
            List<Map<String, String>> messages,
            String system,
            double temperature,
            int maxTokens,
            BedrockConfig.Credentials requestCredentials
        ) {
            this.lastCredentials = requestCredentials;
            return Mono.just(response);
        }

        @Override
        public Mono<String> createMessageWithContentBlocks(
            String model,
            List<Map<String, Object>> messages,
            String system,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            BedrockConfig.Credentials requestCredentials
        ) {
            this.lastCredentials = requestCredentials;
            this.lastRichMessages = messages;
            this.lastTools = tools;
            return Mono.just(response);
        }

        @Override
        public Mono<String> createMessageWithContentBlocks(
            String model,
            List<Map<String, Object>> messages,
            String system,
            double temperature,
            int maxTokens,
            BedrockConfig.Credentials requestCredentials
        ) {
            return createMessageWithContentBlocks(
                model,
                messages,
                system,
                temperature,
                maxTokens,
                List.of(),
                requestCredentials
            );
        }
    }
}

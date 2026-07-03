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

    static class StubBedrockClient extends BedrockClient {
        private final String response;
        private BedrockConfig.Credentials lastCredentials;

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
    }
}

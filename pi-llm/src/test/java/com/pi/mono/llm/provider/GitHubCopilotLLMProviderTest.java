package com.pi.mono.llm.provider;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatOptions;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.core.LLMProvider;
import com.pi.mono.core.MessageRole;
import com.pi.mono.core.Model;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.llm.config.GitHubCopilotConfig;
import com.pi.mono.llm.oauth.GitHubCopilotCredentialStore;
import com.pi.mono.llm.oauth.GitHubCopilotOAuthClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubCopilotLLMProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void isUnavailableUntilCredentialStoreHasAccessToken() {
        GitHubCopilotConfig config = enabledConfig(tempDir.resolve("missing-auth.json"));

        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(config);

        assertFalse(provider.isAvailable());
        assertEquals(HealthStatus.UNHEALTHY, provider.health());
    }

    @Test
    void exposesCopilotModelWhenCredentialStoreHasAccessToken() {
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        saveToken(credentialsFile);
        GitHubCopilotConfig config = enabledConfig(credentialsFile);

        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(
            config,
            new GitHubCopilotCredentialStore(credentialsFile),
            new FakeCopilotChatTransport()
        );

        assertTrue(provider.isAvailable());
        assertEquals(HealthStatus.HEALTHY, provider.health());
        assertEquals("github-copilot", provider.getId());
        assertEquals(List.of("github-copilot"), provider.getAvailableModels().stream().map(Model::id).toList());
        assertEquals(BigDecimal.ZERO, provider.getCostPerToken());
    }

    @Test
    void exposesAccountSpecificCopilotModelsWhenModelEndpointIsAvailable() {
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        saveToken(
            credentialsFile,
            "tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;",
            Map.of("access_token", "tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;")
        );
        FakeCopilotChatTransport transport = new FakeCopilotChatTransport();
        transport.modelsResponse = """
            {"data":[
              {"id":"gpt-5.5-copilot","name":"GPT 5.5 Copilot","context_window":200000},
              {"id":"claude-sonnet-5-copilot","max_input_tokens":180000}
            ]}
            """;
        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(
            enabledConfig(credentialsFile),
            new GitHubCopilotCredentialStore(credentialsFile),
            transport
        );

        List<Model> models = provider.getAvailableModels();

        assertEquals(List.of("gpt-5.5-copilot", "claude-sonnet-5-copilot"), models.stream().map(Model::id).toList());
        assertEquals(List.of("github-copilot", "github-copilot"), models.stream().map(Model::provider).toList());
        assertEquals(200000, models.get(0).maxTokens());
        assertEquals(180000, models.get(1).maxTokens());
        assertEquals("https://api.individual.githubcopilot.com", transport.modelRequests.get(0).baseUrl());
        assertEquals("tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;", transport.modelRequests.get(0).accessToken());
    }

    @Test
    void managerCanResolveCopilotProviderByModelWhenCredentialsExist() {
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        saveToken(credentialsFile);
        GitHubCopilotLLMProvider copilot = new GitHubCopilotLLMProvider(enabledConfig(credentialsFile));
        LLMProvider fallback = new TestProvider(
            "mock-claude",
            true,
            List.of(new Model("mock-claude", "mock", "Mock Claude", 100000, BigDecimal.ZERO))
        );
        LLMProviderManager manager = new LLMProviderManager(List.of(fallback, copilot));

        LLMProvider provider = manager.getAvailableProvider("github-copilot");

        assertEquals("github-copilot", provider.getId());
    }

    @Test
    void sendsChatRequestThroughCredentialBackedCopilotTransport() {
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        saveToken(
            credentialsFile,
            "tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;",
            Map.of("access_token", "tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;")
        );
        FakeCopilotChatTransport transport = new FakeCopilotChatTransport();
        transport.response = """
            {"choices":[{"message":{"content":"copilot ok"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
            """;
        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(
            enabledConfig(credentialsFile),
            new GitHubCopilotCredentialStore(credentialsFile),
            transport
        );
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("github-copilot", 0.2, 1024)
        );

        AgentMessage response = provider.chat(request).join();

        assertEquals(MessageRole.ASSISTANT, response.role());
        assertEquals("copilot ok", response.content());
        assertEquals("stop", response.metadata().get("finishReason"));
        assertEquals(Map.of("inputTokens", 3L, "outputTokens", 2L, "totalTokens", 5L), response.metadata().get("usage"));
        assertEquals("https://api.individual.githubcopilot.com", transport.requests.get(0).baseUrl());
        assertEquals("tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;", transport.requests.get(0).accessToken());
        assertEquals("github-copilot", transport.requests.get(0).model());
        assertEquals(0.2, transport.requests.get(0).temperature());
        assertEquals(1024, transport.requests.get(0).maxTokens());
        assertEquals(List.of(Map.of("role", "user", "content", "hello")), transport.requests.get(0).messages());
        assertEquals("user", transport.requests.get(0).headers().get("X-Initiator"));
        assertEquals("conversation-edits", transport.requests.get(0).headers().get("Openai-Intent"));
    }

    @Test
    void refreshesExpiredCopilotApiTokenBeforeChatAndPersistsReplacement() {
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        String expiredToken = "tid=old;exp=1;proxy-ep=proxy.old.githubcopilot.com;";
        String refreshedToken = "tid=new;exp=999;proxy-ep=proxy.individual.githubcopilot.com;";
        saveToken(
            credentialsFile,
            expiredToken,
            Map.of(
                "access_token", expiredToken,
                "refresh_token", "github-refresh-token",
                "expires_at", 10L
            )
        );
        FakeCopilotChatTransport transport = new FakeCopilotChatTransport();
        transport.response = """
            {"choices":[{"message":{"content":"refreshed ok"},"finish_reason":"stop"}]}
            """;
        FakeCopilotTokenRefresher refresher = new FakeCopilotTokenRefresher(new GitHubCopilotOAuthClient.AccessToken(
            refreshedToken,
            "bearer",
            "read:user",
            Map.of(
                "access_token", refreshedToken,
                "refresh_token", "github-refresh-token",
                "expires_at", 9999999999L
            )
        ));
        GitHubCopilotCredentialStore store = new GitHubCopilotCredentialStore(credentialsFile);
        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(
            enabledConfig(credentialsFile),
            store,
            transport,
            refresher,
            Clock.fixed(Instant.ofEpochSecond(11), ZoneOffset.UTC)
        );

        AgentMessage response = provider.chat(new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("github-copilot", 0.2, 1024)
        )).join();

        assertEquals("refreshed ok", response.content());
        assertEquals(List.of("github-refresh-token"), refresher.githubAccessTokens);
        assertEquals("https://api.individual.githubcopilot.com", transport.requests.get(0).baseUrl());
        assertEquals(refreshedToken, transport.requests.get(0).accessToken());
        assertEquals(refreshedToken, store.load().orElseThrow().accessToken());
    }

    private GitHubCopilotConfig enabledConfig(Path credentialsFile) {
        GitHubCopilotConfig config = new GitHubCopilotConfig();
        config.setEnabled(true);
        config.setCredentialsFile(credentialsFile.toString());
        return config;
    }

    private void saveToken(Path credentialsFile) {
        saveToken(credentialsFile, "ghu_test_token", Map.of("access_token", "ghu_test_token"));
    }

    private void saveToken(Path credentialsFile, String accessToken, Map<String, Object> rawResponse) {
        new GitHubCopilotCredentialStore(credentialsFile).save(new GitHubCopilotOAuthClient.AccessToken(
            accessToken,
            "bearer",
            "read:user",
            rawResponse
        ));
    }

    static class FakeCopilotChatTransport implements GitHubCopilotLLMProvider.CopilotChatTransport {
        private final List<Request> requests = new ArrayList<>();
        private final List<ModelRequest> modelRequests = new ArrayList<>();
        private String response;
        private String modelsResponse;

        @Override
        public String chat(
            String baseUrl,
            String accessToken,
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            Map<String, String> headers
        ) {
            requests.add(new Request(
                baseUrl,
                accessToken,
                model,
                messages,
                temperature,
                maxTokens,
                tools,
                headers
            ));
            return response;
        }

        @Override
        public String models(String baseUrl, String accessToken) {
            modelRequests.add(new ModelRequest(baseUrl, accessToken));
            return modelsResponse;
        }

        record Request(
            String baseUrl,
            String accessToken,
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            Map<String, String> headers
        ) {
        }

        record ModelRequest(String baseUrl, String accessToken) {
        }
    }

    static class FakeCopilotTokenRefresher implements GitHubCopilotLLMProvider.CopilotTokenRefresher {
        private final GitHubCopilotOAuthClient.AccessToken refreshedToken;
        private final List<String> githubAccessTokens = new ArrayList<>();

        FakeCopilotTokenRefresher(GitHubCopilotOAuthClient.AccessToken refreshedToken) {
            this.refreshedToken = refreshedToken;
        }

        @Override
        public GitHubCopilotOAuthClient.AccessToken refresh(
            GitHubCopilotOAuthClient.AccessToken githubAccessToken
        ) {
            githubAccessTokens.add(githubAccessToken.accessToken());
            return refreshedToken;
        }
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
        public java.util.concurrent.CompletableFuture<AgentMessage> chat(ChatRequest request) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<Model> getAvailableModels() {
            return models;
        }

        @Override
        public com.pi.mono.core.ToolCallResult executeToolCall(
            com.pi.mono.core.ToolCall toolCall,
            List<AgentMessage> context
        ) {
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

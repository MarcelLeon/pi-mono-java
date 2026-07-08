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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(config);

        assertTrue(provider.isAvailable());
        assertEquals(HealthStatus.HEALTHY, provider.health());
        assertEquals("github-copilot", provider.getId());
        assertEquals(List.of("github-copilot"), provider.getAvailableModels().stream().map(Model::id).toList());
        assertEquals(BigDecimal.ZERO, provider.getCostPerToken());
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
    void chatFailsExplicitlyUntilCopilotTransportIsImplemented() {
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        saveToken(credentialsFile);
        GitHubCopilotLLMProvider provider = new GitHubCopilotLLMProvider(enabledConfig(credentialsFile));
        ChatRequest request = new ChatRequest(
            "session-1",
            List.of(new AgentMessage(MessageRole.USER, "hello", Map.of())),
            new ChatOptions("github-copilot", 0.2, 1024)
        );

        CompletionException error = assertThrows(CompletionException.class, () -> provider.chat(request).join());

        assertTrue(error.getCause().getMessage().contains("not implemented"));
    }

    private GitHubCopilotConfig enabledConfig(Path credentialsFile) {
        GitHubCopilotConfig config = new GitHubCopilotConfig();
        config.setEnabled(true);
        config.setCredentialsFile(credentialsFile.toString());
        return config;
    }

    private void saveToken(Path credentialsFile) {
        new GitHubCopilotCredentialStore(credentialsFile).save(new GitHubCopilotOAuthClient.AccessToken(
            "ghu_test_token",
            "bearer",
            "read:user",
            Map.of("access_token", "ghu_test_token")
        ));
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

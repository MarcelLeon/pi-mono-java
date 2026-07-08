package com.pi.mono.llm.provider;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.core.LLMProvider;
import com.pi.mono.core.Model;
import com.pi.mono.core.ToolCall;
import com.pi.mono.core.ToolCallResult;
import com.pi.mono.llm.config.GitHubCopilotConfig;
import com.pi.mono.llm.oauth.GitHubCopilotCredentialStore;
import com.pi.mono.llm.oauth.GitHubCopilotOAuthClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "pi.llm.github-copilot.enabled", havingValue = "true")
public class GitHubCopilotLLMProvider implements LLMProvider {
    private static final String PROVIDER_ID = "github-copilot";

    private final GitHubCopilotConfig config;
    private final GitHubCopilotOAuthClient.AccessTokenStore tokenStore;

    public GitHubCopilotLLMProvider(GitHubCopilotConfig config) {
        this(config, new GitHubCopilotCredentialStore(config.getResolvedCredentialsFile()));
    }

    GitHubCopilotLLMProvider(
        GitHubCopilotConfig config,
        GitHubCopilotOAuthClient.AccessTokenStore tokenStore
    ) {
        this.config = config;
        this.tokenStore = tokenStore;
    }

    @Override
    public CompletableFuture<AgentMessage> chat(ChatRequest request) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "GitHub Copilot provider transport is not implemented yet; "
                + "OAuth credentials are only used for provider availability."
        ));
    }

    @Override
    public List<Model> getAvailableModels() {
        return List.of(new Model(
            config.getResolvedModel(),
            getId(),
            "GitHub Copilot OAuth credential-backed model",
            200000,
            BigDecimal.ZERO
        ));
    }

    @Override
    public ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context) {
        return new ToolCallResult(
            toolCall.name(),
            "GitHub Copilot provider does not execute tool calls directly.",
            Map.of("provider", getId(), "unsupported", true)
        );
    }

    @Override
    public HealthStatus health() {
        return isAvailable() ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
    }

    @Override
    public boolean isAvailable() {
        if (!config.isEnabled()) {
            return false;
        }
        try {
            return tokenStore.load()
                .map(GitHubCopilotOAuthClient.AccessToken::accessToken)
                .map(token -> !token.isBlank())
                .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public BigDecimal getCostPerToken() {
        return BigDecimal.ZERO;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}

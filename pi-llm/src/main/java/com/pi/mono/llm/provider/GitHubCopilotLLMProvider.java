package com.pi.mono.llm.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatOptions;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.core.LLMProvider;
import com.pi.mono.core.MessageRole;
import com.pi.mono.core.Model;
import com.pi.mono.core.ToolCall;
import com.pi.mono.core.ToolCallResult;
import com.pi.mono.llm.config.GitHubCopilotConfig;
import com.pi.mono.llm.oauth.GitHubCopilotCredentialStore;
import com.pi.mono.llm.oauth.GitHubCopilotOAuthClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "pi.llm.github-copilot.enabled", havingValue = "true")
public class GitHubCopilotLLMProvider implements LLMProvider {
    private static final String PROVIDER_ID = "github-copilot";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern PROXY_ENDPOINT = Pattern.compile("proxy-ep=([^;]+)");
    private static final String DEFAULT_BASE_URL = "https://api.individual.githubcopilot.com";
    private static final String NO_TOOL_OUTPUT = "(no tool output)";
    private static final String COPILOT_OAUTH_CLIENT_ID = "Iv1.b507a08c87ecfe98";

    private final GitHubCopilotConfig config;
    private final GitHubCopilotOAuthClient.AccessTokenStore tokenStore;
    private final CopilotChatTransport chatTransport;
    private final CopilotTokenRefresher tokenRefresher;
    private final Clock clock;

    public GitHubCopilotLLMProvider(GitHubCopilotConfig config) {
        this(
            config,
            new GitHubCopilotCredentialStore(config.getResolvedCredentialsFile()),
            new WebClientCopilotChatTransport(WebClient.builder().build()),
            new OAuthClientCopilotTokenRefresher(new GitHubCopilotOAuthClient(COPILOT_OAUTH_CLIENT_ID)),
            Clock.systemUTC()
        );
    }

    GitHubCopilotLLMProvider(
        GitHubCopilotConfig config,
        GitHubCopilotOAuthClient.AccessTokenStore tokenStore,
        CopilotChatTransport chatTransport
    ) {
        this(config, tokenStore, chatTransport, token -> {
            throw new IllegalStateException("GitHub Copilot token refresh is not configured.");
        }, Clock.systemUTC());
    }

    GitHubCopilotLLMProvider(
        GitHubCopilotConfig config,
        GitHubCopilotOAuthClient.AccessTokenStore tokenStore,
        CopilotChatTransport chatTransport,
        CopilotTokenRefresher tokenRefresher,
        Clock clock
    ) {
        this.config = config;
        this.tokenStore = Objects.requireNonNull(tokenStore, "tokenStore");
        this.chatTransport = Objects.requireNonNull(chatTransport, "chatTransport");
        this.tokenRefresher = Objects.requireNonNull(tokenRefresher, "tokenRefresher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<AgentMessage> chat(ChatRequest request) {
        try {
            GitHubCopilotOAuthClient.AccessToken token = tokenStore.load()
                .filter(accessToken -> !accessToken.accessToken().isBlank())
                .orElseThrow(() -> new IllegalStateException("GitHub Copilot credentials are not available."));
            token = refreshIfExpired(token);
            ChatOptions options = request.options();
            String model = resolveModel(options);
            double temperature = options == null ? 0.7 : options.temperature();
            int maxTokens = options != null && options.maxTokens() > 0 ? options.maxTokens() : 1000;
            List<Map<String, Object>> tools = options == null ? List.of() : options.tools();
            List<Map<String, Object>> messages = request.messages().stream()
                .map(this::toCopilotMessage)
                .toList();
            String response = chatTransport.chat(
                baseUrl(token.accessToken()),
                token.accessToken(),
                model,
                messages,
                temperature,
                maxTokens,
                tools,
                headers(request, options)
            );
            return CompletableFuture.completedFuture(parseChatCompletion(response));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
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

    private String resolveModel(ChatOptions options) {
        if (options != null && options.model() != null && !options.model().isBlank()) {
            return options.model().trim();
        }
        return config.getResolvedModel();
    }

    private GitHubCopilotOAuthClient.AccessToken refreshIfExpired(GitHubCopilotOAuthClient.AccessToken token) {
        if (!isExpired(token)) {
            return token;
        }
        String refreshToken = textValue(token.rawResponse().get("refresh_token"));
        if (refreshToken.isBlank()) {
            throw new IllegalStateException("GitHub Copilot refresh token is not available.");
        }
        GitHubCopilotOAuthClient.AccessToken refreshed = tokenRefresher.refresh(new GitHubCopilotOAuthClient.AccessToken(
            refreshToken,
            token.tokenType(),
            token.scope(),
            Map.of("access_token", refreshToken)
        ));
        tokenStore.save(refreshed);
        return refreshed;
    }

    private boolean isExpired(GitHubCopilotOAuthClient.AccessToken token) {
        return expiresAt(token)
            .map(expiresAt -> !expiresAt.isAfter(clock.instant()))
            .orElse(false);
    }

    private Optional<Instant> expiresAt(GitHubCopilotOAuthClient.AccessToken token) {
        Object value = token.rawResponse().get("expires_at");
        if (value == null) {
            return Optional.empty();
        }
        try {
            long epochSeconds = value instanceof Number number
                ? number.longValue()
                : Long.parseLong(String.valueOf(value).trim());
            return epochSeconds > 0 ? Optional.of(Instant.ofEpochSecond(epochSeconds)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> toCopilotMessage(AgentMessage message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("role", toOpenAIRole(message.role()));
        payload.put("content", message.role() == MessageRole.TOOL_RESULT
            ? toolResultContent(message.content())
            : message.content());
        if (message.role() == MessageRole.TOOL_RESULT) {
            Object toolCallId = message.metadata().get("toolCallId");
            if (toolCallId != null) {
                payload.put("tool_call_id", String.valueOf(toolCallId));
            }
        }
        return Map.copyOf(payload);
    }

    private String toOpenAIRole(MessageRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case ASSISTANT -> "assistant";
            case TOOL_RESULT -> "tool";
            case USER -> "user";
        };
    }

    private String toolResultContent(String content) {
        return content == null || content.isBlank() ? NO_TOOL_OUTPUT : content;
    }

    private Map<String, String> headers(ChatRequest request, ChatOptions options) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Initiator", initiator(request.messages()));
        headers.put("Openai-Intent", "conversation-edits");
        if (hasImageInput(request.messages())) {
            headers.put("Copilot-Vision-Request", "true");
        }
        if (options != null) {
            headers.putAll(options.headers());
        }
        return Map.copyOf(headers);
    }

    private String initiator(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "user";
        }
        AgentMessage last = messages.get(messages.size() - 1);
        return last.role() == MessageRole.USER ? "user" : "agent";
    }

    private boolean hasImageInput(List<AgentMessage> messages) {
        if (messages == null) {
            return false;
        }
        return messages.stream()
            .filter(message -> message.role() == MessageRole.USER || message.role() == MessageRole.TOOL_RESULT)
            .anyMatch(message -> message.content().contains("data:image/"));
    }

    private String baseUrl(String token) {
        Matcher matcher = PROXY_ENDPOINT.matcher(token);
        if (!matcher.find()) {
            return DEFAULT_BASE_URL;
        }
        String host = matcher.group(1).replaceFirst("^proxy\\.", "api.");
        return "https://" + host;
    }

    private AgentMessage parseChatCompletion(String response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response);
            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String content = message.path("content").asText(null);
            if (content == null) {
                throw new RuntimeException("Invalid response from GitHub Copilot API");
            }
            Map<String, Object> metadata = new HashMap<>();
            String finishReason = choice.path("finish_reason").asText(null);
            if (finishReason != null) {
                metadata.put("finishReason", finishReason);
            }
            Map<String, Object> usage = parseUsage(root.path("usage"));
            if (!usage.isEmpty()) {
                metadata.put("usage", usage);
            }
            return new AgentMessage(MessageRole.ASSISTANT, content, metadata);
        } catch (Exception e) {
            throw new RuntimeException("Invalid response from GitHub Copilot API", e);
        }
    }

    private Map<String, Object> parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return Map.of();
        }
        Map<String, Object> usage = new HashMap<>();
        putLongIfPresent(usage, "inputTokens", usageNode.path("prompt_tokens"));
        putLongIfPresent(usage, "outputTokens", usageNode.path("completion_tokens"));
        putLongIfPresent(usage, "totalTokens", usageNode.path("total_tokens"));
        putLongIfPresent(usage, "reasoningTokens", usageNode.path("completion_tokens_details").path("reasoning_tokens"));
        return Map.copyOf(usage);
    }

    private void putLongIfPresent(Map<String, Object> target, String key, JsonNode valueNode) {
        if (valueNode != null && valueNode.canConvertToLong()) {
            target.put(key, valueNode.asLong());
        }
    }

    interface CopilotChatTransport {
        String chat(
            String baseUrl,
            String accessToken,
            String model,
            List<Map<String, Object>> messages,
            double temperature,
            int maxTokens,
            List<Map<String, Object>> tools,
            Map<String, String> headers
        );
    }

    interface CopilotTokenRefresher {
        GitHubCopilotOAuthClient.AccessToken refresh(GitHubCopilotOAuthClient.AccessToken githubAccessToken);
    }

    static class OAuthClientCopilotTokenRefresher implements CopilotTokenRefresher {
        private final GitHubCopilotOAuthClient oauthClient;

        OAuthClientCopilotTokenRefresher(GitHubCopilotOAuthClient oauthClient) {
            this.oauthClient = Objects.requireNonNull(oauthClient, "oauthClient");
        }

        @Override
        public GitHubCopilotOAuthClient.AccessToken refresh(
            GitHubCopilotOAuthClient.AccessToken githubAccessToken
        ) {
            return oauthClient.refreshCopilotAccessToken(githubAccessToken);
        }
    }

    static class WebClientCopilotChatTransport implements CopilotChatTransport {
        private final WebClient webClient;

        WebClientCopilotChatTransport(WebClient webClient) {
            this.webClient = Objects.requireNonNull(webClient, "webClient");
        }

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
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools.stream().map(this::openAITool).toList());
            }

            return webClient.post()
                .uri(baseUrl + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    applyMutableHeaders(httpHeaders, headers);
                    httpHeaders.setBearerAuth(accessToken);
                    httpHeaders.set(HttpHeaders.USER_AGENT, "GitHubCopilotChat/0.35.0");
                    httpHeaders.set("Editor-Version", "vscode/1.107.0");
                    httpHeaders.set("Editor-Plugin-Version", "copilot-chat/0.35.0");
                    httpHeaders.set("Copilot-Integration-Id", "vscode-chat");
                    httpHeaders.set("X-GitHub-Api-Version", "2026-06-01");
                })
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
        }

        private Map<String, Object> openAITool(Map<String, Object> tool) {
            return Map.of(
                "type", "function",
                "function", Map.of(
                    "name", tool.get("name"),
                    "description", tool.getOrDefault("description", ""),
                    "parameters", tool.getOrDefault("input_schema", Map.of("type", "object"))
                )
            );
        }

        private void applyMutableHeaders(HttpHeaders httpHeaders, Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) {
                return;
            }
            headers.forEach((name, value) -> {
                if (isMutableHeader(name, value)) {
                    httpHeaders.set(name.trim(), value.trim());
                }
            });
        }

        private boolean isMutableHeader(String name, String value) {
            if (name == null || name.isBlank() || value == null) {
                return false;
            }
            String normalized = name.trim().toLowerCase();
            return !normalized.equals("authorization")
                && !normalized.equals("host")
                && !normalized.equals("content-length");
        }
    }
}

package com.pi.mono.llm.client;

import com.pi.mono.llm.config.AnthropicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "pi.llm.anthropic.enabled", havingValue = "true")
public class AnthropicClient {
    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_THINKING_BUDGET_TOKENS = 1024;

    private final WebClient webClient;

    @Autowired
    public AnthropicClient(AnthropicConfig config) {
        WebClient.Builder builder = WebClient.builder()
            .baseUrl(config.getResolvedBaseUrl())
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        applyAuthHeaders(builder, config);
        this.webClient = builder.build();
    }

    AnthropicClient(AnthropicConfig config, WebClient webClient) {
        WebClient.Builder builder = webClient.mutate()
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        applyAuthHeaders(builder, config);
        this.webClient = builder.build();
    }

    public Mono<String> createMessage(
        String model,
        List<Map<String, String>> messages,
        String system,
        double temperature,
        int maxTokens
    ) {
        return createMessage(model, messages, system, temperature, maxTokens, null);
    }

    public Mono<String> createMessage(
        String model,
        List<Map<String, String>> messages,
        String system,
        double temperature,
        int maxTokens,
        String requestApiKey
    ) {
        return createMessage(model, messages, system, temperature, maxTokens, List.of(), requestApiKey);
    }

    public Mono<String> createMessage(
        String model,
        List<Map<String, String>> messages,
        String system,
        double temperature,
        int maxTokens,
        List<Map<String, Object>> tools,
        String requestApiKey
    ) {
        List<Map<String, Object>> contentMessages = messages.stream()
            .<Map<String, Object>>map(message -> new HashMap<>(message))
            .toList();
        return createMessageWithContentParts(
            model,
            contentMessages,
            system,
            temperature,
            maxTokens,
            tools,
            requestApiKey
        );
    }

    public Mono<String> createMessageWithContentParts(
        String model,
        List<Map<String, Object>> messages,
        String system,
        double temperature,
        int maxTokens,
        List<Map<String, Object>> tools,
        String requestApiKey
    ) {
        return createMessageWithContentParts(
            model,
            messages,
            system,
            temperature,
            maxTokens,
            tools,
            requestApiKey,
            Map.of()
        );
    }

    public Mono<String> createMessageWithContentParts(
        String model,
        List<Map<String, Object>> messages,
        String system,
        double temperature,
        int maxTokens,
        List<Map<String, Object>> tools,
        String requestApiKey,
        Map<String, String> requestHeaders
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        if (supportsAdaptiveThinking(model)) {
            requestBody.put("thinking", adaptiveThinkingBlock());
        }
        if (system != null && !system.isBlank()) {
            requestBody.put("system", system);
        }
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools.stream()
                .map(this::anthropicTool)
                .toList());
        }

        return webClient.post()
            .uri("/messages")
            .headers(headers -> {
                applyRequestHeaders(headers, requestHeaders);
                if (requestApiKey != null && !requestApiKey.isBlank()) {
                    headers.set("x-api-key", requestApiKey.trim());
                }
            })
            .bodyValue(requestBody)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse -> {
                log.error("Anthropic API error: {}", clientResponse.statusCode());
                return clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> Mono.error(toAnthropicException(clientResponse.statusCode(), errorBody)));
            })
            .bodyToMono(String.class)
            .retryWhen(Retry.max(1).filter(AnthropicClient::isRetryableProviderError))
            .doOnSuccess(response -> log.debug("Received Anthropic message response"))
            .doOnError(error -> log.error("Anthropic message request failed", error));
    }

    public static String formatHttpErrorMessage(HttpStatusCode statusCode, String responseBody) {
        String body = responseBody == null || responseBody.isBlank() ? "<empty>" : responseBody;
        return "Anthropic API error: " + statusCode + ", response body: " + body;
    }

    private static AnthropicException toAnthropicException(HttpStatusCode statusCode, String responseBody) {
        String message = formatHttpErrorMessage(statusCode, responseBody);
        if (isRetryInstruction(responseBody)) {
            return new RetryableAnthropicException(message);
        }
        return new AnthropicException(message);
    }

    private static boolean isRetryableProviderError(Throwable error) {
        return error instanceof RetryableAnthropicException;
    }

    static boolean isRetryInstruction(String responseBody) {
        if (responseBody == null) {
            return false;
        }
        String normalizedBody = responseBody.toLowerCase();
        return normalizedBody.contains("please retry")
            || normalizedBody.contains("retry the request")
            || normalizedBody.contains("try again")
            || normalizedBody.contains("temporarily unavailable");
    }

    private boolean supportsAdaptiveThinking(String model) {
        return model != null && model.contains("sonnet-5");
    }

    private Map<String, Object> adaptiveThinkingBlock() {
        return Map.of(
            "type", "enabled",
            "budget_tokens", DEFAULT_THINKING_BUDGET_TOKENS
        );
    }

    private Map<String, Object> anthropicTool(Map<String, Object> tool) {
        Map<String, Object> requestTool = new HashMap<>();
        requestTool.put("name", tool.get("name"));
        requestTool.put("description", tool.getOrDefault("description", ""));
        requestTool.put("input_schema", tool.getOrDefault("input_schema", Map.of("type", "object")));
        return requestTool;
    }

    private static void applyAuthHeaders(WebClient.Builder builder, AnthropicConfig config) {
        String apiKey = config.getResolvedApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("x-api-key", apiKey);
        }
        String authToken = config.getResolvedAuthToken();
        if (authToken != null && !authToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authToken);
        }
        config.getResolvedCustomHeaders().forEach(builder::defaultHeader);
    }

    private static void applyRequestHeaders(HttpHeaders headers, Map<String, String> requestHeaders) {
        if (requestHeaders == null || requestHeaders.isEmpty()) {
            return;
        }
        requestHeaders.forEach((name, value) -> {
            if (isMutableRequestHeader(name, value)) {
                headers.set(name.trim(), value.trim());
            }
        });
    }

    private static boolean isMutableRequestHeader(String name, String value) {
        if (name == null || name.isBlank() || value == null) {
            return false;
        }
        String normalized = name.trim().toLowerCase();
        return !normalized.equals("authorization")
            && !normalized.equals("x-api-key")
            && !normalized.equals("host")
            && !normalized.equals("content-length");
    }

    public static class AnthropicException extends RuntimeException {
        public AnthropicException(String message) {
            super(message);
        }
    }

    private static class RetryableAnthropicException extends AnthropicException {
        RetryableAnthropicException(String message) {
            super(message);
        }
    }
}

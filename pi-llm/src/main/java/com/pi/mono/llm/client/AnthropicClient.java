package com.pi.mono.llm.client;

import com.pi.mono.llm.config.AnthropicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicClient {
    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int DEFAULT_THINKING_BUDGET_TOKENS = 1024;

    private final WebClient webClient;

    public AnthropicClient(AnthropicConfig config) {
        this(config, WebClient.builder()
            .baseUrl(config.getResolvedBaseUrl())
            .defaultHeader("x-api-key", config.getApiKey() == null ? "" : config.getApiKey())
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build());
    }

    AnthropicClient(AnthropicConfig config, WebClient webClient) {
        this.webClient = webClient.mutate()
            .defaultHeader("x-api-key", config.getApiKey() == null ? "" : config.getApiKey())
            .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
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
            requestBody.put("tools", tools);
        }

        return webClient.post()
            .uri("/messages")
            .headers(headers -> {
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
            .doOnSuccess(response -> log.debug("Received Anthropic message response"))
            .doOnError(error -> log.error("Anthropic message request failed", error));
    }

    public static String formatHttpErrorMessage(HttpStatusCode statusCode, String responseBody) {
        String body = responseBody == null || responseBody.isBlank() ? "<empty>" : responseBody;
        return "Anthropic API error: " + statusCode + ", response body: " + body;
    }

    private static AnthropicException toAnthropicException(HttpStatusCode statusCode, String responseBody) {
        return new AnthropicException(formatHttpErrorMessage(statusCode, responseBody));
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

    public static class AnthropicException extends RuntimeException {
        public AnthropicException(String message) {
            super(message);
        }
    }
}

package com.pi.mono.llm.client;

import com.pi.mono.llm.config.OpenAIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 简化的OpenAI HTTP客户端
 * 专注于核心功能，避免复杂的类引用
 */
@Component
public class OpenAIClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;

    public OpenAIClient(OpenAIConfig config) {
        this.baseUrl = config.getResolvedBaseUrl();
        this.apiKey = config.getApiKey();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    OpenAIClient(OpenAIConfig config, WebClient webClient) {
        this.baseUrl = config.getResolvedBaseUrl();
        this.apiKey = config.getApiKey();
        this.webClient = webClient;
    }

    /**
     * 创建聊天完成请求
     */
    public Mono<String> createChatCompletion(String model, List<Map<String, String>> messages) {
        return createChatCompletion(model, messages, 0.7, 1000);
    }

    public Mono<String> createChatCompletion(
        String model,
        List<Map<String, String>> messages,
        double temperature,
        int maxTokens
    ) {
        return createChatCompletion(model, messages, temperature, maxTokens, null);
    }

    public Mono<String> createChatCompletion(
        String model,
        List<Map<String, String>> messages,
        double temperature,
        int maxTokens,
        String requestApiKey
    ) {
        return createChatCompletion(model, messages, temperature, maxTokens, List.of(), requestApiKey);
    }

    public Mono<String> createChatCompletion(
        String model,
        List<Map<String, String>> messages,
        double temperature,
        int maxTokens,
        List<Map<String, Object>> tools,
        String requestApiKey
    ) {
        log.debug("Sending chat completion request for model: {}", model);

        // 构建请求体
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools.stream()
                .map(this::openAITool)
                .toList());
        }

        return webClient.post()
                .uri("chat/completions")
                .headers(headers -> headers.setBearerAuth(resolveApiKey(requestApiKey)))
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> {
                    log.error("OpenAI API error: {}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(toOpenAIException(clientResponse.statusCode(), errorBody)));
                })
                .bodyToMono(String.class)
                .retryWhen(Retry.max(1).filter(OpenAIClient::isRetryableProviderError))
                .doOnSuccess(response -> log.debug("Received chat completion response"))
                .doOnError(error -> log.error("Chat completion failed", error));
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

    private String resolveApiKey(String requestApiKey) {
        if (requestApiKey != null && !requestApiKey.isBlank()) {
            return requestApiKey.trim();
        }
        return apiKey;
    }

    /**
     * 测试连接
     */
    public Mono<Boolean> testConnection() {
        return webClient.get()
                .uri("models")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> response != null && response.contains("data"))
                .onErrorReturn(false)
                .doOnNext(success -> log.info("OpenAI connection test: {}", success ? "SUCCESS" : "FAILED"));
    }

    public static String formatHttpErrorMessage(HttpStatusCode statusCode, String responseBody) {
        String body = responseBody == null || responseBody.isBlank() ? "<empty>" : responseBody;
        return "OpenAI API error: " + statusCode + ", response body: " + body;
    }

    private static OpenAIException toOpenAIException(HttpStatusCode statusCode, String responseBody) {
        String message = formatHttpErrorMessage(statusCode, responseBody);
        if (isRetryInstruction(responseBody)) {
            return new RetryableOpenAIException(message);
        }
        return new OpenAIException(message);
    }

    private static boolean isRetryableProviderError(Throwable error) {
        return error instanceof RetryableOpenAIException;
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

    /**
     * OpenAI异常
     */
    public static class OpenAIException extends RuntimeException {
        public OpenAIException(String message) {
            super(message);
        }

        public OpenAIException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RetryableOpenAIException extends OpenAIException {
        public RetryableOpenAIException(String message) {
            super(message);
        }
    }
}

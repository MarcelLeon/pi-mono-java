package com.pi.mono.llm.client;

import com.pi.mono.llm.config.OpenAIConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
        this.baseUrl = config.getBaseUrl();
        this.apiKey = config.getApiKey();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 创建聊天完成请求
     */
    public Mono<String> createChatCompletion(String model, List<Map<String, String>> messages) {
        log.debug("Sending chat completion request for model: {}", model);

        // 构建请求体
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1000);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), clientResponse -> {
                    log.error("OpenAI API error: {}", clientResponse.statusCode());
                    return clientResponse.bodyToMono(String.class)
                            .flatMap(errorBody -> Mono.error(new OpenAIException(
                                    "OpenAI API error: " + clientResponse.statusCode() + ", " + errorBody
                            )));
                })
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.debug("Received chat completion response"))
                .doOnError(error -> log.error("Chat completion failed", error));
    }

    /**
     * 测试连接
     */
    public Mono<Boolean> testConnection() {
        return webClient.get()
                .uri("/models")
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> response != null && response.contains("data"))
                .onErrorReturn(false)
                .doOnNext(success -> log.info("OpenAI connection test: {}", success ? "SUCCESS" : "FAILED"));
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
}
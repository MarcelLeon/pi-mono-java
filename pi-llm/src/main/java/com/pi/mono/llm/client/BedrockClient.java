package com.pi.mono.llm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.llm.config.BedrockConfig;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "pi.llm.bedrock.enabled", havingValue = "true")
public class BedrockClient {
    private static final Logger log = LoggerFactory.getLogger(BedrockClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";
    private static final int DEFAULT_THINKING_BUDGET_TOKENS = 1024;
    private static final String AWS_ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String AWS_SERVICE = "bedrock";
    private static final DateTimeFormatter AMZ_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final BedrockConfig config;
    private final WebClient webClient;
    private final Clock clock;

    @Autowired
    public BedrockClient(BedrockConfig config) {
        this(config, WebClient.builder()
            .baseUrl(config.getRuntimeEndpoint())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build());
    }

    BedrockClient(BedrockConfig config, WebClient webClient) {
        this(config, webClient, Clock.systemUTC());
    }

    BedrockClient(BedrockConfig config, WebClient webClient, Clock clock) {
        this.config = config;
        this.webClient = webClient.mutate()
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.clock = clock;
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
        BedrockConfig.Credentials requestCredentials
    ) {
        List<Map<String, Object>> contentMessages = messages.stream()
            .<Map<String, Object>>map(message -> new HashMap<>(message))
            .toList();
        return createMessageWithContentBlocks(
            model,
            contentMessages,
            system,
            temperature,
            maxTokens,
            requestCredentials
        );
    }

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
            requestCredentials,
            Map.of()
        );
    }

    public Mono<String> createMessageWithContentBlocks(
        String model,
        List<Map<String, Object>> messages,
        String system,
        double temperature,
        int maxTokens,
        List<Map<String, Object>> tools,
        BedrockConfig.Credentials requestCredentials
    ) {
        return createMessageWithContentBlocks(
            model,
            messages,
            system,
            temperature,
            maxTokens,
            tools,
            requestCredentials,
            Map.of()
        );
    }

    public Mono<String> createMessageWithContentBlocks(
        String model,
        List<Map<String, Object>> messages,
        String system,
        double temperature,
        int maxTokens,
        List<Map<String, Object>> tools,
        BedrockConfig.Credentials requestCredentials,
        Map<String, String> headers
    ) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("anthropic_version", ANTHROPIC_VERSION);
        requestBody.put("messages", toBedrockMessages(model, messages, system == null || system.isBlank()));
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        if (supportsAdaptiveThinking(model)) {
            requestBody.put("thinking", adaptiveThinkingBlock());
        }
        if (system != null && !system.isBlank()) {
            requestBody.put("system", List.of(systemBlock(model, system)));
        }
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools.stream()
                .map(this::bedrockTool)
                .toList());
        }
        String requestJson;
        try {
            requestJson = OBJECT_MAPPER.writeValueAsString(requestBody);
        } catch (Exception e) {
            return Mono.error(e);
        }
        String path = "/model/" + model + "/invoke";
        Map<String, String> signingHeaders = signingHeaders("POST", path, requestJson, requestCredentials);

        return webClient.post()
            .uri(path)
            .headers(httpHeaders -> {
                if (headers != null) {
                    headers.forEach(httpHeaders::set);
                }
                signingHeaders.forEach(httpHeaders::set);
            })
            .bodyValue(requestJson)
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse -> {
                log.error("Bedrock API error: {}", clientResponse.statusCode());
                return clientResponse.bodyToMono(String.class)
                    .flatMap(errorBody -> Mono.error(toBedrockException(clientResponse.statusCode(), errorBody)));
            })
            .bodyToMono(String.class)
            .doOnSuccess(response -> log.debug("Received Bedrock message response"))
            .doOnError(error -> log.error("Bedrock message request failed", error));
    }

    public static String formatHttpErrorMessage(HttpStatusCode statusCode, String responseBody) {
        String body = responseBody == null || responseBody.isBlank() ? "<empty>" : responseBody;
        return "Bedrock API error: " + statusCode + ", response body: " + body;
    }

    private List<Map<String, Object>> toBedrockMessages(
        String model,
        List<Map<String, Object>> messages,
        boolean cacheFirstMessage
    ) {
        List<Map<String, Object>> requestMessages = new java.util.ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> message = messages.get(i);
            boolean cacheBlock = cacheFirstMessage && i == 0 && supportsPromptCaching(model);
            requestMessages.add(Map.of(
                "role", message.getOrDefault("role", "user"),
                "content", toContentBlocks(message.get("content"), cacheBlock)
            ));
        }
        return requestMessages;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toContentBlocks(Object content, boolean cacheBlock) {
        if (content instanceof List<?>) {
            return (List<Map<String, Object>>) content;
        }
        return List.of(textBlock(content == null ? "" : content.toString(), cacheBlock));
    }

    private Map<String, Object> systemBlock(String model, String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "text");
        block.put("text", text);
        if (supportsPromptCaching(model)) {
            block.put("cache_control", Map.of("type", "ephemeral"));
        }
        return block;
    }

    private Map<String, Object> textBlock(String text, boolean cacheBlock) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "text");
        block.put("text", text);
        if (cacheBlock) {
            block.put("cache_control", Map.of("type", "ephemeral"));
        }
        return block;
    }

    private Map<String, Object> bedrockTool(Map<String, Object> tool) {
        Map<String, Object> requestTool = new HashMap<>();
        requestTool.put("type", tool.getOrDefault("type", "custom"));
        requestTool.put("name", tool.get("name"));
        requestTool.put("description", tool.getOrDefault("description", ""));
        requestTool.put("input_schema", tool.getOrDefault("input_schema", Map.of("type", "object")));
        return requestTool;
    }

    private static BedrockException toBedrockException(HttpStatusCode statusCode, String responseBody) {
        return new BedrockException(formatHttpErrorMessage(statusCode, responseBody));
    }

    private Map<String, String> signingHeaders(
        String method,
        String path,
        String requestJson,
        BedrockConfig.Credentials requestCredentials
    ) {
        Optional<BedrockConfig.Credentials> credentials = requestCredentials == null
            ? config.resolveCredentials()
            : Optional.of(requestCredentials);
        if (credentials.isEmpty()) {
            return Map.of();
        }

        try {
            BedrockConfig.Credentials resolvedCredentials = credentials.get();
            Instant now = clock.instant();
            String amzDate = AMZ_DATE_FORMAT.format(now);
            String dateStamp = DATE_STAMP_FORMAT.format(now);
            String region = config.getRegion() == null || config.getRegion().isBlank()
                ? "us-east-1"
                : config.getRegion().trim();
            String host = URI.create(config.getRuntimeEndpoint()).getHost();
            String payloadHash = sha256Hex(requestJson);
            Map<String, String> headers = new HashMap<>();
            headers.put("content-type", MediaType.APPLICATION_JSON_VALUE);
            headers.put("host", host);
            headers.put("x-amz-content-sha256", payloadHash);
            headers.put("x-amz-date", amzDate);
            if (resolvedCredentials.sessionToken() != null && !resolvedCredentials.sessionToken().isBlank()) {
                headers.put("x-amz-security-token", resolvedCredentials.sessionToken());
            }

            String signedHeaders = headers.keySet().stream().sorted().reduce((left, right) -> left + ";" + right)
                .orElse("");
            StringBuilder canonicalHeaders = new StringBuilder();
            headers.keySet().stream().sorted().forEach(name ->
                canonicalHeaders.append(name).append(':').append(headers.get(name).trim()).append('\n')
            );
            String canonicalRequest = method + "\n"
                + path + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
            String credentialScope = dateStamp + "/" + region + "/" + AWS_SERVICE + "/aws4_request";
            String stringToSign = AWS_ALGORITHM + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);
            byte[] signingKey = signingKey(resolvedCredentials.secretAccessKey(), dateStamp, region);
            String signature = hmacHex(signingKey, stringToSign);
            headers.put(HttpHeaders.AUTHORIZATION, AWS_ALGORITHM
                + " Credential=" + resolvedCredentials.accessKeyId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature);
            headers.remove("host");
            return headers;
        } catch (Exception e) {
            throw new BedrockException("Failed to sign Bedrock request: " + e.getMessage());
        }
    }

    private byte[] signingKey(String secretAccessKey, String dateStamp, String region) throws Exception {
        byte[] dateKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] dateRegionKey = hmac(dateKey, region);
        byte[] dateRegionServiceKey = hmac(dateRegionKey, AWS_SERVICE);
        return hmac(dateRegionServiceKey, "aws4_request");
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return hex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacHex(byte[] key, String data) throws Exception {
        return hex(hmac(key, data));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private boolean supportsPromptCaching(String model) {
        return model != null && model.contains("claude-sonnet-5");
    }

    private boolean supportsAdaptiveThinking(String model) {
        return model != null && model.contains("claude-sonnet-5");
    }

    private Map<String, Object> adaptiveThinkingBlock() {
        return Map.of(
            "type", "enabled",
            "budget_tokens", DEFAULT_THINKING_BUDGET_TOKENS
        );
    }

    public static class BedrockException extends RuntimeException {
        public BedrockException(String message) {
            super(message);
        }
    }
}

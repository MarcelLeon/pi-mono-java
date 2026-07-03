package com.pi.mono.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.llm.config.BedrockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void writesClaude5InvokeRequestWithPromptCaching() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .baseUrl(testConfig().getRuntimeEndpoint())
            .exchangeFunction(request -> {
                assertEquals(HttpMethod.POST, request.method());
                assertEquals("/model/anthropic.claude-sonnet-5/invoke", request.url().getPath());
                MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
                mockRequest.setWriteHandler(dataBuffers -> DataBufferUtils.join(dataBuffers)
                    .doOnNext(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        body.set(new String(bytes, StandardCharsets.UTF_8));
                    })
                    .then());
                BodyInserter.Context context = new BodyInserter.Context() {
                    @Override
                    public List<HttpMessageWriter<?>> messageWriters() {
                        return ExchangeStrategies.withDefaults().messageWriters();
                    }

                    @Override
                    public Optional<ServerHttpRequest> serverRequest() {
                        return Optional.empty();
                    }

                    @Override
                    public Map<String, Object> hints() {
                        return Map.of();
                    }
                };
                return request.body().insert(mockRequest, context)
                    .thenReturn(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}")
                        .build());
            })
            .build();
        BedrockClient client = new BedrockClient(testConfig(), webClient);

        client.createMessage(
            "anthropic.claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "system prompt",
            0.2,
            512
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        assertEquals("bedrock-2023-05-31", requestBody.path("anthropic_version").asText());
        assertEquals(512, requestBody.path("max_tokens").asInt());
        assertEquals(0.2, requestBody.path("temperature").asDouble());
        assertEquals("enabled", requestBody.path("thinking").path("type").asText());
        assertEquals(1024, requestBody.path("thinking").path("budget_tokens").asInt());
        assertEquals("system prompt", requestBody.path("system").path(0).path("text").asText());
        assertEquals("ephemeral", requestBody.path("system").path(0).path("cache_control").path("type").asText());
        assertEquals("user", requestBody.path("messages").path(0).path("role").asText());
        assertEquals("hello", requestBody.path("messages").path(0).path("content").path(0).path("text").asText());
    }

    @Test
    void writesClaude5PromptCachingOnFirstMessageWhenSystemPromptIsAbsent() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .baseUrl(testConfig().getRuntimeEndpoint())
            .exchangeFunction(request -> {
                MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
                mockRequest.setWriteHandler(dataBuffers -> DataBufferUtils.join(dataBuffers)
                    .doOnNext(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        body.set(new String(bytes, StandardCharsets.UTF_8));
                    })
                    .then());
                BodyInserter.Context context = new BodyInserter.Context() {
                    @Override
                    public List<HttpMessageWriter<?>> messageWriters() {
                        return ExchangeStrategies.withDefaults().messageWriters();
                    }

                    @Override
                    public Optional<ServerHttpRequest> serverRequest() {
                        return Optional.empty();
                    }

                    @Override
                    public Map<String, Object> hints() {
                        return Map.of();
                    }
                };
                return request.body().insert(mockRequest, context)
                    .thenReturn(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}")
                        .build());
            })
            .build();
        BedrockClient client = new BedrockClient(testConfig(), webClient);

        client.createMessage(
            "anthropic.claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "",
            0.2,
            512
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        assertEquals(
            "ephemeral",
            requestBody.path("messages").path(0).path("content").path(0).path("cache_control").path("type").asText()
        );
    }

    @Test
    void signsInvokeRequestWhenAwsCredentialsAreConfigured() {
        BedrockConfig config = testConfig();
        config.setAccessKeyId("AKIDEXAMPLE");
        config.setSecretAccessKey("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY");
        config.setSessionToken("session-token");
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .baseUrl(config.getRuntimeEndpoint())
            .exchangeFunction(request -> {
                headers.set(request.headers());
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}")
                    .build());
            })
            .build();
        BedrockClient client = new BedrockClient(config, webClient);

        client.createMessage(
            "anthropic.claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "",
            0.7,
            1000
        ).block();

        String authorization = headers.get().getFirst(HttpHeaders.AUTHORIZATION);
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 "));
        assertTrue(authorization.contains("Credential=AKIDEXAMPLE/"));
        assertTrue(authorization.contains("/us-west-2/bedrock/aws4_request"));
        assertTrue(authorization.contains("SignedHeaders="));
        assertTrue(authorization.contains("Signature="));
        assertFalse(headers.get().getFirst("x-amz-content-sha256").isBlank());
        assertFalse(headers.get().getFirst("x-amz-date").isBlank());
        assertEquals("session-token", headers.get().getFirst("x-amz-security-token"));
    }

    @Test
    void requestScopedAwsCredentialsSignInvokeRequest() {
        AtomicReference<HttpHeaders> headers = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .baseUrl(testConfig().getRuntimeEndpoint())
            .exchangeFunction(request -> {
                headers.set(request.headers());
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}")
                    .build());
            })
            .build();
        BedrockClient client = new BedrockClient(testConfig(), webClient);

        client.createMessage(
            "anthropic.claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "",
            0.7,
            1000,
            new BedrockConfig.Credentials("REQUESTACCESS", "REQUESTSECRET", "request-token")
        ).block();

        String authorization = headers.get().getFirst(HttpHeaders.AUTHORIZATION);
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 "));
        assertTrue(authorization.contains("Credential=REQUESTACCESS/"));
        assertTrue(authorization.contains("/us-west-2/bedrock/aws4_request"));
        assertEquals("request-token", headers.get().getFirst("x-amz-security-token"));
    }

    @Test
    void includesResponseBodyInHttpErrors() {
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.FORBIDDEN)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"message\":\"signature mismatch\"}")
                .build()))
            .build();
        BedrockClient client = new BedrockClient(testConfig(), webClient);

        RuntimeException error = assertThrows(RuntimeException.class, () -> client.createMessage(
            "anthropic.claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "",
            0.7,
            1000
        ).block());

        assertEquals(
            "Bedrock API error: 403 FORBIDDEN, response body: {\"message\":\"signature mismatch\"}",
            error.getMessage()
        );
    }

    private BedrockConfig testConfig() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");
        return config;
    }
}

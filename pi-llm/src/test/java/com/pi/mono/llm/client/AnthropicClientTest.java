package com.pi.mono.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.llm.config.AnthropicConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnthropicClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void writesMessagesRequestBodyAndHeaders() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        AtomicReference<String> versionHeader = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .baseUrl(testConfig().getResolvedBaseUrl())
            .exchangeFunction(request -> {
                assertEquals(HttpMethod.POST, request.method());
                assertEquals("/v1/messages", request.url().getPath());
                apiKeyHeader.set(request.headers().getFirst("x-api-key"));
                versionHeader.set(request.headers().getFirst("anthropic-version"));
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
        AnthropicClient client = new AnthropicClient(testConfig(), webClient);

        client.createMessage(
            "claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "system prompt",
            0.2,
            512
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        assertEquals("test-key", apiKeyHeader.get());
        assertEquals("2023-06-01", versionHeader.get());
        assertEquals("claude-sonnet-5", requestBody.path("model").asText());
        assertEquals("system prompt", requestBody.path("system").asText());
        assertEquals(512, requestBody.path("max_tokens").asInt());
        assertEquals(0.2, requestBody.path("temperature").asDouble());
        assertEquals("enabled", requestBody.path("thinking").path("type").asText());
        assertEquals(1024, requestBody.path("thinking").path("budget_tokens").asInt());
        assertEquals("user", requestBody.path("messages").path(0).path("role").asText());
        assertEquals("hello", requestBody.path("messages").path(0).path("content").asText());
    }

    @Test
    void includesResponseBodyInHttpErrors() {
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":{\"message\":\"bad anthropic request\"}}")
                .build()))
            .build();
        AnthropicClient client = new AnthropicClient(testConfig(), webClient);

        RuntimeException error = assertThrows(RuntimeException.class, () -> client.createMessage(
            "claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "",
            0.7,
            1000
        ).block());

        assertEquals(
            "Anthropic API error: 400 BAD_REQUEST, response body: {\"error\":{\"message\":\"bad anthropic request\"}}",
            error.getMessage()
        );
    }

    @Test
    void requestScopedApiKeyOverridesConfiguredHeader() {
        AtomicReference<String> apiKeyHeader = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                apiKeyHeader.set(request.headers().getFirst("x-api-key"));
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}")
                    .build());
            })
            .build();
        AnthropicClient client = new AnthropicClient(testConfig(), webClient);

        client.createMessage(
            "claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "hello")),
            "",
            0.7,
            1000,
            "request-key"
        ).block();

        assertEquals("request-key", apiKeyHeader.get());
    }

    @Test
    void writesToolSchemasIntoMessagesRequestBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .baseUrl(testConfig().getResolvedBaseUrl())
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
        AnthropicClient client = new AnthropicClient(testConfig(), webClient);

        client.createMessage(
            "claude-sonnet-5",
            List.of(Map.of("role", "user", "content", "weather?")),
            "",
            0.7,
            1000,
            List.of(Map.of(
                "name", "get_weather",
                "description", "Get weather for a city",
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("city", Map.of("type", "string"))
                )
            )),
            "request-key"
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        assertEquals("get_weather", requestBody.path("tools").path(0).path("name").asText());
        assertEquals("Get weather for a city", requestBody.path("tools").path(0).path("description").asText());
        assertEquals(
            "object",
            requestBody.path("tools").path(0).path("input_schema").path("type").asText()
        );
    }

    private AnthropicConfig testConfig() {
        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey("test-key");
        return config;
    }
}

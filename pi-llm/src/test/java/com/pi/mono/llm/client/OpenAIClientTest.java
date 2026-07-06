package com.pi.mono.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.llm.config.OpenAIConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAIClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void formatsHttpErrorsWithResponseBody() {
        String message = OpenAIClient.formatHttpErrorMessage(
            HttpStatus.BAD_REQUEST,
            "{\"error\":{\"message\":\"bad request detail\"}}"
        );

        assertEquals(
            "OpenAI API error: 400 BAD_REQUEST, response body: {\"error\":{\"message\":\"bad request detail\"}}",
            message
        );
    }

    @Test
    void formatsHttpErrorsWithExplicitEmptyBodyMarker() {
        String message = OpenAIClient.formatHttpErrorMessage(HttpStatus.TOO_MANY_REQUESTS, "   ");

        assertEquals(
            "OpenAI API error: 429 TOO_MANY_REQUESTS, response body: <empty>",
            message
        );
    }

    @Test
    void retriesProviderErrorsThatExplicitlyAskForRetry() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                if (attempts.incrementAndGet() == 1) {
                    return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"error\":{\"message\":\"provider stream failed, please retry the request\"}}")
                        .build());
                }
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                    .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        String response = client.createChatCompletion(
            "gpt-5.5",
            List.of(Map.of("role", "user", "content", "hello"))
        ).block();

        assertEquals("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", response);
        assertEquals(2, attempts.get());
    }

    @Test
    void retriesCloudflareTimeoutStatusFromOpenAICompatibleProviders() {
        AtomicInteger attempts = new AtomicInteger();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                if (attempts.incrementAndGet() == 1) {
                    return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(524))
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body("Cloudflare timeout")
                        .build());
                }
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                    .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        String response = client.createChatCompletion(
            "gpt-5.5",
            List.of(Map.of("role", "user", "content", "hello"))
        ).block();

        assertEquals("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", response);
        assertEquals(2, attempts.get());
    }

    @Test
    void classifiesDs4ContextOverflowErrorsFromOpenAICompatibleProviders() {
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":{\"message\":\"Prompt has 5,958,968 tokens, but the configured context size is 256,000 tokens\"}}")
                .build()))
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> client.createChatCompletion(
            "deepseek-v4",
            List.of(Map.of("role", "user", "content", "hello"))
        ).block());

        assertEquals("ContextOverflowException", exception.getClass().getSimpleName());
    }

    @Test
    void writesRequestScopedModelOptionsIntoRequestBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                assertEquals(HttpMethod.POST, request.method());
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
                        .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                        .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        client.createChatCompletion(
            "gpt-5.5",
            List.of(Map.of("role", "user", "content", "hello")),
            0.25,
            512
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        assertEquals("gpt-5.5", requestBody.path("model").asText());
        assertEquals(0.25, requestBody.path("temperature").asDouble());
        assertEquals(512, requestBody.path("max_tokens").asInt());
        assertEquals("hello", requestBody.path("messages").path(0).path("content").asText());
    }

    @Test
    void clampsMaxTokensBelowOpenAIProviderMinimum() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
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
                        .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                        .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        client.createChatCompletion(
            "gpt-5.5",
            List.of(Map.of("role", "user", "content", "hello")),
            0.25,
            1
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        assertEquals(16, requestBody.path("max_tokens").asInt());
    }

    @Test
    void writesToolSchemasIntoRequestBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
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
                        .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                        .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        client.createChatCompletion(
            "gpt-5.5",
            List.of(Map.of("role", "user", "content", "hello")),
            0.25,
            512,
            List.of(Map.of(
                "name", "weather",
                "description", "Read weather",
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of("city", Map.of("type", "string")),
                    "required", List.of("city")
                )
            )),
            "request-key"
        ).block();

        JsonNode requestBody = OBJECT_MAPPER.readTree(body.get());
        JsonNode tool = requestBody.path("tools").path(0);
        assertEquals("function", tool.path("type").asText());
        assertEquals("weather", tool.path("function").path("name").asText());
        assertEquals("Read weather", tool.path("function").path("description").asText());
        assertEquals("object", tool.path("function").path("parameters").path("type").asText());
        assertEquals("city", tool.path("function").path("parameters").path("required").path(0).asText());
    }

    @Test
    void writesContentPartsIntoRequestBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
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
                        .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                        .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        client.createChatCompletionWithContentParts(
            "gpt-5.5",
            List.of(Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", "inspect"),
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64,abc123"))
                )
            )),
            0.25,
            512,
            List.of(),
            "request-key"
        ).block();

        JsonNode content = OBJECT_MAPPER.readTree(body.get()).path("messages").path(0).path("content");
        assertEquals("text", content.path(0).path("type").asText());
        assertEquals("inspect", content.path(0).path("text").asText());
        assertEquals("image_url", content.path(1).path("type").asText());
        assertEquals("data:image/png;base64,abc123", content.path(1).path("image_url").path("url").asText());
    }

    @Test
    void requestScopedApiKeyOverridesConfiguredAuthorizationHeader() {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> {
                authorizationHeader.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
                    .build());
            })
            .build();

        OpenAIClient client = new OpenAIClient(testConfig(), webClient);

        client.createChatCompletion(
            "gpt-5.5",
            List.of(Map.of("role", "user", "content", "hello")),
            0.25,
            512,
            "request-key"
        ).block();

        assertEquals("Bearer request-key", authorizationHeader.get());
    }

    private OpenAIConfig testConfig() {
        OpenAIConfig config = new OpenAIConfig();
        config.setApiKey("test-key");
        return config;
    }
}

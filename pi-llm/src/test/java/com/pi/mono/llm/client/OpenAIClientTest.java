package com.pi.mono.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.llm.config.OpenAIConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

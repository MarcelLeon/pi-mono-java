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
import com.pi.mono.llm.client.BedrockClient;
import com.pi.mono.llm.config.BedrockConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "pi.llm.bedrock.enabled", havingValue = "true")
public class BedrockLLMProvider implements LLMProvider {
    private static final String PROVIDER_ID = "bedrock";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final BedrockConfig config;
    private final BedrockClient client;

    @Autowired
    public BedrockLLMProvider(BedrockConfig config, BedrockClient client) {
        this.config = config;
        this.client = client;
    }

    public BedrockLLMProvider(BedrockConfig config) {
        this(config, new BedrockClient(config));
    }

    @Override
    public CompletableFuture<AgentMessage> chat(ChatRequest request) {
        try {
            ChatOptions options = request.options();
            String model = resolveModel(options);
            double temperature = options != null ? options.temperature() : 0.7;
            int maxTokens = options != null && options.maxTokens() > 0 ? options.maxTokens() : 1000;
            List<Map<String, String>> messages = toBedrockMessages(request.messages());
            String system = systemPrompt(request.messages());
            BedrockConfig.Credentials requestCredentials = resolveCredentials(options);

            String response = client.createMessage(
                model,
                messages,
                system,
                temperature,
                maxTokens,
                requestCredentials
            ).block();
            return CompletableFuture.completedFuture(parseMessageResponse(response, model));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public List<Model> getAvailableModels() {
        return List.of(
            new Model(
                "anthropic.claude-sonnet-5",
                PROVIDER_ID,
                "Amazon Bedrock Anthropic Claude Sonnet 5 with adaptive thinking and prompt caching",
                200000,
                costPerToken("0.003")
            )
        );
    }

    @Override
    public ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context) {
        return new ToolCallResult(
            toolCall.name(),
            "Bedrock tool call execution is not implemented in this Java adapter yet.",
            Map.of("tool_id", toolCall.id(), "status", "unsupported")
        );
    }

    @Override
    public HealthStatus health() {
        try {
            config.validate();
            return HealthStatus.HEALTHY;
        } catch (IllegalArgumentException e) {
            return HealthStatus.UNHEALTHY;
        }
    }

    @Override
    public boolean isAvailable() {
        return health() == HealthStatus.HEALTHY;
    }

    @Override
    public BigDecimal getCostPerToken() {
        return costPerToken("0.003");
    }

    @Override
    public String getId() {
        return PROVIDER_ID + "-" + config.getModel() + "-" + config.getRegion();
    }

    private String resolveModel(ChatOptions options) {
        if (options != null && options.model() != null && !options.model().isBlank()) {
            return options.model();
        }
        return config.getModel();
    }

    private BedrockConfig.Credentials resolveCredentials(ChatOptions options) {
        if (options == null) {
            return null;
        }
        return config.resolveCredentials(options.env()).orElse(null);
    }

    private List<Map<String, String>> toBedrockMessages(List<AgentMessage> messages) {
        return messages.stream()
            .filter(message -> message.role() != MessageRole.SYSTEM)
            .map(message -> Map.of(
                "role", message.role() == MessageRole.ASSISTANT ? "assistant" : "user",
                "content", message.content() == null ? "" : message.content()
            ))
            .toList();
    }

    private String systemPrompt(List<AgentMessage> messages) {
        return messages.stream()
            .filter(message -> message.role() == MessageRole.SYSTEM)
            .map(AgentMessage::content)
            .filter(content -> content != null && !content.isBlank())
            .findFirst()
            .orElse("");
    }

    private AgentMessage parseMessageResponse(String response, String model) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(response);
        String content = root.path("content").isArray()
            ? firstTextContent(root.path("content"))
            : "";
        JsonNode usage = root.path("usage");

        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("provider", PROVIDER_ID);
        metadata.put("model", model);
        metadata.put("region", config.getRegion());
        metadata.put("endpoint", config.getRuntimeEndpoint());
        metadata.put("adaptiveThinking", supportsAdaptiveThinking(model));
        metadata.put("promptCaching", supportsPromptCaching(model));
        if (usage.has("input_tokens")) {
            metadata.put("inputTokens", usage.path("input_tokens").asInt());
        }
        if (usage.has("output_tokens")) {
            metadata.put("outputTokens", usage.path("output_tokens").asInt());
        }

        return new AgentMessage(MessageRole.ASSISTANT, content, metadata);
    }

    private String firstTextContent(JsonNode content) {
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText()) && item.has("text")) {
                return item.path("text").asText();
            }
        }
        return "";
    }

    private boolean supportsAdaptiveThinking(String model) {
        return model != null && model.contains("claude-sonnet-5");
    }

    private boolean supportsPromptCaching(String model) {
        return model != null && model.contains("claude-sonnet-5");
    }

    private BigDecimal costPerToken(String costPerThousandTokens) {
        return new BigDecimal(costPerThousandTokens).divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
    }
}

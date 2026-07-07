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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "pi.llm.bedrock.enabled", havingValue = "true")
public class BedrockLLMProvider implements LLMProvider {
    private static final String PROVIDER_ID = "bedrock";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NO_TOOL_OUTPUT = "(no tool output)";
    private static final Pattern IMAGE_DATA_URL = Pattern.compile(
        "data:(image/[^;\\s<]+);base64,([A-Za-z0-9+/=]+)"
    );
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
            List<Map<String, Object>> messages = toBedrockMessages(request.messages());
            String system = systemPrompt(request.messages());
            BedrockConfig.Credentials requestCredentials = resolveCredentials(options);
            List<Map<String, Object>> tools = options == null ? List.of() : options.tools();

            String response = client.createMessageWithContentBlocks(
                model,
                messages,
                system,
                temperature,
                maxTokens,
                tools,
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

    private List<Map<String, Object>> toBedrockMessages(List<AgentMessage> messages) {
        return messages.stream()
            .filter(message -> message.role() != MessageRole.SYSTEM)
            .map(this::toBedrockMessage)
            .toList();
    }

    private Map<String, Object> toBedrockMessage(AgentMessage message) {
        Map<String, Object> requestMessage = new HashMap<>();
        requestMessage.put("role", message.role() == MessageRole.ASSISTANT ? "assistant" : "user");
        requestMessage.put("content", toBedrockContent(message));
        return requestMessage;
    }

    private Object toBedrockContent(AgentMessage message) {
        String content = message.content() == null ? "" : message.content();
        if (message.role() == MessageRole.TOOL_RESULT) {
            return List.of(toolResultBlock(message, toolResultContent(content)));
        }
        if (message.role() != MessageRole.USER || !content.contains("data:image/")) {
            return content;
        }

        List<Map<String, Object>> imageParts = imageContentBlocks(content);
        if (imageParts.isEmpty()) {
            return content;
        }

        List<Map<String, Object>> parts = new java.util.ArrayList<>(imageParts);
        String text = textBeforeAttachedFiles(content);
        if (!text.isBlank()) {
            parts.add(Map.of(
                "type", "text",
                "text", text
            ));
        }
        return parts;
    }

    private String toolResultContent(String content) {
        return content.isBlank() ? NO_TOOL_OUTPUT : content;
    }

    private Map<String, Object> toolResultBlock(AgentMessage message, String content) {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "tool_result");
        Object toolCallId = message.metadata().get("toolCallId");
        if (toolCallId != null) {
            block.put("tool_use_id", String.valueOf(toolCallId));
        }
        block.put("content", content);
        Object success = message.metadata().get("success");
        if (Boolean.FALSE.equals(success)) {
            block.put("is_error", true);
        }
        return block;
    }

    private List<Map<String, Object>> imageContentBlocks(String content) {
        List<Map<String, Object>> parts = new java.util.ArrayList<>();
        Matcher matcher = IMAGE_DATA_URL.matcher(content);
        while (matcher.find()) {
            parts.add(Map.of(
                "type", "image",
                "source", Map.of(
                    "type", "base64",
                    "media_type", matcher.group(1),
                    "data", matcher.group(2)
                )
            ));
        }
        return parts;
    }

    private String textBeforeAttachedFiles(String content) {
        int marker = content.indexOf("\n\n[Attached files]\n");
        if (marker >= 0) {
            return content.substring(0, marker).trim();
        }
        return content.trim();
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
        List<Map<String, Object>> toolCalls = toolCalls(root.path("content"));
        if (!toolCalls.isEmpty()) {
            metadata.put("toolCalls", toolCalls);
        }
        String thinking = thinkingContent(root.path("content"));
        if (!thinking.isBlank()) {
            metadata.put("thinking", thinking);
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

    private String thinkingContent(JsonNode content) {
        if (!content.isArray()) {
            return "";
        }
        List<String> thinkingParts = new java.util.ArrayList<>();
        for (JsonNode item : content) {
            String type = item.path("type").asText();
            if ("thinking".equals(type)) {
                String thinking = textValue(item, "thinking");
                if (!thinking.isBlank()) {
                    thinkingParts.add(thinking);
                }
            } else if ("reasoning".equals(type)) {
                String reasoning = textValue(item, "text");
                if (reasoning.isBlank()) {
                    reasoning = textValue(item, "reasoning");
                }
                if (!reasoning.isBlank()) {
                    thinkingParts.add(reasoning);
                }
            }
        }
        return String.join("\n", thinkingParts);
    }

    private String textValue(JsonNode item, String fieldName) {
        return item.has(fieldName) ? item.path(fieldName).asText().trim() : "";
    }

    private List<Map<String, Object>> toolCalls(JsonNode content) {
        if (!content.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> toolCalls = new java.util.ArrayList<>();
        for (JsonNode item : content) {
            if ("tool_use".equals(item.path("type").asText())) {
                Map<String, Object> toolCall = new java.util.HashMap<>();
                toolCall.put("id", item.path("id").asText());
                toolCall.put("name", item.path("name").asText());
                toolCall.put("arguments", OBJECT_MAPPER.convertValue(item.path("input"), Map.class));
                toolCalls.add(toolCall);
            }
        }
        return toolCalls;
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

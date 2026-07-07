package com.pi.mono.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.mono.llm.config.AnthropicConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicLiveSmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @EnabledIfSystemProperty(named = "pi.live.anthropic", matches = "true")
    void callsAnthropicCompatibleMessagesApiFromEnvironment() throws Exception {
        AnthropicConfig config = new AnthropicConfig();
        config.setModel(System.getProperty("pi.llm.anthropic.model", config.getModel()));
        config.validate();
        AnthropicClient client = new AnthropicClient(config);

        String response = client.createMessage(
            config.getModel(),
            List.of(Map.of("role", "user", "content", "请只回复一行：API_OK。")),
            "",
            0.7,
            64
        ).block(config.getTimeout());

        JsonNode root = OBJECT_MAPPER.readTree(response);
        String text = root.path("content").path(0).path("text").asText();
        assertTrue(text.contains("API_OK"), response);
    }
}

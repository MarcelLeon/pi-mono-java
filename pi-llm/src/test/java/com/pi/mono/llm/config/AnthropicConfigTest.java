package com.pi.mono.llm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnthropicConfigTest {

    @Test
    void defaultsToClaudeSonnet5WithAnthropicMessagesEndpoint() {
        AnthropicConfig config = new AnthropicConfig();

        assertEquals("claude-sonnet-5", config.getModel());
        assertEquals("https://api.anthropic.com/v1", config.getResolvedBaseUrl());
        assertEquals("https://api.anthropic.com/v1/messages", config.getMessagesUrl());
    }

    @Test
    void trimsTrailingSlashFromCustomBaseUrl() {
        AnthropicConfig config = new AnthropicConfig();
        config.setBaseUrl("https://anthropic-compatible.example.com/v1/");

        assertEquals("https://anthropic-compatible.example.com/v1", config.getResolvedBaseUrl());
        assertEquals("https://anthropic-compatible.example.com/v1/messages", config.getMessagesUrl());
    }

    @Test
    void validateRequiresApiKeyWhenEnabled() {
        AnthropicConfig config = new AnthropicConfig();

        assertThrows(IllegalArgumentException.class, config::validate);
    }
}

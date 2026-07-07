package com.pi.mono.llm.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnthropicConfigTest {

    @Test
    void defaultsToClaudeSonnet5WithAnthropicMessagesEndpoint() {
        AnthropicConfig config = new AnthropicConfig();

        assertEquals("claude-sonnet-5", config.getModel());
        assertEquals("https://api.anthropic.com/v1", config.getResolvedBaseUrl(Map.of()));
        assertEquals("https://api.anthropic.com/v1/messages", config.getMessagesUrl(Map.of()));
    }

    @Test
    void trimsTrailingSlashFromCustomBaseUrl() {
        AnthropicConfig config = new AnthropicConfig();
        config.setBaseUrl("https://anthropic-compatible.example.com/v1/");

        assertEquals("https://anthropic-compatible.example.com/v1", config.getResolvedBaseUrl(Map.of()));
        assertEquals("https://anthropic-compatible.example.com/v1/messages", config.getMessagesUrl(Map.of()));
    }

    @Test
    void validateRequiresApiKeyWhenEnabled() {
        AnthropicConfig config = new AnthropicConfig();

        assertThrows(IllegalArgumentException.class, () -> config.validate(Map.of()));
    }

    @Test
    void resolvesClaudeCodeCompatibleEnvironment() {
        AnthropicConfig config = new AnthropicConfig();

        Map<String, String> env = Map.of(
            "ANTHROPIC_BASE_URL", "https://proxy.example.com/claude-code-proxy",
            "ANTHROPIC_AUTH_TOKEN", "auth-token",
            "ANTHROPIC_CUSTOM_HEADERS", "langfuse_session_id: session-1\nlangfuse_tags: [\"claude-code\"]"
        );

        assertEquals("https://proxy.example.com/claude-code-proxy/v1", config.getResolvedBaseUrl(env));
        assertEquals("auth-token", config.getResolvedAuthToken(env));
        assertEquals(
            Map.of(
                "langfuse_session_id", "session-1",
                "langfuse_tags", "[\"claude-code\"]"
            ),
            config.getResolvedCustomHeaders(env)
        );
    }

    @Test
    void explicitPropertiesOverrideEnvironment() {
        AnthropicConfig config = new AnthropicConfig();
        config.setBaseUrl("https://explicit.example.com/v1");
        config.setApiKey("explicit-key");
        config.setAuthToken("explicit-token");

        Map<String, String> env = Map.of(
            "ANTHROPIC_BASE_URL", "https://env.example.com",
            "ANTHROPIC_API_KEY", "env-key",
            "ANTHROPIC_AUTH_TOKEN", "env-token"
        );

        assertEquals("https://explicit.example.com/v1", config.getResolvedBaseUrl(env));
        assertEquals("explicit-key", config.getResolvedApiKey(env));
        assertEquals("explicit-token", config.getResolvedAuthToken(env));
    }
}

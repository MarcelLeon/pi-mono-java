package com.pi.mono.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "pi.llm.anthropic")
public class AnthropicConfig {
    private String apiKey;
    private String baseUrl = "https://api.anthropic.com/v1";
    private String model = "claude-sonnet-5";
    private Duration timeout = Duration.ofSeconds(30);
    private boolean enabled = false;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Anthropic API key is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Anthropic model is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    public String getResolvedBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank()
            ? "https://api.anthropic.com/v1"
            : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public String getMessagesUrl() {
        return getResolvedBaseUrl() + "/messages";
    }
}

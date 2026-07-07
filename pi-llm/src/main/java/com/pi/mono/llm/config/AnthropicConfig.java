package com.pi.mono.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "pi.llm.anthropic")
public class AnthropicConfig {
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com/v1";

    private String apiKey;
    private String authToken;
    private String baseUrl = DEFAULT_BASE_URL;
    private String customHeaders;
    private String model = "claude-sonnet-5";
    private Duration timeout = Duration.ofSeconds(30);
    private boolean enabled = false;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(String customHeaders) {
        this.customHeaders = customHeaders;
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
        validate(System.getenv());
    }

    void validate(Map<String, String> env) {
        if (isBlank(getResolvedApiKey(env)) && isBlank(getResolvedAuthToken(env))) {
            throw new IllegalArgumentException("Anthropic API key or auth token is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Anthropic model is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
    }

    public String getResolvedBaseUrl() {
        return getResolvedBaseUrl(System.getenv());
    }

    String getResolvedBaseUrl(Map<String, String> env) {
        String envBaseUrl = env.get("ANTHROPIC_BASE_URL");
        boolean useEnvBaseUrl = isBlankOrDefault(baseUrl) && !isBlank(envBaseUrl);
        String value = useEnvBaseUrl
            ? envBaseUrl.trim()
            : isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (useEnvBaseUrl && !value.endsWith("/v1")) {
            return value + "/v1";
        }
        return value;
    }

    public String getMessagesUrl() {
        return getResolvedBaseUrl() + "/messages";
    }

    String getMessagesUrl(Map<String, String> env) {
        return getResolvedBaseUrl(env) + "/messages";
    }

    public String getResolvedApiKey() {
        return getResolvedApiKey(System.getenv());
    }

    String getResolvedApiKey(Map<String, String> env) {
        if (!isBlank(apiKey)) {
            return apiKey.trim();
        }
        String envApiKey = env.get("ANTHROPIC_API_KEY");
        return isBlank(envApiKey) ? null : envApiKey.trim();
    }

    public String getResolvedAuthToken() {
        return getResolvedAuthToken(System.getenv());
    }

    String getResolvedAuthToken(Map<String, String> env) {
        if (!isBlank(authToken)) {
            return authToken.trim();
        }
        String envAuthToken = env.get("ANTHROPIC_AUTH_TOKEN");
        return isBlank(envAuthToken) ? null : envAuthToken.trim();
    }

    public Map<String, String> getResolvedCustomHeaders() {
        return getResolvedCustomHeaders(System.getenv());
    }

    Map<String, String> getResolvedCustomHeaders(Map<String, String> env) {
        String rawHeaders = !isBlank(customHeaders)
            ? customHeaders
            : env.get("ANTHROPIC_CUSTOM_HEADERS");
        if (isBlank(rawHeaders)) {
            return Map.of();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : rawHeaders.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    private boolean isBlankOrDefault(String value) {
        return isBlank(value) || DEFAULT_BASE_URL.equals(value.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

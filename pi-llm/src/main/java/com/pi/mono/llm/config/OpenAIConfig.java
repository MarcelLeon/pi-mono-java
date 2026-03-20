package com.pi.mono.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * OpenAI配置类
 * 用于配置OpenAI API的连接参数和行为
 */
@Component
@ConfigurationProperties(prefix = "pi.llm.openai")
public class OpenAIConfig {

    /**
     * OpenAI API密钥
     * 必需配置项
     */
    private String apiKey;

    /**
     * OpenAI API基础URL
     * 默认: https://api.openai.com/v1
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * 默认使用的模型
     * 可选值: gpt-3.5-turbo, gpt-4, gpt-4-turbo
     * 默认: gpt-3.5-turbo
     */
    private String model = "gpt-3.5-turbo";

    /**
     * API请求超时时间
     * 默认: 30秒
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * 最大重试次数
     * 默认: 3次
     */
    private int maxRetries = 3;

    /**
     * 重试间隔时间
     * 默认: 1秒
     */
    private Duration retryDelay = Duration.ofSeconds(1);

    /**
     * 是否启用OpenAI提供者
     * 默认: true
     */
    private boolean enabled = true;

    /**
     * 最大并发请求数
     * 默认: 10
     */
    private int maxConcurrency = 10;

    /**
     * 连接池最大连接数
     * 默认: 20
     */
    private int maxConnections = 20;

    /**
     * 连接池空闲连接超时时间
     * 默认: 60秒
     */
    private Duration connectionTimeout = Duration.ofSeconds(60);

    // Getters and Setters
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public Duration getRetryDelay() { return retryDelay; }
    public void setRetryDelay(Duration retryDelay) { this.retryDelay = retryDelay; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    public Duration getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(Duration connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    /**
     * 验证配置是否有效
     */
    public void validate() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAI model is required");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries must be non-negative");
        }
        if (retryDelay == null || retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Retry delay must be positive");
        }
    }

    /**
     * 获取完整的API URL
     */
    public String getApiUrl() {
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }
}
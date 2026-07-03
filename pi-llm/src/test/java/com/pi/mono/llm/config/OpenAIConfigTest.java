package com.pi.mono.llm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAIConfigTest {

    @Test
    void defaultsToLatestUpstreamOpenAIModel() {
        OpenAIConfig config = new OpenAIConfig();

        assertEquals("gpt-5.5", config.getModel());
    }

    @Test
    void keepsStandardOpenAIBaseUrlAndChatPath() {
        OpenAIConfig config = new OpenAIConfig();

        assertEquals("https://api.openai.com/v1", config.getResolvedBaseUrl());
        assertEquals("/chat/completions", config.getChatCompletionsPath());
        assertEquals("https://api.openai.com/v1/chat/completions", config.getApiUrl());
    }

    @Test
    void normalizesAzureFoundryEndpointToOpenAiV1Path() {
        OpenAIConfig config = new OpenAIConfig();
        config.setBaseUrl("https://team-foundry.services.ai.azure.com");

        assertEquals("https://team-foundry.services.ai.azure.com/openai/v1", config.getResolvedBaseUrl());
        assertEquals("https://team-foundry.services.ai.azure.com/openai/v1/chat/completions", config.getApiUrl());
    }

    @Test
    void doesNotDuplicateAzureOpenAiV1Path() {
        OpenAIConfig config = new OpenAIConfig();
        config.setBaseUrl("https://team-foundry.services.ai.azure.com/openai/v1/");

        assertEquals("https://team-foundry.services.ai.azure.com/openai/v1", config.getResolvedBaseUrl());
        assertEquals("https://team-foundry.services.ai.azure.com/openai/v1/chat/completions", config.getApiUrl());
    }
}

package com.pi.mono.llm.provider;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatOptions;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.MessageRole;
import com.pi.mono.llm.config.ExternalCliConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalCliLLMProviderTest {

    @Test
    void sendsRenderedConversationToConfiguredCommandStdin() {
        ExternalCliConfig config = new ExternalCliConfig();
        config.setEnabled(true);
        config.setCommand("/bin/cat");
        config.setModelId("claude-code-cli");
        config.setTimeoutSeconds(5);

        ExternalCliLLMProvider provider = new ExternalCliLLMProvider(config);

        AgentMessage response = provider.chat(new ChatRequest(
            "session-1",
            List.of(
                new AgentMessage(MessageRole.SYSTEM, "System context", java.util.Map.of()),
                new AgentMessage(MessageRole.USER, "Summarize @README.md", java.util.Map.of())
            ),
            new ChatOptions("claude-code-cli", 0.7, 1000)
        )).join();

        assertTrue(response.content().contains("SYSTEM: System context"));
        assertTrue(response.content().contains("USER: Summarize @README.md"));
        assertTrue((Boolean) response.metadata().get("externalCli"));
    }

    @Test
    void isUnavailableUntilEnabledWithCommand() {
        ExternalCliConfig config = new ExternalCliConfig();
        config.setEnabled(true);

        assertFalse(new ExternalCliLLMProvider(config).isAvailable());
    }

    @Test
    void appendsClaudeModelArgumentWhenModelIdIsConcrete() {
        assertEquals(
            List.of("claude", "-p", "--model", "opus-4-7"),
            ExternalCliLLMProvider.prepareCommand("claude -p", "opus-4-7")
        );
    }

    @Test
    void appendsCodexExecModelArgumentBeforeStdinPrompt() {
        assertEquals(
            List.of("codex", "exec", "-m", "gpt-5", "-"),
            ExternalCliLLMProvider.prepareCommand("codex exec -", "gpt-5")
        );
    }

    @Test
    void replacesExplicitModelPlaceholder() {
        assertEquals(
            List.of("custom-ai", "--model", "opus-4-7", "run"),
            ExternalCliLLMProvider.prepareCommand("custom-ai --model {model} run", "opus-4-7")
        );
    }
}

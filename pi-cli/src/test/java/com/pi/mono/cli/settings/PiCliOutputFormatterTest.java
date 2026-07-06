package com.pi.mono.cli.settings;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PiCliOutputFormatterTest {

    @Test
    void appliesOutputPadToUserAndAssistantMessages() {
        PiCliOutputFormatter formatter = new PiCliOutputFormatter(new PiCliSettings(3, Optional.empty()));

        assertEquals("   👤 You: hello", formatter.userMessage("hello"));
        assertEquals("   🤖 Pi: world", formatter.assistantMessage("world"));
        assertEquals("   💭 Thinking: checking context", formatter.thinkingMessage("checking context"));
    }

    @Test
    void defaultSettingsDoNotPadMessages() {
        PiCliOutputFormatter formatter = new PiCliOutputFormatter(PiCliSettings.defaults());

        assertEquals("👤 You: hello", formatter.userMessage("hello"));
        assertEquals("🤖 Pi: world", formatter.assistantMessage("world"));
        assertEquals("💭 Thinking: checking context", formatter.thinkingMessage("checking context"));
    }
}

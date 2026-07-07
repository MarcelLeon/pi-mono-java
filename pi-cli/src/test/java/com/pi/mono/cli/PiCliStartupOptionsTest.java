package com.pi.mono.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiCliStartupOptionsTest {

    @Test
    void parsesRpcMode() {
        PiCliStartupOptions options = PiCliStartupOptions.parse("--rpc");

        assertTrue(options.rpcMode());
        assertFalse(options.ephemeralSession());
    }

    @Test
    void parsesNoSessionWithDeterministicSessionId() {
        PiCliStartupOptions options = PiCliStartupOptions.parse("--no-session", "--session-id", "stable-id");

        assertTrue(options.ephemeralSession());
        assertEquals("stable-id", options.sessionId().orElseThrow());
    }

    @Test
    void rejectsSessionIdWithoutValue() {
        assertThrows(IllegalArgumentException.class, () -> PiCliStartupOptions.parse("--session-id"));
    }

    @Test
    void resolvesConfiguredDefaultModelFirst() {
        assertEquals(
            "gpt-5.5",
            PiCliApplication.resolveStartupModel("gpt-5.5", true, "opus-4-7")
        );
    }

    @Test
    void defaultsToExternalCliModelWhenExternalCliIsEnabled() {
        assertEquals(
            "opus-4-7",
            PiCliApplication.resolveStartupModel("", true, "opus-4-7")
        );
    }

    @Test
    void fallsBackToMockModelWhenNoModelIsConfigured() {
        assertEquals(
            "mock-claude",
            PiCliApplication.resolveStartupModel("", false, "opus-4-7")
        );
    }
}

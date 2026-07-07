package com.pi.mono.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PiCliCommandAliasTest {

    @Test
    void normalizesLegacyCommandsWithSlashPrefix() {
        assertEquals("help", PiCliApplication.normalizeCommandAlias("/help"));
        assertEquals("sessions", PiCliApplication.normalizeCommandAlias("/sessions"));
        assertEquals("tools", PiCliApplication.normalizeCommandAlias("/tools"));
        assertEquals("perm list", PiCliApplication.normalizeCommandAlias("/perm list"));
        assertEquals("tool read path=README.md", PiCliApplication.normalizeCommandAlias("/tool read path=README.md"));
    }

    @Test
    void keepsExistingSlashCommandsUntouched() {
        assertEquals("/session", PiCliApplication.normalizeCommandAlias("/session"));
        assertEquals("/models", PiCliApplication.normalizeCommandAlias("/models"));
        assertEquals("/resources", PiCliApplication.normalizeCommandAlias("/resources"));
    }

    @Test
    void mapsSessionSubcommandAliasesToExistingCommands() {
        assertEquals("/new mock-claude", PiCliApplication.normalizeCommandAlias("/session new mock-claude"));
        assertEquals("sessions", PiCliApplication.normalizeCommandAlias("/session list"));
    }
}

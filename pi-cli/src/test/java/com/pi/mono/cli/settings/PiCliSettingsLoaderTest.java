package com.pi.mono.cli.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PiCliSettingsLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsDefaultsWhenNoSettingsFileExists() {
        PiCliSettings settings = new PiCliSettingsLoader(tempDir.resolve("home"))
            .load(tempDir.resolve("project"), false);

        assertEquals(0, settings.outputPad());
        assertFalse(settings.externalEditor().isPresent());
    }

    @Test
    void loadsUserSettings() throws Exception {
        Path home = Files.createDirectories(tempDir.resolve("home"));
        Files.createDirectories(home.resolve(".pi"));
        Files.writeString(home.resolve(".pi/settings.json"), """
            {
              "outputPad": 4,
              "externalEditor": "code --wait"
            }
            """);

        PiCliSettings settings = new PiCliSettingsLoader(home)
            .load(tempDir.resolve("project"), false);

        assertEquals(4, settings.outputPad());
        assertEquals("code --wait", settings.externalEditor().orElseThrow());
    }

    @Test
    void trustedProjectSettingsOverrideUserSettings() throws Exception {
        Path home = Files.createDirectories(tempDir.resolve("home"));
        Files.createDirectories(home.resolve(".pi"));
        Files.writeString(home.resolve(".pi/settings.json"), """
            {"outputPad": 2, "externalEditor": "nano"}
            """);

        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi"));
        Files.writeString(project.resolve(".pi/settings.json"), """
            {"outputPad": 6, "externalEditor": "vim"}
            """);

        PiCliSettings settings = new PiCliSettingsLoader(home).load(project, true);

        assertEquals(6, settings.outputPad());
        assertEquals("vim", settings.externalEditor().orElseThrow());
    }

    @Test
    void untrustedProjectSettingsAreSkipped() throws Exception {
        Path home = Files.createDirectories(tempDir.resolve("home"));
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Files.createDirectories(project.resolve(".pi"));
        Files.writeString(project.resolve(".pi/settings.json"), """
            {"outputPad": 6, "externalEditor": "vim"}
            """);

        PiCliSettings settings = new PiCliSettingsLoader(home).load(project, false);

        assertEquals(0, settings.outputPad());
        assertFalse(settings.externalEditor().isPresent());
    }
}

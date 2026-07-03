package com.pi.mono.cli.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PiExternalEditorTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsMissingExternalEditorSetting() {
        PiExternalEditor editor = new PiExternalEditor(PiCliSettings.defaults(), tempDir);

        assertThrows(IllegalStateException.class, () -> editor.captureMessage(""));
    }

    @Test
    void appendsTempFilePathWhenCommandHasNoPlaceholder() throws Exception {
        Path editorScript = fakeEditorScript("printf 'Edited from fake editor\\n' > \"$1\"\n");
        PiCliSettings settings = new PiCliSettings(0, Optional.of(editorScript.toString()));
        PiExternalEditor editor = new PiExternalEditor(settings, tempDir);

        String message = editor.captureMessage("draft");

        assertEquals("Edited from fake editor", message);
    }

    @Test
    void replacesFilePlaceholderInCommand() throws Exception {
        Path editorScript = fakeEditorScript("printf 'Edited via placeholder\\n' > \"$1\"\n");
        PiCliSettings settings = new PiCliSettings(0, Optional.of(editorScript + " {file}"));
        PiExternalEditor editor = new PiExternalEditor(settings, tempDir);

        String message = editor.captureMessage("");

        assertEquals("Edited via placeholder", message);
    }

    private Path fakeEditorScript(String body) throws Exception {
        Path script = tempDir.resolve("fake-editor.sh");
        Files.writeString(script, "#!/bin/sh\n" + body);
        script.toFile().setExecutable(true);
        return script;
    }
}

package com.pi.mono.cli.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PiCliSettingsLoader {
    private final Path userHome;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PiCliSettingsLoader() {
        this(Path.of(System.getProperty("user.home")));
    }

    public PiCliSettingsLoader(Path userHome) {
        this.userHome = userHome.toAbsolutePath().normalize();
    }

    public PiCliSettings load(Path cwd, boolean trustedProject) {
        try {
            PiCliSettings settings = readSettings(userHome.resolve(".pi/settings.json"), PiCliSettings.defaults());
            if (!trustedProject) {
                return settings;
            }

            for (Path root : parentFirstRoots(cwd.toAbsolutePath().normalize())) {
                if (!userHome.startsWith(root)) {
                    settings = readSettings(root.resolve(".pi/settings.json"), settings);
                }
            }
            return settings;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Pi CLI settings from " + cwd, e);
        }
    }

    private List<Path> parentFirstRoots(Path cwd) {
        List<Path> roots = new ArrayList<>();
        Path current = cwd;
        while (current != null) {
            roots.add(current);
            current = current.getParent();
        }
        roots.sort(Comparator.comparingInt(Path::getNameCount));
        return roots;
    }

    private PiCliSettings readSettings(Path settingsFile, PiCliSettings base) throws IOException {
        if (!Files.isRegularFile(settingsFile)) {
            return base;
        }

        JsonNode root = objectMapper.readTree(settingsFile.toFile());
        int outputPad = root.has("outputPad") ? Math.max(0, root.get("outputPad").asInt(base.outputPad())) : base.outputPad();
        Optional<String> externalEditor = root.hasNonNull("externalEditor")
            ? Optional.of(root.get("externalEditor").asText()).filter(value -> !value.isBlank())
            : base.externalEditor();
        return new PiCliSettings(outputPad, externalEditor);
    }
}

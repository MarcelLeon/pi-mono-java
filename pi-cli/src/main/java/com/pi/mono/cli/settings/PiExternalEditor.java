package com.pi.mono.cli.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PiExternalEditor {
    private final PiCliSettings settings;
    private final Path workingDirectory;

    public PiExternalEditor(PiCliSettings settings, Path workingDirectory) {
        this.settings = settings;
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
    }

    public String captureMessage(String initialContent) {
        String command = settings.externalEditor()
            .orElseThrow(() -> new IllegalStateException("externalEditor is not configured"));

        try {
            Files.createDirectories(workingDirectory);
            Path draftFile = Files.createTempFile(workingDirectory, "pi-editor-", ".md");
            Files.writeString(draftFile, initialContent == null ? "" : initialContent);

            List<String> commandParts = commandWithDraftFile(command, draftFile);
            Process process = new ProcessBuilder(commandParts)
                .directory(workingDirectory.toFile())
                .inheritIO()
                .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("externalEditor exited with code " + exitCode);
            }

            return Files.readString(draftFile).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open externalEditor", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("externalEditor was interrupted", e);
        }
    }

    private List<String> commandWithDraftFile(String command, Path draftFile) {
        List<String> parts = splitCommand(command);
        boolean replaced = false;
        for (int i = 0; i < parts.size(); i++) {
            if ("{file}".equals(parts.get(i))) {
                parts.set(i, draftFile.toString());
                replaced = true;
            }
        }
        if (!replaced) {
            parts.add(draftFile.toString());
        }
        return parts;
    }

    private List<String> splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;

        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (quote != null) {
                if (ch == quote) {
                    quote = null;
                } else {
                    current.append(ch);
                }
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (Character.isWhitespace(ch)) {
                addPart(parts, current);
            } else {
                current.append(ch);
            }
        }

        addPart(parts, current);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("externalEditor command must not be blank");
        }
        return parts;
    }

    private void addPart(List<String> parts, StringBuilder current) {
        if (current.length() > 0) {
            parts.add(current.toString());
            current.setLength(0);
        }
    }
}

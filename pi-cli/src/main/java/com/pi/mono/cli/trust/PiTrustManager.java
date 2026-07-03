package com.pi.mono.cli.trust;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class PiTrustManager {
    private final Path trustFile;

    public PiTrustManager() {
        this(defaultTrustFile());
    }

    public PiTrustManager(Path trustFile) {
        this.trustFile = trustFile.toAbsolutePath().normalize();
    }

    public boolean isTrusted(Path projectPath) {
        Path normalizedProject = normalize(projectPath);
        return readTrustedPaths().stream().anyMatch(normalizedProject::startsWith);
    }

    public void trust(Path projectPath) {
        try {
            Set<String> paths = new LinkedHashSet<>();
            for (Path path : readTrustedPaths()) {
                paths.add(path.toString());
            }
            paths.add(normalize(projectPath).toString());

            if (trustFile.getParent() != null) {
                Files.createDirectories(trustFile.getParent());
            }
            Files.write(trustFile, paths);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save trust decision for " + projectPath, e);
        }
    }

    public Path trustFile() {
        return trustFile;
    }

    private Set<Path> readTrustedPaths() {
        try {
            if (!Files.isRegularFile(trustFile)) {
                return Set.of();
            }

            Set<Path> paths = new LinkedHashSet<>();
            for (String line : Files.readAllLines(trustFile)) {
                if (!line.isBlank()) {
                    paths.add(normalize(Path.of(line.trim())));
                }
            }
            return paths;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read trust file " + trustFile, e);
        }
    }

    private Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static Path defaultTrustFile() {
        String override = System.getProperty("pi.trust.file");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".pi", "agent", "trust-java.txt");
    }
}

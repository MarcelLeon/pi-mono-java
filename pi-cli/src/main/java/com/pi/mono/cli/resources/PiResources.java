package com.pi.mono.cli.resources;

import java.nio.file.Path;
import java.util.List;

public record PiResources(
    List<ResourceFile> contextFiles,
    List<ResourceFile> promptTemplates,
    List<ResourceFile> skills,
    String combinedContext
) {
    public record ResourceFile(String name, Path path, String content) {}

    public boolean isEmpty() {
        return contextFiles.isEmpty() && promptTemplates.isEmpty() && skills.isEmpty();
    }
}

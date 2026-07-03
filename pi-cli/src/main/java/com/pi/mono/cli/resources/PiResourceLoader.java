package com.pi.mono.cli.resources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class PiResourceLoader {
    private final Path userHome;

    public PiResourceLoader() {
        this(Path.of(System.getProperty("user.home")));
    }

    public PiResourceLoader(Path userHome) {
        this.userHome = userHome.toAbsolutePath().normalize();
    }

    public PiResources load(Path cwd) {
        return load(cwd, true);
    }

    public PiResources load(Path cwd, boolean trustedProject) {
        try {
            Path normalizedCwd = cwd.toAbsolutePath().normalize();
            List<Path> searchRoots = parentFirstRoots(normalizedCwd);
            List<PiResources.ResourceFile> contextFiles = loadContextFiles(searchRoots);
            List<PiResources.ResourceFile> promptTemplates = loadPromptTemplates(searchRoots, trustedProject);
            List<PiResources.ResourceFile> skills = loadSkills(searchRoots, trustedProject);

            return new PiResources(
                List.copyOf(contextFiles),
                List.copyOf(promptTemplates),
                List.copyOf(skills),
                combineContext(contextFiles)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Pi resources from " + cwd, e);
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

    private List<PiResources.ResourceFile> loadContextFiles(List<Path> searchRoots) throws IOException {
        List<PiResources.ResourceFile> contextFiles = new ArrayList<>();
        for (Path root : searchRoots) {
            addIfExists(contextFiles, "AGENTS", root.resolve("AGENTS.md"));
            addIfExists(contextFiles, "CLAUDE", root.resolve("CLAUDE.md"));
        }
        return contextFiles;
    }

    private List<PiResources.ResourceFile> loadPromptTemplates(List<Path> searchRoots, boolean trustedProject) throws IOException {
        List<PiResources.ResourceFile> prompts = new ArrayList<>();
        addPromptsFromDirectory(prompts, userHome.resolve(".pi/agent/prompts"));
        for (Path root : searchRoots) {
            if (!trustedProject || isUserHomeOrAbove(root)) {
                continue;
            }
            addPromptsFromDirectory(prompts, root.resolve(".pi/prompts"));
        }
        return prompts;
    }

    private void addPromptsFromDirectory(List<PiResources.ResourceFile> prompts, Path promptsDir) throws IOException {
        if (!Files.isDirectory(promptsDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(promptsDir)) {
            files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md"))
                .sorted()
                .forEach(path -> prompts.add(readResource(stripMarkdownExtension(path), path)));
        }
    }

    private List<PiResources.ResourceFile> loadSkills(List<Path> searchRoots, boolean trustedProject) throws IOException {
        List<PiResources.ResourceFile> skills = new ArrayList<>();
        addSkillsFromDirectory(skills, userHome.resolve(".pi/agent/skills"));
        addSkillsFromDirectory(skills, userHome.resolve(".agents/skills"));
        for (Path root : searchRoots) {
            if (!trustedProject || isUserHomeOrAbove(root)) {
                continue;
            }
            addSkillsFromDirectory(skills, root.resolve(".pi/skills"));
            addSkillsFromDirectory(skills, root.resolve(".agents/skills"));
        }
        return skills;
    }

    private boolean isUserHomeOrAbove(Path root) {
        return userHome.startsWith(root);
    }

    private void addSkillsFromDirectory(List<PiResources.ResourceFile> skills, Path skillsDir) throws IOException {
        if (!Files.isDirectory(skillsDir)) {
            return;
        }

        try (Stream<Path> entries = Files.list(skillsDir)) {
            entries.filter(Files::isDirectory)
                .sorted()
                .map(skillDir -> skillDir.resolve("SKILL.md"))
                .filter(Files::isRegularFile)
                .forEach(path -> skills.add(readResource(path.getParent().getFileName().toString(), path)));
        }
    }

    private void addIfExists(List<PiResources.ResourceFile> resources, String name, Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            resources.add(new PiResources.ResourceFile(name, path, Files.readString(path)));
        }
    }

    private PiResources.ResourceFile readResource(String name, Path path) {
        try {
            return new PiResources.ResourceFile(name, path, Files.readString(path));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource file: " + path, e);
        }
    }

    private String stripMarkdownExtension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).endsWith(".md")
            ? fileName.substring(0, fileName.length() - 3)
            : fileName;
    }

    private String combineContext(List<PiResources.ResourceFile> contextFiles) {
        StringBuilder combined = new StringBuilder();
        for (PiResources.ResourceFile file : contextFiles) {
            if (!combined.isEmpty()) {
                combined.append("\n\n");
            }
            combined.append("# Context from ").append(file.path()).append("\n");
            combined.append(file.content());
        }
        return combined.toString();
    }
}

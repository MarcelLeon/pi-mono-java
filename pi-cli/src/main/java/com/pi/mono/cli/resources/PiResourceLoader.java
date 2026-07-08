package com.pi.mono.cli.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public class PiResourceLoader {
    private final Path userHome;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            List<ResourcePatternScope> patternScopes = loadResourcePatternScopes(searchRoots, trustedProject);
            List<PiResources.ResourceFile> contextFiles = loadContextFiles(searchRoots);
            List<PiResources.ResourceFile> promptTemplates = applyResourcePatterns(
                loadPromptTemplates(searchRoots, trustedProject),
                patternScopes,
                ResourceKind.PROMPT
            );
            List<PiResources.ResourceFile> skills = applyResourcePatterns(
                loadSkills(searchRoots, trustedProject),
                patternScopes,
                ResourceKind.SKILL
            );

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

    private List<ResourcePatternScope> loadResourcePatternScopes(List<Path> searchRoots, boolean trustedProject) throws IOException {
        List<ResourcePatternScope> scopes = new ArrayList<>();
        addResourcePatternScope(
            scopes,
            userHome.resolve(".pi/settings.json"),
            List.of(userHome.resolve(".pi/agent"), userHome.resolve(".agents"), userHome)
        );
        if (!trustedProject) {
            return scopes;
        }

        for (Path root : searchRoots) {
            if (isUserHomeOrAbove(root)) {
                continue;
            }
            addResourcePatternScope(
                scopes,
                root.resolve(".pi/settings.json"),
                List.of(root.resolve(".pi"), root.resolve(".agents"), root)
            );
        }
        return scopes;
    }

    private void addResourcePatternScope(
        List<ResourcePatternScope> scopes,
        Path settingsFile,
        List<Path> baseDirs
    ) throws IOException {
        if (!Files.isRegularFile(settingsFile)) {
            return;
        }
        JsonNode root = objectMapper.readTree(settingsFile.toFile());
        List<String> prompts = stringArray(root.path("prompts"));
        List<String> skills = stringArray(root.path("skills"));
        if (!prompts.isEmpty() || !skills.isEmpty()) {
            scopes.add(new ResourcePatternScope(prompts, skills, baseDirs));
        }
    }

    private List<String> stringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText().trim());
            }
        });
        return values;
    }

    private List<PiResources.ResourceFile> loadPromptTemplates(List<Path> searchRoots, boolean trustedProject) throws IOException {
        List<PiResources.ResourceFile> prompts = new ArrayList<>();
        addPromptsFromDirectory(prompts, userHome.resolve(".pi/agent/prompts"));
        for (Path root : searchRoots) {
            if (!trustedProject || isUserHomeOrAbove(root)) {
                continue;
            }
            addPromptsFromDirectory(prompts, root.resolve(".pi/prompts"));
            addPackagePrompts(prompts, root.resolve(".pi/packages"));
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
            addPackageSkills(skills, root.resolve(".pi/packages"));
        }
        return skills;
    }

    private boolean isUserHomeOrAbove(Path root) {
        return userHome.startsWith(root);
    }

    private List<PiResources.ResourceFile> applyResourcePatterns(
        List<PiResources.ResourceFile> resources,
        List<ResourcePatternScope> patternScopes,
        ResourceKind kind
    ) {
        List<PiResources.ResourceFile> original = List.copyOf(resources);
        List<PiResources.ResourceFile> result = new ArrayList<>(resources);
        for (ResourcePatternScope scope : patternScopes) {
            List<String> patterns = kind == ResourceKind.PROMPT ? scope.prompts() : scope.skills();
            for (String pattern : patterns) {
                ResourcePattern parsed = ResourcePattern.parse(pattern);
                if (parsed.enabled()) {
                    addMatchingOrExplicitResource(result, original, parsed.target(), scope.baseDirs(), kind);
                } else {
                    result.removeIf(resource -> matchesPattern(resource.path(), parsed.target(), scope.baseDirs()));
                }
            }
        }
        return result;
    }

    private void addMatchingOrExplicitResource(
        List<PiResources.ResourceFile> resources,
        List<PiResources.ResourceFile> original,
        String target,
        List<Path> baseDirs,
        ResourceKind kind
    ) {
        if (resources.stream().anyMatch(resource -> matchesPattern(resource.path(), target, baseDirs))) {
            return;
        }
        original.stream()
            .filter(resource -> matchesPattern(resource.path(), target, baseDirs))
            .filter(resource -> resources.stream().noneMatch(existing -> existing.path().equals(resource.path())))
            .findFirst()
            .ifPresent(resources::add);
        if (resources.stream().anyMatch(resource -> matchesPattern(resource.path(), target, baseDirs))) {
            return;
        }
        resolveExistingResourcePath(target, baseDirs, kind).ifPresent(path -> {
            if (resources.stream().noneMatch(resource -> resource.path().equals(path))) {
                String name = kind == ResourceKind.PROMPT
                    ? stripMarkdownExtension(path)
                    : path.getParent().getFileName().toString();
                resources.add(readResource(name, path));
            }
        });
    }

    private Optional<Path> resolveExistingResourcePath(String target, List<Path> baseDirs, ResourceKind kind) {
        Path targetPath = Path.of(target);
        if (targetPath.isAbsolute()) {
            return normalizeResourcePath(targetPath, kind);
        }
        for (Path baseDir : baseDirs) {
            Optional<Path> resolved = normalizeResourcePath(baseDir.resolve(target), kind);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private Optional<Path> normalizeResourcePath(Path path, ResourceKind kind) {
        Path normalized = path.toAbsolutePath().normalize();
        if (kind == ResourceKind.SKILL && Files.isDirectory(normalized)) {
            normalized = normalized.resolve("SKILL.md");
        }
        if (Files.isRegularFile(normalized)) {
            return Optional.of(normalized);
        }
        return Optional.empty();
    }

    private boolean matchesPattern(Path resourcePath, String target, List<Path> baseDirs) {
        if (target == null || target.isBlank()) {
            return false;
        }
        Path normalizedResource = resourcePath.toAbsolutePath().normalize();
        Path targetPath = Path.of(target);
        if (targetPath.isAbsolute()) {
            return normalizedResource.equals(targetPath.toAbsolutePath().normalize());
        }
        for (Path baseDir : baseDirs) {
            if (normalizedResource.equals(baseDir.resolve(target).toAbsolutePath().normalize())) {
                return true;
            }
        }
        String resource = slashPath(normalizedResource);
        String pattern = slashPath(targetPath);
        return resource.equals(pattern) || resource.endsWith("/" + pattern);
    }

    private String slashPath(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    private void addPackagePrompts(List<PiResources.ResourceFile> prompts, Path packagesDir) throws IOException {
        if (!Files.isDirectory(packagesDir)) {
            return;
        }

        try (Stream<Path> packages = Files.list(packagesDir)) {
            packages.filter(Files::isDirectory)
                .sorted()
                .forEach(packageDir -> {
                    try {
                        addPromptsFromDirectory(prompts, packageDir.resolve("prompts"));
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to load package prompts from " + packageDir, e);
                    }
                });
        }
    }

    private void addPackageSkills(List<PiResources.ResourceFile> skills, Path packagesDir) throws IOException {
        if (!Files.isDirectory(packagesDir)) {
            return;
        }

        try (Stream<Path> packages = Files.list(packagesDir)) {
            packages.filter(Files::isDirectory)
                .sorted()
                .forEach(packageDir -> {
                    try {
                        addSkillsFromDirectory(skills, packageDir.resolve("skills"));
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to load package skills from " + packageDir, e);
                    }
                });
        }
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

    private enum ResourceKind {
        PROMPT,
        SKILL
    }

    private record ResourcePatternScope(List<String> prompts, List<String> skills, List<Path> baseDirs) {}

    private record ResourcePattern(boolean enabled, String target) {
        private static ResourcePattern parse(String pattern) {
            boolean enabled = !pattern.startsWith("-") && !pattern.startsWith("!");
            String target = pattern.startsWith("+") || pattern.startsWith("-") || pattern.startsWith("!")
                ? pattern.substring(1)
                : pattern;
            return new ResourcePattern(enabled, target);
        }
    }
}

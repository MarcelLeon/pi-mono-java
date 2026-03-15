package com.pi.mono.tools;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 文件搜索工具
 */
@Component
public class FindFileTool implements ToolDefinition {

    @Override
    public String getName() {
        return "find";
    }

    @Override
    public String getDescription() {
        return "Find files by pattern or content";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("path", new ToolParameter("string", "Directory to search in", true, null));
        params.put("pattern", new ToolParameter("string", "File pattern to match (e.g., '*.java')", false, "*"));
        params.put("content", new ToolParameter("string", "Content to search for in file names", false, null));
        params.put("maxResults", new ToolParameter("integer", "Maximum number of results", false, 100));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                String pattern = (String) request.arguments().getOrDefault("pattern", "*");
                String content = (String) request.arguments().getOrDefault("content", null);
                Integer maxResults = (Integer) request.arguments().getOrDefault("maxResults", 100);

                if (pathStr == null || pathStr.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Path parameter is required");
                }

                Path dir = Paths.get(pathStr);
                if (!Files.exists(dir)) {
                    return ToolExecutionResult.failure("Directory not found: " + pathStr);
                }

                if (!Files.isDirectory(dir)) {
                    return ToolExecutionResult.failure("Path is not a directory: " + pathStr);
                }

                List<String> results = new ArrayList<>();

                try {
                    Files.walk(dir)
                        .filter(Files::isRegularFile)
                        .filter(path -> matchesPattern(path, pattern))
                        .filter(path -> content == null || path.getFileName().toString().toLowerCase()
                            .contains(content.toLowerCase()))
                        .limit(maxResults)
                        .forEach(path -> results.add(path.toString()));

                    if (results.isEmpty()) {
                        return ToolExecutionResult.failure("No files found matching criteria");
                    }

                    StringBuilder resultBuilder = new StringBuilder();
                    resultBuilder.append("Found ").append(results.size()).append(" file(s):\n");
                    resultBuilder.append("=" .repeat(50)).append("\n");

                    for (String filePath : results) {
                        resultBuilder.append(filePath).append("\n");
                    }

                    return ToolExecutionResult.success(resultBuilder.toString());

                } catch (IOException e) {
                    return ToolExecutionResult.failure("Failed to search directory: " + e.getMessage());
                }
            } catch (Exception e) {
                return ToolExecutionResult.failure("Unexpected error: " + e.getMessage());
            }
        });
    }

    private boolean matchesPattern(Path path, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }

        String fileName = path.getFileName().toString().toLowerCase();
        String patternLower = pattern.toLowerCase();

        if (patternLower.startsWith("*") && patternLower.endsWith("*")) {
            return fileName.contains(patternLower.substring(1, patternLower.length() - 1));
        } else if (patternLower.startsWith("*")) {
            return fileName.endsWith(patternLower.substring(1));
        } else if (patternLower.endsWith("*")) {
            return fileName.startsWith(patternLower.substring(0, patternLower.length() - 1));
        } else {
            return fileName.equals(patternLower);
        }
    }
}
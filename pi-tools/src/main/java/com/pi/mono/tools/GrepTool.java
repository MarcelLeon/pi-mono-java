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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * 内容搜索工具
 */
@Component
public class GrepTool implements ToolDefinition {

    @Override
    public String getName() {
        return "grep";
    }

    @Override
    public String getDescription() {
        return "Search for content in files using regex patterns";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("path", new ToolParameter("string", "File or directory to search in", true, null));
        params.put("pattern", new ToolParameter("string", "Regex pattern to search for", true, null));
        params.put("recursive", new ToolParameter("boolean", "Search recursively in directories", false, false));
        params.put("maxFiles", new ToolParameter("integer", "Maximum number of files to process", false, 50));
        params.put("maxLines", new ToolParameter("integer", "Maximum lines to show per file", false, 100));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                String patternStr = (String) request.arguments().get("pattern");
                Boolean recursive = (Boolean) request.arguments().getOrDefault("recursive", false);
                Integer maxFiles = (Integer) request.arguments().getOrDefault("maxFiles", 50);
                Integer maxLines = (Integer) request.arguments().getOrDefault("maxLines", 100);

                if (pathStr == null || pathStr.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Path parameter is required");
                }

                if (patternStr == null || patternStr.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Pattern parameter is required");
                }

                Path path = Paths.get(pathStr);
                if (!Files.exists(path)) {
                    return ToolExecutionResult.failure("File or directory not found: " + pathStr);
                }

                StringBuilder resultBuilder = new StringBuilder();
                resultBuilder.append("Searching for pattern: ").append(patternStr).append("\n");
                resultBuilder.append("In: ").append(pathStr).append("\n");
                resultBuilder.append("Recursive: ").append(recursive).append("\n");
                resultBuilder.append("=".repeat(50)).append("\n\n");

                Pattern pattern;
                try {
                    pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                } catch (Exception e) {
                    return ToolExecutionResult.failure("Invalid regex pattern: " + e.getMessage());
                }

                int fileCount = 0;
                int matchCount = 0;

                if (Files.isRegularFile(path)) {
                    // 搜索单个文件
                    String result = searchFile(path, pattern, maxLines);
                    if (result != null) {
                        resultBuilder.append(result);
                        fileCount++;
                    }
                } else {
                    // 搜索目录
                    try {
                        List<Path> files = Files.walk(path)
                            .filter(Files::isRegularFile)
                            .limit(maxFiles)
                            .collect(Collectors.toList());

                        for (Path file : files) {
                            String result = searchFile(file, pattern, maxLines);
                            if (result != null) {
                                resultBuilder.append(result);
                                fileCount++;
                                matchCount += countMatches(file, pattern);
                            }
                        }
                    } catch (IOException e) {
                        return ToolExecutionResult.failure("Failed to search directory: " + e.getMessage());
                    }
                }

                if (fileCount == 0) {
                    return ToolExecutionResult.failure("No matches found");
                }

                resultBuilder.append("\n").append("=".repeat(50)).append("\n");
                resultBuilder.append("Summary: ").append(fileCount).append(" file(s), ").append(matchCount).append(" match(es)");

                return ToolExecutionResult.success(resultBuilder.toString());

            } catch (Exception e) {
                return ToolExecutionResult.failure("Unexpected error: " + e.getMessage());
            }
        });
    }

    private String searchFile(Path file, Pattern pattern, int maxLines) {
        try {
            List<String> lines = Files.readAllLines(file);
            StringBuilder result = new StringBuilder();
            boolean found = false;

            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);

                if (matcher.find()) {
                    if (!found) {
                        result.append("File: ").append(file.toString()).append("\n");
                        result.append("-".repeat(50)).append("\n");
                        found = true;
                    }
                    result.append(String.format("%4d: %s\n", i + 1, line.trim()));
                }
            }

            return found ? result.toString() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private int countMatches(Path file, Pattern pattern) {
        try {
            List<String> lines = Files.readAllLines(file);
            int count = 0;

            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                while (matcher.find()) {
                    count++;
                }
            }

            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}
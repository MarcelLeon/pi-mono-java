package com.pi.mono.tools;

import org.springframework.stereotype.Component;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

/**
 * 读取文件工具
 */
@Component
public class ReadFileTool implements ToolDefinition {

    @Override
    public String getName() {
        return "read";
    }

    @Override
    public String getDescription() {
        return "Read the contents of a file";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("path", new ToolParameter("string", "File path to read", true, null));
        params.put("offset", new ToolParameter("integer", "Line offset to start reading from", false, 0));
        params.put("limit", new ToolParameter("integer", "Number of lines to read", false, 100));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                Integer offset = (Integer) request.arguments().getOrDefault("offset", 0);
                Integer limit = (Integer) request.arguments().getOrDefault("limit", 100);

                if (pathStr == null || pathStr.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Path parameter is required");
                }

                Path path = Paths.get(pathStr);
                if (!Files.exists(path)) {
                    return ToolExecutionResult.failure("File not found: " + pathStr);
                }

                if (!Files.isReadable(path)) {
                    return ToolExecutionResult.failure("File is not readable: " + pathStr);
                }

                if (Files.isDirectory(path)) {
                    return ToolExecutionResult.failure("Path is a directory, not a file: " + pathStr);
                }

                // 检查文件大小（限制为10MB）
                long fileSize = Files.size(path);
                if (fileSize > 10 * 1024 * 1024) {
                    return ToolExecutionResult.failure("File is too large to read: " + fileSize + " bytes");
                }

                StringBuilder content = new StringBuilder();
                content.append("File: ").append(pathStr).append("\n");
                content.append("Size: ").append(fileSize).append(" bytes\n");
                content.append("Content:\n");
                content.append("=".repeat(50)).append("\n");

                try {
                    Files.readAllLines(path).stream()
                        .skip(offset)
                        .limit(limit)
                        .forEach(line -> content.append(line).append("\n"));
                } catch (OutOfMemoryError e) {
                    return ToolExecutionResult.failure("File content is too large to process: " + e.getMessage());
                }

                return ToolExecutionResult.success(content.toString());
            } catch (IOException e) {
                return ToolExecutionResult.failure("Failed to read file: " + e.getMessage());
            } catch (Exception e) {
                return ToolExecutionResult.failure("Unexpected error: " + e.getMessage());
            }
        });
    }
}
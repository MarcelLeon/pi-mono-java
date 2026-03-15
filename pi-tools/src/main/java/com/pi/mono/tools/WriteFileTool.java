package com.pi.mono.tools;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;

/**
 * 写入文件工具
 */
@Component
public class WriteFileTool implements ToolDefinition {

    @Override
    public String getName() {
        return "write";
    }

    @Override
    public String getDescription() {
        return "Write content to a file (overwrites existing content)";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("path", new ToolParameter("string", "File path to write to", true, null));
        params.put("content", new ToolParameter("string", "Content to write to the file", true, null));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                String content = (String) request.arguments().get("content");

                if (pathStr == null || pathStr.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Path parameter is required");
                }

                if (content == null) {
                    return ToolExecutionResult.failure("Content parameter is required");
                }

                Path path = Paths.get(pathStr);
                Path parentDir = path.getParent();

                if (parentDir != null && !Files.exists(parentDir)) {
                    try {
                        Files.createDirectories(parentDir);
                    } catch (IOException e) {
                        return ToolExecutionResult.failure("Failed to create parent directory: " + e.getMessage());
                    }
                }

                // 检查文件大小（限制为10MB）
                if (content.getBytes().length > 10 * 1024 * 1024) {
                    return ToolExecutionResult.failure("Content is too large (max 10MB)");
                }

                // 备份现有文件
                if (Files.exists(path)) {
                    Path backupPath = Paths.get(pathStr + ".backup." + System.currentTimeMillis());
                    try {
                        Files.copy(path, backupPath);
                    } catch (IOException e) {
                        return ToolExecutionResult.failure("Failed to create backup: " + e.getMessage());
                    }
                }

                Files.write(path, content.getBytes());
                return ToolExecutionResult.success("File written successfully: " + pathStr);
            } catch (IOException e) {
                return ToolExecutionResult.failure("Failed to write file: " + e.getMessage());
            } catch (Exception e) {
                return ToolExecutionResult.failure("Unexpected error: " + e.getMessage());
            }
        });
    }
}
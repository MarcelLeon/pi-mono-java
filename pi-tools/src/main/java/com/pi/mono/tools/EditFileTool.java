package com.pi.mono.tools;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * 编辑文件工具
 */
@Component
public class EditFileTool implements ToolDefinition {

    @Override
    public String getName() {
        return "edit";
    }

    @Override
    public String getDescription() {
        return "Edit a file by replacing old_string with new_string";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("path", new ToolParameter("string", "File path to edit", true, null));
        params.put("old_string", new ToolParameter("string", "String to replace", true, null));
        params.put("new_string", new ToolParameter("string", "String to replace with", true, null));
        params.put("replace_all", new ToolParameter("boolean", "Replace all occurrences", false, false));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                String oldString = (String) request.arguments().get("old_string");
                String newString = (String) request.arguments().get("new_string");
                Boolean replaceAll = (Boolean) request.arguments().getOrDefault("replace_all", false);

                if (pathStr == null || pathStr.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Path parameter is required");
                }

                if (oldString == null) {
                    return ToolExecutionResult.failure("Old_string parameter is required");
                }

                if (newString == null) {
                    return ToolExecutionResult.failure("New_string parameter is required");
                }

                Path path = Paths.get(pathStr);
                if (!Files.exists(path)) {
                    return ToolExecutionResult.failure("File not found: " + pathStr);
                }

                if (!Files.isReadable(path)) {
                    return ToolExecutionResult.failure("File is not readable: " + pathStr);
                }

                String content = Files.readString(path);

                // 检查是否包含old_string
                if (!content.contains(oldString)) {
                    return ToolExecutionResult.failure("old_string not found in file: " + oldString);
                }

                // 检查是否会导致new_string也包含old_string（可能导致无限递归）
                if (newString.contains(oldString) && !replaceAll) {
                    return ToolExecutionResult.failure("new_string contains old_string, which would cause infinite recursion. Use replace_all=true if intended.");
                }

                // 创建备份
                Path backupPath = Paths.get(pathStr + ".backup." + System.currentTimeMillis());
                Files.copy(path, backupPath);

                String newContent;
                if (replaceAll) {
                    newContent = content.replace(oldString, newString);
                } else {
                    newContent = content.replaceFirst(oldString.replace("\\", "\\\\").replace("$", "\\$"), newString);
                }

                Files.writeString(path, newContent);

                // 生成简单的diff
                String diff = generateDiff(content, newContent);

                return ToolExecutionResult.success(
                    "File edited successfully: " + pathStr + "\n\nDiff:\n" + diff,
                    Map.of("backup", backupPath.toString())
                );
            } catch (IOException e) {
                return ToolExecutionResult.failure("Failed to edit file: " + e.getMessage());
            } catch (Exception e) {
                return ToolExecutionResult.failure("Unexpected error: " + e.getMessage());
            }
        });
    }

    private String generateDiff(String oldContent, String newContent) {
        StringBuilder diff = new StringBuilder();
        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");

        for (int i = 0; i < Math.min(oldLines.length, newLines.length); i++) {
            if (!oldLines[i].equals(newLines[i])) {
                diff.append("-").append(oldLines[i]).append("\n");
                diff.append("+").append(newLines[i]).append("\n");
            }
        }

        return diff.toString();
    }
}
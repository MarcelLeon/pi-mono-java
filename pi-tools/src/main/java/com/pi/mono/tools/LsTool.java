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
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;

/**
 * 列出文件工具
 */
@Component
public class LsTool implements ToolDefinition {

    @Override
    public String getName() {
        return "ls";
    }

    @Override
    public String getDescription() {
        return "List files and directories with details";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("path", new ToolParameter("string", "Directory to list", true, "."));
        params.put("recursive", new ToolParameter("boolean", "List recursively", false, false));
        params.put("showHidden", new ToolParameter("boolean", "Show hidden files", false, false));
        params.put("sort", new ToolParameter("string", "Sort by: name, size, time", false, "name"));
        params.put("maxItems", new ToolParameter("integer", "Maximum items to show", false, 100));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                Boolean recursive = (Boolean) request.arguments().getOrDefault("recursive", false);
                Boolean showHidden = (Boolean) request.arguments().getOrDefault("showHidden", false);
                String sort = (String) request.arguments().getOrDefault("sort", "name");
                Integer maxItems = (Integer) request.arguments().getOrDefault("maxItems", 100);

                if (pathStr == null || pathStr.trim().isEmpty()) {
                    pathStr = ".";
                }

                Path dir = Paths.get(pathStr);
                if (!Files.exists(dir)) {
                    return ToolExecutionResult.failure("Directory not found: " + pathStr);
                }

                if (!Files.isDirectory(dir)) {
                    return ToolExecutionResult.failure("Path is not a directory: " + pathStr);
                }

                StringBuilder resultBuilder = new StringBuilder();
                resultBuilder.append("Directory: ").append(dir.toAbsolutePath()).append("\n");
                resultBuilder.append("Recursive: ").append(recursive).append("\n");
                resultBuilder.append("Show Hidden: ").append(showHidden).append("\n");
                resultBuilder.append("Sort by: ").append(sort).append("\n");
                resultBuilder.append("=".repeat(80)).append("\n\n");

                List<String> items = new ArrayList<>();
                try {
                    if (recursive) {
                        Files.walk(dir)
                            .filter(path -> !path.equals(dir)) // 排除根目录
                            .filter(path -> showHidden || !isHidden(path))
                            .limit(maxItems)
                            .forEach(path -> items.add(formatFileInfo(path)));
                    } else {
                        Files.list(dir)
                            .filter(path -> showHidden || !isHidden(path))
                            .limit(maxItems)
                            .forEach(path -> items.add(formatFileInfo(path)));
                    }

                    // 排序
                    switch (sort.toLowerCase()) {
                        case "size":
                            items.sort((a, b) -> {
                                long sizeA = extractSize(a);
                                long sizeB = extractSize(b);
                                return Long.compare(sizeA, sizeB);
                            });
                            break;
                        case "time":
                            items.sort((a, b) -> {
                                String timeA = extractTime(a);
                                String timeB = extractTime(b);
                                return timeA.compareTo(timeB);
                            });
                            break;
                        case "name":
                        default:
                            items.sort(String::compareTo);
                            break;
                    }

                    for (String item : items) {
                        resultBuilder.append(item).append("\n");
                    }

                    resultBuilder.append("\nTotal items: ").append(items.size());

                } catch (IOException e) {
                    return ToolExecutionResult.failure("Failed to list directory: " + e.getMessage());
                }

                return ToolExecutionResult.success(resultBuilder.toString());

            } catch (Exception e) {
                return ToolExecutionResult.failure("Unexpected error: " + e.getMessage());
            }
        });
    }

    private boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return path.getFileName().toString().startsWith(".");
        }
    }

    private String formatFileInfo(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            StringBuilder info = new StringBuilder();

            // 权限
            String permissions = Files.isWritable(path) ? "w" : "-";
            permissions += Files.isReadable(path) ? "r" : "-";
            permissions += Files.isExecutable(path) ? "x" : "-";
            permissions += Files.isDirectory(path) ? "d" : "-";

            info.append(String.format("%-4s ", permissions));

            // 大小
            long size = attrs.size();
            String sizeStr;
            if (size < 1024) {
                sizeStr = String.format("%4d B", size);
            } else if (size < 1024 * 1024) {
                sizeStr = String.format("%4.1f K", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                sizeStr = String.format("%4.1f M", size / (1024.0 * 1024));
            } else {
                sizeStr = String.format("%4.1f G", size / (1024.0 * 1024 * 1024));
            }

            info.append(String.format("%-8s ", sizeStr));

            // 修改时间
            String time = attrs.lastModifiedTime().toString();
            if (time.length() > 19) {
                time = time.substring(0, 19);
            }
            info.append(String.format("%-20s ", time));

            // 名称
            String name = path.getFileName().toString();
            if (Files.isDirectory(path)) {
                name += "/";
            }
            info.append(name);

            return info.toString();
        } catch (IOException e) {
            return path.toString();
        }
    }

    private long extractSize(String info) {
        String[] parts = info.split("\\s+");
        if (parts.length >= 2) {
            String sizeStr = parts[1];
            if (sizeStr.endsWith("B")) {
                return Long.parseLong(sizeStr.substring(0, sizeStr.length() - 1));
            } else if (sizeStr.endsWith("K")) {
                return (long) (Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 1)) * 1024);
            } else if (sizeStr.endsWith("M")) {
                return (long) (Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 1)) * 1024 * 1024);
            } else if (sizeStr.endsWith("G")) {
                return (long) (Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 1)) * 1024 * 1024 * 1024);
            }
        }
        return 0;
    }

    private String extractTime(String info) {
        String[] parts = info.split("\\s+");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "";
    }
}
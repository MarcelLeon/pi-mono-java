package com.pi.mono.tools;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具权限管理器
 */
@Component
public class ToolPermissionManager {

    private final Map<String, Set<String>> toolPermissions = new ConcurrentHashMap<>();
    private final Map<String, String> toolCategories = new ConcurrentHashMap<>();

    @Autowired
    public ToolPermissionManager() {
        initializeDefaultPermissions();
    }

    private void initializeDefaultPermissions() {
        // 安全工具 - 默认允许
        addPermission("read", "read");
        addPermission("ls", "read");
        addPermission("find", "read");

        // 中等风险工具 - 需要明确权限
        addPermission("edit", "write");
        addPermission("write", "write");

        // 高风险工具 - 需要特殊权限
        addPermission("bash", "system");

        // 工具分类
        toolCategories.put("read", "File Reading");
        toolCategories.put("write", "File Writing");
        toolCategories.put("system", "System Commands");
    }

    public void addPermission(String toolName, String permission) {
        toolPermissions.computeIfAbsent(toolName, k -> new HashSet<>()).add(permission);
    }

    public void removePermission(String toolName, String permission) {
        toolPermissions.computeIfPresent(toolName, (k, v) -> {
            v.remove(permission);
            return v.isEmpty() ? null : v;
        });
    }

    public boolean hasPermission(String toolName, String permission) {
        Set<String> permissions = toolPermissions.get(toolName);
        return permissions != null && permissions.contains(permission);
    }

    public Set<String> getToolPermissions(String toolName) {
        return toolPermissions.getOrDefault(toolName, new HashSet<>());
    }

    public String getToolCategory(String toolName) {
        return toolCategories.get(toolName);
    }

    public boolean isToolAllowed(String toolName, Set<String> userPermissions) {
        Set<String> toolPerms = toolPermissions.get(toolName);
        if (toolPerms == null || toolPerms.isEmpty()) {
            return true; // 无权限要求的工具默认允许
        }

        // 检查用户是否有任意一个所需的权限
        for (String perm : toolPerms) {
            if (userPermissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Set<String>> getAllToolPermissions() {
        return new ConcurrentHashMap<>(toolPermissions);
    }

    public Map<String, String> getToolCategories() {
        return new ConcurrentHashMap<>(toolCategories);
    }
}
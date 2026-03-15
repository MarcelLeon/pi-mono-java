package com.pi.mono.tools;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * 工具管理器
 */
@Service
public class ToolManager {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Set<String> userPermissions = ConcurrentHashMap.newKeySet();

    @Autowired
    private ToolPermissionManager permissionManager;

    @Autowired
    public ToolManager(List<ToolDefinition> toolDefinitions) {
        for (ToolDefinition tool : toolDefinitions) {
            tools.put(tool.getName(), tool);
        }

        // 默认给用户所有权限（开发模式）
        userPermissions.add("read");
        userPermissions.add("write");
        userPermissions.add("system");
    }

    /**
     * 注册工具
     */
    public void registerTool(ToolDefinition tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * 获取工具
     */
    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 执行工具
     */
    public CompletableFuture<ToolExecutionResult> executeTool(String toolName, Map<String, Object> arguments, String sessionId, String nodeId) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return CompletableFuture.completedFuture(
                ToolExecutionResult.failure("Tool not found: " + toolName)
            );
        }

        // 检查权限
        if (!permissionManager.isToolAllowed(toolName, userPermissions)) {
            Set<String> requiredPerms = permissionManager.getToolPermissions(toolName);
            return CompletableFuture.completedFuture(
                ToolExecutionResult.failure("Permission denied. Required: " + requiredPerms)
            );
        }

        ToolExecutionRequest request = new ToolExecutionRequest(toolName, arguments, sessionId, nodeId);
        return tool.execute(request);
    }

    /**
     * 获取所有工具定义
     */
    public Map<String, ToolDefinition> getAllTools() {
        return new ConcurrentHashMap<>(tools);
    }

    /**
     * 获取工具列表（用于LLM调用）
     */
    public List<ToolDefinition> getToolList() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 添加用户权限
     */
    public void addUserPermission(String permission) {
        userPermissions.add(permission);
    }

    /**
     * 移除用户权限
     */
    public void removeUserPermission(String permission) {
        userPermissions.remove(permission);
    }

    /**
     * 设置用户权限
     */
    public void setUserPermissions(Set<String> permissions) {
        userPermissions.clear();
        userPermissions.addAll(permissions);
    }

    /**
     * 获取用户权限
     */
    public Set<String> getUserPermissions() {
        return new HashSet<>(userPermissions);
    }

    /**
     * 获取工具信息（包括权限要求）
     */
    public String getToolInfo(String toolName) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return "Tool not found: " + toolName;
        }

        String category = permissionManager.getToolCategory(toolName);
        Set<String> permissions = permissionManager.getToolPermissions(toolName);

        return String.format(
            "Tool: %s\nDescription: %s\nCategory: %s\nRequired Permissions: %s",
            toolName, tool.getDescription(), category, permissions
        );
    }
}
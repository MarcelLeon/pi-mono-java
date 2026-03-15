package com.pi.mono.cli;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import com.pi.mono.session.SessionManager;
import com.pi.mono.llm.LLMProviderManager;
import com.pi.mono.tools.ToolManager;
import com.pi.mono.tools.ToolPermissionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;
import java.util.Scanner;
import java.util.HashMap;
import java.util.Set;

/**
 * 简化的CLI应用入口
 */
@SpringBootApplication
public class PiCliApplication implements CommandLineRunner {

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private LLMProviderManager llmProviderManager;

    @Autowired
    private ToolManager toolManager;

    @Autowired
    private ToolPermissionManager permissionManager;

    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        SpringApplication.run(PiCliApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("🚀 Pi-Mono Java CLI Starting...");
        System.out.println("Type 'help' for available commands, 'exit' to quit\n");

        // 创建默认会话
        String sessionId = sessionManager.createSession("mock-claude");
        System.out.println("✅ Created session: " + sessionId);

        // 主循环
        while (true) {
            System.out.print("pi> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                System.out.println("👋 Goodbye!");
                break;
            }

            handleCommand(input);
        }
    }

    private void handleCommand(String input) {
        if (input.equalsIgnoreCase("help")) {
            showHelp();
        } else if (input.equalsIgnoreCase("sessions")) {
            listSessions();
        } else if (input.equalsIgnoreCase("tools")) {
            showTools();
        } else if (input.startsWith("tool ")) {
            handleToolCommand(input);
        } else if (input.startsWith("perm ")) {
            handlePermissionCommand(input);
        } else if (input.startsWith("/save")) {
            saveSession();
        } else if (input.startsWith("/new")) {
            createNewSession(input);
        } else {
            // 普通对话
            handleConversation(input);
        }
    }

    private void showHelp() {
        System.out.println("\n📚 Available commands:");
        System.out.println("  help              - Show this help message");
        System.out.println("  sessions          - List all saved sessions");
        System.out.println("  tools             - Show available tools");
        System.out.println("  tool <name> <args> - Execute a tool");
        System.out.println("  perm list         - List user permissions");
        System.out.println("  perm add <perm>   - Add permission");
        System.out.println("  perm remove <perm> - Remove permission");
        System.out.println("  perm tool <tool>  - Show tool permissions");
        System.out.println("  /save             - Save current session");
        System.out.println("  /new <model>      - Create new session with model");
        System.out.println("  exit              - Exit the application");
        System.out.println("  [any text]        - Send message to AI");
        System.out.println();
    }

    private void listSessions() {
        var sessions = sessionManager.listSessions();
        System.out.println("\n📁 Saved sessions:");
        if (sessions.isEmpty()) {
            System.out.println("  No sessions found");
        } else {
            sessions.forEach(session -> System.out.println("  - " + session));
        }
        System.out.println();
    }

    private void showTools() {
        System.out.println("\n🔧 Available tools:");
        var tools = toolManager.getAllTools();
        if (tools.isEmpty()) {
            System.out.println("  No tools available");
        } else {
            tools.forEach((name, tool) -> {
                String category = permissionManager.getToolCategory(name);
                Set<String> perms = permissionManager.getToolPermissions(name);
                boolean allowed = permissionManager.isToolAllowed(name, toolManager.getUserPermissions());
                String status = allowed ? "✅" : "❌";

                System.out.printf("  %s %s: %s [Category: %s, Required: %s]%n",
                    status, name, tool.getDescription(), category, perms);
            });
        }
        System.out.println();
    }

    private void handleToolCommand(String input) {
        String[] parts = input.split(" ", 3);
        if (parts.length < 3) {
            System.out.println("❌ Usage: tool <name> <arguments>");
            System.out.println("Example: tool read path='/Users/name/file.txt'");
            return;
        }

        String toolName = parts[1];
        String argsString = parts[2];

        try {
            // 检查权限
            if (!permissionManager.isToolAllowed(toolName, toolManager.getUserPermissions())) {
                Set<String> requiredPerms = permissionManager.getToolPermissions(toolName);
                System.out.println("❌ Permission denied for tool: " + toolName);
                System.out.println("Required permissions: " + requiredPerms);
                return;
            }

            // 简单的参数解析
            Map<String, Object> arguments = parseArguments(argsString);

            // 执行工具
            var futureResult = toolManager.executeTool(
                toolName,
                arguments,
                sessionManager.getCurrentSessionId(),
                null
            );

            var result = futureResult.get();

            System.out.println("🔧 Tool Result:");
            System.out.println("  Success: " + result.success());
            System.out.println("  Content: " + result.content());
            if (!result.metadata().isEmpty()) {
                System.out.println("  Metadata: " + result.metadata());
            }
            System.out.println();

        } catch (Exception e) {
            System.out.println("❌ Tool execution failed: " + e.getMessage());
        }
    }

    private void handlePermissionCommand(String input) {
        String[] parts = input.split(" ", 3);
        if (parts.length < 2) {
            System.out.println("❌ Usage: perm list | perm add <perm> | perm remove <perm> | perm tool <tool>");
            return;
        }

        String command = parts[1];
        switch (command) {
            case "list":
                showPermissions();
                break;
            case "add":
                if (parts.length > 2) {
                    toolManager.addUserPermission(parts[2]);
                    System.out.println("✅ Added permission: " + parts[2]);
                } else {
                    System.out.println("❌ Usage: perm add <permission>");
                }
                break;
            case "remove":
                if (parts.length > 2) {
                    toolManager.removeUserPermission(parts[2]);
                    System.out.println("✅ Removed permission: " + parts[2]);
                } else {
                    System.out.println("❌ Usage: perm remove <permission>");
                }
                break;
            case "tool":
                if (parts.length > 2) {
                    showToolPermissions(parts[2]);
                } else {
                    System.out.println("❌ Usage: perm tool <tool>");
                }
                break;
            default:
                System.out.println("❌ Unknown permission command: " + command);
        }
    }

    private void showPermissions() {
        System.out.println("\n🔒 User Permissions:");
        Set<String> permissions = toolManager.getUserPermissions();
        if (permissions.isEmpty()) {
            System.out.println("  No permissions");
        } else {
            permissions.forEach(perm -> System.out.println("  - " + perm));
        }

        System.out.println("\n📋 Permission Categories:");
        permissionManager.getToolCategories().forEach((perm, desc) -> {
            System.out.println("  " + perm + ": " + desc);
        });
        System.out.println();
    }

    private void showToolPermissions(String toolName) {
        String info = toolManager.getToolInfo(toolName);
        System.out.println("\n🔧 Tool Information:");
        System.out.println(info);
        System.out.println();
    }

    private void saveSession() {
        try {
            sessionManager.saveSession();
            System.out.println("💾 Session saved successfully!");
        } catch (Exception e) {
            System.out.println("❌ Failed to save session: " + e.getMessage());
        }
    }

    private void createNewSession(String input) {
        String[] parts = input.split("\\s+", 2);
        String model = parts.length > 1 ? parts[1] : "mock-claude";

        try {
            String sessionId = sessionManager.createSession(model);
            System.out.println("✅ Created new session: " + sessionId + " (model: " + model + ")");
        } catch (Exception e) {
            System.out.println("❌ Failed to create session: " + e.getMessage());
        }
    }

    private void handleConversation(String input) {
        try {
            // 发送消息
            var futureResponse = sessionManager.sendMessage(input);

            // 显示用户消息
            System.out.println("👤 You: " + input);

            // 等待并显示AI响应
            AgentMessage response = futureResponse.get();
            System.out.println("🤖 Pi: " + response.content());
            System.out.println();

        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    private Map<String, Object> parseArguments(String argsString) {
        Map<String, Object> args = new HashMap<>();

        // 简单的参数解析（格式: key='value' key2="value2" key3=value3）
        String[] pairs = argsString.trim().split("\\s+");
        for (String pair : pairs) {
            if (pair.contains("=")) {
                String[] keyValue = pair.split("=", 2);
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                // 去掉引号
                if ((value.startsWith("'") && value.endsWith("'")) ||
                    (value.startsWith("\"") && value.endsWith("\""))) {
                    value = value.substring(1, value.length() - 1);
                }

                args.put(key, value);
            }
        }

        return args;
    }
}
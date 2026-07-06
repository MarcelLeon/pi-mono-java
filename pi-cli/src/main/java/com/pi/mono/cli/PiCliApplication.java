package com.pi.mono.cli;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.MessageRole;
import com.pi.mono.cli.attachments.PiFileReferenceResolver;
import com.pi.mono.cli.resources.PiResourceLoader;
import com.pi.mono.cli.resources.PiResourceCommandResolver;
import com.pi.mono.cli.resources.PiResources;
import com.pi.mono.cli.rpc.PiRpcCommandHandler;
import com.pi.mono.cli.settings.PiCliOutputFormatter;
import com.pi.mono.cli.settings.PiCliSettings;
import com.pi.mono.cli.settings.PiCliSettingsLoader;
import com.pi.mono.cli.settings.PiExternalEditor;
import com.pi.mono.cli.trust.PiTrustManager;
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
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
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
    private PiResources resources = new PiResources(java.util.List.of(), java.util.List.of(), java.util.List.of(), "");
    private final PiResourceLoader resourceLoader = new PiResourceLoader();
    private final PiCliSettingsLoader settingsLoader = new PiCliSettingsLoader();
    private final PiTrustManager trustManager = new PiTrustManager();
    private PiCliSettings settings = PiCliSettings.defaults();
    private Path workingDirectory;
    private boolean trustedProject;
    private boolean ephemeralSession;

    public static void main(String[] args) {
        SpringApplication.run(PiCliApplication.class, args);
    }

    @Override
    public void run(String... args) {
        PiCliStartupOptions startupOptions = PiCliStartupOptions.parse(args);
        ephemeralSession = startupOptions.ephemeralSession();

        if (startupOptions.rpcMode()) {
            runRpcMode(startupOptions);
            return;
        }

        System.out.println("🚀 Pi-Mono Java CLI Starting...");
        System.out.println("Type 'help' for available commands, 'exit' to quit\n");

        workingDirectory = Path.of("").toAbsolutePath().normalize();
        reloadResources();
        showResourceStartupSummary();
        registerSessionEventPrinter();

        // 创建默认会话
        String sessionId = createStartupSession(startupOptions);
        System.out.println("✅ Created session: " + sessionId);
        if (ephemeralSession) {
            System.out.println("ℹ️ Session persistence disabled by --no-session");
        }

        // 主循环
        handleCommandLine();
    }

    private void runRpcMode(PiCliStartupOptions startupOptions) {
        workingDirectory = Path.of("").toAbsolutePath().normalize();
        reloadResources();
        createStartupSession(startupOptions);

        PiRpcCommandHandler handler = new PiRpcCommandHandler(sessionManager);
        while (scanner.hasNextLine()) {
            String input = scanner.nextLine().trim();
            if (!input.isBlank()) {
                System.out.println(handler.handle(input));
            }
        }
    }

    private void handleCommandLine() {
        try {
            while (true) {
                System.out.print("pi> ");
                if (scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("exit")) {
                        System.out.println("👋 Goodbye!");
                        break;
                    }

                    handleCommand(input);
                } else {
                    // 如果没有更多输入，退出循环
                    System.out.println("👋 Goodbye!");
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Application error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
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
        } else if (input.equalsIgnoreCase("/session")) {
            showCurrentSession();
        } else if (input.startsWith("/rename")) {
            renameSession(input);
        } else if (input.startsWith("/resume")) {
            resumeSession(input);
        } else if (input.equalsIgnoreCase("/tree")) {
            showSessionTree();
        } else if (input.startsWith("/fork")) {
            forkSession(input);
        } else if (input.startsWith("/export")) {
            exportSession(input);
        } else if (input.startsWith("/import")) {
            importSession(input);
        } else if (input.equalsIgnoreCase("/models")) {
            showModels();
        } else if (input.equalsIgnoreCase("/resources")) {
            showResources();
        } else if (input.equalsIgnoreCase("/prompts")) {
            showPromptTemplates();
        } else if (input.equalsIgnoreCase("/skills")) {
            showSkills();
        } else if (input.equalsIgnoreCase("/settings")) {
            showSettings();
        } else if (input.equalsIgnoreCase("/edit")) {
            editMessage();
        } else if (input.equalsIgnoreCase("/trust")) {
            trustProject();
        } else {
            var resolvedResourceCommand = new PiResourceCommandResolver(resources).resolve(input);
            if (resolvedResourceCommand.isPresent()) {
                handleConversation(resolvedResourceCommand.get());
                return;
            }
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
        System.out.println("  /session          - Show active session info");
        System.out.println("  /rename <name>    - Rename current session and emit session_info_changed");
        System.out.println("  /resume <id>      - Resume a saved session");
        System.out.println("  /tree             - Show current session tree");
        System.out.println("  /fork <nodeId> [model] - Fork a saved path into a new session");
        System.out.println("  /export <file>    - Export current session JSONL");
        System.out.println("  /import <file>    - Import and resume a session JSONL");
        System.out.println("  /models           - List provider models");
        System.out.println("  /resources        - Show loaded context/resource files");
        System.out.println("  /prompts          - Show discovered prompt templates");
        System.out.println("  /skills           - Show discovered skills");
        System.out.println("  /settings         - Show loaded CLI settings");
        System.out.println("  /edit             - Compose next message with configured externalEditor");
        System.out.println("  /trust            - Trust this project and reload project-local resources");
        System.out.println("  exit              - Exit the application");
        System.out.println("  [any text]        - Send message to AI; use @file to attach local file contents");
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
            if (ephemeralSession) {
                System.out.println("❌ Current session was started with --no-session; persistence is disabled");
                return;
            }
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
            String sessionId = sessionManager.createSession(model, resourceMetadata());
            System.out.println("✅ Created new session: " + sessionId + " (model: " + model + ")");
        } catch (Exception e) {
            System.out.println("❌ Failed to create session: " + e.getMessage());
        }
    }

    private void showResourceStartupSummary() {
        if (resources.isEmpty()) {
            System.out.println("ℹ️ No Pi resources found");
            return;
        }
        System.out.printf(
            "📦 Loaded resources: %d context file(s), %d prompt template(s), %d skill(s), trustedProject=%s%n",
            resources.contextFiles().size(),
            resources.promptTemplates().size(),
            resources.skills().size(),
            trustedProject
        );
    }

    private void registerSessionEventPrinter() {
        sessionManager.addSessionEventListener(event -> {
            if ("session_info_changed".equals(event.eventType())) {
                System.out.printf("↻ %s: %s%n", event.eventType(), event.sessionName());
            }
        });
    }

    private String createStartupSession(PiCliStartupOptions startupOptions) {
        Map<String, Object> metadata = new HashMap<>(resourceMetadata());
        metadata.put("ephemeral", startupOptions.ephemeralSession());
        return startupOptions.sessionId()
            .map(sessionId -> sessionManager.createSessionWithId(sessionId, "mock-claude", metadata))
            .orElseGet(() -> sessionManager.createSession("mock-claude", metadata));
    }

    private Map<String, Object> resourceMetadata() {
        return Map.of(
            "contextFileCount", resources.contextFiles().size(),
            "promptTemplateCount", resources.promptTemplates().size(),
            "skillCount", resources.skills().size(),
            "combinedContextLength", resources.combinedContext().length(),
            "trustedProject", trustedProject,
            "outputPad", settings.outputPad(),
            "externalEditorConfigured", settings.externalEditor().isPresent()
        );
    }

    private void showCurrentSession() {
        String sessionId = sessionManager.getCurrentSessionId();
        if (sessionId == null) {
            System.out.println("❌ No active session");
            return;
        }

        var history = sessionManager.getSessionHistory();
        System.out.println("\n📌 Current session:");
        System.out.println("  Session ID: " + sessionId);
        System.out.println("  Ephemeral: " + ephemeralSession);
        sessionManager.getCurrentSessionName()
            .ifPresent(name -> System.out.println("  Session name: " + name));
        System.out.println("  Current node: " + sessionManager.getCurrentBranchId());
        System.out.println("  Messages on active path: " + history.size());
        if (!history.isEmpty()) {
            System.out.println("  Last role: " + history.get(history.size() - 1).message().role());
        }
        System.out.println();
    }

    private void renameSession(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("❌ Usage: /rename <name>");
            return;
        }

        try {
            String sessionName = sessionManager.renameCurrentSession(parts[1]);
            System.out.println("✅ Renamed session: " + sessionName);
        } catch (Exception e) {
            System.out.println("❌ Failed to rename session: " + e.getMessage());
        }
    }

    private void resumeSession(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("❌ Usage: /resume <sessionId>");
            return;
        }

        try {
            String sessionId = sessionManager.loadSession(parts[1].trim());
            System.out.println("✅ Resumed session: " + sessionId);
            showCurrentSession();
        } catch (Exception e) {
            System.out.println("❌ Failed to resume session: " + e.getMessage());
        }
    }

    private void showSessionTree() {
        var nodes = sessionManager.getAllNodes();
        if (nodes.isEmpty()) {
            System.out.println("❌ Current session has no nodes");
            return;
        }

        System.out.println("\n🌳 Session tree:");
        nodes.stream()
            .sorted(Comparator.comparing(node -> node.timestamp()))
            .forEach(node -> {
                String marker = node.id().equals(sessionManager.getCurrentBranchId()) ? "*" : " ";
                String parent = node.parentId() == null ? "root" : node.parentId();
                String content = node.message().content().replace('\n', ' ');
                if (content.length() > 80) {
                    content = content.substring(0, 77) + "...";
                }
                System.out.printf("  %s %s [%s] parent=%s %s%n",
                    marker, node.id(), node.message().role(), parent, content);
            });
        System.out.println();
    }

    private void forkSession(String input) {
        String[] parts = input.split("\\s+", 3);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("❌ Usage: /fork <nodeId> [model]");
            return;
        }

        String model = parts.length > 2 ? parts[2].trim() : "mock-claude";
        try {
            String forkedSessionId = sessionManager.forkCurrentSessionFromNode(parts[1].trim(), model);
            System.out.println("✅ Forked session: " + forkedSessionId);
            System.out.println("  Use /resume " + forkedSessionId + " to switch to it.");
        } catch (Exception e) {
            System.out.println("❌ Failed to fork session: " + e.getMessage());
        }
    }

    private void exportSession(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("❌ Usage: /export <file>");
            return;
        }

        try {
            Path exported = sessionManager.exportSession(Path.of(parts[1].trim()));
            System.out.println("✅ Exported session to: " + exported);
        } catch (Exception e) {
            System.out.println("❌ Failed to export session: " + e.getMessage());
        }
    }

    private void importSession(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("❌ Usage: /import <file>");
            return;
        }

        try {
            String sessionId = sessionManager.importSession(Path.of(parts[1].trim()));
            System.out.println("✅ Imported and resumed session: " + sessionId);
        } catch (Exception e) {
            System.out.println("❌ Failed to import session: " + e.getMessage());
        }
    }

    private void showModels() {
        System.out.println("\n🧠 Available models:");
        llmProviderManager.getAllProviders().forEach(provider -> {
            System.out.println("  Provider: " + provider.getId() + " (available=" + provider.isAvailable() + ")");
            provider.getAvailableModels().forEach(model -> System.out.printf(
                "    - %s: %s [maxTokens=%d]%n",
                model.id(), model.description(), model.maxTokens()
            ));
        });
        System.out.println();
    }

    private void showResources() {
        System.out.println("\n📦 Loaded resources:");
        System.out.println("  Working directory: " + workingDirectory);
        System.out.println("  Trusted project: " + trustedProject);
        System.out.println("  Trust file: " + trustManager.trustFile());
        System.out.println("  Context files: " + resources.contextFiles().size());
        resources.contextFiles().forEach(file -> System.out.println("    - " + file.path()));
        System.out.println("  Prompt templates: " + resources.promptTemplates().size());
        System.out.println("  Skills: " + resources.skills().size());
        System.out.println("  Combined context length: " + resources.combinedContext().length());
        System.out.println();
    }

    private void showSettings() {
        System.out.println("\n⚙️ CLI settings:");
        System.out.println("  outputPad: " + settings.outputPad());
        System.out.println("  externalEditor: " + settings.externalEditor().orElse("(not configured)"));
        System.out.println();
    }

    private void editMessage() {
        try {
            String message = new PiExternalEditor(settings, workingDirectory).captureMessage("");
            if (message.isBlank()) {
                System.out.println("ℹ️ Editor returned an empty message; nothing sent.");
                return;
            }
            handleConversation(message);
        } catch (Exception e) {
            System.out.println("❌ Failed to open external editor: " + e.getMessage());
        }
    }

    private void showPromptTemplates() {
        System.out.println("\n📝 Prompt templates:");
        if (resources.promptTemplates().isEmpty()) {
            System.out.println("  No prompt templates found");
        } else {
            resources.promptTemplates().forEach(file -> System.out.println("  - /" + file.name() + " -> " + file.path()));
        }
        System.out.println();
    }

    private void showSkills() {
        System.out.println("\n🧩 Skills:");
        if (resources.skills().isEmpty()) {
            System.out.println("  No skills found");
        } else {
            resources.skills().forEach(file -> System.out.println("  - " + file.name() + " -> " + file.path()));
        }
        System.out.println();
    }

    private void trustProject() {
        trustManager.trust(workingDirectory);
        reloadResources();
        System.out.println("✅ Trusted project: " + workingDirectory);
        showResourceStartupSummary();
    }

    private void reloadResources() {
        trustedProject = trustManager.isTrusted(workingDirectory);
        resources = resourceLoader.load(workingDirectory, trustedProject);
        settings = settingsLoader.load(workingDirectory, trustedProject);
    }

    private void handleConversation(String input) {
        try {
            String messageContent = new PiFileReferenceResolver(workingDirectory).resolve(input);
            // 发送消息
            var futureResponse = sessionManager.sendMessage(messageContent);
            PiCliOutputFormatter formatter = new PiCliOutputFormatter(settings);

            // 显示用户消息
            System.out.println(formatter.userMessage(input));

            // 等待并显示AI响应
            AgentMessage response = futureResponse.get();
            extractThinking(response).ifPresent(thinking ->
                System.out.println(formatter.thinkingMessage(thinking)));
            System.out.println(formatter.assistantMessage(response.content()));
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

    private Optional<String> extractThinking(AgentMessage response) {
        Object thinking = response.metadata().getOrDefault("thinking", response.metadata().get("reasoning"));
        if (thinking == null) {
            return Optional.empty();
        }
        String content = String.valueOf(thinking).trim();
        return content.isBlank() ? Optional.empty() : Optional.of(content);
    }
}

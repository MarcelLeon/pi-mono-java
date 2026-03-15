package com.pi.mono.tools;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Bash命令执行工具（带安全限制）
 */
@Component
public class BashTool implements ToolDefinition {

    private static final Pattern SAFE_COMMAND_PATTERN = Pattern.compile("^[a-zA-Z0-9_/.\\-\\s\"'\\\\]+$");
    private static final List<String> ALLOWED_COMMANDS = List.of(
        "ls", "cat", "head", "tail", "grep", "find", "echo", "pwd", "date", "whoami", "which",
        "git", "mvn", "npm", "yarn", "docker", "kubectl", "curl", "wget"
    );

    @Override
    public String getName() {
        return "bash";
    }

    @Override
    public String getDescription() {
        return "Execute a bash command with safety restrictions";
    }

    @Override
    public Map<String, ToolParameter> getParameters() {
        Map<String, ToolParameter> params = new HashMap<>();
        params.put("command", new ToolParameter("string", "Bash command to execute", true, null));
        params.put("timeout", new ToolParameter("integer", "Command timeout in seconds (max 60)", false, 30));
        return params;
    }

    @Override
    public CompletableFuture<ToolExecutionResult> execute(ToolExecutionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String command = (String) request.arguments().get("command");
                Integer timeout = (Integer) request.arguments().getOrDefault("timeout", 30);

                if (command == null || command.trim().isEmpty()) {
                    return ToolExecutionResult.failure("Command parameter is required");
                }

                // 安全检查
                if (!isCommandSafe(command)) {
                    return ToolExecutionResult.failure("Command contains unsafe characters or patterns");
                }

                if (!isCommandAllowed(command)) {
                    return ToolExecutionResult.failure("Command is not in the allowed list: " + getCommandName(command));
                }

                if (timeout > 60) {
                    timeout = 60;
                }

                ProcessBuilder processBuilder = new ProcessBuilder();
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    processBuilder.command("cmd.exe", "/c", command);
                } else {
                    processBuilder.command("sh", "-c", command);
                }

                // 设置工作目录为项目根目录
                processBuilder.directory(new File("/Users/wangzq/VsCodeProjects/pi-mono-java"));

                Process process = processBuilder.start();

                // 读取输出
                StringBuilder output = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                }

                boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
                int exitCode = process.exitValue();

                if (!completed) {
                    process.destroyForcibly();
                    return ToolExecutionResult.failure("Command timed out after " + timeout + " seconds");
                }

                if (exitCode == 0) {
                    return ToolExecutionResult.success(
                        "Command executed successfully:\n" + command + "\n\nOutput:\n" + output.toString(),
                        Map.of("exit_code", exitCode, "timeout", timeout)
                    );
                } else {
                    return ToolExecutionResult.failure(
                        "Command failed with exit code " + exitCode + ":\n" + command + "\n\nError Output:\n" + errorOutput.toString(),
                        Map.of("exit_code", exitCode, "timeout", timeout)
                    );
                }
            } catch (Exception e) {
                return ToolExecutionResult.failure("Failed to execute command: " + e.getMessage());
            }
        });
    }

    private boolean isCommandSafe(String command) {
        // 检查是否包含危险字符
        if (!SAFE_COMMAND_PATTERN.matcher(command).matches()) {
            return false;
        }

        // 检查是否包含危险操作
        String[] dangerousPatterns = {
            "rm -rf", "sudo", "chmod", "chown", "mkfs", "dd if=", "mount", "umount",
            "passwd", "useradd", "userdel", "su -", "screen -S", "tmux new -s",
            "| rm ", "| sudo ", "| chmod ", "| chown ", "> /etc/", "> /boot/",
            "< /dev/", "> /dev/", "cat /etc/passwd", "cat /etc/shadow"
        };

        String lowerCommand = command.toLowerCase();
        for (String pattern : dangerousPatterns) {
            if (lowerCommand.contains(pattern)) {
                return false;
            }
        }

        return true;
    }

    private boolean isCommandAllowed(String command) {
        String commandName = getCommandName(command);
        return ALLOWED_COMMANDS.contains(commandName);
    }

    private String getCommandName(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length > 0) {
            String fullCommand = parts[0];
            int slashIndex = fullCommand.lastIndexOf('/');
            return slashIndex >= 0 ? fullCommand.substring(slashIndex + 1) : fullCommand;
        }
        return "";
    }
}
package com.pi.mono.llm.provider;

import com.pi.mono.core.AgentMessage;
import com.pi.mono.core.ChatRequest;
import com.pi.mono.core.HealthStatus;
import com.pi.mono.core.LLMProvider;
import com.pi.mono.core.MessageRole;
import com.pi.mono.core.Model;
import com.pi.mono.core.ToolCall;
import com.pi.mono.core.ToolCallResult;
import com.pi.mono.llm.config.ExternalCliConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "pi.llm.external-cli.enabled", havingValue = "true")
public class ExternalCliLLMProvider implements LLMProvider {
    private final ExternalCliConfig config;

    public ExternalCliLLMProvider(ExternalCliConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<AgentMessage> chat(ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> runExternalCli(renderConversation(request)));
    }

    @Override
    public List<Model> getAvailableModels() {
        return List.of(new Model(
            modelId(),
            getId(),
            config.getDescription() == null || config.getDescription().isBlank()
                ? "External CLI model"
                : config.getDescription(),
            200000,
            BigDecimal.ZERO
        ));
    }

    @Override
    public ToolCallResult executeToolCall(ToolCall toolCall, List<AgentMessage> context) {
        return new ToolCallResult(
            toolCall.name(),
            "External CLI provider does not execute tool calls directly.",
            Map.of("externalCli", true, "unsupported", true)
        );
    }

    @Override
    public HealthStatus health() {
        return isAvailable() ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
    }

    @Override
    public boolean isAvailable() {
        return config.isEnabled() && config.getCommand() != null && !config.getCommand().isBlank();
    }

    @Override
    public BigDecimal getCostPerToken() {
        return BigDecimal.ZERO;
    }

    @Override
    public String getId() {
        return config.getId() == null || config.getId().isBlank()
            ? "external-cli"
            : config.getId().trim();
    }

    private String modelId() {
        return config.getModelId() == null || config.getModelId().isBlank()
            ? getId()
            : config.getModelId().trim();
    }

    private AgentMessage runExternalCli(String prompt) {
        List<String> command = prepareCommand(config.getCommand(), modelId());
        if (command.isEmpty()) {
            throw new IllegalStateException("pi.llm.external-cli.command is required");
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            process.getOutputStream().write(prompt.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            Duration timeout = Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds()));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("External CLI timed out after " + timeout.toSeconds() + "s");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("External CLI exited with code " + process.exitValue() + ": " + output);
            }
            return new AgentMessage(
                MessageRole.ASSISTANT,
                output,
                Map.of(
                    "provider", getId(),
                    "model", modelId(),
                    "externalCli", true,
                    "timestamp", System.currentTimeMillis()
                )
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run external CLI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("External CLI interrupted", e);
        }
    }

    private String renderConversation(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are connected through pi-mono-java external CLI provider.\n");
        prompt.append("Return only the assistant response for the latest user request.\n\n");
        for (AgentMessage message : request.messages()) {
            prompt.append(message.role().name()).append(": ").append(message.content()).append("\n\n");
        }
        return prompt.toString();
    }

    static List<String> splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    static List<String> prepareCommand(String command, String modelId) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        String concreteModel = modelId == null ? "" : modelId.trim();
        if (!concreteModel.isBlank() && command.contains("{model}")) {
            return splitCommand(command.replace("{model}", concreteModel));
        }

        List<String> parts = splitCommand(command);
        if (!shouldInjectModel(parts, concreteModel)) {
            return parts;
        }

        String executable = executableName(parts.get(0));
        if ("claude".equals(executable) && !containsModelOption(parts)) {
            List<String> withModel = new ArrayList<>(parts);
            withModel.add("--model");
            withModel.add(concreteModel);
            return withModel;
        }
        if ("codex".equals(executable) && parts.size() >= 2 && "exec".equals(parts.get(1)) && !containsModelOption(parts)) {
            List<String> withModel = new ArrayList<>(parts);
            int insertAt = withModel.indexOf("-");
            if (insertAt < 0) {
                insertAt = withModel.size();
            }
            withModel.add(insertAt, concreteModel);
            withModel.add(insertAt, "-m");
            return withModel;
        }
        return parts;
    }

    private static boolean shouldInjectModel(List<String> parts, String modelId) {
        return !parts.isEmpty()
            && modelId != null
            && !modelId.isBlank()
            && !"external-cli".equals(modelId);
    }

    private static boolean containsModelOption(List<String> parts) {
        return parts.contains("--model") || parts.contains("-m");
    }

    private static String executableName(String executable) {
        int lastSlash = executable.lastIndexOf('/');
        return lastSlash >= 0 ? executable.substring(lastSlash + 1) : executable;
    }
}

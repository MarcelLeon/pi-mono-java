package com.pi.mono.cli.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PiResourceCommandResolver {
    private final PiResources resources;

    public PiResourceCommandResolver(PiResources resources) {
        this.resources = resources;
    }

    public Optional<String> resolve(String input) {
        if (input == null || !input.startsWith("/")) {
            return Optional.empty();
        }

        if (input.startsWith("/skill:")) {
            return resolveSkill(input);
        }
        return resolvePromptTemplate(input);
    }

    private Optional<String> resolvePromptTemplate(String input) {
        String[] parts = input.substring(1).trim().split("\\s+", 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            return Optional.empty();
        }

        String promptName = parts[0];
        return resources.promptTemplates().stream()
            .filter(file -> file.name().equals(promptName))
            .findFirst()
            .map(file -> expandTemplate(file.content(), parts.length > 1 ? parts[1] : ""));
    }

    private Optional<String> resolveSkill(String input) {
        String command = input.substring("/skill:".length()).trim();
        String[] parts = command.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            return Optional.empty();
        }

        String skillName = parts[0];
        String request = parts.length > 1 ? parts[1].trim() : "";

        return resources.skills().stream()
            .filter(file -> file.name().equals(skillName))
            .findFirst()
            .map(file -> buildSkillPrompt(file, request));
    }

    private String expandTemplate(String template, String arguments) {
        String expanded = template;
        for (Map.Entry<String, String> entry : parseArguments(arguments).entrySet()) {
            expanded = expanded.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return expanded;
    }

    private Map<String, String> parseArguments(String arguments) {
        Map<String, String> values = new HashMap<>();
        if (arguments == null || arguments.isBlank()) {
            return values;
        }

        for (String token : arguments.trim().split("\\s+")) {
            int equalsIndex = token.indexOf('=');
            if (equalsIndex > 0 && equalsIndex < token.length() - 1) {
                values.put(token.substring(0, equalsIndex), stripQuotes(token.substring(equalsIndex + 1)));
            }
        }
        return values;
    }

    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String buildSkillPrompt(PiResources.ResourceFile skill, String request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Use the following skill instructions.\n\n");
        prompt.append(skill.content());
        if (!request.isBlank()) {
            prompt.append("\n\nUser request: ").append(request);
        }
        return prompt.toString();
    }
}

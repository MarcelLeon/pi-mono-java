package com.pi.mono.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "pi.llm.github-copilot")
public class GitHubCopilotConfig {
    private boolean enabled = false;
    private String model = "github-copilot";
    private String credentialsFile = defaultCredentialsFile().toString();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getCredentialsFile() {
        return credentialsFile;
    }

    public void setCredentialsFile(String credentialsFile) {
        this.credentialsFile = credentialsFile;
    }

    public String getResolvedModel() {
        return model == null || model.isBlank() ? "github-copilot" : model.trim();
    }

    public Path getResolvedCredentialsFile() {
        if (credentialsFile == null || credentialsFile.isBlank()) {
            return defaultCredentialsFile();
        }
        String value = credentialsFile.trim();
        if (value.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (value.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(value.substring(2));
        }
        return Path.of(value);
    }

    private static Path defaultCredentialsFile() {
        return Path.of(System.getProperty("user.home"), ".config", "pi", "auth.json");
    }
}

package com.pi.mono.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "pi.llm.bedrock")
public class BedrockConfig {
    private String region = "us-east-1";
    private String profile;
    private String model = "anthropic.claude-sonnet-5";
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    private String credentialsFile;
    private boolean enabled = false;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getCredentialsFile() {
        return credentialsFile;
    }

    public void setCredentialsFile(String credentialsFile) {
        this.credentialsFile = credentialsFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void validate() {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("Bedrock region is required");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Bedrock model is required");
        }
    }

    public String getRuntimeEndpoint() {
        String resolvedRegion = region == null || region.isBlank() ? "us-east-1" : region.trim();
        return "https://bedrock-runtime." + resolvedRegion + ".amazonaws.com";
    }

    public Optional<Credentials> resolveCredentials() {
        Optional<Credentials> configuredCredentials = toCredentials(accessKeyId, secretAccessKey, sessionToken);
        if (configuredCredentials.isPresent()) {
            return configuredCredentials;
        }

        Optional<Credentials> profileCredentials = resolveProfileCredentials();
        if (profileCredentials.isPresent()) {
            return profileCredentials;
        }

        return toCredentials(
            System.getenv("AWS_ACCESS_KEY_ID"),
            System.getenv("AWS_SECRET_ACCESS_KEY"),
            System.getenv("AWS_SESSION_TOKEN")
        );
    }

    public Optional<Credentials> resolveCredentials(Map<String, String> requestEnv) {
        Map<String, String> env = requestEnv == null ? Map.of() : requestEnv;
        Optional<Credentials> requestCredentials = toCredentials(
            env.get("AWS_ACCESS_KEY_ID"),
            env.get("AWS_SECRET_ACCESS_KEY"),
            env.get("AWS_SESSION_TOKEN")
        );
        if (requestCredentials.isPresent()) {
            return requestCredentials;
        }

        Optional<Credentials> requestProfileCredentials = resolveProfileCredentials(
            env.get("AWS_PROFILE"),
            env.get("AWS_SHARED_CREDENTIALS_FILE")
        );
        if (requestProfileCredentials.isPresent()) {
            return requestProfileCredentials;
        }

        return resolveCredentials();
    }

    private Optional<Credentials> resolveProfileCredentials() {
        return resolveProfileCredentials(profile, credentialsFile);
    }

    private Optional<Credentials> resolveProfileCredentials(String preferredProfile, String preferredCredentialsFile) {
        String resolvedProfile = firstPresent(preferredProfile, System.getenv("AWS_PROFILE"));
        if (resolvedProfile == null) {
            return Optional.empty();
        }

        Path path = resolveCredentialsPath(preferredCredentialsFile);
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            return parseCredentialsFile(path).entrySet().stream()
                .filter(entry -> entry.getKey().equals(resolvedProfile))
                .findFirst()
                .flatMap(entry -> toCredentials(
                    entry.getValue().get("aws_access_key_id"),
                    entry.getValue().get("aws_secret_access_key"),
                    entry.getValue().get("aws_session_token")
                ));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path resolveCredentialsPath() {
        return resolveCredentialsPath(credentialsFile);
    }

    private Path resolveCredentialsPath(String preferredCredentialsFile) {
        String configuredPath = firstPresent(preferredCredentialsFile, System.getenv("AWS_SHARED_CREDENTIALS_FILE"));
        if (configuredPath != null) {
            return Path.of(configuredPath);
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null;
        }
        return Path.of(userHome, ".aws", "credentials");
    }

    private Map<String, Map<String, String>> parseCredentialsFile(Path path) throws IOException {
        Map<String, Map<String, String>> profiles = new HashMap<>();
        List<String> lines = Files.readAllLines(path);
        String currentProfile = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentProfile = trimmed.substring(1, trimmed.length() - 1).trim();
                profiles.putIfAbsent(currentProfile, new HashMap<>());
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (currentProfile == null || separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            profiles.get(currentProfile).put(key, value);
        }

        return profiles;
    }

    private Optional<Credentials> toCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
        String resolvedAccessKeyId = firstPresent(accessKeyId, null);
        String resolvedSecretAccessKey = firstPresent(secretAccessKey, null);
        String resolvedSessionToken = firstPresent(sessionToken, null);
        if (resolvedAccessKeyId == null || resolvedSecretAccessKey == null) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(resolvedAccessKeyId, resolvedSecretAccessKey, resolvedSessionToken));
    }

    private String firstPresent(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    public record Credentials(String accessKeyId, String secretAccessKey, String sessionToken) {
    }
}

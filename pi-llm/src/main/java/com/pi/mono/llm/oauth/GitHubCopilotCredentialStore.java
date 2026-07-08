package com.pi.mono.llm.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * File-backed credential store for the GitHub Copilot OAuth device flow.
 */
public class GitHubCopilotCredentialStore implements GitHubCopilotOAuthClient.AccessTokenStore {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String TYPE = "api_key";
    private static final String PROVIDER = "github-copilot";

    private final Path credentialsFile;

    public GitHubCopilotCredentialStore(Path credentialsFile) {
        this.credentialsFile = credentialsFile.toAbsolutePath().normalize();
    }

    @Override
    public void save(GitHubCopilotOAuthClient.AccessToken accessToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", TYPE);
        payload.put("provider", PROVIDER);
        payload.put("access_token", accessToken.accessToken());
        payload.put("token_type", accessToken.tokenType());
        payload.put("scope", accessToken.scope());
        payload.put("raw_response", accessToken.rawResponse());

        try {
            Path parent = credentialsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = credentialsFile.resolveSibling(credentialsFile.getFileName() + ".tmp");
            Files.writeString(
                tempFile,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload),
                StandardCharsets.UTF_8
            );
            Files.move(tempFile, credentialsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save GitHub Copilot credentials: " + credentialsFile, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<GitHubCopilotOAuthClient.AccessToken> load() {
        if (!Files.exists(credentialsFile)) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(credentialsFile.toFile(), MAP_TYPE);
            if (!TYPE.equals(text(payload.get("type"))) || !PROVIDER.equals(text(payload.get("provider")))) {
                return Optional.empty();
            }
            Object rawResponse = payload.get("raw_response");
            Map<String, Object> raw = rawResponse instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
            if (raw.isEmpty()) {
                raw.put("access_token", text(payload.get("access_token")));
                raw.put("token_type", text(payload.get("token_type")));
                raw.put("scope", text(payload.get("scope")));
            }
            return Optional.of(new GitHubCopilotOAuthClient.AccessToken(
                text(payload.get("access_token")),
                text(payload.get("token_type")),
                text(payload.get("scope")),
                raw
            ));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GitHub Copilot credentials: " + credentialsFile, e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

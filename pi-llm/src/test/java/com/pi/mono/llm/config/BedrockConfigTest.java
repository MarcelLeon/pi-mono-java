package com.pi.mono.llm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsToClaudeSonnet5InUsEast1() {
        BedrockConfig config = new BedrockConfig();

        assertEquals("anthropic.claude-sonnet-5", config.getModel());
        assertEquals("us-east-1", config.getRegion());
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com", config.getRuntimeEndpoint());
    }

    @Test
    void runtimeEndpointUsesConfiguredRegion() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion("us-west-2");

        assertEquals("https://bedrock-runtime.us-west-2.amazonaws.com", config.getRuntimeEndpoint());
    }

    @Test
    void resolvesCredentialsFromExplicitConfig() {
        BedrockConfig config = new BedrockConfig();
        config.setAccessKeyId("access-key");
        config.setSecretAccessKey("secret-key");
        config.setSessionToken("session-token");

        BedrockConfig.Credentials credentials = config.resolveCredentials().orElseThrow();

        assertEquals("access-key", credentials.accessKeyId());
        assertEquals("secret-key", credentials.secretAccessKey());
        assertEquals("session-token", credentials.sessionToken());
    }

    @Test
    void resolvesCredentialsFromConfiguredProfileFile() throws IOException {
        Path credentialsFile = tempDir.resolve("credentials");
        Files.writeString(credentialsFile, """
            [default]
            aws_access_key_id=default-access
            aws_secret_access_key=default-secret

            [dev]
            aws_access_key_id=profile-access
            aws_secret_access_key=profile-secret
            aws_session_token=profile-session
            """);
        BedrockConfig config = new BedrockConfig();
        config.setProfile("dev");
        config.setCredentialsFile(credentialsFile.toString());

        BedrockConfig.Credentials credentials = config.resolveCredentials().orElseThrow();

        assertEquals("profile-access", credentials.accessKeyId());
        assertEquals("profile-secret", credentials.secretAccessKey());
        assertEquals("profile-session", credentials.sessionToken());
    }

    @Test
    void resolvesCredentialsFromRequestScopedProfileFile() throws IOException {
        Path credentialsFile = tempDir.resolve("request-credentials");
        Files.writeString(credentialsFile, """
            [default]
            aws_access_key_id=default-access
            aws_secret_access_key=default-secret

            [tenant]
            aws_access_key_id=request-profile-access
            aws_secret_access_key=request-profile-secret
            aws_session_token=request-profile-session
            """);
        BedrockConfig config = new BedrockConfig();

        BedrockConfig.Credentials credentials = config.resolveCredentials(Map.of(
            "AWS_PROFILE", "tenant",
            "AWS_SHARED_CREDENTIALS_FILE", credentialsFile.toString()
        )).orElseThrow();

        assertEquals("request-profile-access", credentials.accessKeyId());
        assertEquals("request-profile-secret", credentials.secretAccessKey());
        assertEquals("request-profile-session", credentials.sessionToken());
    }

    @Test
    void explicitCredentialsOverrideConfiguredProfileFile() throws IOException {
        Path credentialsFile = tempDir.resolve("credentials");
        Files.writeString(credentialsFile, """
            [dev]
            aws_access_key_id=profile-access
            aws_secret_access_key=profile-secret
            """);
        BedrockConfig config = new BedrockConfig();
        config.setProfile("dev");
        config.setCredentialsFile(credentialsFile.toString());
        config.setAccessKeyId("explicit-access");
        config.setSecretAccessKey("explicit-secret");
        config.setSessionToken("explicit-session");

        BedrockConfig.Credentials credentials = config.resolveCredentials().orElseThrow();

        assertEquals("explicit-access", credentials.accessKeyId());
        assertEquals("explicit-secret", credentials.secretAccessKey());
        assertEquals("explicit-session", credentials.sessionToken());
    }

    @Test
    void validateRequiresRegion() {
        BedrockConfig config = new BedrockConfig();
        config.setRegion(" ");

        assertThrows(IllegalArgumentException.class, config::validate);
    }
}

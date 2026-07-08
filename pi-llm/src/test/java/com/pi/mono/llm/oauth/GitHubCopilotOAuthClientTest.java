package com.pi.mono.llm.oauth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubCopilotOAuthClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void startsDeviceFlowAndWaitsBeforePollingForAccessToken() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        FakeTransport transport = new FakeTransport(clock);
        transport.deviceResponses.add(Map.of(
            "device_code", "device-123",
            "user_code", "ABCD-EFGH",
            "verification_uri", "https://github.com/login/device",
            "verification_uri_complete", "https://github.com/login/device?user_code=ABCD-EFGH",
            "expires_in", 900,
            "interval", 5
        ));
        transport.tokenResponses.add(Map.of(
            "access_token", "ghu_test_token",
            "token_type", "bearer",
            "scope", "read:user"
        ));
        transport.copilotTokenResponses.add(copilotTokenResponse("copilot-api-token"));
        OAuthDeviceCodePoller<GitHubCopilotOAuthClient.AccessToken> poller =
            new OAuthDeviceCodePoller<>(clock, sleeper);
        GitHubCopilotOAuthClient client = new GitHubCopilotOAuthClient(
            "test-client",
            "read:user",
            GitHubCopilotOAuthClient.Urls.DEFAULT,
            transport,
            poller
        );

        GitHubCopilotOAuthClient.LoginResult result = client.login();

        assertEquals("ABCD-EFGH", result.deviceAuthorization().userCode());
        assertEquals("copilot-api-token", result.accessToken().accessToken());
        assertEquals(List.of(5000L), sleeper.delaysMs);
        assertEquals(0L, transport.requests.get(0).timeMs());
        assertEquals(5000L, transport.requests.get(1).timeMs());
        assertEquals(GitHubCopilotOAuthClient.Urls.DEFAULT.deviceCodeUrl(), transport.requests.get(0).url());
        assertEquals(GitHubCopilotOAuthClient.Urls.DEFAULT.accessTokenUrl(), transport.requests.get(1).url());
        assertEquals("test-client", transport.requests.get(0).form().get("client_id"));
        assertEquals("read:user", transport.requests.get(0).form().get("scope"));
        assertEquals("device-123", transport.requests.get(1).form().get("device_code"));
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", transport.requests.get(1).form().get("grant_type"));
    }

    @Test
    void passesServerSlowDownIntervalToPoller() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        FakeTransport transport = new FakeTransport(clock);
        transport.deviceResponses.add(Map.of(
            "device_code", "device-123",
            "user_code", "ABCD-EFGH",
            "verification_uri", "https://github.com/login/device",
            "expires_in", 900,
            "interval", 5
        ));
        transport.tokenResponses.add(Map.of("error", "authorization_pending"));
        transport.tokenResponses.add(Map.of("error", "slow_down", "interval", 7));
        transport.tokenResponses.add(Map.of("access_token", "ghu_test_token"));
        transport.copilotTokenResponses.add(copilotTokenResponse("copilot-api-token"));
        OAuthDeviceCodePoller<GitHubCopilotOAuthClient.AccessToken> poller =
            new OAuthDeviceCodePoller<>(clock, sleeper);
        GitHubCopilotOAuthClient client = new GitHubCopilotOAuthClient(
            "test-client",
            "read:user",
            GitHubCopilotOAuthClient.Urls.DEFAULT,
            transport,
            poller
        );

        GitHubCopilotOAuthClient.LoginResult result = client.login();

        assertEquals("copilot-api-token", result.accessToken().accessToken());
        assertEquals(List.of(5000L, 5000L, 7000L), sleeper.delaysMs);
        assertEquals(List.of(0L, 5000L, 10000L, 17000L), transport.requests.stream()
            .map(FakeTransport.Request::timeMs)
            .toList());
    }

    @Test
    void persistsSuccessfulAccessTokenToCredentialStore() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        FakeTransport transport = new FakeTransport(clock);
        transport.deviceResponses.add(Map.of(
            "device_code", "device-123",
            "user_code", "ABCD-EFGH",
            "verification_uri", "https://github.com/login/device",
            "expires_in", 900,
            "interval", 5
        ));
        transport.tokenResponses.add(Map.of(
            "access_token", "ghu_persisted_token",
            "token_type", "bearer",
            "scope", "read:user",
            "refresh_token", "refresh-1"
        ));
        transport.copilotTokenResponses.add(copilotTokenResponse("copilot-persisted-token"));
        OAuthDeviceCodePoller<GitHubCopilotOAuthClient.AccessToken> poller =
            new OAuthDeviceCodePoller<>(clock, sleeper);
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        GitHubCopilotCredentialStore credentialStore = new GitHubCopilotCredentialStore(credentialsFile);
        GitHubCopilotOAuthClient client = new GitHubCopilotOAuthClient(
            "test-client",
            "read:user",
            GitHubCopilotOAuthClient.Urls.DEFAULT,
            transport,
            poller,
            credentialStore
        );

        GitHubCopilotOAuthClient.LoginResult result = client.login();

        assertEquals("copilot-persisted-token", result.accessToken().accessToken());
        assertTrue(Files.exists(credentialsFile));
        GitHubCopilotOAuthClient.AccessToken loaded = credentialStore.load().orElseThrow();
        assertEquals("copilot-persisted-token", loaded.accessToken());
        assertEquals("bearer", loaded.tokenType());
        assertEquals("read:user", loaded.scope());
        assertEquals("ghu_persisted_token", loaded.rawResponse().get("refresh_token"));
        JsonNode persisted = OBJECT_MAPPER.readTree(Files.readString(credentialsFile));
        assertEquals("api_key", persisted.path("type").asText());
        assertEquals("github-copilot", persisted.path("provider").asText());
    }

    @Test
    void exchangesGitHubAccessTokenForCopilotApiTokenBeforePersisting() throws Exception {
        MutableClock clock = new MutableClock();
        RecordingSleeper sleeper = new RecordingSleeper(clock);
        FakeTransport transport = new FakeTransport(clock);
        transport.deviceResponses.add(Map.of(
            "device_code", "device-123",
            "user_code", "ABCD-EFGH",
            "verification_uri", "https://github.com/login/device",
            "expires_in", 900,
            "interval", 5
        ));
        transport.tokenResponses.add(Map.of(
            "access_token", "github-oauth-token",
            "token_type", "bearer",
            "scope", "read:user"
        ));
        transport.copilotTokenResponses.add(Map.of(
            "token", "tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;",
            "expires_at", 999
        ));
        OAuthDeviceCodePoller<GitHubCopilotOAuthClient.AccessToken> poller =
            new OAuthDeviceCodePoller<>(clock, sleeper);
        Path credentialsFile = tempDir.resolve("github-copilot-auth.json");
        GitHubCopilotCredentialStore credentialStore = new GitHubCopilotCredentialStore(credentialsFile);
        GitHubCopilotOAuthClient client = new GitHubCopilotOAuthClient(
            "test-client",
            "read:user",
            GitHubCopilotOAuthClient.Urls.DEFAULT,
            transport,
            poller,
            credentialStore
        );

        GitHubCopilotOAuthClient.LoginResult result = client.login();

        assertEquals("tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;", result.accessToken().accessToken());
        assertEquals("github-oauth-token", result.accessToken().rawResponse().get("refresh_token"));
        assertEquals(999, result.accessToken().rawResponse().get("expires_at"));
        assertEquals(GitHubCopilotOAuthClient.Urls.DEFAULT.copilotTokenUrl(), transport.copilotTokenRequests.get(0).url());
        assertEquals("github-oauth-token", transport.copilotTokenRequests.get(0).githubAccessToken());

        GitHubCopilotOAuthClient.AccessToken loaded = credentialStore.load().orElseThrow();
        assertEquals("tid=1;exp=999;proxy-ep=proxy.individual.githubcopilot.com;", loaded.accessToken());
        assertEquals("github-oauth-token", loaded.rawResponse().get("refresh_token"));
    }

    static class FakeTransport implements GitHubCopilotOAuthClient.Transport {
        private final MutableClock clock;
        private final Queue<Map<String, Object>> deviceResponses = new ArrayDeque<>();
        private final Queue<Map<String, Object>> tokenResponses = new ArrayDeque<>();
        private final Queue<Map<String, Object>> copilotTokenResponses = new ArrayDeque<>();
        private final List<Request> requests = new ArrayList<>();
        private final List<CopilotTokenRequest> copilotTokenRequests = new ArrayList<>();

        FakeTransport(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public Map<String, Object> postForm(String url, Map<String, String> form) {
            requests.add(new Request(clock.millis(), url, new LinkedHashMap<>(form)));
            if (GitHubCopilotOAuthClient.Urls.DEFAULT.deviceCodeUrl().equals(url)) {
                return deviceResponses.remove();
            }
            if (GitHubCopilotOAuthClient.Urls.DEFAULT.accessTokenUrl().equals(url)) {
                return tokenResponses.remove();
            }
            throw new IllegalArgumentException("Unexpected URL: " + url);
        }

        @Override
        public Map<String, Object> refreshCopilotToken(String url, String githubAccessToken) {
            copilotTokenRequests.add(new CopilotTokenRequest(clock.millis(), url, githubAccessToken));
            return copilotTokenResponses.remove();
        }

        record Request(long timeMs, String url, Map<String, String> form) {
        }

        record CopilotTokenRequest(long timeMs, String url, String githubAccessToken) {
        }
    }

    static class MutableClock extends Clock {
        private long millis;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        void advance(long millis) {
            this.millis += millis;
        }
    }

    static class RecordingSleeper implements OAuthDeviceCodePoller.Sleeper {
        private final MutableClock clock;
        private final List<Long> delaysMs = new ArrayList<>();

        RecordingSleeper(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public void sleep(long millis) {
            delaysMs.add(millis);
            clock.advance(millis);
        }
    }

    private static Map<String, Object> copilotTokenResponse(String token) {
        return Map.of("token", token, "expires_at", 999);
    }
}

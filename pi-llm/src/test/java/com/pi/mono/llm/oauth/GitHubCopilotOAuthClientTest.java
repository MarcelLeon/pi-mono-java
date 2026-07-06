package com.pi.mono.llm.oauth;

import org.junit.jupiter.api.Test;

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

class GitHubCopilotOAuthClientTest {

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
        assertEquals("ghu_test_token", result.accessToken().accessToken());
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

        assertEquals("ghu_test_token", result.accessToken().accessToken());
        assertEquals(List.of(5000L, 5000L, 7000L), sleeper.delaysMs);
        assertEquals(List.of(0L, 5000L, 10000L, 17000L), transport.requests.stream()
            .map(FakeTransport.Request::timeMs)
            .toList());
    }

    static class FakeTransport implements GitHubCopilotOAuthClient.Transport {
        private final MutableClock clock;
        private final Queue<Map<String, Object>> deviceResponses = new ArrayDeque<>();
        private final Queue<Map<String, Object>> tokenResponses = new ArrayDeque<>();
        private final List<Request> requests = new ArrayList<>();

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

        record Request(long timeMs, String url, Map<String, String> form) {
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
}

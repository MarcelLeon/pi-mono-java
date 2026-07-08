package com.pi.mono.llm.oauth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal GitHub Copilot-compatible OAuth device flow client.
 */
public class GitHubCopilotOAuthClient {

    private static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

    private final String clientId;
    private final String scope;
    private final Urls urls;
    private final Transport transport;
    private final OAuthDeviceCodePoller<AccessToken> poller;
    private final AccessTokenStore accessTokenStore;

    public GitHubCopilotOAuthClient(String clientId) {
        this(clientId, "read:user");
    }

    public GitHubCopilotOAuthClient(String clientId, String scope) {
        this(clientId, scope, Urls.DEFAULT, new WebClientTransport(WebClient.builder().build()), new OAuthDeviceCodePoller<>());
    }

    GitHubCopilotOAuthClient(
        String clientId,
        String scope,
        Urls urls,
        Transport transport,
        OAuthDeviceCodePoller<AccessToken> poller
    ) {
        this(clientId, scope, urls, transport, poller, AccessTokenStore.noop());
    }

    GitHubCopilotOAuthClient(
        String clientId,
        String scope,
        Urls urls,
        Transport transport,
        OAuthDeviceCodePoller<AccessToken> poller,
        AccessTokenStore accessTokenStore
    ) {
        this.clientId = requireText(clientId, "clientId");
        this.scope = scope == null ? "" : scope.trim();
        this.urls = Objects.requireNonNull(urls, "urls");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.poller = Objects.requireNonNull(poller, "poller");
        this.accessTokenStore = Objects.requireNonNull(accessTokenStore, "accessTokenStore");
    }

    public LoginResult login() throws InterruptedException {
        DeviceAuthorization deviceAuthorization = startDeviceAuthorization();
        AccessToken accessToken = poller.poll(new OAuthDeviceCodePoller.PollOptions<>(
            deviceAuthorization.intervalSeconds(),
            deviceAuthorization.expiresInSeconds(),
            true,
            () -> pollAccessToken(deviceAuthorization)
        ));
        accessTokenStore.save(accessToken);
        return new LoginResult(deviceAuthorization, accessToken);
    }

    private DeviceAuthorization startDeviceAuthorization() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        if (!scope.isBlank()) {
            form.put("scope", scope);
        }

        Map<String, Object> response = transport.postForm(urls.deviceCodeUrl(), form);
        String deviceCode = text(response, "device_code");
        String userCode = text(response, "user_code");
        String verificationUri = text(response, "verification_uri");
        String verificationUriComplete = optionalText(response, "verification_uri_complete");
        long expiresInSeconds = positiveLong(response, "expires_in", 900);
        long intervalSeconds = positiveLong(response, "interval", 5);
        return new DeviceAuthorization(
            deviceCode,
            userCode,
            verificationUri,
            verificationUriComplete,
            expiresInSeconds,
            intervalSeconds
        );
    }

    private OAuthDeviceCodePoller.PollResult<AccessToken> pollAccessToken(DeviceAuthorization deviceAuthorization) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("device_code", deviceAuthorization.deviceCode());
        form.put("grant_type", DEVICE_CODE_GRANT);

        Map<String, Object> response = transport.postForm(urls.accessTokenUrl(), form);
        String accessToken = optionalText(response, "access_token");
        if (!accessToken.isBlank()) {
            return OAuthDeviceCodePoller.PollResult.complete(new AccessToken(
                accessToken,
                optionalText(response, "token_type"),
                optionalText(response, "scope"),
                new LinkedHashMap<>(response)
            ));
        }

        String error = optionalText(response, "error");
        if ("authorization_pending".equals(error)) {
            return OAuthDeviceCodePoller.PollResult.pending();
        }
        if ("slow_down".equals(error)) {
            Long intervalSeconds = optionalPositiveLong(response, "interval");
            return intervalSeconds == null
                ? OAuthDeviceCodePoller.PollResult.slowDown()
                : OAuthDeviceCodePoller.PollResult.slowDown(intervalSeconds);
        }
        return OAuthDeviceCodePoller.PollResult.failed(errorMessage(response, error));
    }

    private static String errorMessage(Map<String, Object> response, String error) {
        String description = optionalText(response, "error_description");
        if (!description.isBlank()) {
            return description;
        }
        if (error != null && !error.isBlank()) {
            return error;
        }
        return "GitHub Copilot OAuth token response did not include an access_token";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String text(Map<String, Object> values, String name) {
        String value = optionalText(values, name);
        if (value.isBlank()) {
            throw new IllegalStateException("GitHub Copilot OAuth response missing " + name);
        }
        return value;
    }

    private static String optionalText(Map<String, Object> values, String name) {
        Object value = values == null ? null : values.get(name);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long positiveLong(Map<String, Object> values, String name, long defaultValue) {
        Long value = optionalPositiveLong(values, name);
        return value == null ? defaultValue : value;
    }

    private static Long optionalPositiveLong(Map<String, Object> values, String name) {
        Object value = values == null ? null : values.get(name);
        if (value == null) {
            return null;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            parsed = Long.parseLong(String.valueOf(value).trim());
        }
        return parsed > 0 ? parsed : null;
    }

    @FunctionalInterface
    public interface Transport {
        Map<String, Object> postForm(String url, Map<String, String> form);
    }

    public interface AccessTokenStore {
        void save(AccessToken accessToken);

        Optional<AccessToken> load();

        static AccessTokenStore noop() {
            return new AccessTokenStore() {
                @Override
                public void save(AccessToken accessToken) {
                    // No persistence by default.
                }

                @Override
                public Optional<AccessToken> load() {
                    return Optional.empty();
                }
            };
        }
    }

    public record Urls(String deviceCodeUrl, String accessTokenUrl) {
        public static final Urls DEFAULT = new Urls(
            "https://github.com/login/device/code",
            "https://github.com/login/oauth/access_token"
        );

        public Urls {
            requireText(deviceCodeUrl, "deviceCodeUrl");
            requireText(accessTokenUrl, "accessTokenUrl");
        }
    }

    public record DeviceAuthorization(
        String deviceCode,
        String userCode,
        String verificationUri,
        String verificationUriComplete,
        long expiresInSeconds,
        long intervalSeconds
    ) {
    }

    public record AccessToken(String accessToken, String tokenType, String scope, Map<String, Object> rawResponse) {
    }

    public record LoginResult(DeviceAuthorization deviceAuthorization, AccessToken accessToken) {
    }

    public static class WebClientTransport implements Transport {
        private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

        private final WebClient webClient;

        public WebClientTransport(WebClient webClient) {
            this.webClient = Objects.requireNonNull(webClient, "webClient");
        }

        @Override
        public Map<String, Object> postForm(String url, Map<String, String> form) {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            form.forEach(body::add);
            Map<String, Object> response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .block();
            return response == null ? Map.of() : response;
        }
    }
}

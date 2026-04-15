package io.tacticl.business.connections.provider;

import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.Map;

public class GitHubOAuthProvider implements OAuthProvider {

    private static final String AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;

    public GitHubOAuthProvider(String clientId, String clientSecret) {
        this(clientId, clientSecret, RestClient.create());
    }

    // Package-private for testing
    GitHubOAuthProvider(String clientId, String clientSecret, RestClient restClient) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClient;
    }

    @Override
    public Type getType() { return Type.GITHUB; }

    @Override
    public String generateAuthUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTH_URL)
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "repo user")
            .queryParam("state", state)
            .build().toUriString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthTokens exchangeCode(String code, String redirectUri) {
        var response = (Map<String, Object>) restClient.post()
            .uri(TOKEN_URL)
            .header("Accept", "application/json")
            .body(Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
            ))
            .retrieve()
            .body(Map.class);

        if (response == null || response.containsKey("error")) {
            var error = response != null ? response.get("error") : "null response";
            throw new IllegalStateException("GitHub token exchange failed: " + error);
        }

        var accessToken = (String) response.get("access_token");
        var identity = fetchAccountIdentity(accessToken);
        return new OAuthTokens(accessToken, null, null, identity);
    }

    @Override
    public OAuthTokens refreshToken(String refreshToken) {
        throw new UnsupportedOperationException("GitHub tokens do not support refresh");
    }

    @SuppressWarnings("unchecked")
    private String fetchAccountIdentity(String accessToken) {
        var user = (Map<String, Object>) restClient.get()
            .uri(USER_URL)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);
        if (user == null || user.get("login") == null) {
            throw new IllegalStateException("GitHub user identity fetch failed");
        }
        return "@" + user.get("login");
    }
}

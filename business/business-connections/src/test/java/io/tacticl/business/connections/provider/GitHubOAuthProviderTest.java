package io.tacticl.business.connections.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GitHubOAuthProviderTest {

    private GitHubOAuthProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GitHubOAuthProvider("test-client-id", "test-client-secret");
    }

    @Test
    void generateAuthUrl_containsClientIdAndState() {
        String url = provider.generateAuthUrl("state-abc", "https://app.tacticl.ai/callback");

        assertThat(url)
            .contains("github.com/login/oauth/authorize")
            .contains("client_id=test-client-id")
            .contains("state=state-abc")
            .contains("scope=")
            .contains("redirect_uri=");
    }

    @Test
    void getType_returnsGitHub() {
        assertThat(provider.getType()).isEqualTo(OAuthProvider.Type.GITHUB);
    }
}

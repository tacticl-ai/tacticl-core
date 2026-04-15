package io.tacticl.business.connections.config;

import io.tacticl.business.connections.provider.GitHubOAuthProvider;
import io.tacticl.business.connections.provider.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConnectionsConfig {

    @Bean
    public OAuthProvider gitHubOAuthProvider(
            @Value("${tacticl.github.client-id}") String clientId,
            @Value("${tacticl.github.client-secret}") String clientSecret) {
        return new GitHubOAuthProvider(clientId, clientSecret);
    }
}

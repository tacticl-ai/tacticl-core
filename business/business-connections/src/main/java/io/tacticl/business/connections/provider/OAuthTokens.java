package io.tacticl.business.connections.provider;

import java.time.Instant;

public record OAuthTokens(
    String accessToken,
    String refreshToken,
    Instant expiresAt,
    String accountIdentity
) {}

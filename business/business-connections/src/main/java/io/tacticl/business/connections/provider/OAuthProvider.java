package io.tacticl.business.connections.provider;

public interface OAuthProvider {

    Type getType();
    String generateAuthUrl(String state, String redirectUri);
    OAuthTokens exchangeCode(String code, String redirectUri);
    OAuthTokens refreshToken(String refreshToken);

    enum Type {
        GITHUB, INSTAGRAM, TWITTER, LINKEDIN, SLACK,
        GOOGLE_PHOTOS, GOOGLE_DRIVE, DROPBOX
    }
}

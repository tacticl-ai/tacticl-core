package io.tacticl.data.token.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import java.time.Instant;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A user-issued personal access token (PAT) for programmatic API access.
 *
 * <p>The plaintext token is shown to the user exactly once at creation and never stored.
 * We persist only a SHA-256 hash ({@code tokenHash}) plus a short display prefix
 * ({@code tokenPrefix}, e.g. {@code "tac_ab12"}) so the UI can render a masked label
 * without exposing the secret. Revocation is a soft-delete ({@code isActive=false}).
 *
 * <p>NOTE: token <em>validation</em> in the auth filter is not wired in this slice — this
 * entity backs CRUD management only. Follow-up: hash inbound bearer tokens and look up by
 * {@code tokenHash} where {@code isActive=true}, bumping {@code lastUsedAt}.
 */
@Document("personal_access_tokens")
public class PersonalAccessToken extends BaseMongoEntity {

    @Indexed
    private String userId;

    /** Human label chosen by the user, e.g. "CI deploy key". */
    private String name;

    /** SHA-256 hex of the full plaintext token. The plaintext is never persisted. */
    @Indexed(unique = true)
    private String tokenHash;

    /** Short, non-secret display prefix for masked rendering, e.g. {@code "tac_ab12"}. */
    private String tokenPrefix;

    private Instant lastUsedAt;

    public static PersonalAccessToken create(String userId, String name, String tokenHash, String tokenPrefix) {
        var t = new PersonalAccessToken();
        t.userId = userId;
        t.name = name;
        t.tokenHash = tokenHash;
        t.tokenPrefix = tokenPrefix;
        return t;
    }

    public void recordUse(Instant now) {
        this.lastUsedAt = now;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getTokenHash() { return tokenHash; }
    public String getTokenPrefix() { return tokenPrefix; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}

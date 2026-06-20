package io.tacticl.business.token;

import io.tacticl.data.token.entity.PersonalAccessToken;
import io.tacticl.data.token.repository.PersonalAccessTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and manages user personal access tokens (PATs).
 *
 * <p>Security model: the plaintext token is generated as {@code "tac_" + 32 random bytes}
 * (URL-safe base64, no padding) and returned to the caller <em>exactly once</em> at
 * issuance. We persist only its SHA-256 hash and a short display prefix; the plaintext is
 * never stored and cannot be recovered. Revocation is a soft-delete.
 *
 * <p>NOTE: token validation in the auth filter is intentionally out of scope for this
 * slice (CRUD management only). Wiring validation = hash the inbound bearer token and look
 * it up via {@code findByTokenHashAndIsActiveTrue}, then bump {@code lastUsedAt}.
 */
@Service
public class PersonalAccessTokenService {

    private static final Logger log = LoggerFactory.getLogger(PersonalAccessTokenService.class);

    private static final String TOKEN_PREFIX = "tac_";
    /** Random byte count for the secret portion (256 bits of entropy). */
    private static final int TOKEN_BYTES = 32;
    /** Display prefix length (the literal "tac_" plus the first few chars of the secret). */
    private static final int DISPLAY_PREFIX_CHARS = 8;

    private final PersonalAccessTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalAccessTokenService(PersonalAccessTokenRepository repository) {
        this.repository = repository;
    }

    /**
     * Issue a new token for {@code userId}. Returns the persisted record plus the one-time
     * plaintext — surface the plaintext to the user now; it can never be retrieved again.
     */
    public IssuedToken issue(String userId, String name) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        String label = (name == null || name.isBlank()) ? "Untitled token" : name.trim();

        String plaintext = generatePlaintext();
        String hash = sha256Hex(plaintext);
        String displayPrefix = plaintext.substring(0, Math.min(DISPLAY_PREFIX_CHARS, plaintext.length()));

        PersonalAccessToken saved = repository.save(
                PersonalAccessToken.create(userId, label, hash, displayPrefix));
        log.info("PAT issued user={} tokenId={} name={}", userId, saved.getId(), label);
        return new IssuedToken(saved, plaintext);
    }

    /** A user's active tokens, newest first (never includes plaintext). */
    public List<PersonalAccessToken> list(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return repository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId);
    }

    /**
     * Revoke (soft-delete) a token. Ownership-scoped. Returns {@code true} if a matching
     * active token was found and deactivated, else {@code false} (controller → 404).
     */
    public boolean revoke(String userId, String tokenId) {
        if (userId == null || userId.isBlank() || tokenId == null || tokenId.isBlank()) {
            return false;
        }
        return repository.findByIdAndUserId(tokenId, userId)
                .filter(PersonalAccessToken::isActive)
                .map(token -> {
                    token.delete();
                    repository.save(token);
                    log.info("PAT revoked user={} tokenId={}", userId, tokenId);
                    return true;
                })
                .orElse(false);
    }

    private String generatePlaintext() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return TOKEN_PREFIX + secret;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS — unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Result of {@link #issue}: persisted record + the one-time plaintext token. */
    public record IssuedToken(PersonalAccessToken record, String plaintext) {}
}

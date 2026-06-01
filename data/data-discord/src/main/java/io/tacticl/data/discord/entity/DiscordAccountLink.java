package io.tacticl.data.discord.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Binds a Discord user (snowflake id) to a tacticl user id. This is the hard account-link
 * precondition for ingress: an unlinked Discord user must never trigger a PDLC run.
 *
 * <p>A pending link carries a one-time {@code linkToken} (with its own expiry) that the user
 * redeems out-of-band to associate their Discord snowflake with their tacticl account. Once
 * redeemed, {@code tacticlUserId} is populated and {@code linkedAt} is set; the token fields
 * are cleared. Mirrors the Telegram link/link-token shapes (collapsed into one entity here
 * because Discord identity is keyed purely on the immutable snowflake).
 */
@Document("discord_account_links")
public class DiscordAccountLink extends BaseMongoEntity {

    @Indexed(unique = true)
    private String discordUserId;

    @Indexed
    private String tacticlUserId;

    private String discordUsername;

    // One-time link token redeemed out-of-band to bind the snowflake to a tacticl account.
    @Indexed(unique = true, sparse = true)
    private String linkToken;

    private Instant linkTokenExpiresAt;

    private Instant linkedAt;

    /**
     * Creates a pending link: the snowflake is known but not yet bound to a tacticl user.
     * The caller supplies a one-time token the user redeems to complete the link.
     */
    public static DiscordAccountLink pending(String discordUserId, String discordUsername,
                                             String linkToken, int tokenTtlMinutes) {
        var link = new DiscordAccountLink();
        link.discordUserId = discordUserId;
        link.discordUsername = discordUsername;
        link.linkToken = linkToken;
        link.linkTokenExpiresAt = Instant.now().plusSeconds(tokenTtlMinutes * 60L);
        return link;
    }

    /**
     * Creates an already-linked record (e.g. when an admin pre-provisions a mapping).
     */
    public static DiscordAccountLink linked(String discordUserId, String discordUsername, String tacticlUserId) {
        var link = new DiscordAccountLink();
        link.discordUserId = discordUserId;
        link.discordUsername = discordUsername;
        link.tacticlUserId = tacticlUserId;
        link.linkedAt = Instant.now();
        return link;
    }

    public boolean isLinked() { return tacticlUserId != null && !tacticlUserId.isBlank(); }

    public boolean isTokenExpired() {
        return linkTokenExpiresAt != null && Instant.now().isAfter(linkTokenExpiresAt);
    }

    /** Completes the link by binding to a tacticl user and clearing the one-time token. */
    public void redeem(String tacticlUserId) {
        this.tacticlUserId = tacticlUserId;
        this.linkedAt = Instant.now();
        this.linkToken = null;
        this.linkTokenExpiresAt = null;
    }

    public String getDiscordUserId() { return discordUserId; }
    public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }

    public String getTacticlUserId() { return tacticlUserId; }
    public void setTacticlUserId(String tacticlUserId) { this.tacticlUserId = tacticlUserId; }

    public String getDiscordUsername() { return discordUsername; }
    public void setDiscordUsername(String discordUsername) { this.discordUsername = discordUsername; }

    public String getLinkToken() { return linkToken; }
    public void setLinkToken(String linkToken) { this.linkToken = linkToken; }

    public Instant getLinkTokenExpiresAt() { return linkTokenExpiresAt; }
    public void setLinkTokenExpiresAt(Instant linkTokenExpiresAt) { this.linkTokenExpiresAt = linkTokenExpiresAt; }

    public Instant getLinkedAt() { return linkedAt; }
    public void setLinkedAt(Instant linkedAt) { this.linkedAt = linkedAt; }
}

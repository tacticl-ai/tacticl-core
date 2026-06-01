package io.tacticl.data.pipeline.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Registry row binding an inbound transport key to the product + repo + playbook a pipeline run
 * should target. The pipeline front door resolves one of these per ingress request — tacticl is
 * ingress-only; the arbiter owns orchestration, and this entity is how a Discord guild/channel (or
 * Telegram chat, or WEB default) maps onto a {@code product}.
 *
 * <p>The {@code (channel, externalKey)} pair is unique. Lookups are narrowest-first
 * (e.g. {@code guildId:channelId} → {@code guildId} → the channel default flagged by
 * {@code isDefaultForChannel}).
 */
@Document("entry_points")
@CompoundIndexes({
    // Resolver's primary lookup + the uniqueness guarantee on the routing key.
    @CompoundIndex(name = "channel_externalKey",
                   def = "{'channel': 1, 'externalKey': 1}",
                   unique = true)
})
public class EntryPoint {

    @Id private String id;

    /** Transport surface this row routes for. Stored as the enum name. */
    private String channel;

    /**
     * Channel-scoped routing key. For Discord, a narrowest-first probe value such as
     * {@code "guildId:channelId"} or {@code "guildId"}. The channel-wide default row (where
     * {@code isDefaultForChannel = true}) typically uses a sentinel key.
     */
    private String externalKey;

    /** Product the resolved run targets — the primary discriminator on the arbiter wire. */
    private String productId;

    /** Repository the pipeline operates on. For the Discord test row this is a THROWAWAY test repo. */
    private String repoUrl;

    /** Playbook to submit (e.g. {@code "FULL_PDLC"}). */
    private String defaultPlaybook;

    /**
     * Template for the arbiter knowledge namespace, e.g. {@code "tacticl-{userId}"}. The dispatch
     * front door substitutes the resolved user id; {@code PdlcV2Service} currently owns its own
     * prefix, so this is carried for forward use by the ingress dispatcher.
     */
    private String knowledgeNamespaceTemplate;

    /**
     * Internal tacticl user ids permitted to fire EXPLICIT_TRIGGER / CHECKPOINT_DECISION / CANCEL_RUN
     * through this entry point. Empty set = no one is authorized (deny by default).
     */
    private Set<String> adminUserIds;

    /** Per-run cost ceiling in USD passed through to the arbiter. */
    private double costCeilingUsd;

    /**
     * Vault reference (path/key) for the GitHub token this entry point's runs use. Stored as a
     * reference, never the raw token — the dispatcher resolves it at submit time.
     */
    private String githubTokenRef;

    /** Marks this row as the catch-all for its channel when no narrower key matches. */
    @Indexed private boolean isDefaultForChannel;

    private boolean isActive;

    private Instant createdAt;
    private Instant updatedAt;

    protected EntryPoint() {}

    public static EntryPoint create(String channel, String externalKey, String productId,
                                    String repoUrl, String defaultPlaybook,
                                    String knowledgeNamespaceTemplate, Set<String> adminUserIds,
                                    double costCeilingUsd, String githubTokenRef,
                                    boolean isDefaultForChannel) {
        EntryPoint ep = new EntryPoint();
        ep.id = UUID.randomUUID().toString();
        ep.channel = channel;
        ep.externalKey = externalKey;
        ep.productId = productId;
        ep.repoUrl = repoUrl;
        ep.defaultPlaybook = defaultPlaybook;
        ep.knowledgeNamespaceTemplate = knowledgeNamespaceTemplate;
        ep.adminUserIds = adminUserIds == null ? new HashSet<>() : new HashSet<>(adminUserIds);
        ep.costCeilingUsd = costCeilingUsd;
        ep.githubTokenRef = githubTokenRef;
        ep.isDefaultForChannel = isDefaultForChannel;
        ep.isActive = true;
        ep.createdAt = Instant.now();
        ep.updatedAt = ep.createdAt;
        return ep;
    }

    public boolean isAdmin(String tacticlUserId) {
        return tacticlUserId != null && adminUserIds != null && adminUserIds.contains(tacticlUserId);
    }

    public String getId() { return id; }
    public String getChannel() { return channel; }
    public String getExternalKey() { return externalKey; }
    public String getProductId() { return productId; }
    public String getRepoUrl() { return repoUrl; }
    public String getDefaultPlaybook() { return defaultPlaybook; }
    public String getKnowledgeNamespaceTemplate() { return knowledgeNamespaceTemplate; }
    public Set<String> getAdminUserIds() { return adminUserIds; }
    public double getCostCeilingUsd() { return costCeilingUsd; }
    public String getGithubTokenRef() { return githubTokenRef; }
    public boolean isDefaultForChannel() { return isDefaultForChannel; }
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setChannel(String channel) { this.channel = channel; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public void setProductId(String productId) { this.productId = productId; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public void setDefaultPlaybook(String defaultPlaybook) { this.defaultPlaybook = defaultPlaybook; }
    public void setKnowledgeNamespaceTemplate(String t) { this.knowledgeNamespaceTemplate = t; }
    public void setAdminUserIds(Set<String> adminUserIds) { this.adminUserIds = adminUserIds; }
    public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }
    public void setGithubTokenRef(String githubTokenRef) { this.githubTokenRef = githubTokenRef; }
    public void setDefaultForChannel(boolean v) { this.isDefaultForChannel = v; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

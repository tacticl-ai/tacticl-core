package io.tacticl.business.pipeline.ingress;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized definition of the {@code entry_points} registry — the rows that map an inbound
 * transport key (a Discord guild/channel, a Telegram chat, the WEB or VOICE surface) onto the
 * product/repo/playbook a pipeline run should target. {@link EntryPointSeeder} upserts these on
 * boot so a new entry point (e.g. a Discord alert channel that routes to {@code BUG_FIX}) is a
 * config change, not a code change.
 *
 * <p>Example (one Discord alert channel + the voice surface):
 * <pre>
 * tacticl.ingress.entry-points[0].channel=DISCORD
 * tacticl.ingress.entry-points[0].external-key=&lt;guildId&gt;:&lt;sevChannelId&gt;
 * tacticl.ingress.entry-points[0].product-id=tacticl
 * tacticl.ingress.entry-points[0].repo-url=https://github.com/tacticl-ai/&lt;repo&gt;.git
 * tacticl.ingress.entry-points[0].playbook=BUG_FIX
 * tacticl.ingress.entry-points[0].admin-user-ids[0]=&lt;tacticlUserId&gt;
 * tacticl.ingress.entry-points[0].cost-ceiling-usd=10
 * tacticl.ingress.entry-points[1].channel=VOICE
 * tacticl.ingress.entry-points[1].external-key=voice-default
 * tacticl.ingress.entry-points[1].product-id=tacticl
 * tacticl.ingress.entry-points[1].repo-url=https://github.com/tacticl-ai/&lt;repo&gt;.git
 * tacticl.ingress.entry-points[1].playbook=FULL_PDLC
 * tacticl.ingress.entry-points[1].admin-user-ids[0]=&lt;tacticlUserId&gt;
 * tacticl.ingress.entry-points[1].default-for-channel=true
 * </pre>
 *
 * <p>An empty list (the default) seeds nothing, so the registry stays dormant until rows are
 * configured per environment.
 */
@Component
@ConfigurationProperties(prefix = "tacticl.ingress")
public class IngressRegistryProperties {

    private List<EntryPointDef> entryPoints = new ArrayList<>();

    public List<EntryPointDef> getEntryPoints() {
        return entryPoints;
    }

    public void setEntryPoints(List<EntryPointDef> entryPoints) {
        this.entryPoints = entryPoints == null ? new ArrayList<>() : entryPoints;
    }

    /**
     * One registry row, as supplied by configuration. JavaBean-shaped (no-arg ctor + setters) so
     * Spring Boot relaxed binding can populate {@code tacticl.ingress.entry-points[i].*}.
     */
    public static class EntryPointDef {

        /** Transport surface — must match a {@link ChannelType} name (DISCORD/TELEGRAM/WEB/VOICE). */
        private String channel;

        /** Channel-scoped routing key, e.g. {@code "guildId:channelId"} for Discord, {@code "voice-default"} for voice. */
        private String externalKey;

        private String productId = "tacticl";

        private String repoUrl;

        /** Playbook/pipeline name to submit. Alert channels typically use {@code BUG_FIX}. */
        private String playbook = "FULL_PDLC";

        private String knowledgeNamespaceTemplate = "tacticl-{userId}";

        /** Internal tacticl user ids permitted to trigger/approve through this entry point (deny by default). */
        private List<String> adminUserIds = new ArrayList<>();

        private double costCeilingUsd = 10.0;

        /** Vault reference for the GitHub token this entry point's runs use; blank ⇒ the global PAT is used. */
        private String githubTokenRef;

        /** Marks this row as the catch-all for its channel when no narrower key matches. */
        private boolean defaultForChannel;

        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }

        public String getExternalKey() { return externalKey; }
        public void setExternalKey(String externalKey) { this.externalKey = externalKey; }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getRepoUrl() { return repoUrl; }
        public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

        public String getPlaybook() { return playbook; }
        public void setPlaybook(String playbook) { this.playbook = playbook; }

        public String getKnowledgeNamespaceTemplate() { return knowledgeNamespaceTemplate; }
        public void setKnowledgeNamespaceTemplate(String t) { this.knowledgeNamespaceTemplate = t; }

        public List<String> getAdminUserIds() { return adminUserIds; }
        public void setAdminUserIds(List<String> adminUserIds) {
            this.adminUserIds = adminUserIds == null ? new ArrayList<>() : adminUserIds;
        }

        public double getCostCeilingUsd() { return costCeilingUsd; }
        public void setCostCeilingUsd(double costCeilingUsd) { this.costCeilingUsd = costCeilingUsd; }

        public String getGithubTokenRef() { return githubTokenRef; }
        public void setGithubTokenRef(String githubTokenRef) { this.githubTokenRef = githubTokenRef; }

        public boolean isDefaultForChannel() { return defaultForChannel; }
        public void setDefaultForChannel(boolean defaultForChannel) { this.defaultForChannel = defaultForChannel; }
    }
}

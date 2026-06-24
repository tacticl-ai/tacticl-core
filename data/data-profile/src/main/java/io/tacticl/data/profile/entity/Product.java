package io.tacticl.data.profile.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A user-registered grouping of repositories and channel bindings. A {@code Product} is a
 * tacticl-side concept only — it groups one or more GitHub repos together with the inbound
 * {@code EntryPoint} channel bindings (Discord/Telegram/WEB/VOICE) that route to them.
 *
 * <p>Note: the arbiter wire {@code productId} stays the literal {@code "tacticl"}; this entity
 * does <em>not</em> introduce a new arbiter product. It is purely a tacticl grouping over repos
 * and channel routing keys, surfaced in onboarding/settings.
 */
@Document("products")
public class Product extends BaseMongoEntity {

    @Indexed
    private String userId;

    private String name;

    /** Canonical GitHub repo URLs grouped under this product. */
    private List<String> repos = new ArrayList<>();

    /**
     * The user's chosen default repo (canonical GitHub repo URL). Backs {@code isDefault} in the
     * repos-in-scope listing. Nullable.
     */
    private String defaultRepoUrl;

    /** Channel bindings (Discord/Telegram/WEB/VOICE) that route inbound work to this product. */
    private List<ChannelBinding> channels = new ArrayList<>();

    // createdAt / updatedAt are inherited from BaseMongoEntity (@CreatedDate / @LastModifiedDate).

    public static Product create(String userId, String name) {
        Product p = new Product();
        p.userId = userId;
        p.name = name;
        p.repos = new ArrayList<>();
        p.channels = new ArrayList<>();
        return p;
    }

    /**
     * An inbound channel binding: which transport + external routing key maps onto this product.
     */
    public static class ChannelBinding {

        /** Transport surface — a {@code ChannelType} name (DISCORD/TELEGRAM/WEB/VOICE). */
        private String channelType;

        /** Channel-scoped routing key (e.g. a Discord {@code guildId:channelId}). */
        private String externalKey;

        /** Human-friendly label for the binding. */
        private String label;

        public ChannelBinding() {}

        public ChannelBinding(String channelType, String externalKey, String label) {
            this.channelType = channelType;
            this.externalKey = externalKey;
            this.label = label;
        }

        public String getChannelType() { return channelType; }
        public void setChannelType(String channelType) { this.channelType = channelType; }
        public String getExternalKey() { return externalKey; }
        public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getRepos() { return repos; }
    public void setRepos(List<String> repos) { this.repos = repos; }
    public String getDefaultRepoUrl() { return defaultRepoUrl; }
    public void setDefaultRepoUrl(String defaultRepoUrl) { this.defaultRepoUrl = defaultRepoUrl; }
    public List<ChannelBinding> getChannels() { return channels; }
    public void setChannels(List<ChannelBinding> channels) { this.channels = channels; }
}

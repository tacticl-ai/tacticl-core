package io.tacticl.data.profile.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import java.time.Instant;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A GitHub repository a user has worked in via the analyst — the per-user repo memory.
 *
 * <p>Auto-registered whenever a build attaches or provisions a repo (see
 * {@code UserRepoService.registerRepoUse}), then surfaced back to the analyst as
 * grounding so future conversations can offer "use one of your repos, or a new one?"
 * once requirements are understood. Keyed by {@code cidadelUserId} (== the ingress
 * user id == {@link UserProfile#getCidadelUserId()}); a unique
 * {@code (cidadelUserId, repoUrl)} index makes registration an idempotent upsert.
 */
@Document("user_repos")
@CompoundIndex(name = "user_repo_unique", def = "{'cidadelUserId': 1, 'repoUrl': 1}", unique = true)
public class UserRepo extends BaseMongoEntity {

    @Indexed
    private String cidadelUserId;

    /** GitHub owner — a user login ("cuztomizer") or org ("tacticl-ai"). */
    private String owner;

    /** Repository name, e.g. "health-endpoint". */
    private String name;

    /** Canonical https URL (no trailing .git/slash), the dedup key. */
    private String repoUrl;

    /** Owner type (USER vs ORG); UNKNOWN until resolved. */
    private RepoKind kind = RepoKind.UNKNOWN;

    /** How it first entered the registry. */
    private RepoSource source = RepoSource.ATTACHED;

    private Instant firstSeenAt;
    private Instant lastUsedAt;
    private int useCount;

    public static UserRepo create(String cidadelUserId, String owner, String name, String repoUrl,
                                  RepoKind kind, RepoSource source, Instant now) {
        var r = new UserRepo();
        r.cidadelUserId = cidadelUserId;
        r.owner = owner;
        r.name = name;
        r.repoUrl = repoUrl;
        r.kind = kind == null ? RepoKind.UNKNOWN : kind;
        r.source = source == null ? RepoSource.ATTACHED : source;
        r.firstSeenAt = now;
        r.lastUsedAt = now;
        r.useCount = 1;
        return r;
    }

    /** Record another use: bump the counter and recency. */
    public void recordUse(Instant now) {
        this.useCount += 1;
        this.lastUsedAt = now;
    }

    public String getCidadelUserId() { return cidadelUserId; }
    public String getOwner() { return owner; }
    public String getName() { return name; }
    public String getRepoUrl() { return repoUrl; }
    public RepoKind getKind() { return kind; }
    public void setKind(RepoKind kind) { this.kind = kind; }
    public RepoSource getSource() { return source; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public int getUseCount() { return useCount; }
}

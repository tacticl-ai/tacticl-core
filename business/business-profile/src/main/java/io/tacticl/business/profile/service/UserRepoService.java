package io.tacticl.business.profile.service;

import io.tacticl.data.profile.entity.RepoKind;
import io.tacticl.data.profile.entity.RepoRef;
import io.tacticl.data.profile.entity.RepoSource;
import io.tacticl.data.profile.entity.UserRepo;
import io.tacticl.data.profile.repository.UserRepoRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Per-user repo memory: records which GitHub repos a user works in (auto, on use) and
 * serves them back as grounding so the analyst can offer "use one of yours, or a new
 * one?" once requirements are understood.
 *
 * <p>Registration is an idempotent upsert keyed by the unique {@code (cidadelUserId,
 * repoUrl)} index — first use inserts, repeat use bumps {@code useCount}/{@code
 * lastUsedAt}. Callers register best-effort (a failure must never block a build).
 */
@Service
public class UserRepoService {

    private static final Logger log = LoggerFactory.getLogger(UserRepoService.class);

    /** Cap on how many repos we surface as grounding (bounds the prompt). */
    public static final int DEFAULT_GROUNDING_LIMIT = 8;

    private final UserRepoRepository repository;

    public UserRepoService(UserRepoRepository repository) {
        this.repository = repository;
    }

    /**
     * Record that {@code userId} used {@code repoUrl} for a build. Upserts: bumps an
     * existing row's use count/recency, or inserts a new one. No-op (logged) if the URL
     * isn't a parseable GitHub repo. Never throws to the caller's critical path.
     */
    public void registerRepoUse(String userId, String repoUrl, RepoSource source) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        Optional<RepoRef> parsed = RepoRef.parse(repoUrl);
        if (parsed.isEmpty()) {
            log.debug("Skipping repo registration — not a GitHub repo URL: {}", repoUrl);
            return;
        }
        RepoRef ref = parsed.get();
        Instant now = Instant.now();
        repository.findByCidadelUserIdAndRepoUrlAndIsActiveTrue(userId, ref.canonicalUrl())
            .ifPresentOrElse(
                existing -> {
                    existing.recordUse(now);
                    repository.save(existing);
                    log.info("Repo memory bumped user={} repo={} useCount={}",
                             userId, ref.canonicalUrl(), existing.getUseCount());
                },
                () -> {
                    RepoKind kind = inferKind(ref.owner());
                    repository.save(UserRepo.create(userId, ref.owner(), ref.name(),
                                                    ref.canonicalUrl(), kind, source, now));
                    log.info("Repo memory added user={} repo={} kind={} source={}",
                             userId, ref.canonicalUrl(), kind, source);
                });
    }

    /**
     * Explicitly attach (grant) a repo to {@code userId} from the Settings UI. Idempotent
     * upsert keyed by the canonical {@code (cidadelUserId, repoUrl)} — re-attaching an
     * existing repo bumps its use/recency rather than duplicating. Unlike
     * {@link #registerRepoUse}, this returns the persisted row and surfaces a parse failure
     * (so the controller can answer 400) since it is a deliberate user action.
     *
     * @throws IllegalArgumentException if {@code repoUrl} is not a parseable GitHub repo URL
     */
    public UserRepo attach(String userId, String repoUrl) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        RepoRef ref = RepoRef.parse(repoUrl)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Not a GitHub repository URL: " + repoUrl));
        Instant now = Instant.now();
        return repository.findByCidadelUserIdAndRepoUrlAndIsActiveTrue(userId, ref.canonicalUrl())
                .map(existing -> {
                    existing.recordUse(now);
                    UserRepo saved = repository.save(existing);
                    log.info("Repo memory re-attached user={} repo={} useCount={}",
                             userId, ref.canonicalUrl(), saved.getUseCount());
                    return saved;
                })
                .orElseGet(() -> {
                    RepoKind kind = inferKind(ref.owner());
                    UserRepo saved = repository.save(UserRepo.create(userId, ref.owner(), ref.name(),
                            ref.canonicalUrl(), kind, RepoSource.ATTACHED, now));
                    log.info("Repo memory attached user={} repo={} kind={}",
                             userId, ref.canonicalUrl(), kind);
                    return saved;
                });
    }

    /**
     * Revoke (soft-delete) a repo a user has attached. Ownership-scoped: only the owning
     * user's repo is touched. Returns {@code true} if a matching active repo was found and
     * deactivated, {@code false} otherwise (so the controller can answer 404).
     */
    public boolean revoke(String userId, String repoId) {
        if (userId == null || userId.isBlank() || repoId == null || repoId.isBlank()) {
            return false;
        }
        return repository.findByIdAndCidadelUserId(repoId, userId)
                .filter(UserRepo::isActive)
                .map(repo -> {
                    repo.delete();
                    repository.save(repo);
                    log.info("Repo memory revoked user={} repo={}", userId, repo.getRepoUrl());
                    return true;
                })
                .orElse(false);
    }

    /** A user's repos, most-recently-used first (uncapped). */
    public List<UserRepo> list(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return repository.findByCidadelUserIdAndIsActiveTrueOrderByLastUsedAtDesc(userId);
    }

    /** A user's repos, most-recently-used first, capped to {@code limit}. */
    public List<UserRepo> recentRepos(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<UserRepo> all = repository.findByCidadelUserIdAndIsActiveTrueOrderByLastUsedAtDesc(userId);
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    /**
     * Cheap owner-type inference without a GitHub round-trip: known org owners → ORG,
     * else UNKNOWN (the persona just offers the repo by name; exact kind is backfillable).
     */
    private static RepoKind inferKind(String owner) {
        if (owner == null) {
            return RepoKind.UNKNOWN;
        }
        return switch (owner.toLowerCase()) {
            case "tacticl-ai", "cidadel-platform", "strategiz-io" -> RepoKind.ORG;
            default -> RepoKind.UNKNOWN;
        };
    }
}
